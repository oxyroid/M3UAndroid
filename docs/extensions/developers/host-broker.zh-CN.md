# 开发订阅 Provider

[English](host-broker.md) · [插件开发指南](README.zh-CN.md)

一个订阅 Provider 只需要提供一份表单和五个类型化 Hook。表单界面、账号存储、刷新调度、频道导入和播放 Session 恢复都由 M3UAndroid 负责。

完整实现见 [`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt)。

## 五个 Hook

| HookSpec | 输入 | 返回 |
| --- | --- | --- |
| `SubscriptionHookSpecs.Discover` | Locale | Provider 名称、可选类型和表单 Schema |
| `SubscriptionHookSpecs.Validate` | 用户选择的类型和提交值 | 宿主认证成功回执 |
| `SubscriptionHookSpecs.Refresh` | 账号、凭据句柄、刷新原因和上次同步信息 | Source 和完整频道快照 |
| `SubscriptionHookSpecs.ResolvePlayback` | 账号、凭据句柄、播放引用和播放偏好 | 播放 URL、Header 和可选 Session |
| `SubscriptionHookSpecs.ClosePlayback` | 账号、凭据句柄、播放引用、Session 和关闭原因 | 关闭结果 |

在 `TypedExtensionService` 中注册每个 Hook，并在 `ExtensionManifest` 中声明同一个 Hook 及其 Schema Version。

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

## 1. 声明 Provider 和表单

`Discover` 返回 `SubscriptionProviderDescriptor`。`providerId` 和每个 `ProviderKind` 在后续版本中必须保持稳定。按显示顺序列出可选类型，并声明认证时会读取的全部字段。

```kotlin
SubscriptionProviderDescriptor(
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
```

Schema 必须包含必填的 `base_url` 文本字段。密码或 Token 使用 `SECRET` 字段。普通文本位于 `request.settingValues`，敏感字段位于 `request.credentialHandles`。

## 2. 认证账号

`Validate` 通过 `broker.authenticate(...)` 发送登录请求。需要告诉 Broker：登录响应中的访问凭据位于哪里，以及后续请求还要使用哪些不透明上下文。

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

返回值只有状态码和回执。M3UAndroid 使用回执创建账号并保存凭据；插件不解析、也不返回登录响应正文。

请求值、上下文、Capability 和错误处理见 [使用 Provider Broker](reference/provider-broker.zh-CN.md)。

## 3. 返回完整刷新快照

`Refresh` 返回一个 `SubscriptionSourceDescriptor` 和当前完整的 `SubscriptionChannelDescriptor` 列表。每个频道都需要稳定的 `remoteId` 和 `PlaybackReference`。

M3UAndroid 会用完整快照更新 Provider 数据，同时保留宿主管理的频道本地状态。`syncMetadata` 只保存下次刷新能够理解的数据。

## 4. 解析并关闭播放

`ResolvePlayback` 把收到的 `PlaybackReference` 转成 `PlaybackSourceResolveResult`。返回播放 URL、必要 Header、适用时的媒体源 ID，以及服务端开启 Session 时的 `PlaybackSessionDescriptor`。

只要返回了 Session，`ClosePlayback` 就会收到同一个 Descriptor。关闭操作必须幂等：远端 Session 已经关闭时也返回成功。

## 验收标准

以下五项全部成立，Provider 才算完成：

1. 每个已声明类型都能打开对应表单；
2. 合法输入可以创建一个账号并导入频道；
3. 刷新会替换远端快照，但不会丢失频道本地状态；
4. 频道能以所需 Header 解析并播放；
5. 停止播放后，远端 Session 已关闭。

失败场景和更新检查见[测试插件](testing.zh-CN.md)。
