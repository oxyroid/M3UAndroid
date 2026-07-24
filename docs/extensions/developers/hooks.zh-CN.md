# 选择 Hook

[English](hooks.md) · [插件开发指南](README.zh-CN.md)

按照“哪个功能需要调用插件”来选择 Hook。只声明并注册实际实现的 Hook。

| 功能 | HookSpec | 基础 capability | 调用时机 |
| --- | --- | --- | --- |
| 动态添加设置 | `HostHookSpecs.SettingsSchema` | `settings.contribute` | 用户打开已启用插件的设置页。 |
| 添加搜索结果 | `HostHookSpecs.SearchProvider` | `search.read` | 用户在手机端搜索。 |
| 修改频道标题或分类 | `HostHookSpecs.MetadataEnrichment` | `metadata.write` | 播放列表或 Provider 刷新导入频道后。 |
| 添加节目单 | `HostHookSpecs.EpgRefresh` | `epg.read` | 播放列表或 Provider 刷新请求节目数据时。 |
| 运行周期任务 | `HostHookSpecs.BackgroundTask` | `background.task` | WorkManager 运行插件声明的任务。 |

表中的基础 capability 必须声明。某个 Hook 需要通过宿主访问网络时，再为该 Hook 加上
`network`；通过 Broker 发送凭据句柄时，再加上 `credential.read`。M3UAndroid 每次调用
只提供“该 Hook 已声明且用户已批准”的 capability。

搜索、频道信息和 EPG 请求可能带有 Provider 账号。带账号时，网络作用域只属于该账号；
不带账号时，使用插件已获批准的 Origin。发起请求前请阅读
[使用宿主网络 Broker](reference/provider-broker.zh-CN.md)。

订阅 Provider 使用另一组五个 Hook，见[开发订阅 Provider](host-broker.zh-CN.md)。其中
`Discover` 始终离线运行。

## 每个 Hook 的输入与输出

### 动态设置

输入：`SettingsSchemaRequest`，包含 locale 与界面类型。

输出：`SettingsSchemaResult`，包含声明式设置分组。M3UAndroid 负责显示和保存字段。固定
不变的设置直接写入 `ExtensionManifest.settingsSchema`。

示例：[`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt)。

### 搜索

输入：`SearchProviderRequest`，包含查询文字、结果上限与可选的 Provider 账号。

输出：`SearchProviderResult`。返回宿主已知频道的稳定账号 ID 和频道 ID。M3UAndroid 只
显示能够解析到可见频道的结果。

### 频道信息

输入：`MetadataEnrichmentRequest`，包含本次刷新的频道，以及可选的 Provider 账号。

输出：`MetadataEnrichmentResult`，每项修改通过 `stableReference` 对应频道。只返回
本次请求中频道的修改。

### 节目单

输入：`EpgRefreshRequest`，包含来源 ID、请求的时间范围与可选的 Provider 账号。

输出：`EpgRefreshResult`。调用失败时保留插件上一次贡献；成功返回空节目列表时清除它。

### 后台任务

输入：`BackgroundTaskRequest`，包含已声明的任务 ID 和当前重试次数。

输出：`BackgroundTaskResult`。在 `ExtensionManifest.backgroundTasks` 中声明调度：

```kotlin
backgroundTasks = listOf(
    ExtensionBackgroundTaskDeclaration(
        taskId = "catalog.refresh",
        repeatIntervalHours = 24,
        requiresNetwork = true,
    )
)
```

同时声明 `HostHookSpecs.BackgroundTask` 和 `background.task`。当
`requiresNetwork = true` 时，同一个 Hook 还必须声明 `network`。插件启用且授权完成后，
M3UAndroid 通过 WorkManager 调度任务，并设置联网约束。`repeatIntervalHours` 的范围是
6 到 168。

## Result 规则

- 只返回与当前请求有关的条目；
- `stableReference` 在多次调用之间保持稳定；
- 预期内的失败返回 `HookResult.Failure`，不要返回部分有效的结果；
- 结果元数据和诊断信息中不要包含凭据，也不要写入能识别用户的请求数据。

精确字段与当前 Schema Version 见
[`HostHookContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/HostHookContracts.kt)。
