# 注解系统

## 问题背景

验证 Protocol Buffers 实际上是复杂的，因为 protobuf 不是一种语言——而是三种使用同一语法的不同方言。Proto2 *要求*字段标签（`required`、`optional`、`repeated`）；proto3 *禁止* `required` 并将 `optional` 视为存在性信号。Proto3 禁止 groups、extensions、weak imports 和默认值——这些在 proto2 中都是合法的。Editions 引入了一个特性标志模型，将语义与语法版本完全分离。

单一整体验证器无法干净地表达"此规则仅在文件说 `syntax = "proto3"` 时适用"而不陷入条件纠缠。与此同时，*通用*的规则——字段编号唯一性、命名约定、符号解析、map 键类型约束——必须无论方言如何都运行。设计挑战是在不复制逻辑或不在这两层之间创建耦合的情况下分离版本特定规则和通用规则。

除了结构验证，错误必须可操作。未解析的类型名是令人沮丧的；带一键"添加导入"修复的未解析类型名是有成效的。注解系统必须尽可能将诊断与修复配对。

## 设计决策

### 决策 1：按版本划分的注解器（分层验证）

**为什么不一个大的注解器？** 因为版本特定的规则是*相互排斥*且独立变化的。Proto3 的"枚举必须有零值"规则没有 proto2 对应项。Proto2 的"字段必须有标签"规则与 proto3 行为相反。将这些混合在一个类中意味着每条规则都需要 `when (syntax)` 守卫，添加 edition 支持会将每个分支分成三个。

选择的方案使用**分层架构**：

| 层级 | 类 | 何时激活 |
|------|-----|---------|
| 通用 | `ProtobufAnnotator` | 始终（所有 `.proto` 文件） |
| Proto2 | `Protobuf2Annotator` | `syntax` 是 `"proto2"` 或未指定 |
| Proto3 | `Protobuf3Annotator` | `syntax` 恰好是 `"proto3"` |
| Editions | `ProtobufEditionAnnotator` | `edition()` 非空 |

每个版本注解器在 `annotate()` 中用早期返回**自门控**。IntelliJ 在每个 PSI 元素上调用所有注册的注解器，所以门控是廉价的——对 `file.syntax()` 的单次调用。通用注解器无门控，无条件运行。

**维护收益：** 添加 edition 特定验证意味着填充 `ProtobufEditionAnnotator`（当前是桩）而不触及 proto2 或 proto3 逻辑。`ProtobufFeature` 数据类已经定义了 `EDITION_2023` 的特性标志——注解器只需执行它们。无需更改现有代码。

### 决策 2：注解器优于检查器

IntelliJ 提供两种验证机制：**注解器**（实时、不可压制）和**检查器**（可配置、可压制、可批量）。本插件对几乎所有验证使用注解器。为什么？

**Protobuf 错误不是意见。** 未解析的符号、重复的字段编号、proto2 中缺失的 `required` 标签都是规范违规——`protoc` 会拒绝文件。允许用户通过检查器静默真实错误会产生虚假的安全感。注解器是正确的工具，因为它们镜像编译器：始终开启、不可协商。

**实时反馈对 protobuf 很重要。** Protobuf 文件是被代码生成器消费的 schema 定义。字段类型中的拼写错误不会导致你可以调试的运行时异常——它会导致你可能没有在阅读的生成语言中出现构建失败。在输入时即时红色下划线可在代价高昂的 `protoc` 往返之前捕获错误。

**权衡：** 命名约定警告（如字段的 `snake_case`）*是*风格导向的，可以说属于检查器。当前设计对这些使用 `WEAK_WARNING` 严重级别，使它们可见但不突兀——务实的中间立场，避免将验证基础设施拆分到两个框架。

### 决策 3：快速修复作为一等公民

注解器不仅仅报告问题——它们通过 IntelliJ 的 `withFix()` API 附加可操作的修复。这是一个深思熟虑的设计选择，而非便利功能。

**AddImportFix — 关键路径。** 当 `ProtobufAnnotator` 遇到未解析的类型名时，创建带附加 `AddImportFix` 的 `ERROR` 注解。修复按短名称搜索 stub 索引，按限定名后缀过滤候选项——如果存在一个匹配——直接插入导入。对于多个匹配，它呈现弹出窗口。这实现了 `HintAction`，所以修复显示为内联建议而无需 Alt+Enter。整个流程（检测 → 搜索 → 导入 → 更新引用）在不离开编辑器的情况下完成。

**RenameFix — 安全重构优于手动编辑。** 命名约定违规附加一个 `RenameFix`，委托给 IntelliJ 的 `RefactoringFactory.createRename()`。这确保所有引用原子更新——关键因为 protobuf 名称传播到多种语言的生成代码中。

**OptimizeImportsFix — 批量清理。** 未使用导入警告（来自 `FileTracker`）附加 `OptimizeImportsFix`，一次动作移除所有未使用的导入，匹配 Java/Kotlin 开发中熟悉的"优化导入"习惯用法。

**为什么要配对而非分离？** 没有修复的注解是投诉。有修复的注解是工作流。通过在同一位置放置检测和修复，最了解问题的代码也提供解决方案——无需通过单独的修复注册系统间接处理。

## 整体协作

```
                     IntelliJ 注解框架
                     （每个 PSI 元素调用所有已注册的注解器）
                                    │
                 ┌──────────────────┼──────────────────┐
                 │                  │                   │
        ProtobufAnnotator   Protobuf3Annotator   Protobuf2Annotator
        （通用规则）         （仅 proto3 规则）    （仅 proto2 规则）
                 │
      ┌──────────┼──────────┐
      │          │          │
  FileTracker NumberTracker ScopeTracker
  （导入）   （字段编号）  （名称唯一性）
```

**追踪器是缓存工具，不是注解器。** `FileTracker`、`NumberTracker` 和 `ScopeTracker` 使用 `CachedValuesManager` 在每次 PSI 修改时计算约束集一次，然后单个 `visit()` 调用检查单个元素对缓存集的符合情况。这避免了 O(n²) 重新计算——追踪器在 `init` 中记录所有项，每个注解器访问只是对缓存数据的 O(1) 查找。

**记录-访问模式：** 每个追踪器在构造期间遍历作用域记录所有定义（`init { scope.items().forEach { record(it) } }`），然后暴露 `visit(element, holder)` 方法，检查一个元素对记录集的符合情况。注解器在每个元素委托给追踪器；追踪器保存跨元素知识。

## 核心洞察

注解系统架构反映 protobuf 规范的一个基本属性：大多数规则是通用的，但版本之间不同的规则是*矛盾的*，而非仅仅是附加的。Proto2 要求 proto3 禁止的内容。你无法将其表达为单一验证器上的特性标志而不使逻辑变成"如果 proto2 做 X，否则如果 proto3 做 X 的反面"。独立的注解器使每个版本的规则自包含且可独立测试——并且它们使 edition 注解器成为一个干净的扩展点，而非现有条件树中的另一个分支。
