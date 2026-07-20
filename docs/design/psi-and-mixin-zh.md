# PSI 与 Mixin 模式

## 问题背景

IntelliJ 语言插件需要一个 **PSI（程序结构接口）**——一个类型化的元素树，IDE 可以查询它来进行导航、补全、重构和索引。对于 protobuf，这个树非常复杂：消息嵌套消息，字段跨文件引用类型，带点号的名字链式嵌套，而且两种不同的文件格式（`.proto` 和 `.textproto`）共享很多语义概念但有不同的语法。

手写 PSI 会将语法和语义耦合在一起。每次语法变更都会波及引用解析、补全、stub 和折叠。本项目的解决方案是将问题拆分为**四个独立的层**，在代码生成时组合，使每个关注点可以独立演进。

## 设计决策

### 决策 1：BNF 生成的 PSI + Mixin 注入

Grammar-Kit BNF 文件（`protobuf.bnf`、`prototext.bnf`）是 PSI 结构的**单一真实来源**。每个语法规则可以声明三个正交扩展点：

```bnf
MessageDefinition ::= message Identifier MessageBody {
    implements=[ "...ProtobufElement", "...BodyOwner", "...ProtobufOptionOwner" ]
    mixin="...ProtobufMessageDefinitionMixin"
    stubClass="...ProtobufMessageStub"
}
```

| 属性 | 作用 |
|------|------|
| `implements` | 通过组合挂载横切*功能接口* |
| `mixin` | 注入提供默认行为的*抽象基类* |
| `stubClass` | 接入*stub 序列化*以便索引时访问 |

**为什么不用手写 PSI 类？** 因为语法*一定会*变更。新的 proto editions、自定义选项、AIP 注解——每一个都需要同步编辑解析器、PSI 接口、实现、stub 类型和测试。使用当前方案，修改语法规则会自动重新生成 PSI 类；行为保留在 mixin 和功能接口中，不需要改动，除非语义本身变了。

**为什么不把逻辑都放在生成的类中？** Grammar-Kit 生成访问器的样板代码（`fun identifier()`、`fun messageBody()`）。语义行为——名称解析、stub 数据提取、引用提供——属于 mixin，这些在重新生成时不会被覆盖。

### 决策 2：功能接口作为横切关注点

`lang/psi/feature/` 包定义了大约十几个小接口：

| 接口 | 关注点 |
|------|--------|
| `NamedElement` | 有用户可见名称的任何元素 |
| `ReferenceElement` | 引用其他元素的任何元素 |
| `QualifiedElement<T>` | 带点号的名字链（`a.b.c`）及根/叶遍历 |
| `BodyOwner` / `BodyElement` | 有 `{}` 分隔体的元素 |
| `DocumentOwner` / `DocumentElement` | 文档注释关联 |
| `LookupableElement` | 补全项生成 |
| `FoldingElement` | 代码折叠区域 |
| `ValueElement<T>` / `ValueAssign` | 选项值类型化 |

**为什么用独立接口而非一个臃肿的基类？** 因为组合是不规则的。`MessageDefinition` 既是 `BodyOwner` 又是 `NamedElement` 和 `DocumentOwner`。`FieldDefinition` 是 `NamedElement` 和 `OptionOwner` 但*不是* `BodyOwner`（除非有内联选项）。`ImportStatement` 以上都不是——它是一个指向文件的 `ReferenceElement`。接口让语法声明每个规则的确切能力集，而 IDE 功能（折叠 provider、文档 provider、结构视图）可以查询单一接口而不需要知道具体类型。

### 决策 3：双 PSI 层级通过共享功能统一

Proto（`.proto`）和 prototext（`.textproto`）有独立的语法、独立的解析器、独立的 PSI 元素层级（`Protobuf*` vs `ProtoText*`）。然而两者都需要字段引用、值赋值、文档和补全。

插件不强制合并为一种 AST，而是让每个都有自己的根接口（`ProtobufElement`、`ProtoTextElement`）和自己的 mixin 包，但两者都实现 `lang/psi/feature/` 中的*相同*功能接口。操作 `NamedElement` 或 `ReferenceElement` 的 IDE 功能跨两种格式都能工作，不需要知道是哪个语法生成的树。

**为什么不是一种语法？** 两种语法差异足够大（注释、字段赋值语法、textproto 中没有类型定义），合并会使 BNF 难以阅读，解析器的错误恢复更差。独立语法使每个解析器保持简单；共享功能接口使 IDE 层统一。

### 决策 4：Stub 支持的定义实现索引时加速

11 种元素类型（消息、字段、枚举、服务等）有 stub 支持。每个 mixin 提供一个 `stubData()` 方法，序列化足够的信息——通常是名称和可选的资源类型——使 IDE 可以在不解析文件的情况下填充 `ShortNameIndex` 和 `QualifiedNameIndex`。

```kotlin
// 在 ProtobufMessageDefinitionMixin 中
override fun stubData(): Array<String> {
    return arrayOf(name() ?: "", resourceType() ?: "")
}
```

每个基于 stub 的 mixin 有**双构造器**——AST 模式下一个（解析时），stub 模式下一个（索引加载时）。这意味着 `ProtobufMessageDefinition` 可以回答 `name()` 和 `qualifiedName()`，无论它是从源码解析还是从 stub 缓存反序列化。

### 决策 5：扩展函数作为第三行为层

并非所有行为都能优雅地放入 mixin。Kotlin 扩展函数（如 `ProtobufTypeName.kt`、`ProtobufFieldName.kt`、`ProtobufImportStatement.kt`）添加领域特定方法（`ProtobufTypeName.absolutely()`、`ProtobufImportStatement.public()`）而不污染 mixin 类层次结构。

这创建了清晰的三层模型：

1. **功能接口** — 契约（`NamedElement`、`BodyOwner`）
2. **Mixin** — 在生成时注入的默认实现
3. **扩展函数** — 任何人都可以调用的领域助手

当行为是不需要覆盖生成方法或参与 stub 生命周期的纯查询时，首选扩展函数。

## 整体协作

```
 Grammar (BNF)
   │  generates PSI interfaces + Impl classes
   │  stitches in:  implements ──▶ Feature Interfaces
   │                mixin ──────▶ Mixin Base Classes
   │                stubClass ──▶ Stub Definitions
   ▼
 PSI Tree  ◀── parsed from source (AST mode)
   │           or deserialized from cache (stub mode)
   │
   ├─▶ References (created by mixins via getReferences())
   │     └─▶ ProtobufSymbolResolver
   │           walks scope chain upward for relative names,
   │           walks import stack for cross-file names,
   │           filters results by context (field type vs extend target)
   │
   ├─▶ Stubs (serialized by stubData(), indexed into ShortName / QualifiedName)
   │     └─▶ Completion queries index, then enriches with scope-local items
   │
   └─▶ IDE Features
         query feature interfaces:
           FoldingElement  ──▶ folding provider
           DocumentOwner   ──▶ quick-doc provider
           LookupableElement ──▶ completion contributor
           NamedElement    ──▶ rename refactoring
```

## 核心洞察

语法声明每个 PSI 元素*是什么*（其能力）；mixin 和扩展定义它*如何*表现。这种分离意味着语法变更重新生成结构而不丢失语义，新的 IDE 功能可以针对功能接口而不需要知道每个具体类型。整个设计针对语法、语义和工具的**独立演进**进行了优化。
