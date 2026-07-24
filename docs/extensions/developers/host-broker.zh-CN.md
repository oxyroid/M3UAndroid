# 开发订阅 Provider

[English](host-broker.md) · [插件开发指南](README.zh-CN.md)

一个订阅 Provider 提供一份连接表单和五个类型化 Hook。M3UAndroid 负责显示表单、保存
账号、调度刷新、导入频道和恢复播放 Session。

完整示例见 [`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt)。

## 五个 Hook

| HookSpec | Schema | 基础 capability | 输入与返回 |
| --- | --- | --- | --- |
| `SubscriptionHookSpecs.Discover` | 3 | 无 | Locale → 一个 Provider Descriptor |
| `SubscriptionHookSpecs.Validate` | 2 | `credential.write` | 提交值 → 宿主认证回执 |
| `SubscriptionHookSpecs.Refresh` | 4 | `subscription.read` | 账号与刷新原因 → Source 和完整频道快照 |
| `SubscriptionHookSpecs.ResolvePlayback` | 4 | `playback.resolve` | 账号与播放引用 → URL、Header 和可选 Session |
| `SubscriptionHookSpecs.ClosePlayback` | 3 | `playback.resolve` | 账号、播放引用与 Session → 关闭结果 |

在 `TypedExtensionService` 中注册每个 `HookSpec`，再在 `ExtensionManifest` 中声明相同的
Hook 和 Schema Version。四个 Hook 会访问服务器，因此使用带 Broker 的处理函数：

```kotlin
init {
    handle(SubscriptionHookSpecs.Discover) { _, _ -> discoverProvider() }
    handleResultWithBroker(SubscriptionHookSpecs.Validate) { request, _, broker ->
        validateProvider(request, broker)
    }
    handleResultWithBroker(SubscriptionHookSpecs.Refresh) { request, _, broker ->
        refreshProvider(request, broker)
    }
    handleResultWithBroker(SubscriptionHookSpecs.ResolvePlayback) { request, _, broker ->
        resolvePlayback(request, broker)
    }
    handleResultWithBroker(SubscriptionHookSpecs.ClosePlayback) { request, _, broker ->
        closePlayback(request, broker)
    }
}
```

`Discover` 离线运行。其他四个 Hook 声明 `network`。Broker 请求使用本次提交或已保存的
凭据句柄时，再声明 `credential.read`。

## 1. 声明 Provider 和表单

`Discover` 返回一个 `SubscriptionProviderDescriptor`。`providerId` 和每个
`ProviderKind` 在不同版本间保持稳定。按显示顺序列出可选类型，并包含登录需要的全部
字段。

```kotlin
SubscriptionProviderDiscoverResult(
    provider = SubscriptionProviderDescriptor(
        providerId = extensionManifest.id,
        displayName = "Example Media Server",
        variants = listOf(
            SubscriptionProviderVariant(
                kind = ProviderKind("example"),
                displayName = "Example",
            )
        ),
        settingsSchema = providerSettings,
    )
)
```

Schema 必须包含必填的 `base_url` 文本字段。密码或 Token 使用 `SECRET` 字段。普通文本位于 `request.settingValues`，敏感字段位于 `request.credentialHandles`。

## 2. 认证账号

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

返回一个 `SubscriptionSourceDescriptor` 和完整频道快照。Schema 4 的 Source 只有
`remoteId` 与 `providerKind`，没有 Title。

```kotlin
SubscriptionSourceDescriptor(
    remoteId = request.account.serverId,
    providerKind = request.account.providerKind,
)
```

每个频道都需要稳定的 `remoteId` 和 `PlaybackReference`。引用中只放稳定 ID。URL、Token
和 Cookie 在 `ResolvePlayback` 中解析。

M3UAndroid 会用完整快照更新 Provider 数据，同时保留宿主管理的频道本地状态。

## 4. 解析并关闭播放

`ResolvePlayback` 返回播放 URL、必要 Header、媒体源 ID 和可选的
`PlaybackSessionDescriptor`。已保存的凭据和账号信息使用 `BrokerValue` 引用；
M3UAndroid 会在发起请求或打开媒体时解析。

只要返回了 Session，`ClosePlayback` 就会收到同一个 Descriptor，以及自己的账号作用域
Broker。关闭操作必须幂等。远端 Session 已经关闭时也返回成功。

## 验收标准

1. `Discover` 返回一个 Descriptor，每个类型都能打开表单；
2. 合法输入完成宿主管理的认证；
3. 首次刷新导入完整快照并创建账号；
4. 频道能使用宿主解析的 Header 播放；
5. 停止播放会关闭远端 Session，重复关闭也成功。

下一步：[通过宿主 Broker 发送认证请求](reference/provider-broker.zh-CN.md)，然后
[测试插件](testing.zh-CN.md)。
