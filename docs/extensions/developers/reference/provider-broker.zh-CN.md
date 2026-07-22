# 使用 Provider Broker

[English](provider-broker.md) · [开发订阅 Provider](../host-broker.zh-CN.md)

Provider 的 HTTP 请求使用当前 Handler 收到的 `ExtensionHostNetworkBroker`：

- `authenticate(...)` 用于账号登录，返回认证回执；
- `execute(...)` 用于刷新、播放、关闭 Session 等普通请求。

## Capability

每项 Capability 都要加入使用它的 Hook 声明，并同时加入 `ExtensionManifest.capabilities`。

| 操作 | Capability |
| --- | --- |
| 发送 Broker 请求 | `network` |
| 使用 `BrokerValue.Secret` | `credential.read` |
| 调用 `authenticate(...)` | `credential.write` |

参考 Provider 的 `Validate` Hook 会申请以上三项，因为登录正文使用了密码句柄。刷新、播放和关闭只需要 `network` 与 `credential.read`。

## 构造普通请求

普通值使用 `BrokerValue.Literal`；当前 Request 提供的凭据句柄使用 `BrokerValue.Secret`。

```kotlin
val response = broker.execute(
    BrokeredHttpRequest(
        method = "GET",
        url = request.account.baseUrl + "/channels",
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
    )
)
```

值需要按 JSON、表单或 Base64 编码时，使用 `BrokerValue.Encoded`。Broker 会先解析凭据，再执行编码。

```kotlin
BrokerValue.Encoded(
    value = BrokerValue.Secret(SecretReference(passwordHandle)),
    encoding = BrokerValueEncoding.JsonString,
)
```

认证阶段的 URL 从本次提交的 `base_url` 构造；后续 Hook 从 `request.account.baseUrl` 构造。每次 Handler 只使用当前 Request 提供的句柄。

## 认证

`authenticate(...)` 必须指定一个主要访问凭据，也可以保存后续请求需要的 Provider 上下文。

```kotlin
val response = broker.authenticate(
    BrokerAuthenticationRequest(
        exchange = BrokerHttpExchange(
            method = "POST",
            url = baseUrl + "/login",
            headers = mapOf(
                "Content-Type" to BrokerValue.Literal("application/json"),
            ),
            body = loginBody,
        ),
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

`ResponseValueSource` 可以读取响应 Header，也可以使用绝对 JSON Pointer。调用成功后只返回一次性的 `ProviderAuthenticationReceipt`；响应正文、主要凭据和保存的上下文都不会返回给插件。

从 `SubscriptionHookSpecs.Validate` 返回这张回执：

```kotlin
SubscriptionProviderValidateResult(
    evidence = ProviderValidationEvidence.HostBrokerReceipt(
        receipt = requireNotNull(response.receipt),
    )
)
```

## 使用已保存的上下文

后续请求通过相同的 Key 引用上下文。Broker 会在发送请求时解析实际值。

```kotlin
headers = mapOf(
    "X-Provider-User" to BrokerValue.Context(
        ContextReference(ProviderAuthenticationContextKeys.UserId)
    )
)
```

只有 Provider 协议确实需要时才发送上下文。M3UAndroid 还会使用保存的服务器和用户身份稳定地识别账号，但不会把原始值交给插件。

## 处理响应与错误

- 解码普通响应正文前，先检查 HTTP 状态；
- 对预期内的 Provider 拒绝，返回带稳定错误码的 `HookResult.Failure`；
- 无法取得 HTTP 响应时，`BrokerException` 会报告 `invalid_request`、`capability_denied`、`scope_denied`、`timeout`、`network_failed`、`response_too_large` 或 `internal`；
- 取消会以 `CancellationException` 传递，继续交给调用方；
- 按接口实际响应大小设置 `maximumResponseBytes`；
- 诊断信息中不要写入请求正文、响应正文或凭据句柄。

API 类型见 [`HostNetworkBrokerContracts.kt`](../../../../extension/api/src/main/kotlin/com/m3u/extension/api/security/HostNetworkBrokerContracts.kt)。可运行的登录与刷新请求见 [`ReferenceExtensionService.kt`](../../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt)。
