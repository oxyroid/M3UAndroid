# Register a typed Hook

[简体中文](first-hook.zh-CN.md) · [Developer guide](README.md)

A Hook is one function M3UAndroid can call. Its `HookSpec` fixes the request type, result type, and schema version. This example adds one setting based on the current UI surface.

## 1. Add the Hook to the manifest

Add the Hook and its capability to `ExtensionManifest`:

```kotlin
hooks = setOf(
    ExtensionHookDeclaration(
        hook = HostHookSpecs.SettingsSchema.hook,
        schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
        requiredCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
    )
),
capabilities = setOf(
    ExtensionCapabilityRequest(
        capability = ExtensionCapabilityIds.SettingsContribute,
        reason = "Add settings for the current device type",
    )
),
```

Use `HostHookSpecs.SettingsSchema.hook` and `.schemaVersion` directly so the declaration follows the selected contract.

## 2. Return the typed result

Register handlers in the `TypedExtensionService` initializer:

```kotlin
init {
    handle(HostHookSpecs.SettingsSchema) { request, _ ->
        val (label, defaultValue) = when (request.surface) {
            "phone" -> "Phone name" to "My phone"
            "tv" -> "TV name" to "My TV"
            else -> "Device name" to "My device"
        }
        SettingsSchemaResult(
            sections = listOf(
                ExtensionSettingSection(
                    id = "device",
                    title = "Device",
                    schema = ExtensionSettingSchema(
                        version = 1,
                        fields = listOf(
                            ExtensionSettingField(
                                key = "name",
                                label = label,
                                type = ExtensionSettingType.TEXT,
                                defaultValue = JsonPrimitive(defaultValue),
                            )
                        ),
                    ),
                )
            )
        )
    }
}
```

For this `HookSpec`, `request` is `SettingsSchemaRequest` and the returned value must be `SettingsSchemaResult`. The SDK handles serialization.

## 3. Use the call context only when needed

The second handler argument is `ExtensionCallContext`:

| Property | Use |
| --- | --- |
| `invocationId` | Correlate diagnostics for one call. |
| `grantedCapabilities` | Capabilities declared by this Hook and approved for this call. Capabilities from other Hooks are absent. |
| `settings.values` | Read non-secret settings saved by the host. |
| `settings.credentialHandles` | Read opaque handles for secret settings. |

If the second handler argument is named `context`, read a setting with its qualified key:

```kotlin
val key = ExtensionSettingKeys.qualified("manifest", "greeting")
val greeting = context.settings.values[key]
```

Use the section ID returned by a dynamic settings Hook instead of `manifest`. Secret settings use
the same qualified key in `credentialHandles`; their plaintext is not available to the extension.

Use `handleResult(...)` if the Hook can return an expected validation or domain failure. Return
`HookResult.Failure(ExtensionError(...))` for that case. Do not catch coroutine cancellation.

## Common contract errors

- A handler is registered but the Hook is missing from the manifest.
- The declaration does not use the selected `HookSpec.schemaVersion`.
- A required capability is missing from `manifest.capabilities`.
- The same Hook is registered twice.
- The handler returns a result from another `HookSpec`.

After adding the handler, repeat the [Hello acceptance check](quickstart.md#2-check-the-result-in-m3uandroid).

Next: [choose another Hook](hooks.md). Provider extensions should continue with [Build a subscription provider](host-broker.md).
