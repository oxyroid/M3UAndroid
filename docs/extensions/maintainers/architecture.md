# Current architecture and code map

[简体中文](architecture.zh-CN.md) · [Maintainer guide](README.md)

This page answers one question: where does an extension call travel in the current code? Incomplete capabilities are kept in the [status page](status-and-release.md).

## Shared path

```text
user action or Worker
  -> product repository
  -> ExtensionRuntime
  -> extension implementation
  -> product repository receives and applies result
  -> Room, UI, or player
```

There are two extension implementations:

- **Built-in:** the handler runs in the host process, such as Emby/Jellyfin.
- **APK:** the runtime calls a Service in another APK through `AndroidBoundExtensionTransport`.

Both paths meet at `ExtensionRuntime` and use the same `HookSpec<Request, Result>`.

## Ownership by layer

| Layer | Owns | First code to open |
| --- | --- | --- |
| Contract | Extension identity, settings, and Hook request/result types | [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api) |
| Runtime | Registration, versions, capabilities, size, concurrency, timeout, health | [`ExtensionRuntime`](../../../extension/runtime/src/main/kotlin/com/m3u/extension/runtime/ExtensionRuntime.kt) |
| Android discovery | Installed extension Services and signing identity | [`AndroidExtensionDiscovery`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidExtensionDiscovery.kt) |
| Android invocation | Service binding, handshake, invocation, and cancellation | [`AndroidBoundExtensionTransport`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidBoundExtensionTransport.kt) |
| APK SDK | Receive a call in the extension process and run a typed handler | [`ExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/ExtensionService.kt) |
| Plugin lifecycle | Trust, enable, disable, reconnect, reauthorize, diagnostics | [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt) |
| Provider product flow | Discover, validate, refresh, playback, close session | [`SubscriptionProviderRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryImpl.kt) |
| Result application | Validate and write host data, or map to UI/player | [`data/extension`](../../../data/src/main/java/com/m3u/data/extension), [`data/repository/extension`](../../../data/src/main/java/com/m3u/data/repository/extension) |

## One Hook call

For any typed Hook:

1. A repository selects a `HookSpec` and creates a request.
2. The runtime loads the extension ID selected by the caller and confirms that it is enabled and declares the Hook.
3. The runtime checks API/schema, granted capabilities, payload, concurrency, and timeout.
4. A built-in handler runs directly; an APK handler crosses the Android transport.
5. The runtime decodes the result and records success or failure.
6. The repository handles the result according to the current product flow; validation maturity differs by flow.

Step 6 cannot live in the generic runtime. Search results, EPG entries, channel snapshots, and playback URLs each have different ownership and validity rules. Missing checks are listed under [release blockers](status-and-release.md#release-blockers).

## Real example: Emby/Jellyfin refresh

```text
ProviderWorker or user refresh
  -> SubscriptionProviderRepositoryImpl
  -> SubscriptionHookSpecs.Refresh
  -> ExtensionRuntime
  -> EmbyCompatibleProvider
  -> SubscriptionProviderImporter
  -> Room
```

This path is connected today: the repository reads the account and credentials, the built-in provider returns a channel snapshot, and the importer updates that account in one transaction before metadata and EPG contributions run.

When an APK provider eventually replaces the built-in implementation, the repository and importer around `ExtensionRuntime` should not change. Only the extension invocation crosses Android transport.

## External Service registration

```text
discovery finds Service
  -> host verifies one package owns the process and network stays brokered
  -> host reads manifest
  -> user confirms identity and capabilities
  -> repository registers transport
  -> runtime can invoke Hooks
```

These terms refer to distinct states:

- **Discovered:** a Service with the expected entry declaration exists on the device.
- **Enabled:** the user allows the host to call it.
- **Registered:** the current host process has a usable transport.

After an app restart, the bootstrap worker restores extensions that are still trusted and enabled. After an extension update or Binder disconnect, the next plugin-list refresh or restore run makes the repository rebuild registration; the runtime does not own Android Service lifecycle.

## Most important ownership rules

- The runtime safely completes one call; it does not write Room or update UI.
- Repositories/importers decide whether a result belongs to the request and may replace old data.
- The plugin repository owns external APK trust, enablement, and grants.
- The credential vault and Android Keystore own secrets; extension contracts carry handles.

Continue with [Change by task](change-guide.md) before editing code.
