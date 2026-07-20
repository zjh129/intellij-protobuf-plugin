# Stub 索引

## 问题背景

任何有实际规模的 protobuf 项目都有成百上千个 `.proto` 文件和深度导入链。Google 的已知类型仅 ~30 个文件；一个大型 API 表面（googleapis、gRPC 定义）轻松达到数千个。每个 IDE 功能——跳转符号、补全、查找用法、引用解析——都需要回答"所有这些文件中存在哪些符号？"

朴素答案是：解析每个文件，遍历每个 AST。但解析是昂贵的。完整解析构建完整的语法树——注释、空格、选项体、字段编号——大多数在只需要知道"每个消息和服务叫什么完全限定名"时无关紧要。每击键都这样做会让 IDE 冻结。

IntelliJ 的 stub 系统通过让我们**预提取每个符号的身份并序列化到磁盘**来解决这个问题。在后续 IDE 启动时，平台读取轻量级二进制 stub 而非重新解析源文件。Stub 是一切基于索引查找的基础——它们使解析 `google.protobuf.Timestamp` 而不在每次引用时解析 `timestamp.proto` 成为可能。

Protobuf 使这尤其关键，因为：
- **深度导入图**：单个文件可以通过 `import` 和 `import public` 传递导入数百个其他文件
- **跨语言代码生成**：Java、Go 和 Kotlin 各自从同一个 `.proto` 文件派生不同的生成名称，所以索引必须同时支持多种命名方案
- **大型已知类型集**：标准库（`google/protobuf/`、`google/api/`）始终在作用域内，没有缓存会不断重新解析

## 设计决策

### 决策 1：什么需要 Stub（什么不需要）

**需要 Stub**（11 种元素类型）：`Message`、`Enum`、`EnumValue`、`Service`、`Rpc`、`Field`、`MapField`、`Oneof`、`Group`、`Extend`、`PackageName`。

**不需要 Stub**：字段类型、字段编号、选项值、注释、默认值、方法请求/响应类型。

**为什么是这个边界？** 需要 stub 的元素正是构成**命名空间树**的元素集——用户导航时所在的嵌套作用域层级（输入 `package.Message.NestedEnum.VALUE`）。Stub 捕获*身份*（东西叫什么）和*结构*（什么嵌套在什么里面）。它们有意跳过*内容*（字段引用什么类型、选项持有什么值），因为内容需要**跨文件解析**，而这正是 stub 存在的目的。

权衡是具体的：每个 stub 存储一个小的 `Array<String>`（通常 1-2 个条目：名称，可选的 AIP 资源类型）。添加字段类型信息意味着存储可能需要解析才能有用的限定类型引用——这就违背了轻量级索引的目的。通过保持 stub 仅限身份，单个二进制读取就给了我们文件的完整符号表。

### 决策 2：三种核心索引类型

插件注册了三个 `StringStubIndexExtension`，每个针对不同的访问模式优化：

| 索引 | 键 | 优化目标 |
|------|-----|----------|
| **ShortNameIndex** | 简单名称（`"Timestamp"`） | 补全——"用户输入了 `Time`，显示所有匹配" |
| **QualifiedNameIndex** | 完整路径（`"google.protobuf.Timestamp"`） | 引用解析——"解析这个确切符号" |
| **ResourceTypeIndex** | AIP 资源类型（`"type.googleapis.com/..."`） | AIP 合规性——"找到此资源的 message" |

**为什么不是一个？** 因为访问模式根本不同：

- **补全**需要对整个项目中简单名称的模糊匹配。限定名索引要求用户在搜索前知道完整路径——恰恰与补全应该提供的功能相反。
- **解析**需要通过限定名精确查找。在简单名称索引中搜索 `"Timestamp"` 会返回每个包中名为 `Timestamp` 的消息，需要过滤步骤击败 O(1) 查找。
- **资源类型**是与 protobuf 自有命名正交的 Google AIP 概念。消息的资源类型（`google.api.resource` 注解）不遵循包层级，所以需要自己的键空间。只有 `ProtobufMessageStub` 填充此索引。

所有三个索引在 `ProtobufStubTypeBase.indexStub()` 期间在单次序列化遍历中填充，所以维护多个索引的成本可以忽略不计。

### 决策 3：外部数据系统

每个 stub 带有一个 `Map<String, String>` 的**外部数据**，在 stub 创建时由 `ProtobufStubExternalProvider` 实现填充。

驱动问题：protobuf 文件包含**语言特定选项**，控制代码生成——`java_package`、`java_outer_classname`、`go_package`、`json_name`。插件需要这些数据从生成的 Java/Go/Kotlin 代码导航回原始 `.proto` 定义，但不应该将其烘焙到核心 stub 格式中，因为：

1. **并非所有用户都需要所有语言。** Go 团队不应为 Java 类名支付索引开销。
2. **新语言会出现。** 将语言支持硬编码到 stub 模式中需要每次新语言模块都进行 stub 版本升级（缓存失效）。
3. **选项是开放的。** 自定义 protobuf 选项（如 sisyphus 框架注解）是项目特定的。

解决方案拆分为两个扩展点：

- **`stubExternalProvider`**：在 stub 创建期间提取选项值并存储在外部数据映射中。例如：`FileJavaOptionsProvider` 读取文件选项中的 `java_package`、`java_outer_classname` 和 `java_multiple_files`，以及字段选项中的 `json_name`。这些数据被序列化到二进制 stub 中，无需重新解析即可使用。

- **`indexProvider`**：在索引遍历期间从 stub 数据构建自定义索引条目。例如：`JavaIndexProvider` 计算生成的 gRPC 存根类名（`*ImplBase`、`*BlockingStub`、`*FutureStub`、`*CoroutineStub`）并在 `JavaNameIndex` 下索引它们。`GoIndexProvider` 对 Go 客户端/服务器接口名做同样的事情。`ServiceMethodIndexProvider` 索引 gRPC 方法路径（`package.Service/Method`）。

这种两阶段设计意味着核心 stub 格式（`Array<String>` + `Map<String, String>`）是稳定的，而语言特定的索引逻辑位于可选模块（`protobuf-java`、`protobuf-go`、`protobuf-sisyphus`）中，可以独立演进。

### 决策 4：携带作用域的 Stub 用于限定名计算

一个独特的设计选择：**限定名从 stub 树本身计算**，而非原样存储。

`ProtobufDefinitionStub.qualifiedName()` 通过 `parentOfType<ProtobufScopeStub>()?.scope()?.append(name)` 遍历父 stub 链。每个定义作用域的 stub（`ProtobufFileStubImpl`、`ProtobufMessageStub`、`ProtobufEnumStub`、`ProtobufServiceStub`）实现 `ProtobufScopeStub.scope()` 返回自己的限定名，形成递归链：文件 stub 提供包前缀，消息 stub 追加自己的名称，叶定义（字段、枚举值、RPC）追加自己的名称。

**为什么计算而非存储？** 因为存储每个嵌套元素的限定名会在每个文件中重复包路径数百次。一个包含 `google.cloud.bigquery.v2` 包的文件有 50 个消息和总共 500 个字段，会将该前缀存储 550 次。从树结构计算只需存储包名恰好一次（在文件 stub 中），每个元素只存储自己的短名称。

这也意味着 stub 树精确镜像 protobuf 作用域层级——这就是为什么 `PackageName` 虽然不是通常意义上的"定义"，但也需要 stub。它为整个文件建立了根作用域。

### 决策 5：单一全局 Stub 版本

文件 stub 版本是单一整数（`getStubVersion() = 1`），而非每元素或每提供者的复合版本。为*任何*元素类型更改 stub 格式都需要递增此数字，这会使整个 stub 缓存失效。

这是深思熟虑的简单性而非细粒度的权衡。Protobuf 的 schema 语言演进缓慢——需要 stub 的构造集（message、enum、service 等）多年来没有变化。每元素版本控制方案为几乎永远不会发生的迁移增加了复杂性。外部数据映射（`Map<String, String>`）通过无模式设计吸收了大部分可变性——新键可以由提供者添加而无需更改二进制格式。

## 整体协作

```
                        解析                    序列化
  .proto 文件  ───────────────►  PSI 树  ─────────────────►  二进制 Stub
                                    │                              │
                              stubData()                    readStringArray()
                           stubExternalData()                  readMap()
                                    │                              │
                                    ▼                              ▼
                             Stub 树 ◄──────────────────── Stub 树
                                    │         反序列化
                                    │
                              indexStub()
                              ┌─────┼──────────────┐
                              ▼     ▼              ▼
                         ShortName  QualifiedName  ProtobufIndexProvider
                          索引      索引           ├─ JavaIndexProvider
                                       │           ├─ GoIndexProvider
                                       │           └─ ServiceMethodIndexProvider
                                       ▼
                              ResourceTypeIndex
                            （仅消息）
```

查询时：
- 补全 → ShortNameIndex.key → 模糊匹配 → stub PSI
- 跳转符号 → QualifiedNameIndex.key → 精确查找 → stub PSI
- Java 导航 → JavaNameIndex.key → gRPC stubs → stub PSI
- AIP 资源 → ResourceTypeIndex.key → 注解 → stub PSI

PSI mixin 中的双构造器模式（如 `ProtobufMessageDefinitionMixin`）使这对消费者透明：相同的 `ProtobufMessageDefinition` 接口无论是由解析 AST 还是反序列化 stub 备份都能工作。调用 `element.name()` 或 `element.qualifiedName()` 的代码不知道也不关心是哪条路径创建的元素。

## 核心洞察

Stub 系统的力量来自严格的分离：**身份是廉价的，内容是昂贵的**。通过仅提取 protobuf 定义的名称和嵌套结构——并将其他一切（类型、选项、体）推迟到按需解析——插件可以以几乎为零的运行时成本维护任意大型 protobuf 项目的完整符号表。扩展点系统（`stubExternalProvider` + `indexProvider`）然后在其上分层语言特定关注点，而不将它们耦合到核心模式，让 Java、Go 和自定义框架支持在共享相同底层 stub 基础设施的同时独立演进。
