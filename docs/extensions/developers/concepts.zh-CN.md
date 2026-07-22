# 定义插件 manifest

[English](concepts.md) · [插件开发指南](README.zh-CN.md)

插件需要一个 `TypedExtensionService` 和一个 `ExtensionManifest`。先复制 Hello 的两个文件，再把示例身份和功能替换为自己的内容。

## 1. 添加 SDK

```kotlin
dependencies {
    implementation(project(":extension:sdk-android"))
}
```

## 2. 声明 Service

把以下 Service 声明加入插件的 [`AndroidManifest.xml`](../../../samples/hello-extension/src/main/AndroidManifest.xml)，只替换类名：

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

该类必须继承 `TypedExtensionService`：

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

需要替换的值：

| 值 | 填写内容 |
| --- | --- |
| `id` | 插件自有的小写稳定 ID。 |
| `displayName` | M3UAndroid 中显示的名称。 |
| `extensionVersion` | 当前插件构建的版本。 |
| `apiRange` | 当前构建支持的 M3UAndroid 插件 API 范围。 |
| `metadata["developer"]` | 随插件显示的开发者名称。 |

## 3. 声明每个已实现 Hook

例如，设置 Hook 需要以下 manifest 声明：

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

handler 与声明必须使用同一个 `HookSpec`。`requiredCapabilities` 中的每一项都必须有对应的 `ExtensionCapabilityRequest`，并填写用户能看懂的具体用途。

## 4. 按需声明固定设置

不依赖当前 request 的字段写在 `settingsSchema` 中：

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

M3UAndroid 负责显示和保存这些值。如果字段取决于 Hook request，例如 `request.surface`，应改用设置 Hook 返回字段。

发布更新时必须保持插件身份稳定，具体字段见[准备发布或更新](reference/compatibility.zh-CN.md)。

下一步：[注册类型化 Hook](first-hook.zh-CN.md)。
