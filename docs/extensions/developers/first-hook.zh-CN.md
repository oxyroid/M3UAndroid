# 注册类型化 Hook

[English](first-hook.md) · [插件开发指南](README.zh-CN.md)

一个 Hook 就是一个可由 M3UAndroid 调用的函数。它的 `HookSpec` 固定请求类型、结果类型和
Schema Version。以下示例根据当前界面类型返回一个设置项。

## 1. 把 Hook 加入 Manifest

在 `ExtensionManifest` 中加入 Hook 及其 capability：

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

直接使用 `HostHookSpecs.SettingsSchema.hook` 和 `.schemaVersion`，让声明跟随所选契约。

## 2. 返回类型化结果

在 `TypedExtensionService` 的初始化代码中注册处理函数：

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

这个 `HookSpec` 的请求是 `SettingsSchemaRequest`，返回值必须是
`SettingsSchemaResult`。序列化由 SDK 处理。

## 3. 只在需要时使用调用上下文

处理函数的第二个参数是 `ExtensionCallContext`：

| 属性 | 用途 |
| --- | --- |
| `invocationId` | 关联同一次调用的诊断信息。 |
| `grantedCapabilities` | 当前 Hook 已声明且本次获准的 capability；不包含其他 Hook 的 capability。 |
| `settings.values` | 读取宿主保存的非敏感设置。 |
| `settings.credentialHandles` | 读取敏感设置对应的不透明 handle。 |

将处理函数的第二个参数命名为 `context`，再用“分组 ID + 字段名”组成的完整 Key 读取设置：

```kotlin
val key = ExtensionSettingKeys.qualified("manifest", "greeting")
val greeting = context.settings.values[key]
```

动态设置使用 Hook 返回的分组 ID，不使用 `manifest`。敏感设置以相同完整 Key 存在
`credentialHandles` 中；插件不能取得其明文。

Hook 可能发生预期内的校验或业务失败时，使用 `handleResult(...)`，并返回 `HookResult.Failure(ExtensionError(...))`。不要捕获协程取消。

## 常见契约错误

- 注册了处理函数，却没有在 Manifest 中声明 Hook；
- 声明没有使用所选 `HookSpec.schemaVersion`；
- 必要 capability 没有加入 `manifest.capabilities`；
- 同一个 Hook 注册了两次；
- 处理函数返回了其他 `HookSpec` 的结果。

加入处理函数后，重新执行
[Hello 验收步骤](quickstart.zh-CN.md#2-在-m3uandroid-中检查结果)。

下一步：[选择其他 Hook](hooks.zh-CN.md)。Provider 插件继续阅读
[开发订阅 Provider](host-broker.zh-CN.md)。
