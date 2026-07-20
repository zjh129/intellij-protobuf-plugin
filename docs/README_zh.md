# 文档

欢迎使用 IntelliJ Protobuf 插件文档。在这里找到你需要的内容。

## 新手入门

| 文档 | 你将学到 |
|------|---------|
| [概览](overview-zh.md) | 插件的功能和设计初衷 |
| [架构](architecture-zh.md) | 各模块如何协作（高层视图） |

## 贡献者

| 文档 | 你将学到 |
|------|---------|
| [快速开始](getting-started-zh.md) | 构建、运行、测试插件 |
| [贡献指南](contributing-zh.md) | 代码风格、功能开发和模块扩展 |

## 设计文档

深入了解每个子系统设计的*原因*。修改特定领域时请阅读这些文档。

| 文档 | 核心问题 |
|------|---------|
| [PSI 与 Mixin 模式](design/psi-and-mixin-zh.md) | 为什么用 mixin 而非继承来实现 PSI 行为？ |
| [符号解析](design/symbol-resolution-zh.md) | 为什么用两阶段解析加作用域层级？ |
| [Stub 索引](design/stub-indexing-zh.md) | 为什么需要 stub，索引设计有哪些权衡？ |
| [注解系统](design/annotation-system-zh.md) | 为什么用版本专用注解器而非统一验证器？ |
| [代码补全](design/code-completion-zh.md) | 为什么用每个上下文一个 Provider 的模型？ |
| [编译器系统](design/compiler-system-zh.md) | 为什么构建进程内编译器而非调用 protoc？ |
| [ProtoText 支持](design/prototext-zh.md) | 为什么要单独实现一种语言？ |
| [Protobuf Editions](design/editions-zh.md) | 为什么要用特性集模型而非语法版本号？ |

## 扩展与集成

| 文档 | 你将学到 |
|------|---------|
| [扩展点](extension-points-zh.md) | 插件的公共扩展 API |
| [Java 模块](modules/java-zh.md) | Java/Kotlin 代码生成集成 |
| [Go 模块](modules/go-zh.md) | Go 支持与 descriptor 反编译 |
| [gRPC 模块](modules/grpc-zh.md) | gRPC 端点与请求执行 |
| [AIP 模块](modules/aip-zh.md) | Google API 设计规范支持 |
| [Sisyphus 模块](modules/sisyphus-zh.md) | Sisyphus 框架集成 |
