<div align="center">

# ![Logo](resources/logo.svg) IntelliJ Protobuf Language Plugin

**JetBrains IDE 全功能 Protocol Buffers 插件**

[![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/v/16422?label=Marketplace)](https://plugins.jetbrains.com/plugin/16422-protobuf)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/16422?label=Downloads)](https://plugins.jetbrains.com/plugin/16422-protobuf)
[![Build](https://github.com/devkanro/intellij-protobuf-plugin/workflows/Build/badge.svg)](https://github.com/devkanro/intellij-protobuf-plugin/actions)

*语义分析 · 跨文件导航 · 多语言集成 · Protobuf Editions*

</div>

---

<!-- Plugin description -->

## 功能亮点

本插件让你的 JetBrains IDE 成为一个真正理解 Protobuf 语义的开发环境——不仅仅是语法高亮，而是对 schema 的深度理解。

> [!WARNING]
> 与 JetBrains 内置的 [Protocol Buffer 插件](https://plugins.jetbrains.com/plugin/14004-protocol-buffers) 不兼容。
> 安装前请先禁用 **Protocol Buffer** 和 **gRPC**。

<!-- Plugin description end -->

## 快速开始

1. 打开 **Settings → Plugins → Marketplace**
2. 搜索 **"Protobuf"**（作者：kanro）
3. 安装并重启 IDE

## 功能概览

### Schema 智能理解

深度理解 `.proto` 文件的语义——跨文件和跨导入解析类型、验证字段编号、检查命名约定、实时捕获错误。

![截图](resources/screenshot.png)

### 跨文件导航

支持跨文件、跨包的跳转定义、查找用法、重命名重构。

<table>
<tr>
<td width="50%">

**引用解析**
![引用](resources/reference.webp)

</td>
<td width="50%">

**原地重命名**
![重命名](resources/rename.webp)

</td>
</tr>
</table>

### 智能编辑

上下文感知的自动补全——类型、字段名、枚举值、选项。内置自动导入和导入优化。

<table>
<tr>
<td width="50%">

**自动导入**
![自动导入](resources/auto_import.webp)

</td>
<td width="50%">

**导入优化器**
![导入优化](resources/import_optimizer.webp)

</td>
</tr>
</table>

### 多语言集成

| 模块 | 功能 |
|------|------|
| **Java / Kotlin** | 在 `.proto` 和生成的 Java 代码之间导航 |
| **Go** | Go 代码导航、从 proto descriptor 反编译 |
| **gRPC** | 通过 [HTTP Client](https://plugins.jetbrains.com/plugin/13121-http-client) 发送请求、通过 [Endpoints](https://plugins.jetbrains.com/plugin/16890-endpoints) 浏览 API |
| **AIP** | [Google API 设计规范](https://google.aip.dev/) 验证与补全 |
| **Sisyphus** | [Sisyphus](https://github.com/ButterCam/sisyphus) Kotlin 框架集成 |

### 现代 Protobuf

- 支持 [Protobuf Editions](https://protobuf.dev/editions/overview/)（`edition = "2023"`）
- [Text Format](https://protobuf.dev/reference/protobuf/textformat-spec/)（`.textproto`）含基于 schema 的验证
- 通过 [配套插件](https://plugins.jetbrains.com/plugin/19147-buf-for-protocol-buffers) 支持 [Buf](https://buf.build/)

### AIP 规范支持

验证并自动补全 [Google API 改进提案](https://google.aip.dev/)——资源定义、HTTP 规则和命名约定。

![AIP](resources/aip.webp)

## AI 辅助开发

本项目使用 AI 辅助开发，采用结构化工作流：

```
brainstorm → implement → ship → reflect
```

每个非平凡的改动都要经过 brainstorm（带状态机的设计讨论）、实现、带质量门控的发版，以及发版后的反思。学习成果积累在知识库中，反馈到未来的工作中。

详见 [`.github/copilot-instructions.md`](.github/copilot-instructions.md) 和 [`.github/skills/`](.github/skills/)。

## 文档

|  |  |
|---|---|
| **[概览](docs/overview-zh.md)** | 插件功能介绍 |
| **[架构](docs/architecture-zh.md)** | 各模块如何协作 |
| **[快速开始](docs/getting-started-zh.md)** | 构建、运行和测试 |
| **[贡献指南](docs/contributing-zh.md)** | 代码风格和功能开发 |
| **[设计文档](docs/design/)** | 子系统设计决策详解 |
| **[扩展点](docs/extension-points-zh.md)** | 插件扩展 API |

## 致谢

灵感来源：[protobuf-jetbrains-plugin](https://github.com/ksprojects/protobuf-jetbrains-plugin) 和 [intellij-protobuf-editor](https://github.com/jvolkman/intellij-protobuf-editor)。
