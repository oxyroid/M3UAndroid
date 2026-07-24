# Choose a Hook

[简体中文](hooks.zh-CN.md) · [Developer guide](README.md)

Choose the feature that should call your extension. Declare and register only the Hooks you
implement.

| Feature | HookSpec | Base capability | When it runs |
| --- | --- | --- | --- |
| Add settings dynamically | `HostHookSpecs.SettingsSchema` | `settings.contribute` | The user opens the enabled extension's settings. |
| Add search results | `HostHookSpecs.SearchProvider` | `search.read` | The user searches in the phone app. |
| Change channel titles or categories | `HostHookSpecs.MetadataEnrichment` | `metadata.write` | A playlist or provider refresh imports channels. |
| Add programme entries | `HostHookSpecs.EpgRefresh` | `epg.read` | A playlist or provider refresh requests programme data. |
| Run periodic work | `HostHookSpecs.BackgroundTask` | `background.task` | WorkManager runs a task declared by the extension. |

The base capability in this table is mandatory. Add `network` to the declaration for a Hook that
uses the host network broker. Add `credential.read` as well if that Hook sends a credential handle
through the broker.
M3UAndroid gives a call only the capabilities declared by that Hook and approved by the user.

Search, metadata, and EPG calls may include a provider account. In that case, their network scope
is limited to that account. Calls without an account use the extension's approved origins.
Read [Use the host network broker](reference/provider-broker.md) before making a request.

Subscription providers use a separate five-Hook lifecycle described in
[Build a subscription provider](host-broker.md). Its `Discover` Hook is always offline.

## What each Hook receives and returns

### Dynamic settings

Input: `SettingsSchemaRequest` with the locale and UI surface.

Output: `SettingsSchemaResult` containing declarative sections. M3UAndroid renders and stores the
fields. Put settings that never change in `ExtensionManifest.settingsSchema` instead.

Example: [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt).

### Search

Input: `SearchProviderRequest` with the query, result limit, and an optional provider account.

Output: `SearchProviderResult`. Return stable account and channel IDs for channels already known to
the host. M3UAndroid displays only references it can resolve to visible channels.

### Channel metadata

Input: `MetadataEnrichmentRequest` containing the channels being refreshed and, when applicable,
their provider account.

Output: `MetadataEnrichmentResult` with patches keyed by `stableReference`. Return patches only for
channels present in the request.

### Programme guide

Input: `EpgRefreshRequest` containing source IDs, the requested time window, and, when applicable,
their provider account.

Output: `EpgRefreshResult`. A failed call keeps the extension's previous contribution. A successful
empty programme list clears it.

### Background task

Input: `BackgroundTaskRequest` with the declared task ID and current retry attempt.

Output: `BackgroundTaskResult`. Declare each schedule in `ExtensionManifest.backgroundTasks`:

```kotlin
backgroundTasks = listOf(
    ExtensionBackgroundTaskDeclaration(
        taskId = "catalog.refresh",
        repeatIntervalHours = 24,
        requiresNetwork = true,
    )
)
```

Also declare `HostHookSpecs.BackgroundTask` and `background.task`. A task with
`requiresNetwork = true` must declare `network` on the same Hook. M3UAndroid schedules enabled,
authorized tasks through WorkManager and applies the connected-network constraint.
`repeatIntervalHours` accepts 6 through 168.

## Result rules

- Return only entries related to the current request.
- Keep `stableReference` values stable between calls.
- Use `HookResult.Failure` for an expected failure; do not return a partly valid result.
- Do not include credentials or user-identifying request data in result metadata or diagnostics.

Read the exact fields and current schema versions in
[`HostHookContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/HostHookContracts.kt).
