# Change by task

[简体中文](change-guide.zh-CN.md) · [Maintainer guide](README.md)

Before editing code, write the complete path: who creates the request, which Hook is called, who applies the result, and where the user sees the change. A contract with no host caller or result applier is still a placeholder.

Extension changes target only `app/smartphone`, including its phone and tablet layouts. `app/tv`
exposes and invokes no extension capability; it supports only M3U and Xtream.

## Add or change a Hook

Work in this order:

1. Define request, result, and `HookSpec` in [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api). Put general features in `HostHookContracts.kt` and provider features under `subscription/`.
2. Add that official `HookSpec` and its base capabilities to the single [`ExtensionContractCatalog`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/ExtensionContractCatalog.kt) entry list. Its supported-schema and capability views are derived from this list.
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

New provider kinds are driven by descriptors and declarative settings. The generic source selector,
form state, subscription repository, and importer do not branch on concrete `ProviderKind`; only
the provider implementation does. Provider subscriptions are stored as `DataSource.Provider`.
Every provider implements `Discover`, `Validate`, `Refresh`, `Browse`, `ResolvePlayback`,
`UpdatePlayback`, and `ClosePlayback`.

## Change APK discovery, trust, or IPC

| Change | Main files |
| --- | --- |
| AIDL, handshake, callback, or PFD wire behavior | [`:extension:transport-protocol-android`](../../../extension/transport-protocol-android) |
| Service discovery and package identity | [`AndroidExtensionDiscovery`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidExtensionDiscovery.kt) |
| Explicit binding, handshake, PFD, and calls | [`AndroidBoundExtensionTransport`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidBoundExtensionTransport.kt) |
| APK-side Binder implementation | [`ExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/ExtensionService.kt) |
| APK-side typed Hook registration | [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt) |
| Certificate pinning and user authorization | [`ExtensionTrustStore`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/ExtensionTrustStore.kt), [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt) |
| Cross-process fixture | [`testing/extension-reference`](../../../testing/extension-reference) |

Every lifecycle change must cover normal return, cancellation, timeout, Binder death, disable, revocation, and process restart. Use [`ExtensionConnectionStateTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionConnectionStateTest.kt) for connection state and [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt) for the cross-process path.

When changing behavior shared by built-in and APK handlers, update
[`ExtensionConformanceSuite`](../../../extension/conformance/src/main/kotlin/com/m3u/extension/conformance/ExtensionConformanceSuite.kt)
and keep its built-in runtime, SDK backend, and standalone reference-APK adapters green.

## Change the published SDK

Keep the SDK version in the root and standalone Hello properties aligned. Run
`testing/bin/verify-extension-sdk-distribution.sh`; it generates the local Maven repository, checks
the final zip and checksum, rejects host-module references in published metadata, samples required
fixtures, and compiles Hello without project substitution. Both CI workflows upload the resulting
zip and SHA-256 file.

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

Any change to a Hook request, capability, trust rule, broker scope, or persisted result must also
update both versions of the [external APK threat model](threat-model.md). Record the newly exposed
asset or data, the enforcing host boundary, and any risk that remains after the test passes.

## Change smartphone UI

UI observes repository state and sends operations; it does not discover or bind services directly.

Start with [`PlaylistManagementScreen`](../../../app/smartphone/src/main/java/com/m3u/smartphone/ui/business/setting/fragments/PlaylistManagementScreen.kt), [`ExtensionPluginManagementScreen`](../../../app/smartphone/src/main/java/com/m3u/smartphone/ui/business/setting/fragments/ExtensionPluginManagementScreen.kt), and [`ExtensionSettingsScreen`](../../../app/smartphone/src/main/java/com/m3u/smartphone/ui/business/setting/fragments/ExtensionSettingsScreen.kt).

Check full-row clicks, scrollable authorization, visible errors, settings persistence, and adaptive
phone/tablet layouts.

## Validation evidence

| Change | Closest evidence |
| --- | --- |
| API or runtime | [`ExtensionContractTest`](../../../extension/api/src/test/kotlin/com/m3u/extension/api/ExtensionContractTest.kt), [`ExtensionRuntimeTest`](../../../extension/runtime/src/test/kotlin/com/m3u/extension/runtime/ExtensionRuntimeTest.kt) |
| APK SDK and typed handlers | [`TypedExtensionServiceTest`](../../../extension/sdk-android/src/test/java/com/m3u/extension/sdk/android/TypedExtensionServiceTest.kt), [`hello-extension`](../../../samples/hello-extension) |
| SDK archive contents, published metadata, and dependency boundary | `verifyExtensionSdkBundle`, `verifyExtensionSdkRepository`, [`verify-extension-sdk-distribution.sh`](../../../testing/bin/verify-extension-sdk-distribution.sh) |
| Behavior shared by built-in and APK handlers | [`BuiltInExtensionConformanceTest`](../../../extension/runtime/src/test/kotlin/com/m3u/extension/runtime/BuiltInExtensionConformanceTest.kt), [`TypedExtensionConformanceTest`](../../../extension/sdk-android/src/test/java/com/m3u/extension/sdk/android/TypedExtensionConformanceTest.kt), [`ExternalExtensionConformanceIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionConformanceIpcTest.kt) |
| Certificate trust and connection state | [`CertificateSetFingerprintTest`](../../../extension/transport-android/src/test/java/com/m3u/extension/transport/android/CertificateSetFingerprintTest.kt), [`ExtensionTrustStoreTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionTrustStoreTest.kt), [`ExtensionConnectionStateTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionConnectionStateTest.kt) |
| Cross-process discovery, binding, PFD, invocation, and cancellation | [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt), [`ExternalExtensionConformanceIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionConformanceIpcTest.kt), [`extension-reference`](../../../testing/extension-reference) |
| External provider lifecycle | [`ExternalProviderEndToEndTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalProviderEndToEndTest.kt), [`extension-reference`](../../../testing/extension-reference), [`mock-server`](../../../testing/mock-server) |
| Built-in provider and importer | [`EmbyCompatibleProviderIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/extension/emby/EmbyCompatibleProviderIntegrationTest.kt), [`SubscriptionProviderRepositoryIntegrationTest`](../../../data/src/androidTest/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryIntegrationTest.kt), and the applier tests linked above |
| Smartphone product flow | The phone or tablet UI test for the changed trigger and visible result |
