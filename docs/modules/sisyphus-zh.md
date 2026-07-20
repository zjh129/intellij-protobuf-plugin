# Sisyphus 集成

**包**：`io.kanro.idea.plugin.protobuf.sisyphus`
**配置**：`META-INF/io.kanro.idea.plugin.protobuf-sisyphus.xml`
**依赖**：`org.jetbrains.kotlin`（可选）

## 概述

与 [Sisyphus](https://github.com/nicecraftz/sisyphus) Kotlin/gRPC 框架集成。Sisyphus 从 proto 定义生成 Kotlin DSL API——本模块提供 proto 源码和生成 Kotlin 代码之间的导航。

## 功能

### 行标记

- **Proto → Kotlin**：定义上的边栏图标，跳转到 Sisyphus 生成的 Kotlin 类
- **Kotlin → Proto**：生成的 Kotlin 类型上的边栏图标，跳转到 proto

### 查找用法

使用 Sisyphus 生成类型的 Kotlin 代码会出现在 proto 定义的"查找用法"中。

### 索引

`SisyphusIndexProvider` 使用 Sisyphus 命名约定，用 Sisyphus Kotlin 类名索引 proto 元素。

## 关键文件

| 文件 | 用途 |
|------|------|
| `Extensions.kt` | 扩展声明 |
| `SisyphusIndexProvider.kt` | Stub 索引贡献 |
| `SisyphusNameIndex.kt` | 名称索引 |
| `SisyphusKotlinLineMarkerProvider.kt` | Kotlin 侧边栏图标 |
| `SisyphusProtobufLineMarkerProvider.kt` | Proto 侧边栏图标 |
| `SisyphusFindUsageFactory.kt` | 查找用法集成 |
| `Names.kt` | Sisyphus 命名约定 |
