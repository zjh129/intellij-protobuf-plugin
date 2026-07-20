# 快速开始

## 前置要求

- **JDK 21** — 构建和运行必需（当前平台 262 需要 JDK 25，见下方说明）
- **IntelliJ IDEA** — 推荐 Ultimate Edition（完整插件开发支持）
- **Gradle 9.0.0** — 通过 wrapper 提供（`gradlew`）

## 构建

```bash
./gradlew build
```

这将执行以下步骤：
1. 从语法文件生成解析器和词法分析器（`src/main/grammar/`）
2. 编译 Kotlin / Java 源码
3. 运行测试
4. 构建插件产物

## 运行

在沙箱 IDE 实例中运行插件：

```bash
./gradlew runIde
```

## 测试

```bash
./gradlew test
```

测试源码位于 `src/test/`。

## 项目结构

```
├── build.gradle.kts          # 构建配置和依赖
├── gradle.properties          # 插件版本、平台版本
├── settings.gradle.kts        # 项目名称
├── src/
│   ├── main/
│   │   ├── grammar/           # BNF 和 FLEX 语法文件
│   │   ├── java/              # Java 源码（解析器工具类）
│   │   ├── kotlin/            # Kotlin 源码（主要代码库）
│   │   └── resources/         # 插件描述符、图标、内置 proto
│   └── test/                  # 测试源码
├── resources/                 # 市场宣传素材（截图、Logo）
└── docs/                      # 文档
```

## 关键配置

### gradle.properties

```properties
pluginVersion=2.5.0
platformType=IU
platformVersion=2026.2
```

### 插件兼容性

本插件支持 IntelliJ 平台 build `252` 到 `263.*`（在 `gradle.properties` 中定义）。

### JDK 版本说明

| 平台版本 | Branch | 需要的 JDK |
|---------|--------|----------|
| 2026.1  | 261    | JDK 21    |
| 2026.2  | 262    | JDK 25    |

升级平台版本时，务必同时更新 Kotlin JVM toolchain。详见 [.cursor/rules/intellij-platform-upgrade.mdc](.cursor/rules/intellij-platform-upgrade.mdc)。

## 语法开发

解析器和词法分析器从语法文件生成：

| 文件 | 工具 | 输出 |
|------|------|------|
| `protobuf.bnf` | Grammar-Kit | 解析器 + PSI 类 |
| `protobuf.flex` | JFlex | 词法分析器 |
| `prototext.bnf` | Grammar-Kit | Proto 文本解析器 + PSI |
| `prototext.flex` | JFlex | Proto 文本词法分析器 |

生成代码输出到 `build/generated/sources/grammar/`。**不要直接编辑生成的文件**——请修改语法定义文件。

修改语法后，重新运行构建即可重新生成。IntelliJ 也支持通过 Grammar-Kit 插件在 IDE 内直接生成。

## CI/CD

- **构建工作流**（`.github/workflows/build.yml`）— 在 push 到 `main` 和所有 PR 时运行
- **发布工作流**（`.github/workflows/release.yml`）— 由 GitHub Releases 触发，发布到 JetBrains Marketplace
