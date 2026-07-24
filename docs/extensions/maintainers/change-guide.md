# Change by task

[简体中文](change-guide.zh-CN.md) · [Maintainer guide](README.md)

Before editing code, write the complete path: who creates the request, which Hook is called, who applies the result, and where the user sees the change. A contract with no host caller or result applier is still a placeholder.

## Add or change a Hook

Work in this order:

1. Define request, result, and `HookSpec` in [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api). Put general features in `HostHookContracts.kt` and provider features under `subscription/`.
2. Register host-supported schema versions and capabilities in [`ExtensionContractCatalog`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/ExtensionContract.kt).
3. Cover affected registration, version, capability, error, size, timeout, and cancellation behavior in [`ExtensionRuntimeTest`](../../../extension/runtime/src/test/kotlin/com/m3u/extension/runtime/ExtensionRuntimeTest.kt).
4. Add a real host caller; the manifest declaration only advertises that the Hook is available.
5. Add a scoped host applier and test errors, oversized results, partial failure, and successful empty results.
6. Update an APK example with [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt), then cover cross-process behavior in [`extension-reference`](../../../testing/extension-reference).
7. Update the English and Chinese developer feature page and maintainer status page.

Additive compatible fields need defaults. Raise the Hook schema version when an old implementation cannot safely handle the new shape; raise the API major only for platform-wide incompatibility.

## Change the built-in Emby/Jellyfin provider

The complete path is:

```text
SubscriptionProviderRepositoryImpl
  -> ExtensionRuntime.invoke(SubscriptionHookSpecs.*)
  -> EmbyCompatibleProvider -> EmbyCompatibleClient
  <- typed result
SubscriptionProviderRepositoryImpl -> SubscriptionProviderImporter
```

Start in [`EmbyCompatibleProviderIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/extension/emby/EmbyCompatibleProviderIntegrationTest.kt) for HTTP behavior and [`SubscriptionProviderRepositoryIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryIntegrationTest.kt) for persistence and lifecycle.

New provider kinds are driven by descriptors and declarative settings. App, business, and data remain independent of concrete provider kinds, and provider implementations return generic contract types.

## Change APK discovery, trust, or IPC

| Change | Main files |
| --- | --- |
| Service action, discovery, and package identity | [`ExtensionProtocol`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/ExtensionProtocol.kt), [`AndroidExtensionDiscovery`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidExtensionDiscovery.kt) |
| Explicit binding, handshake, PFD, and calls | [`AndroidBoundExtensionTransport`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidBoundExtensionTransport.kt) |
| APK-side Binder implementation | [`ExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/ExtensionService.kt) |
| APK-side typed Hook registration | [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt) |
| Certificate pinning and user authorization | [`ExtensionTrustStore`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/ExtensionTrustStore.kt), [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt) |
| Cross-process fixture | [`testing/extension-reference`](../../../testing/extension-reference) |

Every lifecycle change must cover normal return, cancellation, timeout, Binder death, disable, revocation, and process restart. Use [`ExtensionConnectionStateTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionConnectionStateTest.kt) for connection state and [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt) for the cross-process path.

## Change a result applier

An applier must define the current request scope, result owner, and old-data replacement scope.

At minimum, cover that:

- a failure or exception preserves previous valid data;
- one extension failure does not affect another extension;
- a successful empty result clears only the current owner's data;
- cancellation continues upward;
- count or field overflow is not treated as a complete truncated success;
- deletion and insertion happen in one transaction.

For persistent channel, provider, and EPG work, start with [`SubscriptionProviderImporter`](../../../data/src/main/java/com/m3u/data/extension/SubscriptionProviderImporter.kt), [`ExtensionContributionRepositoryImplTest`](../../../data/src/androidTest/java/com/m3u/data/repository/extension/ExtensionContributionRepositoryImplTest.kt), and [`ExtensionContributionImporterTest`](../../../data/src/androidTest/java/com/m3u/data/extension/ExtensionContributionImporterTest.kt).

## Change settings, credentials, or the network broker

[`ExtensionSettingsRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/extension/ExtensionSettingsRepositoryImpl.kt) owns settings schema and ordinary values. Host vaults encrypt secret settings and provider tokens. Extension contracts carry credential handles, never plaintext secrets.

When changing the broker, add denial cases to [`HostNetworkBrokerSecurityTest`](../../../data/src/androidTest/java/com/m3u/data/extension/security/HostNetworkBrokerSecurityTest.kt) before implementing behavior. Changes to owner, origin, redirect, authentication headers, response size, or credential capture must also be checked against the [external extension release gates](status-and-release.md#before-opening-external-extensions).

## Change phone or TV UI

UI observes repository state and sends operations; it does not discover or bind services directly.

- On phone, start with [`SubscriptionsFragment`](../../../app/smartphone/src/main/java/com/m3u/smartphone/ui/business/setting/fragments/SubscriptionsFragment.kt) and [`ExtensionSettingsDialog`](../../../app/smartphone/src/main/java/com/m3u/smartphone/ui/business/setting/fragments/ExtensionSettingsDialog.kt).
- On TV, start with [`TvHomeViewModel`](../../../app/tv/src/main/java/com/m3u/tv/TvHomeViewModel.kt) and [`TvScreens`](../../../app/tv/src/main/java/com/m3u/tv/TvScreens.kt).

Check full-row clicks, scrollable authorization, visible errors, and settings persistence on phone. Check DPad order, focus contrast, back behavior, and long text on TV.

## Validation evidence

| Change | Closest evidence |
| --- | --- |
| API or runtime | [`ExtensionContractTest`](../../../extension/api/src/test/kotlin/com/m3u/extension/api/ExtensionContractTest.kt), [`ExtensionRuntimeTest`](../../../extension/runtime/src/test/kotlin/com/m3u/extension/runtime/ExtensionRuntimeTest.kt) |
| APK SDK and typed handlers | [`TypedExtensionServiceTest`](../../../extension/sdk-android/src/test/java/com/m3u/extension/sdk/android/TypedExtensionServiceTest.kt), [`hello-extension`](../../../samples/hello-extension) |
| Certificate trust and connection state | [`CertificateSetFingerprintTest`](../../../extension/transport-android/src/test/java/com/m3u/extension/transport/android/CertificateSetFingerprintTest.kt), [`ExtensionTrustStoreTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionTrustStoreTest.kt), [`ExtensionConnectionStateTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionConnectionStateTest.kt) |
| Cross-process discovery, binding, PFD, invocation, and cancellation | [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt), [`extension-reference`](../../../testing/extension-reference) |
| External provider lifecycle | [`ExternalProviderEndToEndTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalProviderEndToEndTest.kt), [`extension-reference`](../../../testing/extension-reference), [`mock-server`](../../../testing/mock-server) |
| Built-in provider and importer | [`EmbyCompatibleProviderIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/extension/emby/EmbyCompatibleProviderIntegrationTest.kt), [`SubscriptionProviderRepositoryIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryIntegrationTest.kt), and the applier tests linked above |
| Phone or TV product flow | The product UI test for the changed trigger and visible result |
