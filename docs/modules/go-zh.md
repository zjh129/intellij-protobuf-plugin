# Go 集成

**包**：`io.kanro.idea.plugin.protobuf.golang`
**配置**：`META-INF/io.kanro.idea.plugin.protobuf-go.xml`
**依赖**：`org.jetbrains.plugins.go`（可选）

## 概述

提供 `.proto` 定义与生成的 Go 代码之间的导航，以及从 Go protobuf descriptor 反编译回 `.proto` 源码的功能。

## 功能

### 行标记

- **Proto → Go**：定义上的边栏图标，跳转到生成的 `.pb.go` 文件
- **Go → Proto**：生成的 Go 类型上的边栏图标，跳转到 proto 源码

### 反编译

当 Go 模块包含编译后的 proto descriptor（嵌入在 `.pb.go` 文件中）时，插件可以将原始 `.proto` 定义重建。这对于浏览只提供编译后 descriptor 的依赖非常有用。

- `GoDecompileService` — 协调反编译过程
- `GoDecompileLineMarker` — 触发反编译的边栏图标

### 导入根

`GoRootProvider` 将 Go 模块 proto 路径添加到导入解析链，使 proto 文件中的导入可以解析到 Go 依赖中的 proto。

### 索引

`GoIndexProvider` 使用 protobuf-Go 命名约定，用 Go 类型名索引 proto 元素。

## 关键文件

| 文件 | 用途 |
|------|------|
| `Extension.kt` | 扩展点声明 |
| `GoRootProvider.kt` | 从 Go 模块导入根 |
| `GoIndexProvider.kt` | Stub 索引贡献 |
| `GoLineMarkerProvider.kt` | Go 侧边栏图标 |
| `GoDecompileService.kt` | 从 Go 反编译 proto |
| `GoDecompileLineMarker.kt` | 反编译边栏动作 |
| `GoNameIndex.kt` | 名称索引 |
| `GoUnimplementedServerNameIndex.kt` | gRPC 未实现服务器索引 |
| `Names.kt` | Go 命名约定 |
