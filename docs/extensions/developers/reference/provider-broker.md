# Use the provider broker

[简体中文](provider-broker.zh-CN.md) · [Build a subscription provider](../host-broker.md)

Use the `ExtensionHostNetworkBroker` passed to the current handler for provider HTTP calls:

- `authenticate(...)` performs account login and returns an authentication receipt;
- `execute(...)` performs refresh, playback, session-close, and other ordinary requests.

## Capabilities

Declare each capability on the Hook that uses it and in `ExtensionManifest.capabilities`.

| Operation | Capability |
| --- | --- |
| Send a broker request | `network` |
| Use `BrokerValue.Secret` | `credential.read` |
| Call `authenticate(...)` | `credential.write` |

The reference provider's `Validate` Hook requests all three because its login body contains a password handle. Refresh, playback, and close request `network` plus `credential.read`.

## Build an ordinary request

Use `BrokerValue.Literal` for ordinary values and `BrokerValue.Secret` for a credential handle from the current request.

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

For JSON, form, or Base64 values, wrap the value in `BrokerValue.Encoded`. Encoding happens after the broker resolves a secret.

```kotlin
BrokerValue.Encoded(
    value = BrokerValue.Secret(SecretReference(passwordHandle)),
    encoding = BrokerValueEncoding.JsonString,
)
```

Build URLs from the submitted `base_url` during validation and from `request.account.baseUrl` in later Hooks. Use only handles supplied to the current handler call.

## Authenticate

`authenticate(...)` needs one primary credential source. It may also capture provider values required by later requests.

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

`ResponseValueSource` can read a response header or an absolute JSON Pointer. A successful call returns a one-time `ProviderAuthenticationReceipt`. The response body, primary credential, and captured contexts are not returned to the plugin.

Return that receipt from `SubscriptionHookSpecs.Validate`:

```kotlin
SubscriptionProviderValidateResult(
    evidence = ProviderValidationEvidence.HostBrokerReceipt(
        receipt = requireNotNull(response.receipt),
    )
)
```

## Use a captured context

Refer to a captured context by the same key in a later request. The broker resolves its value when sending the request.

```kotlin
headers = mapOf(
    "X-Provider-User" to BrokerValue.Context(
        ContextReference(ProviderAuthenticationContextKeys.UserId)
    )
)
```

Use a context only when the provider protocol requires it. M3UAndroid also uses captured server and user identities to keep accounts stable without exposing those raw values to the plugin.

## Handle responses and errors

- Check every HTTP status before decoding an ordinary response body.
- For an expected provider rejection, return `HookResult.Failure` with a stable extension error code.
- `BrokerException` reports `invalid_request`, `capability_denied`, `scope_denied`, `timeout`, `network_failed`, `response_too_large`, or `internal` when no HTTP response is available.
- Cancellation is delivered as `CancellationException` and must continue to the caller.
- Set `maximumResponseBytes` to a realistic upper bound for that endpoint.
- Keep request bodies, response bodies, and credential handles out of diagnostics.

API types are in [`HostNetworkBrokerContracts.kt`](../../../../extension/api/src/main/kotlin/com/m3u/extension/api/security/HostNetworkBrokerContracts.kt). Working login and refresh requests are in [`ReferenceExtensionService.kt`](../../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt).
