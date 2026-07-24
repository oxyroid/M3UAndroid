# 定义插件 Manifest

[English](concepts.md) · [插件开发指南](README.zh-CN.md)

插件由 `TypedExtensionService` 和 `ExtensionManifest` 组成。以 Hello 示例为起点，再把身份
和功能替换为自己的内容。

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

外部插件通过宿主 Broker 访问网络。声明了 `android.permission.INTERNET` 的插件会显示为不兼容。

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

处理函数和声明必须使用同一个 `HookSpec`。`requiredCapabilities` 中的每一项都要有对应
的 `ExtensionCapabilityRequest`，并填写用户能看懂的具体用途。下一页会给出完整的设置
Hook 声明和处理函数。

## 4. 按需声明固定设置

不依赖当前请求的字段写在 `settingsSchema` 中：

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

M3UAndroid 负责显示和保存这些值。如果字段取决于 Hook 请求，例如 `request.surface`，
应由设置 Hook 返回。

发布更新时必须保持插件身份稳定，具体字段见[准备发布或更新](reference/compatibility.zh-CN.md)。

下一步：[注册类型化 Hook](first-hook.zh-CN.md)。
