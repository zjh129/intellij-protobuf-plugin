# 插件概览

## 这是什么？

IntelliJ Protobuf Language Plugin 是一个功能完整的 [Protocol Buffers](https://protobuf.dev/) 语言插件，支持所有 JetBrains IDE。它提供了比 JetBrains 官方 Protobuf 插件更完整的实现：深度语义理解、跨文件符号解析、多语言集成，以及对 Editions 等现代 protobuf 特性的支持。

## 它能做什么？

本插件将你的 IDE 变成一个真正"理解" protobuf 的开发环境：

- **理解 schema** — 不仅仅是语法高亮，而是完整的语义分析。它知道字段类型引用了另一个文件中定义的消息，知道某个导入是否未使用，知道字段编号是否冲突。
- **像代码一样导航** — 跨文件和跨导入的跳转定义、查找所有用法、跨文件更新的重命名。
- **尽早捕获错误** — 实时错误高亮：命名约定违规、类型不匹配、proto2/proto3 规则违反、AIP 规范合规性。
- **配合你的技术栈** — Java、Go、Kotlin（Sisyphus）、gRPC 的语言特定集成。插件理解 proto 定义如何映射到生成的代码。
- **支持文本格式** — 完整的 `.textproto` 文件编辑器支持，带基于 schema 的补全和验证。

## 核心概念

### Proto 文件作为一等公民

本插件像 IntelliJ 对待 Java 或 Kotlin 一样对待 `.proto` 文件——构建完整的 PSI（程序结构接口）树，维护 stub 索引以实现快速查找，跨整个项目范围（包括库和 SDK）解析引用。

### 多源符号解析

Proto 文件可能存在于多个位置：项目源码、library JAR、protobuf SDK、反编译的 descriptor。插件的根提供者系统透明地聚合所有这些来源，使导入"正常工作"，无论 proto 文件位于何处。

### 集成模块

语言特定功能（Java 类导航、Go 反编译、gRPC 端点发现）实现为可选模块，仅在相关 IDE 插件存在时激活。这保持了核心插件的轻量，同时实现了深度集成。

### Protobuf Editions

插件支持较新的 [Protobuf Editions](https://protobuf.dev/editions/overview/) 系统（`edition = "2023"`），它用可配置的的特性集取代了 `syntax = "proto2"` / `syntax = "proto3"` 模型。

## 下一步

- **想要贡献代码？** → [快速开始](getting-started-zh.md)
- **想了解架构？** → [架构](architecture-zh.md)
- **想深入某个子系统？** → [设计文档](design/)
- **想扩展插件？** → [扩展点](extension-points-zh.md)
- **想看模块特定文档？** → [模块文档](modules/)
