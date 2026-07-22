# 按改动类型操作

[English](change-guide.md) · [维护者指南](README.zh-CN.md)

开始改代码前，先写出一条完整链路：谁发起请求、调用哪个 Hook、谁应用结果、用户在哪里看到变化。缺少宿主调用方或结果应用器的契约仍只是占位符。

## 增加或修改 Hook

按这个顺序工作：

1. 在 [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api) 定义 request、result 和 `HookSpec`。通用能力放在 `HostHookContracts.kt`，provider 能力放在 `subscription/`。
2. 在 [`ExtensionContractCatalog`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/ExtensionContract.kt) 登记宿主支持的 schema version 和 capability。
3. 在 [`ExtensionRuntimeTest`](../../../extension/runtime/src/test/kotlin/com/m3u/extension/runtime/ExtensionRuntimeTest.kt) 覆盖注册、版本、能力、错误、大小、超时和取消中受影响的行为。
4. 增加真实宿主调用点；manifest 声明只表示该 Hook 可用。
5. 增加范围明确的宿主应用器，并测试错误结果、超限结果、部分失败和成功空结果。
6. 用 [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt) 更新 APK 示例，并在 [`extension-reference`](../../../testing/extension-reference) 覆盖跨进程行为。
7. 更新开发者功能页和维护者状态页的中英文版本。

兼容性新增字段应有默认值。旧实现无法安全处理新形状时提高该 Hook 的 schema version；只有整个插件 API 不兼容时才提高 API major。

## 修改内置 Emby/Jellyfin provider

完整路径是：

```text
SubscriptionProviderRepositoryImpl
  -> ExtensionRuntime.invoke(SubscriptionHookSpecs.*)
  -> EmbyCompatibleProvider -> EmbyCompatibleClient
  <- 类型化结果
SubscriptionProviderRepositoryImpl -> SubscriptionProviderImporter
```

从 [`EmbyCompatibleProviderIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/extension/emby/EmbyCompatibleProviderIntegrationTest.kt) 开始复现 HTTP 行为，从 [`SubscriptionProviderRepositoryIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryIntegrationTest.kt) 检查持久化和生命周期。

新增 provider kind 由 descriptor 与声明式设置驱动。App、business 和 data 保持与具体 provider kind 无关，provider 实现返回通用契约类型。

## 修改 APK 发现、信任或 IPC

| 改动 | 主要文件 |
| --- | --- |
| service action、发现与 package 身份 | [`ExtensionProtocol`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/ExtensionProtocol.kt)、[`AndroidExtensionDiscovery`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidExtensionDiscovery.kt) |
| 显式绑定、handshake、PFD 和调用 | [`AndroidBoundExtensionTransport`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidBoundExtensionTransport.kt) |
| APK 侧 Binder 实现 | [`ExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/ExtensionService.kt) |
| APK 侧类型化 Hook 注册 | [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt) |
| 证书固定和用户授权 | [`ExtensionTrustStore`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/ExtensionTrustStore.kt)、[`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt) |
| 跨进程 fixture | [`testing/extension-reference`](../../../testing/extension-reference) |

任何生命周期改动都要检查正常返回、取消、超时、Binder death、停用、撤销和进程重启。连接状态由 [`ExtensionConnectionStateTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionConnectionStateTest.kt) 验证，跨进程链路由 [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt) 验证。

## 修改结果应用器

应用器需要明确三件事：本次请求范围、结果所有者、旧数据替换范围。

至少覆盖：

- 插件失败或抛异常时保留此前有效数据；
- 一个插件失败不影响另一个插件；
- 成功空结果只清除当前所有者的数据；
- cancellation 继续向上传播；
- 数量或字段超限时不把截断结果当成完整成功；
- 所有删除和写入在同一事务完成。

频道、provider、EPG 等持久化改动从 [`SubscriptionProviderImporter`](../../../data/src/main/java/com/m3u/data/extension/SubscriptionProviderImporter.kt)、[`ExtensionContributionRepositoryImplTest`](../../../data/src/androidTest/java/com/m3u/data/repository/extension/ExtensionContributionRepositoryImplTest.kt) 和 [`ExtensionContributionImporterTest`](../../../data/src/androidTest/java/com/m3u/data/extension/ExtensionContributionImporterTest.kt) 开始。

## 修改设置、凭据或网络 broker

设置 schema 和普通值由 [`ExtensionSettingsRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/extension/ExtensionSettingsRepositoryImpl.kt) 管理；密码设置和 provider token 由宿主 vault 加密。插件契约只能携带凭据句柄，不能携带明文 secret。

修改 broker 时，从 [`HostNetworkBrokerSecurityTest`](../../../data/src/androidTest/java/com/m3u/data/extension/security/HostNetworkBrokerSecurityTest.kt) 增加拒绝场景，再实现行为。涉及 owner、origin、redirect、认证 header、响应大小或凭据 capture 的改动也必须检查[剩余发布门槛](status-and-release.zh-CN.md#剩余发布门槛)。

## 修改手机或 TV 界面

界面只观察 repository state 并发送操作，不直接发现或绑定 service。

- 手机从 [`SubscriptionsFragment`](../../../app/smartphone/src/main/java/com/m3u/smartphone/ui/business/setting/fragments/SubscriptionsFragment.kt) 和 [`ExtensionSettingsDialog`](../../../app/smartphone/src/main/java/com/m3u/smartphone/ui/business/setting/fragments/ExtensionSettingsDialog.kt) 开始。
- TV 从 [`TvHomeViewModel`](../../../app/tv/src/main/java/com/m3u/tv/TvHomeViewModel.kt) 和 [`TvScreens`](../../../app/tv/src/main/java/com/m3u/tv/TvScreens.kt) 开始。

手机检查整行点击、滚动授权、错误可见性和设置保存；TV 检查 DPad 顺序、聚焦态、返回行为和长文本。

## 验证证据

| 改动 | 最接近的证据 |
| --- | --- |
| API 或 runtime | [`ExtensionContractTest`](../../../extension/api/src/test/kotlin/com/m3u/extension/api/ExtensionContractTest.kt)、[`ExtensionRuntimeTest`](../../../extension/runtime/src/test/kotlin/com/m3u/extension/runtime/ExtensionRuntimeTest.kt) |
| APK SDK 与类型化 handler | [`TypedExtensionServiceTest`](../../../extension/sdk-android/src/test/java/com/m3u/extension/sdk/android/TypedExtensionServiceTest.kt)、[`hello-extension`](../../../samples/hello-extension) |
| 证书信任与连接状态 | [`CertificateSetFingerprintTest`](../../../extension/transport-android/src/test/java/com/m3u/extension/transport/android/CertificateSetFingerprintTest.kt)、[`ExtensionTrustStoreTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionTrustStoreTest.kt)、[`ExtensionConnectionStateTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionConnectionStateTest.kt) |
| 跨进程发现、绑定、PFD、调用与取消 | [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt)、[`extension-reference`](../../../testing/extension-reference) |
| 外部 provider 生命周期 | [`ExternalProviderEndToEndTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalProviderEndToEndTest.kt)、[`extension-reference`](../../../testing/extension-reference)、[`mock-server`](../../../testing/mock-server) |
| 内置 provider 与 importer | [`EmbyCompatibleProviderIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/extension/emby/EmbyCompatibleProviderIntegrationTest.kt)、[`SubscriptionProviderRepositoryIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryIntegrationTest.kt)，以及上文链接的应用器测试 |
| 手机或 TV 产品链路 | 对应触发入口与可见结果的产品 UI 测试 |
