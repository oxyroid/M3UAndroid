# Hook catalog

[简体中文](hooks.zh-CN.md) · [Developer guide](README.md)

Use this page to choose the next feature after completing [Your first Hook](first-hook.md).

- **Available:** has a real user trigger and visible result.
- **Limited:** called only from a specific host flow.
- **Not available:** the contract exists, but the product path is incomplete.

| User goal | Hook | APK extension status |
| --- | --- | --- |
| Generate settings dynamically | `settings.schema.contribute` | **Available** |
| Extend phone search | `search.provider.query` | **Limited** |
| Change channel titles or categories | `metadata.channel.enrich` | **Limited** |
| Add programme guide entries | `epg.content.refresh` | **Limited** |
| Provide a complete subscription service | Provider Hook group | **Not available** |
| Run background work | `background.task.run` | **Not available** |

## Dynamic settings

- **Called when:** the user opens settings for an enabled extension.
- **Input:** current language and UI surface.
- **Output:** declarative setting sections; M3UAndroid renders the controls.
- **Capability:** `settings.contribute`.
- **Example:** [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt).

Put settings that never change directly in `ExtensionManifest.settingsSchema`; they do not need a Hook.

## Phone search

- **Called when:** the user types on the phone search screen.
- **Input:** query text and a result limit.
- **Output:** stable channel references.
- **Host behavior:** M3UAndroid resolves returned references to channels that already exist locally and are currently visible.
- **Capability:** `search.read`.

## Channel metadata and programme guide

These Hooks currently run only after a generic provider refresh, not after ordinary M3U or Xtream imports.

`metadata.channel.enrich` returns title or category changes for channels in the request and needs `metadata.write`. `epg.content.refresh` returns programmes for the requested channels and time window and needs `epg.read`.

If an EPG call fails, the host keeps that extension's last successful data. A successful empty list clears only that extension's programmes.

## Complete subscription service

A complete provider must support this sequence:

```text
list service -> validate account -> refresh channels -> resolve playback -> close session
```

The built-in Emby/Jellyfin extension completes it. APK extensions do not yet have a usable end-to-end login, subscription, and playback path, so this Hook group is not a publishable feature.

## Background work

The `background.task.run` request and result exist, but the host does not schedule it yet.

Look up request and result types in [`HostHookContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/HostHookContracts.kt) and [`SubscriptionProviderContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/subscription/SubscriptionProviderContracts.kt).
