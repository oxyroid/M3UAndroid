# 开发 M3UAndroid 插件

[English](README.md) · [插件文档首页](../README.zh-CN.md)

插件接收 M3UAndroid 发出的类型化请求，并返回类型化结果。外部插件目前属于开发者预览，
SDK 以仓库内的 `project(":extension:sdk-android")` 模块提供。

## 从这里开始

- [运行 Hello](quickstart.zh-CN.md)：先得到可运行结果，再阅读契约细节。
- [定义 Manifest](concepts.zh-CN.md)：填写插件身份、Hook、capability（能力授权）和设置。
- [注册类型化 Hook](first-hook.zh-CN.md)：为插件增加一个可调用功能。
- [开发订阅 Provider](host-broker.zh-CN.md)：实现登录、刷新、播放解析和 Session 关闭。
- [选择 Hook](hooks.zh-CN.md)：查找每项受支持功能的请求、结果、capability 和触发时机。

最小模板见
[`HelloExtensionService`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt)。
完整 Provider 示例见
[`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt)。

## API 参考

- [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt)
- [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api)
- [使用宿主网络 Broker](reference/provider-broker.zh-CN.md)
- [契约术语](reference/glossary.zh-CN.md)
- [测试插件](testing.zh-CN.md)
- [准备更新](reference/compatibility.zh-CN.md)
