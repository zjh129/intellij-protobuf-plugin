# 编译器系统

## 问题背景

protobuf 的 IDE 插件需要的不仅仅是解析。像 gRPC 请求测试、跨文件类型解析和选项验证等功能都需要 **descriptor 元数据**——与 `protoc` 生成的相同 `FileDescriptorProto` 结构。没有 descriptor，插件无法知道字段类型跨导入解析到哪个特定消息，或者如何将消息编组为 JSON 以进行 gRPC 测试调用。

问题在于：这些 descriptor 从哪来？显而易见的答案——调用 `protoc`——有严重的可用性问题。用户需要安装、配置 `protoc` 并使其匹配项目中的 proto 版本。路径错误或版本不匹配时插件会静默失败。构建工具集成在不同生态系统中差异很大。

正如 `Protoc.kt` 中的注释所说："可能不包含官方 protoc 编译器的所有功能。"插件有意用可移植性换取完整性。

## 设计决策

### 决策 1：进程内编译

**选择：** 构建一个纯 Kotlin 编译器，直接遍历 IntelliJ PSI 树并产生 `FileDescriptorProto` 对象。无需外部进程、无子进程、无 `protoc` 二进制文件。

**为什么？**

- **零配置。** 插件安装后立即工作——无需 PATH 设置、无 SDK 配置、无需构建工具集成。
- **PSI 集成。** 编译器直接从已解析的 PSI 树读取，避免双重解析。结果通过 `CachedValuesManager` 缓存，并在源文件更改时自动失效（以 `PsiModificationTracker` 为键）。
- **优雅降级。** 编译器将每个元素的编译包装在 try-catch 中，静默跳过格式不正确的项。一个消息中的语法错误不会阻止文件其余部分的编译。这对 IDE 至关重要，因为文件*总是*处于部分编辑状态。

**接受的权衡：**

- 自定义选项的扩展值仅部分支持
- Edition 特定语义未完全建模
- 代码生成超出范围——此编译器产生元数据，而非 Java/Go/C++ 代码

**主要消费者：** gRPC 模块中的 `ProtoFileReflection` 调用 `Protoc.compileFiles()` 获取 descriptor，用 sisyphus 库的 `DynamicFileSupport` 注册它们，并用它们在 IDE 内 marshal/unmarshal 消息以进行 gRPC 调用。

### 决策 2：基于插件的架构

**选择：** 编译分布在 10 个 `ProtobufCompilerPlugin` 实现中，每个负责一种元素类型。它们注册为 IntelliJ 扩展点并动态加载。

内置插件有：

| 插件 | 编译 | 关键逻辑 |
|------|------|----------|
| `FileCompiler` | 文件 → `FileDescriptorProto` | 包、语法、依赖提取 |
| `MessageCompiler` | 消息 → `DescriptorProto` | 递归嵌套、嵌套类型路由 |
| `MessageFieldCompiler` | 字段 → `FieldDescriptorProto` | 类型解析（内置 vs 自定义）、标签推断、proto3 optional |
| `MessageOneofCompiler` | Oneof 组 | oneof 容器内的字段分组 |
| `MessageMapEntryCompiler` | Map 字段 → 合成 `Entry` 类型 | 生成隐藏的 key/value 消息类型 |
| `MessageMapFieldCompiler` | Map 字段包装器 | 将 map 字段链接到其合成条目 |
| `EnumCompiler` | 枚举 → `EnumDescriptorProto` | 名称和值提取 |
| `EnumValueCompiler` | 枚举值 | 各个常量定义 |
| `ServiceCompiler` | 服务 → `ServiceDescriptorProto` | RPC 方法路由 |
| `ServiceMethodCompiler` | RPC 方法 → `MethodDescriptorProto` | 输入/输出类型解析、流标志 |

**为什么不是单一编译器？**

- **隔离。** `EnumValueCompiler` 中的失败不会使 `MessageCompiler` 崩溃。每个插件捕获自己的异常。
- **可扩展性。** `protocPlugin` 扩展点意味着第三方 IntelliJ 插件可以注入自定义编译步骤（如用于自定义选项或 proto 扩展）而不修改核心。
- **清晰性。** 每个插件的范围从其名称和类型参数即可明显看出。`MessageFieldCompiler` 只能看到 `MessageFieldCompilingState`。

### 决策 3：状态机模型

**选择：** 每个被编译的 PSI 节点变成类型化状态对象层次结构中的状态。`CompileContext` 将状态分派给插件，插件为嵌套元素创建子状态。

状态层次结构镜像 proto 结构：

```
FileCompilingState
├── MessageCompilingState
│   ├── MessageFieldCompilingState
│   ├── MessageOneofCompilingState
│   │   └── MessageFieldCompilingState
│   └── MessageMapEntryCompilingState
│       └── MessageFieldCompilingState
├── EnumCompilingState
│   └── EnumValueCompilingState
└── ServiceCompilingState
    └── ServiceMethodCompilingState
```

每个状态携带两样东西：
- `target()` — 被填充的可变 protobuf descriptor
- `element()` — 被编译的源 PSI 元素

子状态还携带 `parent()`，允许字段编译器在需要时访问其包含消息的 descriptor。

**为什么用状态而非直接递归？** 状态将*遍历*与*转换*解耦。`CompileContext` 处理遍历——决定哪些插件看到哪些状态。插件处理转换——填充 descriptor。这意味着新插件可以参与编译而不需要理解或修改遍历逻辑。

**为什么用类型化状态？** 泛型签名 `ProtobufCompilingState<TDesc: MutableMessage, TPsi: ProtobufElement>` 意味着 `MessageCompiler` 物理上无法接收 `EnumCompilingState`。类型错误在编译时捕获，而非运行时。

## 整体协作

```
.proto 文件 → PSI 树（解析器）
                  ↓
              Protoc.compileFiles()
                  ↓
              基于栈的文件遍历（处理导入、检测循环）
                  ↓
              每个文件创建 FileCompilingState
                  ↓
              CompileContext.advance(state) 分派给所有注册的插件
                  ↓
              插件创建子状态 → 递归分派
                  ↓
              FileDescriptorSet（填充的 descriptor 集合）
                  ↓
              ProtoFileReflection 注册 descriptor 以便 gRPC 测试
```

导入解析是基于栈的并带循环检测：每个编译文件的导入被推入栈，已编译文件（按导入路径追踪）被跳过。

## 核心洞察

这个编译器存在于完整性-可用性谱系的特定点上。完整的 `protoc` 重实现是一个代价高昂的工作，回报递减——需要 descriptor 的 IDE 功能（gRPC 测试、类型解析、选项验证）需要*结构*，而非*代码生成语义*。通过构建足够准确的 descriptor 编译器，并使其对部分/损坏的输入具有弹性，插件提供了"开箱即用"的体验，而这对于外部工具依赖是不可能的。
