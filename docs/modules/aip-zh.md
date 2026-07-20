# AIP 支持

**包**：`io.kanro.idea.plugin.protobuf.aip`
**配置**：在主 `plugin.xml` 中注册

## 概述

实现对 [Google API 改进提案（AIP）](https://google.aip.dev/) 的支持——这是一组 Google API 设计指南。AIP 规范定义了资源类型、标准方法和字段行为的约定，本模块对其进行了验证和辅助。

## 功能

### 注解

`AipAnnotator` 验证 proto 文件中的 AIP 约定：
- 资源定义正确性
- 标准方法模式（Create、Get、List、Update、Delete）
- 字段行为注解

### 补全

`AipCompletionContributor` 提供：
- 资源类型名称补全
- AIP 方法模式建议
- AIP 资源的标准字段名

### 资源引用

解析 `(google.api.resource_reference)` 类型字符串到目标资源定义。这允许从一个 proto 文件中的资源引用导航到另一个文件中的资源定义。

### 快速修复

- `AddResourceImportFix` — 自动导入缺失的资源类型定义

### 规范方法

`AipSpecMethod` 定义标准 AIP 方法模式及其预期签名。

## 关键文件

| 文件 | 用途 |
|------|------|
| `AipOptions.kt` | AIP 选项定义和常量 |
| `Extension.kt` | 扩展声明 |
| `annotator/AipAnnotator.kt` | AIP 约定验证 |
| `completion/AipCompletionContributor.kt` | AIP 感知补全 |
| `method/AipSpecMethod.kt` | 标准方法模式 |
| `quickfix/AddResourceImportFix.kt` | 自动导入修复 |
| `reference/AipResourceReference.kt` | 资源引用解析 |
| `reference/AipResourceResolver.kt` | 资源类型解析器 |
