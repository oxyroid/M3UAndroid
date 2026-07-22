# 刚才那次调用经过了什么

[English](concepts.md) · [插件开发指南](README.zh-CN.md)

你已经运行过动态设置 Hook。现在只给那条链路中的代码命名：

```text
打开 Hello 设置
  -> HostHookSpecs.SettingsSchema
  -> HelloExtensionService
  -> handle { request, context -> result }
  -> SettingsSchemaResult
  -> M3UAndroid 绘制 Device 分组
```

## M3UAndroid 如何发现 Service

[`AndroidManifest.xml`](../../../samples/hello-extension/src/main/AndroidManifest.xml) 把 `HelloExtensionService` 注册为 M3UAndroid 发现和连接的组件。

## ExtensionManifest 是插件说明

[`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt) 中的 `ExtensionManifest` 告诉宿主：

- 插件身份和版本；
- 它实现哪些 Hook；
- 它申请哪些 capability；
- 是否有固定设置字段。

Service 声明提供连接入口，`ExtensionManifest` 提供 M3UAndroid 插件契约。

## HookSpec 固定一次调用的类型

`HostHookSpecs.SettingsSchema` 指定：

- Hook 名称是 `settings.schema.contribute`；
- schema version 是 1；
- request 类型是 `SettingsSchemaRequest`；
- result 类型是 `SettingsSchemaResult`。

因此 `handle(HostHookSpecs.SettingsSchema)` 中不需要手工判断 Hook 字符串或解析 JSON。

## Capability 是用户授权

Hello 声明 `settings.contribute`，因为它会给宿主设置页增加字段。首次启用时，M3UAndroid 把申请原因显示给用户；runtime 只在该 capability 已授权时调用 handler。

Capability 描述“插件可以做哪类事情”，Hook 描述“这一次具体调用什么”。

## 固定设置与动态设置

Hello 同时展示了两种方式：

- **Greeting** 来自 `ExtensionManifest.settingsSchema`，每次都相同。
- **Phone name** 来自 `settings.schema.contribute`，可以根据 request 返回不同字段。

两者都只返回声明式 schema。字段的绘制、校验和保存由 M3UAndroid 完成。

## 内置插件只少了 Android IPC

Emby/Jellyfin 的 handler 在 M3UAndroid 进程内，Hello 的 handler 在独立 APK 中。它们使用相同的 `HookSpec` 和 request/result；APK 路径多了一次 Service 调用。

需要查询 `applicationId`、Service 类名和 `ExtensionId` 的区别时，查看 [术语与插件身份](reference/glossary.zh-CN.md)。下一项功能从 [Hook 目录](hooks.zh-CN.md) 选择。
