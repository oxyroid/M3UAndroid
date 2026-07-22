# Choose a Hook

[简体中文](hooks.zh-CN.md) · [Developer guide](README.md)

Choose by the M3UAndroid feature that should call your code. Declare and register only the Hooks you implement.

| Your feature | HookSpec | Required capability | When M3UAndroid calls it |
| --- | --- | --- | --- |
| Add settings dynamically | `HostHookSpecs.SettingsSchema` | `settings.contribute` | The user opens the enabled extension's settings. |
| Contribute phone search results | `HostHookSpecs.SearchProvider` | `search.read` | The user searches on the phone search screen. |
| Enrich channel metadata | `HostHookSpecs.MetadataEnrichment` | `metadata.write` | After a provider subscription refresh imports channels. |
| Contribute programme entries | `HostHookSpecs.EpgRefresh` | `epg.read` | After a provider subscription refresh requests programme data. |

Subscription providers use a separate five-Hook lifecycle described in [Build a subscription provider](host-broker.md).

## What each Hook receives and returns

### Dynamic settings

Input: `SettingsSchemaRequest` with the locale and UI surface.

Output: `SettingsSchemaResult` containing declarative sections. M3UAndroid renders and stores the fields. Put settings that never change in `ExtensionManifest.settingsSchema` instead.

Example: [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt).

### Phone search

Input: `SearchProviderRequest` with the query, result limit, and optional continuation token.

Output: `SearchProviderResult`. Return stable references for channels already known to the host. M3UAndroid displays only references it can resolve to currently visible channels.

### Channel metadata

Input: `MetadataEnrichmentRequest` containing the channels being refreshed.

Output: `MetadataEnrichmentResult` with patches keyed by `stableReference`. Return patches only for channels present in the request.

### Programme guide

Input: `EpgRefreshRequest` containing source IDs and the requested time window.

Output: `EpgRefreshResult`. A failed call preserves the extension's previous contribution; a successful empty programme list clears that contribution.

## Result rules

- Return only entries related to the current request.
- Keep `stableReference` values stable between calls.
- Use `HookResult.Failure` for an expected failure; do not return a partly valid result.
- Do not include credentials or user-identifying request data in result metadata or diagnostics.

Read the exact fields and current schema versions in [`HostHookContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/HostHookContracts.kt).
