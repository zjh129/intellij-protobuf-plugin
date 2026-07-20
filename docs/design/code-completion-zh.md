# 代码补全

## 问题背景

Protobuf 补全从根本上**依赖上下文**，这与通用语言中的关键字补全不同。在哪里输入决定了什么是有效的：

- 在 `message Foo {` 之后 → 字段类型、关键字（`repeated`、`optional`、`oneof`）、嵌套定义
- 在类型位置内 → 内置标量（`int32`、`string`）和用户定义的消息/枚举类型
- 在字段名位置 → 从字段类型派生的名称（`User` 字段 → `user`）
- 在枚举值名位置 → 遵循 proto 约定的 `SCREAMING_SNAKE_CASE` 值
- 在 `syntax = "` 内 → 恰好 `"proto2"` 或 `"proto3"`

单一整体补全处理器会淹没在条件分支中。更糟糕的是，*插入行为*因上下文而异：补全类型应该添加空格，补全字段名应该插入 `= <number>;`，补全导入应该添加导入语句。

## 设计决策

### 决策 1：每个上下文一个 Provider 的模型

**选择：** 每个补全上下文有自己在 `ProtobufCompletionContributor` 中注册的 `CompletionProvider`，都通过 PSI 模式接线在一起。

**为什么不是一个大的处理器？** 因为建议逻辑和插入行为是*按上下文耦合*，而非按文件。`KeywordsProvider` 知道 `import` 需要附加 `" \"\""`。`FieldNameProvider` 知道必须自动递增字段编号。将这些混合到一个类中，会造成每次更改都可能破坏不相关上下文的维护噩梦。

**为什么用模式匹配？** IntelliJ 的 `PlatformPatterns` API 让每个 provider 精确声明它在哪里触发——`withParent(PsiErrorElement).inside(ProtobufEnumBody)` 用于枚举作用域，`withSuperParent(2, ProtobufFile)` 用于顶级。这将上下文检测移出 provider 代码并进入声明性注册，使哪个 provider 拥有哪个位置一目了然。

当前 provider 有：

| Provider | 触发时机 | 建议内容 |
|----------|----------|----------|
| `KeywordsProvider` | 作用域敏感（顶级、消息、枚举、服务、方法） | 该嵌套深度有效的关键字 |
| `BuiltInTypeProvider` | 在 `ProtobufTypeName` 内、不在 `.` 之后 | `int32`、`string`、`bool` 等 |
| `SyntaxProvider` | 在 `ProtobufSyntaxStatement` 字符串内 | `proto2`、`proto3` |
| `FieldNameProvider` | 字段定义中的字段名位置 | 从字段类型派生的名称 |
| `EnumValueNameProvider` | 枚举值名位置 | `ENUM_UNSPECIFIED`、父名大写 |
| `AipMethodCompletion` | RPC 标识符内 | 标准 AIP 方法前缀（`Get`、`List`、`Create`...） |
| `AipResourceCompletion` | AIP 方法前缀之后 | 带资源名称的完整 RPC 签名 |

### 决策 2：智能插入处理器

**选择：** 每个查找元素携带自定义 `InsertHandler`，它在*插入文本之外*转换文档——添加闭合分隔符、定位光标、自动递增字段编号、添加导入、触发后续补全。

**为什么重要：** 在 protobuf 中，补全关键字很少是交互的终点。当用户补全 `import` 时，他们希望光标在引号之间。当他们补全字段名时，他们希望 `= N;` 带下一个可用字段编号。当他们补全 AIP 方法前缀（如 `Get`）时，他们希望后续弹出窗口提供资源名称。

关键插入处理器类：

- **`SmartInsertHandler`** — 核心处理器，在给定偏移量插入文本，避免重复已有文本（通过 `commonPrefixWith`），可选触发后续补全
- **`AddImportInsertHandler`** — 补全跨文件类型时添加导入语句
- **`AutoPopupInsertHandler`** — 触发 `autoPopupMemberLookup` 以链接补全
- **`ComposedInsertHandler`** — 为复杂插入顺序执行多个处理器

### 决策 3：约定感知的名称生成

**选择：** `FieldNameProvider` 和 `EnumValueNameProvider` 不只是提供静态列表——它们*从 proto schema 派生*建议，使用命名约定。

`FieldNameProvider` 检查字段类型并生成上下文相关的名称：
- 已知类型特殊处理：`FieldMask` → `mask`、`Timestamp` → `time`
- 用户定义类型通过单词分割变成 snake_case 字段名
- `repeated` 字段获得复数名称（`repeated User` → `users`）
- 插入处理器找到前一个字段编号并提供 `= <next>;`

`EnumValueNameProvider` 将父枚举名转换为 `SCREAMING_SNAKE_CASE`，并建议 `<ENUM>_UNSPECIFIED = 0;` 作为第一个值（proto3 约定）。

这意味着补全在用户输入时教授 protobuf 约定，而不仅仅是语法有效的内容。

## 整体协作

```
用户在 .proto 文件中输入
    ↓
ProtobufCompletionContributor 将光标位置与 PSI 模式匹配
    ↓
匹配的 provider 生成带定制 InsertHandler 的 LookupElement
    ↓
用户选择一个建议
    ↓
InsertHandler 触发：插入文本 + 调整光标 + 添加导入 + 触发后续弹出窗口
```

AIP 补全系统（`AipCompletionContributor`）注册为独立的 contributor，展示了此架构如何扩展：它添加 Google API 设计模式建议而不修改核心补全逻辑。

## 核心洞察

补全系统的力量来自将每个上下文视为*工作流*，而非仅仅是单词查找。补全字段名不是"插入文本"——它是"插入名称、分配下一个字段编号、用分号终止、定位光标。"每个上下文一个 Provider 的模型使这些工作流可组合且可独立测试，而智能插入处理器链意味着工作流的每个步骤都可以混合和匹配。
