# Understand and modify your first Hook

[简体中文](first-hook.zh-CN.md) · [Developer guide](README.md)

The **Phone name** field in Hello settings is not hard-coded in the extension manifest. When the settings screen opens, M3UAndroid invokes `settings.schema.contribute`, and Hello returns a field for the current UI surface.

This page uses only the three relevant parts of [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt).

## 1. Declare the Hook

```kotlin
hooks = setOf(
    ExtensionHookDeclaration(
        hook = HostHookSpecs.SettingsSchema.hook,
        schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
        requiredCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
    )
)
```

This tells the host that Hello implements the dynamic-settings Hook and that it can run only after the user grants `settings.contribute`.

The manifest also explains why the extension requests that capability:

```kotlin
capabilities = setOf(
    ExtensionCapabilityRequest(
        capability = ExtensionCapabilityIds.SettingsContribute,
        reason = "Add settings for the current device type",
    )
)
```

## 2. Register a typed handler

```kotlin
handle(HostHookSpecs.SettingsSchema) { request, _ ->
    val (fieldLabel, defaultValue) = when (request.surface) {
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
                            label = fieldLabel,
                            type = ExtensionSettingType.TEXT,
                            defaultValue = JsonPrimitive(defaultValue),
                        )
                    ),
                ),
            )
        )
    )
}
```

`request` is already a `SettingsSchemaRequest`, and the return value must be a `SettingsSchemaResult`. `TypedExtensionService` converts between these types and the extension transport.

## 3. Change the Hook result

Change the phone branch to:

```kotlin
"phone" -> "Handset name" to "My handset"
```

Deploy the updated sample, refresh the extension page, and open Hello settings again. **Phone name** should become **Handset name**.

This edit adds no Hook or capability, so it needs no reauthorization. If an update adds a required capability, the host disables the old grant until the user confirms it.

## What just happened

```text
open Hello settings
  -> M3UAndroid creates SettingsSchemaRequest
  -> Hello's typed handler runs
  -> returns SettingsSchemaResult
  -> M3UAndroid renders the Device section
```

Other Hooks follow the same basic shape: declare them in the manifest, register a handler in `TypedExtensionService`, receive a typed request, and return a typed result.

Next: [Attach names to this call path](concepts.md), or jump to the [Hook catalog](hooks.md).
