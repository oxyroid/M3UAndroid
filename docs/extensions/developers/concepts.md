# Define the extension manifest

[简体中文](concepts.zh-CN.md) · [Developer guide](README.md)

You need one `TypedExtensionService` and one `ExtensionManifest`. Copy the Hello files first, then replace the example identity and features with your own.

## 1. Add the SDK

```kotlin
dependencies {
    implementation(project(":extension:sdk-android"))
}
```

## 2. Declare the Service

Add this Service entry to the extension's [`AndroidManifest.xml`](../../../samples/hello-extension/src/main/AndroidManifest.xml), changing only the class name:

```xml
<service
    android:name=".HelloExtensionService"
    android:exported="true"
    android:permission="com.m3u.permission.BIND_EXTENSION_HOST">
    <intent-filter>
        <action android:name="com.m3u.extension.action.BIND_EXTENSION" />
    </intent-filter>
</service>
```

The declared class must extend `TypedExtensionService`:

```kotlin
class HelloExtensionService : TypedExtensionService() {
    override val extensionManifest = ExtensionManifest(
        id = ExtensionId("com.m3u.samples.hello"),
        displayName = "Hello Extension",
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        hooks = emptySet(),
        capabilities = emptySet(),
        metadata = mapOf("developer" to "M3UAndroid sample"),
    )
}
```

Replace these values:

| Value | What to enter |
| --- | --- |
| `id` | A lowercase, stable ID owned by your extension. |
| `displayName` | The name shown by M3UAndroid. |
| `extensionVersion` | This extension build's version. |
| `apiRange` | The M3UAndroid extension API range this build supports. |
| `metadata["developer"]` | The developer name shown with the extension. |

## 3. Declare each Hook you implement

For example, a settings Hook needs this manifest declaration:

```kotlin
hooks = setOf(
    ExtensionHookDeclaration(
        hook = HostHookSpecs.SettingsSchema.hook,
        schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
        requiredCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
    )
)
capabilities = setOf(
    ExtensionCapabilityRequest(
        capability = ExtensionCapabilityIds.SettingsContribute,
        reason = "Add settings for the current device type",
    )
)
```

The handler and declaration must use the same `HookSpec`. Every item in `requiredCapabilities` must also have an `ExtensionCapabilityRequest` with a concrete user-facing reason.

## 4. Declare fixed settings, if any

Put fields that do not depend on the current request in `settingsSchema`:

```kotlin
settingsSchema = ExtensionSettingSchema(
    version = 1,
    fields = listOf(
        ExtensionSettingField(
            key = "greeting",
            label = "Greeting",
            type = ExtensionSettingType.TEXT,
            defaultValue = JsonPrimitive("Hello from my extension"),
        )
    ),
)
```

M3UAndroid renders and stores these values. Use the settings Hook instead when fields depend on its request, such as `request.surface`.

Keep the extension identity stable when publishing an update. The exact identity fields are listed in [Prepare a release or update](reference/compatibility.md).

Next: [register the typed Hook](first-hook.md).
