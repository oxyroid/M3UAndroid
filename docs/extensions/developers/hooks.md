# Choose a hook

[简体中文](hooks.zh-CN.md) · [Developer guide](README.md)

This page separates the contract that exists in `:extension:api` from the host path that is usable today.

## Status labels

- **Preview usable**: the external APK path has host UI or import behavior and can be exercised with the reference extension.
- **Partial**: part of the host path exists, but important consumers, platforms, or failure cases are missing.
- **Built-in only**: Emby/Jellyfin use the hook through a built-in extension; an external APK cannot complete the same product flow yet.
- **Contract only**: types and transport tests exist, but the app has no product entry point.

## Current matrix

| Hook | Purpose | External APK status |
| --- | --- | --- |
| `settings.schema.contribute` | Add declarative settings sections | **Preview usable** on phone and TV |
| `search.provider.query` | Return channel references for a search | **Partial**; smartphone only |
| `metadata.channel.enrich` | Suggest channel title/category changes | **Partial**; generic provider refresh only |
| `epg.content.refresh` | Return programmes for existing channels | **Partial**; generic provider refresh only, failure replacement still being hardened |
| `subscription.provider.discover` | Advertise subscription providers | **Partial**; discovery does not yet lead to a complete external subscription |
| `subscription.provider.validate` | Validate login settings and create an account description | **Built-in only** |
| `subscription.content.refresh` | Return a provider channel snapshot | **Built-in only** |
| `playback.source.resolve` | Resolve a channel to a playable source | **Built-in only** |
| `playback.session.close` | Close a provider playback session | **Built-in only** |
| `background.task.run` | Run a declared background task | **Contract only**; no host scheduling entry point |

This table describes the current host, not the intended final platform. Recheck it before starting a plugin that depends on a partial hook.

## Settings

`settings.schema.contribute` receives `SettingsSchemaRequest` and returns named `ExtensionSettingSection` values. Use it when settings differ by locale or cannot all live in the manifest schema.

Phone and TV render text, secret, boolean, number, and single-choice fields. Secret values are stored as handles. A secret setting cannot yet be used as a network-broker credential, so it is currently suitable only for presence/configuration flows demonstrated by the reference extension.

## Search

`search.provider.query` receives a query and limit. Each result must contain a `stableReference` that already identifies a host channel. Unknown or hidden channel references are dropped.

The smartphone search path is connected. The current renderer uses the host channel rather than the extension-provided title, subtitle, artwork, or metadata. TV search and continuation tokens are not connected.

## Metadata

`metadata.channel.enrich` receives snapshots of channels already owned by the host. Return `ChannelMetadataPatch` values only for references from that request.

The current importer can apply approved title and category changes after a generic provider refresh. M3U and Xtream imports do not call this hook yet, and free-form metadata is not consumed.

## EPG

`epg.content.refresh` receives host channel references and a time window. Return programmes whose `channelReference` came from the request and whose times fall inside the requested range.

The hook currently runs after a generic provider refresh. It does not run for every M3U/Xtream path. Production EPG use remains blocked until per-extension failure replacement is hardened.

## Subscription provider

The provider family is a five-hook lifecycle:

```text
discover -> validate -> refresh -> resolve playback -> close session
```

The built-in Emby/Jellyfin extension completes this lifecycle. External APK support is not ready because pre-account login credentials, broker authorization, provider result validation, and end-to-end reference coverage are still being completed.

The reference APK declares only `subscription.provider.discover`. Its provider descriptor is an IPC fixture, not a subscribable provider.

## Background task

`background.task.run` has typed requests, runtime policy, a Worker implementation, and transport-level cancellation coverage. No production code currently schedules that Worker, and the manifest has no public task schedule declaration. The hook remains unavailable until a host trigger and conformance tests are added.

## Contract source

- General contribution specs: [`HostHookContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/HostHookContracts.kt)
- Provider specs: [`SubscriptionProviderContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/subscription/SubscriptionProviderContracts.kt)
- IDs, capabilities, manifest, and envelopes: [`ExtensionContract.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/ExtensionContract.kt)

Next: [Test an extension](testing.md).
