# 贡献指南

## 代码风格

- Kotlin 是主要语言（99%+ 源码）
- 遵循代码库中现有的模式
- PSI 元素行为放在 mixin 中，而非生成的类中
- 使用扩展函数编写工具类代码

## 添加新语言功能

1. **注解器 / 检查**：添加到 `lang/annotator/`，在 `plugin.xml` 中注册
2. **补全**：在 `lang/completion/` 添加 provider，在 `ProtobufCompletionContributor` 中注册
3. **快速修复**：添加到 `lang/quickfix/`，通过注解器或检查器注册
4. **引用**：在 `lang/reference/` 添加 provider，在 `ProtobufSymbolReferenceContributor` 中注册

各子系统的设计原理详见[设计文档](design/)。

## 添加新集成模块

1. 在 `src/main/kotlin/.../protobuf/<module>/` 下创建包
2. 在 `src/main/resources/META-INF/` 下创建 XML 配置文件（`io.kanro.idea.plugin.protobuf-<module>.xml`）
3. 在主 `plugin.xml` 中添加可选依赖
4. 实现所需的 provider（索引、行标记、查找用法）
5. 在 `docs/modules/<module>-zh.md` 中添加文档

可用扩展 API 详见[扩展点](extension-points-zh.md)。

## Copilot 工作流

本项目使用 AI 辅助开发，工作流遵循结构化周期：

```
brainstorm → implement → ship → reflect
```

- **Brainstorm**：设计讨论在 `.github/brainstorm.md` 中进行，带状态机防止过早编码
- **Ship**：提交经过构建验证、测试执行和 brainstorm 状态检查
- **Reflect**：发版后反思在学习库 `.github/knowledge.md` 中积累经验

详见 [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) 了解完整工作流规范。
