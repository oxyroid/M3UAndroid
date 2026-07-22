# Build an M3UAndroid extension

[简体中文](README.zh-CN.md) · [Extension docs](../README.md)

An extension implements typed requests from M3UAndroid and returns typed results. This guide covers the in-repository development preview: extension modules currently use `project(":extension:sdk-android")`.

## Start here

- [Run Hello](quickstart.md): see a result first, then define its manifest and handler.
- [Build a subscription provider](host-broker.md): implement discovery, login, refresh, playback, and close.
- [Choose a Hook](hooks.md): find the request, result, capability, and host trigger for an existing extension.

The two examples to copy are:

- [`HelloExtensionService`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt): the smallest extension;
- [`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt): a complete subscription provider.

## API reference

- [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt)
- [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api)
- [Contract terms](reference/glossary.md)
- [Test an extension](testing.md)
- [Prepare an update](reference/compatibility.md)
