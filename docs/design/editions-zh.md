# Protobuf Editions

## 问题背景

Protobuf 最初的版本控制模型——`syntax = "proto2"` vs `syntax = "proto3"`——创造了一个非此即彼的世界，每个版本的行为捆绑在一起。Proto3 *一次改变了多件事*：开放枚举、隐式字段存在、packed repeated 编码、required UTF-8 验证。你不能在不同时失去显式字段存在的情况下采用 proto3 的开放枚举。

Editions 用 `edition = "2023"` 取代了这一点，其中每个行为差异成为可单独切换的**特性**。一个 edition 定义默认特性集，但单个文件或字段可以覆盖特定特性。这使 protobuf 演进是增量的而非革命的。

对于 IDE 插件，这带来了挑战：以前硬编码每个语法版本的验证规则（"proto3 字段不能有标签"）现在依赖于可以在每个元素上变化的*特性值组合*。插件需要一个足够灵活的模型来表达这一点。

## 设计决策

### 决策 1：特性集模型

**选择：** 将 edition 差异表示为具有六个独立可切换属性的 `ProtobufFeature` 数据类：

```kotlin
data class ProtobufFeature(
    val enumType: ProtobufEnumType,                       // OPEN | CLOSED
    val fieldPresence: ProtobufFieldPresence,               // LEGACY_REQUIRED | EXPLICIT | IMPLICIT
    val jsonFormat: ProtobufJsonFormat,                   // ALLOW | LEGACY_BEST_EFFORT
    val messageEncoding: ProtobufMessageEncoding,         // LENGTH_PREFIXED | DELIMITED
    val repeatedFieldEncoding: ProtobufRepeatedFieldEncoding,  // PACKED | EXPANDED
    val utf8Validation: ProtobufUtf8Validation,           // VERIFY | NONE
)
```

**为什么用特性而非版本标志？** 因为版本标志会产生组合爆炸。用散布在代码库中的 `if (proto3)` 守卫，添加新 edition 意味着审查每个守卫以决定哪个行为适用。用特性，每条验证规则检查它关心的确切属性：

- "此字段可以省略标签吗？" → 检查 `fieldPresence`
- "第一个枚举值必须为零吗？" → 检查 `enumType`
- "允许 `required` 吗？" → 检查 `fieldPresence == LEGACY_REQUIRED`

每个特性映射到 proto2、proto3 和 edition 2023 之间实际变化的六个维度——匹配官方 protobuf 特性规范。

### 决策 2：向后兼容的特性默认值

**选择：** 将 PROTO2 和 PROTO3 定义为与 EDITION_2023 并列的具体 `ProtobufFeature` 实例，而非将其视为特殊情况：

```
PROTO2:        CLOSED 枚举, EXPLICIT 存在性, LEGACY_BEST_EFFORT JSON, EXPANDED repeated, 无 UTF-8 检查
PROTO3:        OPEN 枚举,   IMPLICIT 存在性, ALLOW JSON,              PACKED repeated,   VERIFY UTF-8
EDITION_2023:  OPEN 枚举,   EXPLICIT 存在性, ALLOW JSON,              PACKED repeated,   VERIFY UTF-8
```

**为什么这统一了验证：** 无需维护三个独立代码路径（`Protobuf2Annotator`、`Protobuf3Annotator`、`ProtobufEditionAnnotator`），*预期*架构是将任何文件——无论是 `syntax = "proto2"`、`syntax = "proto3"` 还是 `edition = "2023"`——解析为 `ProtobufFeature` 实例，然后运行单一组特性驱动验证规则。

这使 EDITION_2023 可表达为"基本上是 proto3，但有显式字段存在性"——这恰恰是它的实际含义。未来的 editions（2024、2025）将通过定义新的特性集常量添加，无需触及验证逻辑。

### 决策 3：有意增量实现

**选择：** 特性集模型**已定义但尚未接入验证**。这是深思熟虑的脚手架，而非放弃的代码。

**当前实现状态：**

| 组件 | 状态 | 详情 |
|------|------|------|
| 语法（`EditionStatement`） | ✅ 完成 | 解析 `edition = "2023";` 与 `syntax = "..."` 并列 |
| PSI 模型 | ✅ 完成 | 生成 `ProtobufEditionStatementImpl`，`file.edition()` API 可用 |
| 特性集定义 | ✅ 完成 | 定义了 `PROTO2`、`PROTO3`、`EDITION_2023` 常量 |
| 特性枚举 | ✅ 完成 | 六个枚举类型及其所有有效值 |
| `ProtobufEditionAnnotator` | ⚠️ 桩 | 类存在但提前返回；**未在 plugin.xml 中注册** |
| 特性驱动验证 | ❌ 未开始 | 验证仍使用基于语法的注解器 |

**为什么先发布模型？** 因为模型是艰难的设计工作。当前的注解器（`Protobuf2Annotator`、`Protobuf3Annotator`）是生产测试和正确的。将它们迁移到特性驱动逻辑是重构任务，而非设计任务。有了特性集模型：

- Edition 文件正确解析且不会产生虚假错误（edition 注解器提前返回而非应用 proto2/proto3 规则）
- 迁移路径清晰：用 `if (features.enumType == OPEN)` 检查替换 `if (syntax == "proto3")` 检查
- 新 editions 可以通过定义特性集常量添加，即使验证尚未完全迁移

**明确跳过的内容：** 每个元素特性覆盖（单个字段选择加入与其文件默认值不同的特性值）。这是 proto editions 功能，增加了显著复杂性，可以在基础迁移之后添加。

## 整体协作

**当前架构（基于语法）：**
```
file.syntax() == "proto3"  →  Protobuf3Annotator（硬编码 proto3 规则）
file.syntax() == "proto2"  →  Protobuf2Annotator（硬编码 proto2 规则）
file.edition() != null     →  ProtobufEditionAnnotator（提前返回，无验证）
```

**目标架构（基于特性）：**
```
file.syntax() == "proto3"  →  resolve to ProtobufFeature.PROTO3    ─┐
file.syntax() == "proto2"  →  resolve to ProtobufFeature.PROTO2    ─┼→ 统一特性驱动验证器
file.edition() == "2023"   →  resolve to ProtobufFeature.EDITION_2023 ─┘
```

语法和 PSI 层已经将 `EditionStatement` 和 `SyntaxStatement` 视为文件级别上相互排斥的替代项。文件 API 暴露了 `syntax()` 和 `edition()` 方法，且恰好一个返回非空。特性集模型已准备就绪，可以将其桥接到单一验证路径。

## 核心洞察

Editions 设计反映了 protobuf 自身的演进哲学：让新模型*包含*旧模型而非替换它。通过将 proto2 和 proto3 表达为特性集实例，插件避免了其验证代码中的"传统 vs 现代"分裂。当前状态——模型已定义、迁移待完成——是务实的选择：基于语法的注解器今天正常工作，特性模型已就绪等待 editions 采用需要统一路径的时候。
