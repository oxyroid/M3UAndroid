# 维护插件平台

[English](README.md) · [插件文档首页](../README.zh-CN.md)

从用户看到的故障开始，沿调用链找到第一处负责代码。开发独立 APK 请改看 [插件开发指南](../developers/README.zh-CN.md)。

## 问题停在哪一步？

一次外部插件调用只有四段：

```text
发现插件 Service -> 用户授权 -> 调用 Hook -> 宿主应用结果
```

| 用户看到的现象 | 先看哪里 | 最接近的验证 |
| --- | --- | --- |
| Service discovery 返回空列表 | [`AndroidExtensionDiscovery`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidExtensionDiscovery.kt) | [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt) |
| 插件出现了，但不能启用或更新后失联 | [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt)、[`AndroidBoundExtensionTransport`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidBoundExtensionTransport.kt) | [`ExtensionTrustStoreTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionTrustStoreTest.kt)、[`ExtensionConnectionStateTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionConnectionStateTest.kt) 与 [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt) |
| Hook 没被调用、超时或显示不兼容 | [`ExtensionRuntime`](../../../extension/runtime/src/main/kotlin/com/m3u/extension/runtime/ExtensionRuntime.kt)、[`ExtensionContractCatalog`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/ExtensionContract.kt) | [`ExtensionRuntimeTest`](../../../extension/runtime/src/test/kotlin/com/m3u/extension/runtime/ExtensionRuntimeTest.kt) |
| Hook 成功了，但界面、Room 或播放没有变化 | 发起调用的 repository，以及它的 importer/renderer | 相邻 repository/importer 测试 |

如果问题属于 Emby/Jellyfin 的订阅、刷新或播放，从 [`SubscriptionProviderRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryImpl.kt) 开始。它们是内置插件，不经过 Android IPC。

## 修复时只回答四个问题

1. 哪个用户操作触发了这条流程？
2. 调用了哪个 `HookSpec`？
3. 谁检查并应用返回结果？
4. 最小的可见验收结果是什么？

这四项写不完整，通常说明链路中仍缺宿主调用点或结果应用器。

## 三条边界

- 插件返回候选数据；插件本身不直接修改 Room、界面或播放器。
- 一个插件失败，不能删除另一个插件或自己上一次成功的数据。
- 内置和 APK 插件共用同一份 Hook 契约，差别只在是否经过 Android IPC。

需要追完整调用链时，阅读 [当前架构与代码地图](architecture.zh-CN.md)。准备修改时，使用 [按改动类型操作](change-guide.zh-CN.md)。只有判断能力是否已经开放或能否发布时，才阅读 [当前状态与发布门槛](status-and-release.zh-CN.md)。
