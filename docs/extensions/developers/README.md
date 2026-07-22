# Build an M3UAndroid extension

[简体中文](README.zh-CN.md)

An M3UAndroid extension is a separate Android APK that contributes typed data or behavior to the app. Start with the reference extension, get it running on a device, and then replace one hook at a time with your own implementation.

## Start here

1. [Run the reference extension](quickstart.md) — build, install, enable, and inspect a working APK.
2. [Understand the extension model](concepts.md) — service, manifest, hooks, settings, and credentials.
3. [Choose a hook](hooks.md) — see what each hook does and how much host support exists today.
4. [Test an extension](testing.md) — local checks, device checks, upgrade checks, and release expectations.

## What is ready today

The external APK platform is a developer preview. The reference extension can be discovered, authorized, invoked, disabled, reauthorized, configured, and diagnosed on phone and TV.

Not every public hook is ready for a third-party product. Declarative settings have the most complete external path. Search, metadata, EPG, provider, and background hooks still have limitations listed in the [hook status](hooks.md). The SDK is also not published as a stable Maven artifact yet.

Preview builds should target the same M3UAndroid source revision. Cross-version compatibility starts when the SDK artifact and version policy are published.

## Reference implementation

The executable example lives in [`:testing:extension-reference`](../../../testing/extension-reference). It is the canonical in-repository example for APK wiring and typed request dispatch. Public contract types live in [`:extension:api`](../../../extension/api), and the Android service base lives in [`:extension:sdk-android`](../../../extension/sdk-android).
