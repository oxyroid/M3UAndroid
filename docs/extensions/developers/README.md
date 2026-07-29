# Build an M3UAndroid extension

[简体中文](README.zh-CN.md) · [Extension docs](../README.md)

An extension handles typed requests from M3UAndroid and returns typed results. External extensions
are currently a developer preview. This checkout can build the `1.0.0-alpha01` SDK as a ZIP
containing a local Maven repository. Extensions run only in the smartphone app's phone and tablet
layouts. The TV app does not load extensions and supports only M3U and Xtream.

## Start here

- [Add the SDK](sdk-installation.md): configure the Maven bundle and build requirements.
- [Run Hello](quickstart.md): get a working result before reading the contract reference.
- [Define the manifest](concepts.md): set the extension identity, Hooks, capabilities, and settings.
- [Register a typed Hook](first-hook.md): add one callable feature to an extension.
- [Build a subscription provider](host-broker.md): implement the complete discover, subscription,
  refresh, browse, playback resolve, playback update, and session-close contract.
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
