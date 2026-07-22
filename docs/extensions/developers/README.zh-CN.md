# 开发 M3UAndroid 插件

[English](README.md) · [插件文档首页](../README.zh-CN.md)

插件接收 M3UAndroid 发出的类型化 request，并返回类型化 result。本指南对应仓库内的开发预览；插件模块目前通过 `project(":extension:sdk-android")` 引用 SDK。

## 从这里开始

- [运行 Hello](quickstart.zh-CN.md)：先看到结果，再定义它的 manifest 与 handler。
- [开发订阅 provider](host-broker.zh-CN.md)：实现发现、登录、刷新、播放与关闭。
- [选择 Hook](hooks.zh-CN.md)：为已有插件查找 request、result、capability 与宿主触发时机。

可以直接参考两个示例：

- [`HelloExtensionService`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt)：最小插件；
- [`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt)：完整订阅 provider。

## API 参考

- [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt)
- [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api)
- [契约术语](reference/glossary.zh-CN.md)
- [测试插件](testing.zh-CN.md)
- [准备更新](reference/compatibility.zh-CN.md)
