# gRPC 支持

**包**：`io.kanro.idea.plugin.protobuf.grpc`
**配置**：`META-INF/io.kanro.idea.plugin.protobuf-client.xml`、`META-INF/io.kanro.idea.plugin.protobuf-microservices.xml`
**依赖**：`com.jetbrains.restClient`（可选）、`com.intellij.modules.microservices`（可选）

## 概述

通过 IntelliJ HTTP Client 提供 gRPC 请求执行、运行请求的边栏图标，以及微服务端点发现功能。

## 功能

### gRPC 请求执行

直接在 IDE 中使用 IntelliJ 的 HTTP Client 基础设施执行 gRPC 请求：

- **原生 gRPC** — 通过 HTTP/2 的二进制 proto 请求
- **gRPC-Web** — 浏览器兼容的 gRPC
- **HTTP 转码** — 对 gRPC 服务的 RESTful JSON 请求

### 边栏图标

`rpc` 定义上的边栏图标支持一键执行请求。点击生成预配置好的 HTTP Client 请求文件。

### 端点发现

微服务模块将 gRPC 服务暴露在 IntelliJ 的端点视图中，使其与 REST API 一起可被发现。

### 请求编辑器

JSON 字段补全，用于 gRPC 请求体，理解 proto 消息 schema。

## 子包

| 包 | 用途 |
|---------|------|
| `grpc/request/` | 请求执行支持、MIME 类型、内容处理 |
| `grpc/gutter/` | 运行动作的边栏图标 provider |
| `grpc/index/` | 服务方法索引用于查找 |
| `grpc/referece/` | HTTP 请求文件的引用贡献者 |
| `grpc/editor/` | 请求体中的 JSON 字段补全 |
| `microservices/` | 端点视图集成 |

## 关键文件

| 文件 | 用途 |
|------|------|
| `Utils.kt` | gRPC 工具函数 |
| `request/GrpcRequestExecutionSupport.kt` | 核心请求执行 |
| `request/GrpcMimeTypeProvider.kt` | MIME 类型注册 |
| `gutter/GrpcRunRequestGutterProvider.kt` | 运行边栏图标 |
| `index/ServiceMethodIndex.kt` | 服务方法 stub 索引 |
| `microservices/GrpcEndpointsProvider.kt` | 端点集成 |
