# Java 集成

**包**：`io.kanro.idea.plugin.protobuf.java`
**配置**：`META-INF/io.kanro.idea.plugin.protobuf-java.xml`
**依赖**：`com.intellij.modules.java`（可选）

## 概述

提供 `.proto` 定义与生成的 Java 代码之间的双向导航。当 IDE 中有 Java 支持时，proto 文件和 Java 文件中都会显示边栏图标，支持一键跳转。

## 功能

### 行标记

- **Proto → Java**：消息/服务/枚举定义上的边栏图标，跳转到生成的 Java 类
- **Java → Proto**：生成的 Java 类上的边栏图标，跳转到 proto 源码

### 查找用法

Java 代码中使用生成的 proto 类时，会出现在对应 proto 定义的"查找用法"结果中。

### 索引

`JavaIndexProvider` 使用以下选项计算出的 Java 类名索引 proto 元素：
- `java_package` 选项（若未设置则用 proto 包名）
- `java_outer_classname` 选项
- `java_multiple_files` 选项

### 名称解析

`Names.kt` 实现了 Java 命名规则：
- 包名：`java_package` 选项 -> proto 包名
- 外部类：`java_outer_classname` 选项 -> 从文件名派生
- 多文件模式：`java_multiple_files = true` 时，顶层消息各自生成独立文件

## 关键文件

| 文件 | 用途 |
|------|------|
| `Extension.kt` | 扩展点声明 |
| `JavaIndexProvider.kt` | Stub 索引贡献 |
| `JavaLineMarkerProvider.kt` | Java 侧边栏图标 |
| `ProtobufLineMarkerProvider.kt` | Proto 侧边栏图标 |
| `JavaFindUsageFactory.kt` | 查找用法集成 |
| `JavaNameIndex.kt` | 名称索引 |
| `FileJavaOptionsProvider.kt` | Java 选项 stub 数据 |
| `Names.kt` | Java 命名规则 |
