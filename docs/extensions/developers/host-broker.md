# Build a subscription provider

[简体中文](host-broker.zh-CN.md) · [Developer guide](README.md)

A subscription provider supplies a connection form and implements seven typed Hooks. All seven are
required: discover, validate the subscription, refresh, browse, resolve playback, update playback,
and close playback. M3UAndroid displays the form, stores the account, schedules refreshes, imports
media, and recovers playback sessions in the smartphone app.

The complete example is [`ReferenceExtensionService`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt).

## Provider Hooks

| HookSpec | Schema | Base capability | Request and result |
| --- | --- | --- | --- |
| `SubscriptionHookSpecs.Discover` | 1 | None | Locale → one Provider descriptor |
| `SubscriptionHookSpecs.Validate` | 1 | `credential.write` | Submitted values → subscription validation and host authentication receipt |
| `SubscriptionHookSpecs.Refresh` | 1 | `subscription.read` | Account and refresh reason → source and complete channel snapshot |
| `SubscriptionHookSpecs.Browse` | 1 | `subscription.read` | Account, optional parent, cursor, and limit → one media page |
| `SubscriptionHookSpecs.ResolvePlayback` | 1 | `playback.resolve` | Account and playback reference → URL, headers, and optional session |
| `SubscriptionHookSpecs.UpdatePlayback` | 1 | `playback.resolve` | Account, playback session, event, and position → acceptance |
| `SubscriptionHookSpecs.ClosePlayback` | 1 | `playback.resolve` | Account, playback reference, and session → close result |

Built-in and external providers use this same complete contract. Register every `HookSpec` with
`TypedExtensionService`, then declare the same Hook and schema version in `ExtensionManifest`.
Networked Hooks use broker-backed handlers:

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

All seven handlers are required. `Discover` is offline. Every other provider Hook that makes a
server request declares `network`. Add `credential.read` when a broker request uses a submitted or
saved credential handle.

## 1. Describe the provider and its form

`Discover` returns one `SubscriptionProviderDescriptor`. Keep `providerId` and every
`ProviderKind` stable between releases. List every supported variant in display order and include
every field needed for login.

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

The schema must contain the required `base_url` text field. Use a `SECRET` field for a password or
token. Submitted text is available in `request.settingValues`; secret fields are available in
`request.credentialHandles`.

Localize every user-facing name, label, description, and choice from `request.localeTag`, with a
fallback to the extension's default language. Return plain text in natural reading order and do not
insert bidi control characters; the host handles RTL isolation. Text should make sense when read
aloud. IDs, URLs, and handles remain untranslated.

## 2. Authenticate and subscribe the account

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

Return one `SubscriptionSourceDescriptor` and the complete channel snapshot. The source contains
only `remoteId` and `providerKind`; it has no title.

```kotlin
SubscriptionSourceDescriptor(
    remoteId = request.account.serverId,
    providerKind = request.account.providerKind,
)
```

Every channel needs a stable `remoteId` and `PlaybackReference`. Keep only stable IDs in the
reference. Resolve URLs, tokens, and cookies in `ResolvePlayback`.

M3UAndroid compares this snapshot with stored provider data and preserves host-owned local channel state.

## 4. Browse movies and series

`Browse` is paged. Omit both `parentReference` and `cursor` for the first root page. Pass the
selected item's required `reference` back as `parentReference` to open a series, season, or other
browsable item. Treat `cursor` as an opaque, non-secret continuation value. A page requests at
most 200 items and the result must not exceed that bound.

Each `SubscriptionContentItemDescriptor` has an extensible `ProviderMediaKind`. The SDK publishes
`unknown`, `live`, `movie`, `series`, `season`, and `episode` as common values; a host must not
assume that this list is closed. Set at least one of `playable` or `browsable` to true:

- A movie or episode is normally playable.
- A series or season may be `playable = false` and `browsable = true`.
- An item that can both open children and play directly sets both to true.

The `reference` is required even for a browse-only item. It is the stable value used for the next
browse request and, when `playable` is true, for `ResolvePlayback`. Do not put a URL, token, or
cookie in it.

`imageUrl`, `category`, `subtitle`, `overview`, `productionYear`, `seasonNumber`, and
`episodeNumber` are optional presentation data. Keep them concise; the contract enforces byte and
number bounds. `nextCursor` and `total` are optional. Return `nextCursor = null` after the final
page.

## 5. Resolve, update, and close playback

`ResolvePlayback` returns the playable URL, required headers, selected media source ID, and an
optional `PlaybackSessionDescriptor`. It also returns an extensible `PlaybackMethod`. Use
`direct_play`, `direct_stream`, or `transcode` when known. Use `BrokerValue` references for saved
credentials and captured account values; M3UAndroid resolves them when making the request or
opening the media.

M3UAndroid sends `started`, `progress`, `paused`, or `resumed` through
`PlaybackSessionUpdateRequest`. The event vocabulary is extensible.
`positionTicks` is non-negative and one tick is 100 nanoseconds. Echo the method selected by
`ResolvePlayback`, set `isPaused` to the current state, and return whether the remote service
accepted the update. Progress rejection must not make the media URL invalid.

When a session is returned, `ClosePlayback` receives the same descriptor and its own account-scoped
broker. It also receives the final non-negative `positionTicks`. Closing must be idempotent.
Return success when the remote session is already closed.

## Acceptance

1. `Discover` returns one descriptor and every variant opens its form.
2. `Validate` completes host-managed subscription authentication.
3. `Refresh` imports one complete snapshot and creates the account.
4. Root and child `Browse` pages honor the requested limit and terminate with a null cursor.
5. `ResolvePlayback` returns media that plays with host-resolved headers.
6. `UpdatePlayback` reports playback state; a rejected update does not interrupt local playback.
7. `ClosePlayback` closes the remote session and succeeds when repeated.

Next: [send authenticated requests through the host broker](reference/provider-broker.md), then
[test the extension](testing.md).
