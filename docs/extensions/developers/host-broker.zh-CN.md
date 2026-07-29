# 开发订阅 Provider

[English](host-broker.md) · [插件开发指南](README.zh-CN.md)

一个订阅 Provider 提供一份连接表单，并实现七个类型化 Hook：发现、验证订阅、刷新、
媒体浏览、播放解析、播放更新和播放关闭。七个 Hook 缺一不可。M3UAndroid 在 smartphone
应用中负责显示表单、保存账号、调度刷新、导入媒体和恢复播放 Session。

完整示例见 [`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt)。

## Provider Hook

| HookSpec | Schema | 基础 capability | 输入与返回 |
| --- | --- | --- | --- |
| `SubscriptionHookSpecs.Discover` | 1 | 无 | Locale → 一个 Provider Descriptor |
| `SubscriptionHookSpecs.Validate` | 1 | `credential.write` | 提交值 → 订阅验证与宿主认证回执 |
| `SubscriptionHookSpecs.Refresh` | 1 | `subscription.read` | 账号与刷新原因 → Source 和完整频道快照 |
| `SubscriptionHookSpecs.Browse` | 1 | `subscription.read` | 账号、可选父项、Cursor 与数量上限 → 一页媒体 |
| `SubscriptionHookSpecs.ResolvePlayback` | 1 | `playback.resolve` | 账号与播放引用 → URL、Header 和可选 Session |
| `SubscriptionHookSpecs.UpdatePlayback` | 1 | `playback.resolve` | 账号、播放 Session、事件与位置 → 是否接受 |
| `SubscriptionHookSpecs.ClosePlayback` | 1 | `playback.resolve` | 账号、播放引用与 Session → 关闭结果 |

内置 Provider 与外部 Provider 使用同一套完整契约。在 `TypedExtensionService` 中注册
全部 `HookSpec`，再在 `ExtensionManifest` 中声明相同的 Hook 和 Schema Version。需要
联网的 Hook 使用带 Broker 的处理函数：

```kotlin
init {
    handle(SubscriptionHookSpecs.Discover) { request, _ ->
        discoverProvider(request.localeTag)
    }
    handleResultWithBroker(SubscriptionHookSpecs.Validate) { request, _, broker ->
        validateProvider(request, broker)
    }
    handleResultWithBroker(SubscriptionHookSpecs.Refresh) { request, _, broker ->
        refreshProvider(request, broker)
    }
    handleResultWithBroker(SubscriptionHookSpecs.Browse) { request, _, broker ->
        browseProvider(request, broker)
    }
    handleResultWithBroker(SubscriptionHookSpecs.ResolvePlayback) { request, _, broker ->
        resolvePlayback(request, broker)
    }
    handleResultWithBroker(SubscriptionHookSpecs.UpdatePlayback) { request, _, broker ->
        updatePlayback(request, broker)
    }
    handleResultWithBroker(SubscriptionHookSpecs.ClosePlayback) { request, _, broker ->
        closePlayback(request, broker)
    }
}
```

七个 Handler 都必须实现。`Discover` 离线运行；其他 Provider Hook 只有在访问服务器时
才声明 `network`。Broker 请求使用本次提交或已保存的凭据句柄时，再声明
`credential.read`。

## 1. 声明 Provider 和表单

`Discover` 返回一个 `SubscriptionProviderDescriptor`。`providerId` 和每个
`ProviderKind` 在不同版本间保持稳定。按显示顺序列出所有支持的类型，并包含登录需要的
全部字段。

```kotlin
private fun discoverProvider(localeTag: String?): SubscriptionProviderDiscoverResult {
    val copy = providerCopy(localeTag)
    return SubscriptionProviderDiscoverResult(
        provider = SubscriptionProviderDescriptor(
            providerId = extensionManifest.id,
            displayName = copy.providerName,
            variants = listOf(
                SubscriptionProviderVariant(
                    kind = ProviderKind("example"),
                    displayName = copy.variantName,
                )
            ),
            settingsSchema = providerSettings(localeTag),
        )
    )
}
```

Schema 必须包含必填的 `base_url` 文本字段。密码或 Token 使用 `SECRET` 字段。普通文本位于 `request.settingValues`，敏感字段位于 `request.credentialHandles`。

面向用户的名称、Label、说明和选项都应按 `request.localeTag` 本地化，并在缺失或不支持时
回退插件默认语言。返回自然书写顺序的纯文本，不插入双向控制字符；RTL 隔离由宿主处理。
文本应能独立朗读；ID、URL 与 Handle 不翻译。

## 2. 认证并订阅账号

`Validate` 通过 `broker.authenticate(...)` 发送登录请求。需要告诉 Broker：登录响应中的
访问凭据位于哪里，以及 M3UAndroid 应保存哪些服务器或用户 ID 来识别账号。

```kotlin
val response = broker.authenticate(
    BrokerAuthenticationRequest(
        exchange = loginExchange,
        primaryCredentialSource = ResponseValueSource.JsonPointer("/accessToken"),
        opaqueContexts = listOf(
            OpaqueContextCapture(
                key = ProviderAuthenticationContextKeys.ServerId,
                source = ResponseValueSource.JsonPointer("/server_id"),
            ),
            OpaqueContextCapture(
                key = ProviderAuthenticationContextKeys.UserId,
                source = ResponseValueSource.JsonPointer("/user_id"),
            ),
        ),
    )
)

if (response.statusCode !in 200..299) {
    return HookResult.Failure(authenticationError(response.statusCode))
}

return HookResult.Success(
    SubscriptionProviderValidateResult(
        evidence = ProviderValidationEvidence.HostBrokerReceipt(
            receipt = requireNotNull(response.receipt),
        ),
    )
)
```

返回值只有状态码和回执。M3UAndroid 使用回执创建账号并保存凭据；插件不解析、也不返回
登录响应正文。

请求值、上下文、Capability 和错误处理见
[使用宿主网络 Broker](reference/provider-broker.zh-CN.md)。

## 3. 返回完整刷新快照

返回一个 `SubscriptionSourceDescriptor` 和完整频道快照。Source 只有 `remoteId` 与
`providerKind`，没有 Title。

```kotlin
SubscriptionSourceDescriptor(
    remoteId = request.account.serverId,
    providerKind = request.account.providerKind,
)
```

每个频道都需要稳定的 `remoteId` 和 `PlaybackReference`。引用中只放稳定 ID。URL、Token
和 Cookie 在 `ResolvePlayback` 中解析。

M3UAndroid 会用完整快照更新 Provider 数据，同时保留宿主管理的频道本地状态。

## 4. 浏览电影与剧集

`Browse` 使用分页。第一次请求根页面时，同时省略 `parentReference` 和 `cursor`。打开
剧集、季或其他可浏览条目时，把所选条目必填的 `reference` 原样作为下一次请求的
`parentReference`。`cursor` 是不透明且不含敏感信息的续页值。单次最多请求和返回 200 项。

每个 `SubscriptionContentItemDescriptor` 都有可扩展的 `ProviderMediaKind`。SDK 提供
`unknown`、`live`、`movie`、`series`、`season` 与 `episode` 这些常用值，但宿主不能假设
取值只限于这份列表。`playable` 与 `browsable` 至少一个为 true：

- 电影或单集通常可播放；
- 剧集或季可以是 `playable = false`、`browsable = true`；
- 既能直接播放又能打开子项时，两个值都设为 true。

即使条目只能浏览，`reference` 也必须提供。下一次浏览使用它定位父项；当
`playable = true` 时，`ResolvePlayback` 也使用同一个引用。引用中不要放 URL、Token
或 Cookie。

`imageUrl`、`category`、`subtitle`、`overview`、`productionYear`、`seasonNumber` 与
`episodeNumber` 都是可选展示信息，内容应简洁；契约会限制字节数与数值范围。
`nextCursor` 与 `total` 也是可选字段；最后一页返回 `nextCursor = null`。

## 5. 解析、更新并关闭播放

`ResolvePlayback` 返回播放 URL、必要 Header、媒体源 ID 和可选的
`PlaybackSessionDescriptor`，同时返回可扩展的 `PlaybackMethod`。已知时使用
`direct_play`、`direct_stream` 或 `transcode`。已保存的凭据和账号信息使用
`BrokerValue` 引用；M3UAndroid 会在发起请求或打开媒体时解析。

M3UAndroid 通过 `PlaybackSessionUpdateRequest` 发送 `started`、`progress`、`paused`
或 `resumed`。
事件取值可继续扩展。`positionTicks` 不得为负，1 tick 等于 100 纳秒。`playMethod`
使用 `ResolvePlayback` 选定的值，`isPaused` 表示当前暂停状态，返回值说明远端服务是否
接受本次更新。进度更新被拒绝时，不能把本地播放 URL 视为失效。

只要返回了 Session，`ClosePlayback` 就会收到同一个 Descriptor，以及自己的账号作用域
Broker。它还会收到最终的非负 `positionTicks`。关闭操作必须幂等。远端 Session 已经
关闭时也返回成功。

## 验收标准

1. `Discover` 返回一个 Descriptor，每个类型都能打开表单；
2. `Validate` 完成宿主管理的订阅认证；
3. `Refresh` 导入完整快照并创建账号；
4. `Browse` 的根页面和子页面遵守请求上限，并以空 Cursor 正常结束；
5. `ResolvePlayback` 返回可使用宿主解析 Header 播放的媒体；
6. `UpdatePlayback` 上报播放状态；更新被拒绝时不会中断本地播放；
7. `ClosePlayback` 关闭远端 Session，重复调用仍然成功。

下一步：[通过宿主 Broker 发送认证请求](reference/provider-broker.zh-CN.md)，然后
[测试插件](testing.zh-CN.md)。
