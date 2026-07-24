# 使用宿主网络 Broker

[English](provider-broker.md) · [插件开发指南](../README.zh-CN.md)

插件代码不直接建立网络连接。Hook 通过 `ExtensionHostNetworkBroker` 提交请求，M3UAndroid
检查 Hook、capability、目标地址、大小和超时后再发送。

## 先确定可访问的 Origin

| 调用 | 可访问的 Origin |
| --- | --- |
| `SubscriptionHookSpecs.Discover` | 无。Discover 始终离线。 |
| Provider `Validate` | 本次登录提交的 Origin。 |
| Provider `Refresh`、`ResolvePlayback` 或 `ClosePlayback` | 当前账号的 Base Origin。 |
| 带 provider 账号的搜索、Metadata 或 EPG | 该账号的 Base Origin。 |
| 设置、后台任务，或不带账号的搜索、Metadata、EPG | 插件已获批准的 Origin。 |

带账号的调用只使用该账号的 Origin，不会再加入插件的其他已批准 Origin。

## 在使用网络的 Hook 上声明权限

只有同时满足以下三个条件，Hook 才能使用 Broker：

1. 该 Hook 的 `ExtensionHookDeclaration.requiredCapabilities` 包含 `network`；
2. `ExtensionManifest.capabilities` 申请了 `network`，并且用户已经批准；
3. 本次调用有账号 Origin，或插件至少有一个已批准 Origin。

如果 Broker 请求使用本次提交或已保存的凭据句柄，再为同一个 Hook 加上
`credential.read`。Provider `Validate` 通过 `authenticate(...)` 保存返回的凭据，因此需要
`credential.write`。该 Hook 原本的基础 capability 也必须保留。

例如，需要联网的搜索 Hook 声明为：

```kotlin
ExtensionHookDeclaration(
    hook = HostHookSpecs.SearchProvider.hook,
    schemaVersion = HostHookSpecs.SearchProvider.schemaVersion,
    requiredCapabilities = setOf(
        ExtensionCapabilityIds.SearchRead,
        ExtensionCapabilityIds.Network,
    ),
)
```

每项必要 capability 还要在 manifest 中有对应的 `ExtensionCapabilityRequest`。每次调用
只能得到“该 Hook 已声明且用户已批准”的 capability；其他 Hook 声明的 capability 不会
带进来。

## 让用户批准插件 Origin

服务地址固定时，使用 `ExtensionManifest.networkOrigins`：

```kotlin
networkOrigins = setOf(
    ExtensionNetworkOrigin("https://api.example.com"),
)
```

M3UAndroid 会在用户授权插件时显示这些 Origin。后续版本新增 Origin，不会自动扩大原有
授权范围。

服务地址由用户选择时，使用标记为 `networkOrigin` 的文本设置：

```kotlin
ExtensionSettingField(
    key = "api_origin",
    label = "Server address",
    type = ExtensionSettingType.TEXT,
    required = true,
    networkOrigin = true,
)
```

该字段不能有默认值。用户保存字段时批准当前值；清空字段时撤销批准。如果设置 schema
version 发生变化，用户需要重新保存。

Origin 只能由 `http` 或 `https`、Host 与可选端口组成。不要包含 Path、Query、Fragment、
用户信息或通配符。当前契约不支持 IPv6 Literal。

## 发送请求

普通值使用 `BrokerValue.Literal`。只有本次调用收到的凭据句柄才能放进
`BrokerValue.Secret`：

```kotlin
val response = broker.execute(
    BrokeredHttpRequest(
        method = "GET",
        url = apiOrigin + "/channels",
        headers = mapOf(
            "Authorization" to BrokerValue.Concatenated(
                listOf(
                    BrokerValue.Literal("Bearer "),
                    BrokerValue.Secret(
                        SecretReference(request.credential.handle)
                    ),
                )
            )
        ),
        maximumResponseBytes = 512 * 1024,
    )
)
```

宿主在构造请求时解析 Secret 与 Context 引用，不会把明文值返回给插件。如果解析后的值
需要 JSON String、表单字段或 Base64 编码，使用 `BrokerValue.Encoded`。

如果服务端回显了这些解析后的值，宿主会将其替换为 `***`。对于 JSON 响应，宿主还会遮蔽
以下认证字段的值：`token`、`accessToken`、`refreshToken`、`idToken`、`authToken`、
`bearerToken`、`sessionToken`、`password`、`secret`、`clientSecret`、
`authorization`、`credential` 和 `apiKey`。字段匹配不区分大小写和分隔符。

`nextPageToken`、`continuationToken`、`tokenType`、`tokenExpiry` 等分页字段不属于认证
字段，原值会保留。如果没有内容需要遮蔽，响应正文会保持不变。

解析响应正文前先检查 `response.statusCode`。按照接口的实际响应大小设置
`maximumResponseBytes`。

## 认证 Provider

只有 Provider `Validate` 流程使用 `authenticate(...)`。告诉 Broker 登录请求、返回凭据
的位置，以及后续调用需要的账号值：

```kotlin
val response = broker.authenticate(
    BrokerAuthenticationRequest(
        exchange = loginExchange,
        primaryCredentialSource =
            ResponseValueSource.JsonPointer("/accessToken"),
        opaqueContexts = listOf(
            OpaqueContextCapture(
                key = ProviderAuthenticationContextKeys.UserId,
                source = ResponseValueSource.JsonPointer("/user_id"),
            )
        ),
    )
)
```

`ResponseValueSource` 可以读取响应 Header，也可以使用 RFC 6901 JSON Pointer。调用成功后
只返回 HTTP 状态码和一次性的 `ProviderAuthenticationReceipt`，不返回登录正文或保存的
值。插件把这张回执作为 `SubscriptionHookSpecs.Validate` 的结果返回。

后续 Provider 调用可以按 Key 使用已保存的值：

```kotlin
BrokerValue.Context(
    ContextReference(ProviderAuthenticationContextKeys.UserId)
)
```

## 作用域与生命周期

每个 Broker 作用域只属于一个插件身份、一个 Hook 和一次调用。账号作用域还绑定一个
Provider 账号。Hook 完成或取消时，作用域随即关闭。来自其他插件、Hook、调用或账号的引用
会被拒绝。

首次 URL 与每次重定向都必须保持已批准的 Scheme、Host 和 Port。改变 Origin 会返回
`scope_denied`。

Provider 播放 Header 也可以在 `PlaybackHeaderValue` 中使用 `BrokerValue.Secret` 或
`BrokerValue.Context`。M3UAndroid 在打开媒体前解析这些引用。播放 URL 必须留在账号的
Base Origin。

## 处理失败

- 对预期内的服务端拒绝，返回带稳定插件错误码的 `HookResult.Failure`；
- 无法取得 HTTP 响应时，`BrokerException` 使用 `invalid_request`、
  `capability_denied`、`scope_denied`、`timeout`、`network_failed`、
  `response_too_large` 或 `internal`；
- 让 `CancellationException` 继续传给调用方；
- 诊断信息中不要写入请求正文、响应正文、凭据或凭据句柄。

API 类型见
[`HostNetworkBrokerContracts.kt`](../../../../extension/api/src/main/kotlin/com/m3u/extension/api/security/HostNetworkBrokerContracts.kt)。
完整 Provider 流程见
[`ReferenceExtensionService`](../../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt)。
