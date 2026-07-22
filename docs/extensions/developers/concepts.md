# Understand the extension model

[简体中文](concepts.zh-CN.md) · [Developer guide](README.md)

You only need four pieces to understand an M3UAndroid extension:

1. an Android service makes the APK available to the host;
2. a manifest describes the extension;
3. hooks receive and return typed contract data;
4. M3UAndroid validates and applies each result.

## The service is the entry point

Declare one service in the extension APK:

```xml
<service
    android:name=".ExampleExtensionService"
    android:exported="true"
    android:permission="com.m3u.permission.BIND_EXTENSION_HOST">
    <intent-filter>
        <action android:name="com.m3u.extension.action.BIND_EXTENSION" />
    </intent-filter>
</service>
```

The service supplies an `ExtensionTransport`:

```kotlin
class ExampleExtensionService : ExtensionService() {
    override val transport: ExtensionTransport = ExampleTransport
}
```

Use [`ReferenceExtensionService.kt`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt) as the complete executable example.

## The manifest describes one stable extension

`ExtensionManifest` is read before any hook runs.

| Field | Meaning |
| --- | --- |
| `id` | Stable lowercase identifier, such as `com.example.guide` |
| `displayName` | Name shown in M3UAndroid |
| `extensionVersion` | Your semantic version |
| `apiRange` | Host extension API versions accepted by this APK |
| `hooks` | Hook IDs and schema versions implemented by the APK |
| `capabilities` | Capabilities requested from the user, with a plain-language reason |
| `settingsSchema` | Optional settings shown by the host |
| `metadata["developer"]` | Developer name shown during authorization |

The current host API is `1.0`, and current hook schemas use version `1`. Keep the extension ID stable across upgrades. Increment `extensionVersion` when you ship a new APK.

A hook may list required capabilities only when the same capabilities are requested by the manifest. Declare only hooks that can return a valid response.

## A hook is a typed function

Every published `HookSpec<Request, Result>` contains the hook ID, schema version, and serializers for both directions. Use those serializers instead of defining another JSON shape.

The dispatch pattern is:

```kotlin
val spec = HostHookSpecs.SearchProvider
val input = json.decodeFromJsonElement(spec.requestSerializer, envelope.payload)
val output = SearchProviderResult(/* ... */)

return SerializedExtensionResult(
    invocationId = envelope.invocationId,
    extensionId = manifest.id,
    hook = spec.hook,
    schemaVersion = spec.schemaVersion,
    payload = json.encodeToJsonElement(spec.responseSerializer, output),
)
```

The result must repeat the invocation ID, extension ID, hook, and schema version from the request. Return either a payload or an `ExtensionError`, never both.

## The host applies results

Hook results are contributions, not direct database or player commands. For example:

- search returns stable references that the host resolves to existing channels;
- metadata returns a narrow patch that the host validates;
- EPG returns programme descriptions that the host imports;
- playback returns a source description that the host checks before use.

This keeps extension code independent from M3UAndroid's Room entities and UI models. Check [Choose a hook](hooks.md) before relying on a contribution path; several are still partial in the developer preview.

## Settings

Use `ExtensionSettingSchema` for settings that should appear in M3UAndroid. Supported field types are text, secret, boolean, number, and single choice.

The manifest schema appears under the `manifest` section. A settings-schema hook can add more named sections. Hook requests receive the current values in `ExtensionSettingsSnapshot` using keys such as `manifest/enabled` or `playback/quality`.

Secret fields have no plaintext default. Their snapshot entry is a `CredentialHandle`, while ordinary values appear in `values`. Treat every value as optional because a user can clear it or a schema upgrade can reset it.

## Capabilities

A manifest capability request contains:

- the capability ID;
- a reason written for the person enabling the extension;
- whether the capability is required.

The host may grant a requested capability, leave an optional one ungranted, or reject an incompatible extension. A hook runs only with its granted capabilities. A later APK that adds capabilities may require reauthorization.

## Network access and credentials

The Android SDK exposes `ExtensionHostNetworkBroker` for host-mediated requests. Credentials are represented by handles; hooks do not receive the secret value.

The complete external login and provider-account workflow is not ready for third-party use yet. In particular, pre-account login and secret settings are not currently usable as broker credentials. Treat broker-dependent provider work as experimental until the [provider hooks](hooks.md#subscription-provider) are marked ready.

## Cancellation and errors

- Track work by `InvocationId` and stop it when `cancel` is called.
- Make refresh, close, retry, and cleanup operations idempotent.
- Expect concurrent invocations.
- Treat reason fields as open strings; a newer host may send a value your APK has not seen before.
- Put safe, actionable text in `ExtensionError`; keep credentials and response bodies out of errors and logs.

Next: [Choose a hook](hooks.md).
