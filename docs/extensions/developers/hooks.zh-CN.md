# 选择 Hook

[English](hooks.md) · [插件开发指南](README.zh-CN.md)

本页区分“`:extension:api` 中已经存在的契约”和“当前真正可用的宿主链路”。

## 状态说明

- **预览可用**：外部 APK 已有宿主 UI 或导入行为，可以用参考插件实际验证。
- **部分接通**：已有一部分宿主链路，但仍缺少重要消费端、平台或故障处理。
- **仅内置插件**：Emby/Jellyfin 已通过内置插件使用，但外部 APK 还无法完成同样的产品流程。
- **仅有契约**：已有类型和传输测试，但应用中没有产品入口。

## 当前状态

| Hook | 用途 | 外部 APK 状态 |
| --- | --- | --- |
| `settings.schema.contribute` | 增加声明式设置分组 | 手机和 TV **预览可用** |
| `search.provider.query` | 为搜索返回频道引用 | **部分接通**，仅手机端 |
| `metadata.channel.enrich` | 建议修改频道标题或分类 | **部分接通**，仅通用 provider 刷新链路 |
| `epg.content.refresh` | 为已有频道返回节目 | **部分接通**，仅通用 provider 刷新链路，失败替换逻辑仍在加固 |
| `subscription.provider.discover` | 声明可用的订阅 provider | **部分接通**，发现后还不能完成外部订阅 |
| `subscription.provider.validate` | 校验登录设置并返回账号描述 | **仅内置插件** |
| `subscription.content.refresh` | 返回 provider 的频道快照 | **仅内置插件** |
| `playback.source.resolve` | 把频道解析为播放源 | **仅内置插件** |
| `playback.session.close` | 关闭 provider 播放 session | **仅内置插件** |
| `background.task.run` | 执行声明的后台任务 | **仅有契约**，宿主尚无调度入口 |

这张表描述当前宿主，而不是最终设计。开始开发依赖“部分接通”Hook 的插件前，请重新确认状态。

## 设置

`settings.schema.contribute` 接收 `SettingsSchemaRequest`，返回若干命名的 `ExtensionSettingSection`。当设置需要随 locale 变化，或者不适合全部写在 manifest schema 中时，可以使用这个 Hook。

手机和 TV 能渲染文本、密码、布尔、数字和单选字段。密码只以 handle 保存。密码设置目前还不能作为网络 broker 的凭据，因此现阶段只适合参考插件所演示的“配置是否存在”一类流程。

## 搜索

`search.provider.query` 接收查询文本和数量上限。每个结果都必须带有 `stableReference`，并且该引用已经对应宿主中的频道。未知频道和已隐藏频道会被忽略。

手机端搜索链路已经接通。当前 UI 最终展示宿主频道，不会使用插件返回的 title、subtitle、artwork 或 metadata。TV 搜索和 continuation token 尚未接通。

## 元数据

`metadata.channel.enrich` 接收宿主已有频道的快照。返回的 `ChannelMetadataPatch` 只能引用本次请求中的频道。

通用 provider 刷新完成后，当前 importer 可以应用允许修改的标题和分类。M3U 与 Xtream 导入还不会调用该 Hook，自由格式 metadata 也没有消费端。

## EPG

`epg.content.refresh` 接收宿主频道引用和时间范围。返回节目的 `channelReference` 必须来自本次请求，节目时间也应位于请求范围内。

当前 Hook 只在通用 provider 刷新后运行，并没有覆盖所有 M3U/Xtream 链路。在按插件隔离失败和替换旧数据的逻辑加固完成前，它还不能承担生产 EPG 链路。

## Subscription provider

Provider 由五个 Hook 组成完整生命周期：

```text
discover -> validate -> refresh -> resolve playback -> close session
```

内置 Emby/Jellyfin 插件已经完成这条链路。外部 APK 仍缺少账号创建前的登录凭据链路、完整 broker 授权、provider 结果校验和端到端参考实现，因此目前不能用于第三方产品。

参考 APK 只声明了 `subscription.provider.discover`。它返回的 provider descriptor 用于验证 IPC，不是可完成订阅的 provider。

## 后台任务

`background.task.run` 已有类型化请求、runtime 策略、Worker 实现和传输层取消测试。但生产代码没有调度该 Worker，manifest 也没有公开的任务调度声明。在增加宿主触发入口和一致性测试前，这个 Hook 仍不可用。

## 契约源码

- 通用贡献 Hook：[`HostHookContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/HostHookContracts.kt)
- Provider Hook：[`SubscriptionProviderContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/subscription/SubscriptionProviderContracts.kt)
- ID、capability、manifest 与 envelope：[`ExtensionContract.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/ExtensionContract.kt)

下一步：[测试插件](testing.zh-CN.md)。
