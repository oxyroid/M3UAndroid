# Build an M3UAndroid extension

[简体中文](README.zh-CN.md) · [Extension docs](../README.md)

An extension handles typed requests from M3UAndroid and returns typed results. External extensions
are currently a developer preview, and the SDK is available as the in-repository
`project(":extension:sdk-android")` module.

## Start here

- [Run Hello](quickstart.md): get a working result before reading the contract reference.
- [Define the manifest](concepts.md): set the extension identity, Hooks, capabilities, and settings.
- [Register a typed Hook](first-hook.md): add one callable feature to an extension.
- [Build a subscription provider](host-broker.md): implement login, refresh, playback, and session
  close.
- [Choose a Hook](hooks.md): find the request, result, capability, and host trigger for each
  supported feature.

Start from [`HelloExtensionService`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt).
For a complete provider, use
[`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt).

## API reference

- [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt)
- [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api)
- [Use the host network broker](reference/provider-broker.md)
- [Contract terms](reference/glossary.md)
- [Test an extension](testing.md)
- [Prepare an update](reference/compatibility.md)
