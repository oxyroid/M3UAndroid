# Maintain the extension platform

[简体中文](README.zh-CN.md) · [Extension docs](../README.md)

Start from the failure the user can see, then follow the call path to the first owning component. To build an independent APK, use the [extension developer guide](../developers/README.md) instead.

Extension and built-in-provider product surfaces belong only to the smartphone app's phone and
tablet layouts. The TV app has no extension API dependency and supports only M3U and Xtream.

## Where did the flow stop?

An external extension call has four stages:

```text
discover extension Service -> user authorizes -> invoke Hook -> host applies result
```

| Visible symptom | Start here | Closest validation |
| --- | --- | --- |
| Service discovery returns an empty list | [`AndroidExtensionDiscovery`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidExtensionDiscovery.kt) | [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt) |
| It appears, but cannot be enabled or disconnects after an update | [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt), [`AndroidBoundExtensionTransport`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidBoundExtensionTransport.kt) | [`ExtensionTrustStoreTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionTrustStoreTest.kt), [`ExtensionConnectionStateTest`](../../../extension/transport-android/src/androidTest/java/com/m3u/extension/transport/android/ExtensionConnectionStateTest.kt), and [`ExternalExtensionIpcTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalExtensionIpcTest.kt) |
| A Hook times out, is rejected, or becomes unhealthy | [`ExtensionRuntime`](../../../extension/runtime/src/main/kotlin/com/m3u/extension/runtime/ExtensionRuntime.kt), [`ExtensionContractCatalog`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/ExtensionContractCatalog.kt) | [`ExtensionRuntimeTest`](../../../extension/runtime/src/test/kotlin/com/m3u/extension/runtime/ExtensionRuntimeTest.kt) |
| Built-in and APK handlers disagree on request context, schema, capability, or cancellation | The adapter at the serialized invocation boundary | [`ExtensionConformanceSuite`](../../../extension/conformance/src/main/kotlin/com/m3u/extension/conformance/ExtensionConformanceSuite.kt) |
| A standalone provider fails during sign-in, background refresh, or playback | [`SubscriptionProviderRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryImpl.kt); for broker failures, [`HostNetworkBrokerImpl`](../../../data/src/main/java/com/m3u/data/extension/security/HostNetworkBrokerImpl.kt) | [`ExternalProviderEndToEndTest`](../../../app/smartphone/src/androidTest/java/com/m3u/testing/ExternalProviderEndToEndTest.kt) |
| The Hook succeeds but UI, Room, or playback does not change | The calling repository and its importer or renderer | The adjacent repository/importer test |

For Emby/Jellyfin subscription, refresh, or playback failures, start at [`SubscriptionProviderRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryImpl.kt). They are built-in extensions and do not use Android IPC.

## Answer four questions before editing

1. Which user action triggers this flow?
2. Which `HookSpec` does it invoke?
3. Who validates and applies the result?
4. What is the smallest visible acceptance result?

If any answer is missing, the flow usually still lacks a real host caller or result applicator.

## Three boundaries

- An extension returns candidate data; it does not directly modify Room, UI, or the player.
- One extension's failure must not delete another extension's data or its own last successful data.
- Built-in and APK extensions share the same Hook contracts; only the APK path crosses Android IPC.

Use [Current architecture and code map](architecture.md) to trace a complete path, [Change by task](change-guide.md) before editing, and [Extension UI quality gates](ui-quality-gates.md) for any plugin-facing phone or tablet change. Read [Current status and release gate](status-and-release.md) only when deciding whether a capability is open or releasable.

Before changing what an APK can read, call, or retain, review the
[external APK threat model](threat-model.md).
