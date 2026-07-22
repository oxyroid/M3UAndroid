# 选择 Hook

[English](hooks.md) · [插件开发指南](README.zh-CN.md)

按照需要调用插件代码的 M3UAndroid 功能选择 Hook。只声明并注册实际实现的 Hook。

| 需要实现的功能 | HookSpec | 必要 capability | M3UAndroid 调用时机 |
| --- | --- | --- | --- |
| 动态添加设置 | `HostHookSpecs.SettingsSchema` | `settings.contribute` | 用户打开已启用插件的设置页。 |
| 提供手机搜索结果 | `HostHookSpecs.SearchProvider` | `search.read` | 用户在手机搜索页搜索。 |
| 补充频道信息 | `HostHookSpecs.MetadataEnrichment` | `metadata.write` | provider 订阅刷新导入频道后。 |
| 提供节目单 | `HostHookSpecs.EpgRefresh` | `epg.read` | provider 订阅刷新请求节目数据时。 |

订阅 provider 使用独立的五个 Hook 生命周期，见[开发订阅 provider](host-broker.zh-CN.md)。

## 每个 Hook 的输入与输出

### 动态设置

输入：`SettingsSchemaRequest`，包含 locale 与界面类型。

输出：`SettingsSchemaResult`，包含声明式设置分组。M3UAndroid 负责绘制和保存字段。固定不变的设置应直接写入 `ExtensionManifest.settingsSchema`。

示例：[`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt)。

### 手机搜索

输入：`SearchProviderRequest`，包含查询文字、结果上限与可选 continuation token。

输出：`SearchProviderResult`。返回宿主已知频道的稳定引用；M3UAndroid 只显示能解析为当前可见频道的引用。

### 频道信息

输入：`MetadataEnrichmentRequest`，包含本次刷新的频道。

输出：`MetadataEnrichmentResult`，patch 通过 `stableReference` 对应频道。只返回 request 中频道的 patch。

### 节目单

输入：`EpgRefreshRequest`，包含 source ID 与请求的时间范围。

输出：`EpgRefreshResult`。调用失败时保留插件上一次贡献；成功返回空节目列表时清除该贡献。

## Result 规则

- 只返回与当前 request 有关的条目；
- `stableReference` 在多次调用之间保持稳定；
- 预期内的失败返回 `HookResult.Failure`，不要返回部分有效的 result；
- result metadata 或 diagnostics 中不要包含凭据，也不要写入能识别用户的 request 数据。

精确字段与当前 schema version 见 [`HostHookContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/HostHookContracts.kt)。
