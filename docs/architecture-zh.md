# 架构

插件结构的高层概览。各子系统的*设计原因*见[设计文档](design/)。

## 层级图

```
┌─────────────────────────────────────────────┐
│             UI 与设置                         │
│   设置、机构视图、图标、动作                     │
├─────────────────────────────────────────────┤
│           语言支持                            │
│   补全、格式化、注解、引用、快速修复、查找用法    │
├─────────────────────────────────────────────┤
│         PSI（程序结构接口）                    │
│   元素、Mixin、作用域、功能                     │
├─────────────────────────────────────────────┤
│           索引与 Stubs                        │
│   Stub 索引、根提供者、缓存                     │
├─────────────────────────────────────────────┤
│           解析与词法分析                       │
│   词法分析器（FLEX）、解析器（BNF）、语言定义     │
├─────────────────────────────────────────────┤
│       集成模块（可选）                         │
│   Java、Go、Sisyphus、gRPC、AIP              │
└─────────────────────────────────────────────┘
```

每层只依赖其下方的层。集成模块横向放置，通过扩展点挂接到特定层。

## 解析与词法分析

将 `.proto` 源文本转换为结构化 AST。语法文件位于 `src/main/grammar/`：`protobuf.bnf` 用于 proto2/proto3/editions，`prototext.bnf` 用于文本格式。Grammar-Kit 和 JFlex 生成解析器和词法分析器。

→ 设计文档：[ProtoText](design/prototext-zh.md)、[Editions](design/editions-zh.md)

## PSI 层

解析后的 AST 成为一棵类型化 PSI 元素树。行为通过 *mixin*（而非继承）注入，使生成的类保持不变。*功能接口* 定义横切能力，如命名、作用域和引用解析。

→ 设计文档：[PSI 与 Mixin 模式](design/psi-and-mixin-zh.md)

## 索引与 Stubs

Stub 是 PSI 元素的轻量级序列化快照，使符号查找无需解析每个文件即可快速进行。三个索引（`ShortName`、`QualifiedName`、`ResourceType`）覆盖常见查找模式。根提供者从项目源码、库、SDK 和反编译的 descriptor 聚合 proto 文件。

→ 设计文档：[Stub 索引](design/stub-indexing-zh.md)

## 语言支持

用户交互的 IDE 功能：补全、注解、格式化、引用、快速修复。各项功能遵循 IntelliJ 的扩展模型，但针对 protobuf 的需求做了特定设计。

→ 设计文档：[代码补全](design/code-completion-zh.md)、[注解系统](design/annotation-system-zh.md)、[符号解析](design/symbol-resolution-zh.md)

## 内部编译器

插件包含一个进程内 protobuf 编译器，从 PSI 生成 `FileDescriptorProto`——无需调用外部 `protoc`。这使得需要 descriptor 信息（如选项验证）的 IDE 功能无需构建工具配置即可工作。

→ 设计文档：[编译器系统](design/compiler-system-zh.md)

## 集成模块

可选模块在相关 IDE 依赖存在时激活（如仅在 `com.intellij.modules.java` 可用时加载 Java 模块）。每个模块通过扩展点扩展核心，添加语言特定的导航、反编译或代码生成功能。

→ 文档：[Java](modules/java-zh.md)、[Go](modules/go-zh.md)、[gRPC](modules/grpc-zh.md)、[AIP](modules/aip-zh.md)、[Sisyphus](modules/sisyphus-zh.md)

## 扩展点

五个扩展点允许第三方插件扩展核心功能：

| 扩展点 | 功能 |
|--------|------|
| `rootProvider` | 添加自定义 proto 文件搜索位置 |
| `symbolReferenceProvider` | 添加自定义符号解析策略 |
| `indexProvider` | 向 stub 索引贡献额外数据 |
| `stubExternalProvider` | 向 stub 附加外部元数据 |
| `protocPlugin` | 扩展内部编译器 |

→ 文档：[扩展点](extension-points-zh.md)
