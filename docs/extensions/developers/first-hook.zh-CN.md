# 读懂并修改第一个 Hook

[English](first-hook.md) · [插件开发指南](README.zh-CN.md)

你在 Hello 设置页看到的 **Phone name** 不是写死在插件 manifest 里的。打开设置页时，M3UAndroid 调用了 `settings.schema.contribute`，Hello 根据当前界面返回了这个字段。

本页只看 [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt) 中与这次调用有关的三处代码。

## 1. 声明要提供的 Hook

```kotlin
hooks = setOf(
    ExtensionHookDeclaration(
        hook = HostHookSpecs.SettingsSchema.hook,
        schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
        requiredCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
    )
)
```

这段声明告诉宿主：Hello 实现了动态设置 Hook，而且只有在用户授予 `settings.contribute` 后才能调用。

manifest 中还要说明为什么申请该 capability：

```kotlin
capabilities = setOf(
    ExtensionCapabilityRequest(
        capability = ExtensionCapabilityIds.SettingsContribute,
        reason = "Add settings for the current device type",
    )
)
```

## 2. 注册类型化 handler

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

`request` 已经是 `SettingsSchemaRequest`，返回值必须是 `SettingsSchemaResult`。`TypedExtensionService` 负责在这些类型与插件 transport 之间转换。

## 3. 改一次 Hook 结果

把手机分支改为：

```kotlin
"phone" -> "Handset name" to "My handset"
```

部署修改后的样例，在插件页点击刷新，再打开 Hello 设置。**Phone name** 应变成 **Handset name**。

这次修改没有新增 Hook 或 capability，所以不需要重新授权。如果新增了必要 capability，宿主会停用旧授权，直到用户确认。

## 刚才发生了什么

```text
打开 Hello 设置
  -> M3UAndroid 创建 SettingsSchemaRequest
  -> Hello 的类型化 handler 运行
  -> 返回 SettingsSchemaResult
  -> M3UAndroid 绘制 Device 分组
```

这就是其他 Hook 也遵循的基本形状：在 manifest 声明，在 `TypedExtensionService` 中注册 handler，接收类型化 request，返回类型化 result。

下一步：[给这条调用链中的代码名词对上号](concepts.zh-CN.md)，或直接查看 [Hook 目录](hooks.zh-CN.md)。
