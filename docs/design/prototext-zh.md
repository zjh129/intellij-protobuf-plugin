# ProtoText 支持

## 问题背景

Protobuf 有两种表面相似但根本上是不同语言的文件格式：

- **`.proto` 文件** — schema 定义（声明结构：消息、字段、类型）
- **`.textproto` / `.txtpb` 文件** — 数据实例（为消息的字段赋值）

它们共享词汇（字段名、类型名、枚举值）但在语法、目的和——关键的——语义确定方式上不同。`.proto` 文件是自包含的：其含义来自自身的声明。`.textproto` 文件**没有 schema 就毫无意义**——如果不告诉它文件代表哪个消息类型，你就无法验证字段名、检查类型或解析枚举。

重用 proto 解析器意味着将实例格式强制放入定义语法。这两种语法在结构上不兼容：proto 是 `message Foo { int32 bar = 1; }`，textproto 是 `bar: 42`。共享解析需要如此多的特殊处理，"共享"代码比两个独立实现更难维护。

## 设计决策

### 决策 1：独立语言、共享概念

**选择：** ProtoText 是一个完全独立的 IntelliJ 语言，有自己的语法（`prototext.bnf`，约 200 行 vs proto 的约 600 行）、自己的词法分析器、自己的 PSI 树层级（`ProtoTextElement` vs `ProtobufElement`）和自己的标记类型（特别是 `#` 注释 vs `//` 和 `/* */`）。

**共享会破坏什么？**

- **语法结构。** Proto 的顶级是 `(Edition|Syntax)? (Import|Package|Option|Message|Enum|Service)*`。TextProto 的顶级只是 `Field*`。共享语法意味着 textproto 解析器为每个 proto 构造携带死代码。
- **标记类型。** TextProto 使用 `#` 作为注释；proto 使用 `//` 和 `/* */`。共享词法分析器意味着到处都是条件注释处理。
- **PSI 语义。** Proto 中的 `FieldName` 是声明位置。TextProto 中的 `FieldName` 是必须解析到 proto 声明的使用位置。同名相反角色——共享类会混淆定义和引用。

**什么是共享的：** 引用解析基础设施。TextProto 引用解析*到* proto PSI 类（`ProtobufFieldDefinition`、`ProtobufEnumValueDefinition`、`ProtobufMessageDefinition`）。两种语言都用 `ProtobufSymbolResolver` 进行基于作用域的查找，并用相同的 stub 索引（`ShortNameIndex`、`QualifiedNameIndex`）进行跨文件解析。分离在语言边界；集成在语义边界。

### 决策 2：通过头注释进行 Schema 链接

**选择：** TextProto 文件使用文件顶部的特殊注释声明其 schema：

```
# proto-file: path/to/schema.proto
# proto-message: package.MessageName
```

这些注释由 `ProtoTextSharpCommentReferenceContributor` 解析并创建真实的 IntelliJ 引用：

- `ProtoTextHeaderFileReference` 将 `# proto-file:` 解析到 `ProtobufFile`（通过 `ProtobufRootResolver` 支持相对路径和模块路径）
- `ProtoTextHeaderMessageReference` 将 `# proto-message:` 在解析后的文件作用域内解析到 `ProtobufMessageDefinition`

**为什么用注释而非语言构造？** 因为文本格式规范没有定义 schema 链接语法。这些头注释是事实上的约定（被 Google 内部工具和 `buf` 使用），而非语法的一部分。将它们变成注释意味着：

- 文件无论工具如何都保持有效的 textproto
- 链接机制是可选的——没有头注释的文件只是失去 IDE 功能
- `ProtoTextFile.schema()` 方法在缺少头注释时优雅地返回 `null`

### 决策 3：非对称引用系统

**选择：** TextProto 引用始终指向外部的 proto 定义。Proto 从不引用 textproto。这创建了单向依赖图。

textproto 中的三种引用类型是：

| 引用 | 源 | 解析到 |
|------|-----|--------|
| `ProtoTextFieldReference` | 赋值中的字段名 | message schema 中的 `ProtobufFieldLike` |
| `ProtoTextTypeNameReference` | 扩展/any 类型括号 `[pkg.Type]` | `ProtobufFieldDefinition` 或通过 stub 索引的全局类型 |
| `ProtoTextEnumValueReference` | 枚举字面量值 | 字段枚举类型的 `ProtobufEnumValueDefinition` |

**解析策略：** 字段引用向上遍历 textproto 树找到所有者消息上下文，然后在消息项中搜索匹配的字段名。对于 map 字段，合成 `key` 和 `value` 字段被特殊处理。扩展的类型引用使用 `ProtobufSymbolResolver`；对于 `Any` 类型，使用全局 `QualifiedNameIndex`。

补全利用相同的解析：知道当前消息上下文让插件建议所有有效字段名（消息字段带 `" {}"`、标量带 `": "`）和所有有效枚举值。

## 整体协作

```
.textproto 文件
    ↓
用 prototext 语法解析 → ProtoTextFile PSI 树
    ↓
读取头注释：
    # proto-file: → ProtoTextHeaderFileReference → ProtobufFile
    # proto-message: → ProtoTextHeaderMessageReference → ProtobufMessageDefinition
    ↓
对每个字段赋值：
    FieldName → ProtoTextFieldReference → ProtobufFieldLike
        如果字段类型是消息 → 递归进入嵌套消息上下文
        如果字段类型是枚举 → ProtoTextEnumValueReference → ProtobufEnumValueDefinition
        如果是扩展语法 [Type] → ProtoTextTypeNameReference → 全局解析
```

schema 充当 textproto 的"类型系统"。没有它，字段名只是字符串。有了它，插件提供补全、验证、导航和重命名重构——都从两行头注释流出。

## 核心洞察

核心设计张力是：textproto 在语法上独立但在语义上依赖 proto。架构通过让*语言*完全分离（不同语法、词法分析器、PSI 树）同时让*引用系统*完全集成（textproto 引用解析到 proto PSI 节点，使用相同的符号解析基础设施）来解决这个问题。这意味着每种语言可以独立演进解析，但 IDE 功能如跳转定义和补全可以跨边界无缝工作。
