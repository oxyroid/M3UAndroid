# Build a subscription provider

[简体中文](host-broker.zh-CN.md) · [Developer guide](README.md)

A subscription provider supplies a connection form and five typed Hooks. M3UAndroid displays the
form, stores the account, schedules refreshes, imports channels, and recovers playback sessions.

The complete example is [`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt).

## The five Hooks

| HookSpec | Schema | Base capability | Request and result |
| --- | --- | --- | --- |
| `SubscriptionHookSpecs.Discover` | 3 | None | Locale → one Provider descriptor |
| `SubscriptionHookSpecs.Validate` | 2 | `credential.write` | Submitted values → host authentication receipt |
| `SubscriptionHookSpecs.Refresh` | 4 | `subscription.read` | Account and refresh reason → source and complete channel snapshot |
| `SubscriptionHookSpecs.ResolvePlayback` | 4 | `playback.resolve` | Account and playback reference → URL, headers, and optional session |
| `SubscriptionHookSpecs.ClosePlayback` | 3 | `playback.resolve` | Account, playback reference, and session → close result |

Register every `HookSpec` with `TypedExtensionService`, then declare the same Hook and schema
version in `ExtensionManifest`. Four Hooks make server requests and therefore use broker-backed
handlers:

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

`Discover` is offline. The other four Hooks declare `network`. Add `credential.read` when a broker
request uses a submitted or saved credential handle.

## 1. Describe the provider and its form

`Discover` returns one `SubscriptionProviderDescriptor`. Keep `providerId` and every
`ProviderKind` stable between releases. List variants in display order and include every field
needed for login.

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

The schema must contain the required `base_url` text field. Use a `SECRET` field for a password or
token. Submitted text is available in `request.settingValues`; secret fields are available in
`request.credentialHandles`.

## 2. Authenticate the account

`Validate` sends the login exchange through `broker.authenticate(...)`. Tell the broker where the
returned access credential is located and which server or user IDs M3UAndroid should keep to
identify the account.

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

The response contains only the status code and receipt. M3UAndroid consumes that receipt to create
the account and store the credential. The plugin never parses or returns the login response body.

See [Use the host network broker](reference/provider-broker.md) for request values, contexts,
capabilities, and errors.

## 3. Return a complete refresh snapshot

Return one `SubscriptionSourceDescriptor` and the complete channel snapshot. In schema 4 the source
contains only `remoteId` and `providerKind`; it has no title.

```kotlin
SubscriptionSourceDescriptor(
    remoteId = request.account.serverId,
    providerKind = request.account.providerKind,
)
```

Every channel needs a stable `remoteId` and `PlaybackReference`. Keep only stable IDs in the
reference. Resolve URLs, tokens, and cookies in `ResolvePlayback`.

M3UAndroid compares this snapshot with stored provider data and preserves host-owned local channel state.

## 4. Resolve and close playback

`ResolvePlayback` returns the playable URL, required headers, selected media source ID, and an
optional `PlaybackSessionDescriptor`. Use `BrokerValue` references for saved credentials and
captured account values; M3UAndroid resolves them when making the request or opening the media.

When a session is returned, `ClosePlayback` receives the same descriptor and its own account-scoped
broker. Closing must be idempotent. Return success when the remote session is already closed.

## Acceptance

1. `Discover` returns one descriptor and every variant opens its form.
2. Valid values complete host-managed authentication.
3. Initial refresh imports one complete snapshot and creates the account.
4. A channel resolves and plays with host-resolved headers.
5. Stopping playback closes the remote session. Repeated close also succeeds.

Next: [send authenticated requests through the host broker](reference/provider-broker.md), then
[test the extension](testing.md).
