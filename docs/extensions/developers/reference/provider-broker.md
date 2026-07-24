# Use the host network broker

[简体中文](provider-broker.zh-CN.md) · [Developer guide](../README.md)

Extension code does not open network connections itself. A Hook sends a request through
`ExtensionHostNetworkBroker`, and M3UAndroid checks the Hook, capabilities, destination, size, and
timeout before sending it.

## Know which origin the Hook can use

| Call | Allowed origin |
| --- | --- |
| `SubscriptionHookSpecs.Discover` | None. Discovery is offline. |
| Provider `Validate` | The origin submitted for this login. |
| Provider `Refresh`, `ResolvePlayback`, or `ClosePlayback` | The current account's base origin. |
| Search, metadata, or EPG with a provider account | That account's base origin. |
| Settings, background work, or a search/metadata/EPG call without an account | Origins approved for the extension. |

An account-scoped call uses only its account origin. It does not add the extension's other approved
origins.

## Declare access on the Hook that uses it

A Hook can use the broker only when all three conditions are true:

1. Its `ExtensionHookDeclaration.requiredCapabilities` contains `network`.
2. `ExtensionManifest.capabilities` requests `network`, and the user has approved it.
3. The call has an account origin or at least one approved extension origin.

Add `credential.read` to the same Hook when its broker request uses a submitted or saved credential
handle. Provider `Validate` uses `credential.write` because `authenticate(...)` captures the
returned credential.
Keep the Hook's base capability too.

For example, a network-backed search Hook declares:

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

Every required capability must also have an `ExtensionCapabilityRequest` in the manifest.
M3UAndroid gives each invocation only the capabilities declared by that Hook and approved by the
user. A capability declared by another Hook is not available.

## Approve an extension origin

Use `ExtensionManifest.networkOrigins` for a fixed service:

```kotlin
networkOrigins = setOf(
    ExtensionNetworkOrigin("https://api.example.com"),
)
```

M3UAndroid shows these origins when the user authorizes the extension. Adding an origin in a later
version does not silently expand an existing approval.

Use a text setting marked `networkOrigin` when the user chooses the server:

```kotlin
ExtensionSettingField(
    key = "api_origin",
    label = "Server address",
    type = ExtensionSettingType.TEXT,
    required = true,
    networkOrigin = true,
)
```

This field cannot have a default. Saving the field approves its current value. Clearing it removes
the approval. If its settings schema version changes, the user must save the value again.

An origin is exactly `http` or `https` plus a host and optional port. Do not include a path,
query, fragment, user information, or wildcard. IPv6 literals are not supported by the current
contract.

## Send a request

Use `BrokerValue.Literal` for ordinary values. Use `BrokerValue.Secret` only with a credential
handle supplied in the current call:

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

The host resolves secret and context references while building the request. Their plaintext values
are not returned to the extension. Use `BrokerValue.Encoded` when a resolved value needs JSON
string, form-component, or Base64 encoding.

If the server echoes one of those resolved values, the host replaces it with `***`. In JSON
responses, the host also masks values in these authentication fields: `token`, `accessToken`,
`refreshToken`, `idToken`, `authToken`, `bearerToken`, `sessionToken`, `password`, `secret`,
`clientSecret`, `authorization`, `credential`, and `apiKey`. Field matching ignores case and
separators.

Pagination fields such as `nextPageToken`, `continuationToken`, `tokenType`, and `tokenExpiry` are
not authentication fields and keep their values. When nothing needs masking, the response body is
returned unchanged.

Check `response.statusCode` before decoding the body. Set `maximumResponseBytes` to a realistic
limit for the endpoint.

## Authenticate a provider

Only the provider `Validate` flow uses `authenticate(...)`. Give the broker the login exchange,
the location of the returned credential, and any account values needed by later calls:

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

`ResponseValueSource` may read a response header or an RFC 6901 JSON Pointer. A successful call
returns an HTTP status and a one-time `ProviderAuthenticationReceipt`; it does not return the login
body or captured values. Return that receipt from `SubscriptionHookSpecs.Validate`.

Later provider calls can use a captured value by key:

```kotlin
BrokerValue.Context(
    ContextReference(ProviderAuthenticationContextKeys.UserId)
)
```

## Scope and lifetime

Each broker scope belongs to one extension principal, one Hook, and one invocation. Account scopes
also belong to one provider account. The scope closes when the Hook completes or is cancelled.
A reference from another extension, Hook, call, or account is rejected.

The first URL and every redirect must keep an approved scheme, host, and port. A different origin
fails with `scope_denied`.

Provider playback headers may also contain `BrokerValue.Secret` or `BrokerValue.Context` inside
`PlaybackHeaderValue`. M3UAndroid resolves them before opening the media. The playback URL must stay
on the account's base origin.

## Handle failures

- Return `HookResult.Failure` with a stable extension error code for an expected server rejection.
- `BrokerException` uses `invalid_request`, `capability_denied`, `scope_denied`, `timeout`,
  `network_failed`, `response_too_large`, or `internal` when no HTTP response is available.
- Let `CancellationException` continue to the caller.
- Keep request bodies, response bodies, credentials, and credential handles out of diagnostics.

API types are in
[`HostNetworkBrokerContracts.kt`](../../../../extension/api/src/main/kotlin/com/m3u/extension/api/security/HostNetworkBrokerContracts.kt).
The complete provider flow is in
[`ReferenceExtensionService`](../../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt).
