# Verify extension behavior

[简体中文](testing.zh-CN.md) · [Developer guide](README.md)

An extension feature is complete when its Hook produces the expected result at the M3UAndroid product entry that invokes it.

## Verify through the product entry

After deploying a change, refresh **Settings → Playlist management → Extension plugins**, then trigger the Hook from the product surface listed in the [Hook catalog](hooks.md). Use **Reauthorize** only when the extension adds a required capability.

| Hook area | Acceptance result |
| --- | --- |
| Settings | The extension settings page renders the returned sections and reloads saved values. |
| Phone search | Returned stable references promote existing visible channels in phone search. |
| Metadata and EPG | A generic provider refresh applies contributions only to the requested channels; a failed EPG call preserves the previous successful contribution. |
| Provider and background work | These APK product paths are not available yet; current calls verify the contract only. |

## Read M3UAndroid extension state

- **Incompatible:** the extension API range or a declared Hook schema is unsupported.
- **Unhealthy:** consecutive Hook failures reached the runtime threshold; after correcting the handler, disable and enable the extension to retry it.

SDK tests verify the typed handler. Product acceptance is the visible host result described above.

See [Publish and upgrade](reference/compatibility.md) for release constraints. Host-platform changes use the [maintainer validation evidence](../maintainers/change-guide.md#validation-evidence).
