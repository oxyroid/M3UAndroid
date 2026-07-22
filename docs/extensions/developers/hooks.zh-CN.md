# Hook 目录

[English](hooks.md) · [插件开发指南](README.zh-CN.md)

完成 [第一个 Hook](first-hook.zh-CN.md) 后，再用本页选择下一项功能。

- **可用**：已有真实用户入口和可见结果。
- **有限可用**：只有指定宿主流程会调用。
- **尚不可用**：契约存在，但产品链路没有接完。

| 用户目标 | Hook | APK 插件状态 |
| --- | --- | --- |
| 动态生成设置 | `settings.schema.contribute` | **可用** |
| 扩展手机搜索 | `search.provider.query` | **有限可用** |
| 修改频道标题或分类 | `metadata.channel.enrich` | **有限可用** |
| 为频道补充节目单 | `epg.content.refresh` | **有限可用** |
| 提供完整订阅服务 | provider Hook 组 | **尚不可用** |
| 运行后台任务 | `background.task.run` | **尚不可用** |

## 动态设置

- **何时调用：** 用户打开已启用插件的设置页。
- **输入：** 当前语言和界面类型。
- **输出：** 声明式设置分组；界面由 M3UAndroid 绘制。
- **Capability：** `settings.contribute`。
- **示例：** [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt)。

固定不变的设置可以直接写入 `ExtensionManifest.settingsSchema`，不需要 Hook。

## 手机搜索

- **何时调用：** 用户在手机搜索页输入内容。
- **输入：** 查询文字和结果上限。
- **输出：** 频道的稳定引用。
- **宿主如何使用：** M3UAndroid 只把返回的引用解析为本地已有且当前可见的频道。
- **Capability：** `search.read`。

## 频道信息与节目单

这两个 Hook 目前只在通用 provider 刷新之后调用，不会在普通 M3U 或 Xtream 导入后调用。

`metadata.channel.enrich` 返回本次请求中频道的标题或分类修改，需要 `metadata.write`。`epg.content.refresh` 返回指定频道和时间窗口内的节目，需要 `epg.read`。

EPG 调用失败时，宿主保留该插件上一次成功的数据；成功返回空列表时，只清除该插件自己的节目单。

## 完整订阅服务

完整 provider 需要依次支持：

```text
列出服务 -> 验证账号 -> 刷新频道 -> 解析播放地址 -> 关闭播放会话
```

Emby/Jellyfin 内置插件已经走通。APK 插件目前还没有可用的登录、订阅和播放端到端入口，因此这一组 Hook 不能作为可发布功能。

## 后台任务

`background.task.run` 的请求和返回类型已经存在，但宿主尚未调度它。

请求和返回类型可在 [`HostHookContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/HostHookContracts.kt) 与 [`SubscriptionProviderContracts.kt`](../../../extension/api/src/main/kotlin/com/m3u/extension/api/subscription/SubscriptionProviderContracts.kt) 查询。
