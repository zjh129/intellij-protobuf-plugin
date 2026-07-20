# 扩展点

本插件定义了允许第三方插件扩展功能的自定义扩展点。所有扩展点都是动态的（支持运行时加载/卸载）。

## rootProvider

**接口**：`io.kanro.idea.plugin.protobuf.lang.root.ProtobufRootProvider`

提供 proto 文件解析的导入根目录。当插件遇到 `import` 语句时，查询所有已注册的根提供者来找到被导入的文件。

### 内置提供者

| 提供者 | 来源 |
|--------|------|
| `ModuleSourceRootProvider` | 标记为 proto 根的模块源码目录 |
| `LibraryRootProvider` | 包含 `.proto` 文件的 JAR 库 |
| `EmbeddedRootProvider` | 插件内置的 Google 标准 proto 定义 |
| `DecompiledRootProvider` | 从编译后的 descriptor 重建的 proto 文件 |
| `GoRootProvider` | Go 模块 proto 路径（来自 golang 集成） |

### 使用方法

```xml
<extensions defaultExtensionNs="io.kanro.idea.plugin.protobuf">
    <rootProvider implementation="com.example.MyRootProvider"/>
</extensions>
```

```kotlin
class MyRootProvider : ProtobufRootProvider {
    override fun roots(context: ProtobufRootProviderContext): List<VirtualFile> {
        // 返回包含 .proto 文件的目录列表
    }
}
```

## symbolReferenceProvider

**接口**：`io.kanro.idea.plugin.protobuf.lang.psi.feature.ProtobufSymbolReferenceProvider`

提供标准 proto 引用之外的自定义符号引用。AIP 用它来解析字符串字面量中的资源类型名称。

### 内置提供者

- `AipResourceReferenceProvider` — 解析 `(google.api.resource_reference)` 类型字符串

## indexProvider

**接口**：`io.kanro.idea.plugin.protobuf.lang.psi.feature.ProtobufIndexProvider`

为 proto 元素贡献索引条目，使 proto 定义和生成代码之间的导航成为可能。

### 内置提供者

| 提供者 | 用途 |
|--------|------|
| `JavaIndexProvider` | 索引 Java 生成的类名 |
| `GoIndexProvider` | 索引 Go 生成的类型名 |
| `SisyphusIndexProvider` | 索引 Sisyphus Kotlin 类型名 |

### 使用方法

```kotlin
class MyIndexProvider : ProtobufIndexProvider {
    override fun buildIndex(element: ProtobufElement): Map<StubIndexKey<*, *>, List<String>> {
        // 返回此元素的索引条目
    }
}
```

## stubExternalProvider

**接口**：`io.kanro.idea.plugin.protobuf.lang.psi.feature.ProtobufStubExternalProvider`

提供与 PSI stub 一起存储的额外外部数据。Java 集成用它来缓存 Java 选项值。

### 内置提供者

- `FileJavaOptionsProvider` — 提取 `java_package`、`java_outer_classname`、`java_multiple_files` 文件选项

## protocPlugin

**接口**：`io.kanro.idea.plugin.protobuf.compile.ProtobufCompilerPlugin`

钩入 protobuf 编译过程，允许自定义代码生成插件。

### 内置编译器

| 编译器 | 用途 |
|--------|------|
| `FileCompiler` | 文件级编译 |
| `MessageCompiler` | 消息类型编译 |
| `EnumCompiler` | 枚举类型编译 |
| `MessageFieldCompiler` | 消息字段编译 |
| `MessageMapEntryCompiler` | Map 条目类型编译 |
| `MessageMapFieldCompiler` | Map 字段编译 |
| `MessageOneofCompiler` | Oneof 字段编译 |
| `EnumValueCompiler` | 枚举值编译 |
| `ServiceCompiler` | 服务编译 |
| `ServiceMethodCompiler` | 服务方法编译 |
