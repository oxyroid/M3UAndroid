# Build a subscription provider

[简体中文](host-broker.zh-CN.md) · [Developer guide](README.md)

A subscription provider supplies one form and five typed Hooks. M3UAndroid owns the form UI, account storage, refresh scheduling, channel import, and playback session recovery.

The complete example is [`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt).

## The five Hooks

| HookSpec | Request | Result |
| --- | --- | --- |
| `SubscriptionHookSpecs.Discover` | Locale | Provider name, variants, and form schema |
| `SubscriptionHookSpecs.Validate` | Selected variant and submitted values | A successful host authentication receipt |
| `SubscriptionHookSpecs.Refresh` | Account, credential handle, refresh reason, and previous sync metadata | Source and complete channel snapshot |
| `SubscriptionHookSpecs.ResolvePlayback` | Account, credential handle, playback reference, and preferences | Playable URL, headers, and optional session |
| `SubscriptionHookSpecs.ClosePlayback` | Account, credential handle, playback reference, session, and close reason | Close result |

Register each implemented `HookSpec` with `TypedExtensionService` and declare the same Hook and schema version in `ExtensionManifest`.

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

## 1. Describe the provider and its form

`Discover` returns `SubscriptionProviderDescriptor`. Keep `providerId` and each `ProviderKind` stable across releases. List variants in display order and declare every field used during validation.

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

The schema must contain the required `base_url` text field. Use a `SECRET` field for a password or token. Submitted text is available in `request.settingValues`; secret fields are available in `request.credentialHandles`.

## 2. Authenticate the account

`Validate` sends the login exchange through `broker.authenticate(...)`. Tell the broker where the returned access credential is located and, when needed by later calls, which provider values to keep as opaque contexts.

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

The response contains only the status code and receipt. M3UAndroid consumes that receipt to create the account and store the credential. The plugin never parses or returns the login response body.

See [Use the provider broker](reference/provider-broker.md) for request values, contexts, capabilities, and errors.

## 3. Return a complete refresh snapshot

`Refresh` returns one `SubscriptionSourceDescriptor` and the complete current list of `SubscriptionChannelDescriptor` values. Every channel needs a stable `remoteId` and `PlaybackReference`.

M3UAndroid compares this snapshot with stored provider data and preserves host-owned local channel state. Put only data understood by the next refresh in `syncMetadata`.

## 4. Resolve and close playback

`ResolvePlayback` turns the supplied `PlaybackReference` into `PlaybackSourceResolveResult`. Return the playable URL, required headers, the selected media source ID when applicable, and a `PlaybackSessionDescriptor` when the server opens a session.

When a session is returned, `ClosePlayback` receives the same descriptor. Closing must be idempotent: an already-closed remote session is a successful result.

## Acceptance

A provider is complete when:

1. every declared variant opens the declared form;
2. valid values create one account and import channels;
3. refresh replaces the remote snapshot without losing local channel state;
4. a channel resolves and plays with the required headers;
5. stopping playback closes the remote session.

Use [Test an extension](testing.md) for failure and update checks.
