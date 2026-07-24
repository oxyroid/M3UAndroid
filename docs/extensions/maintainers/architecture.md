# Current architecture and code map

[简体中文](architecture.zh-CN.md) · [Maintainer guide](README.md)

Use this page to find the owner of an extension bug. Current release gaps are listed separately in
[Status and release gates](status-and-release.md).

## Start with the main path

```text
user action or Worker
  -> feature repository creates a typed request
  -> ExtensionRuntime invokes one Hook
  -> built-in handler or external transport
  -> feature repository validates the typed result
  -> Room, UI, or player
```

The runtime completes one call. The feature repository decides what the result means and whether it
may change product state. Keeping these jobs separate prevents a generic Hook runner from writing
search, EPG, channel, or playback data without feature-specific checks.

There are two implementations:

- A **built-in extension** runs its handler in the M3UAndroid process. Emby/Jellyfin uses this path.
- An **external extension** runs in another process through `AndroidBoundExtensionTransport`.

Both use the same `HookSpec<Request, Result>` and runtime policy.

## Ownership by layer

| Owner | Responsibility | Start here |
| --- | --- | --- |
| API contract | Extension identity, manifest, settings, Hook request/result, wire fields | [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api) |
| Runtime | Registration, API/schema negotiation, per-Hook capabilities, payload limits, concurrency, timeout, cancellation, health | [`ExtensionRuntime`](../../../extension/runtime/src/main/kotlin/com/m3u/extension/runtime/ExtensionRuntime.kt) |
| Android transport | Service discovery, identity, binding, handshake, streamed payloads, Binder death | [`:extension:transport-android`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android) |
| External SDK | Decode a call and run the registered typed handler | [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt) |
| Plugin lifecycle | Trust, certificate pin, enablement, grants, reconnect, reauthorization, diagnostics | [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt) |
| Settings lifecycle | Rendered schema, saved values, secret handles, and edit authorization | [`ExtensionSettingsRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/extension/ExtensionSettingsRepositoryImpl.kt) |
| Network scope | Choose approved origins and credentials for one external Hook call | [`ExtensionHookBrokerScopeProvider`](../../../data/src/main/java/com/m3u/data/extension/security/ExtensionHookBrokerScopeProvider.kt) |
| Network execution | Validate scope, URL, redirect, values, size, and timeout; then send HTTP | [`HostNetworkBrokerImpl`](../../../data/src/main/java/com/m3u/data/extension/security/HostNetworkBrokerImpl.kt) |
| Provider flow | Discover, validate, refresh, playback resolve, and session close | [`SubscriptionProviderRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryImpl.kt) |
| Result application | Validate ownership and write host data, or map a result to UI/player | [`data/extension`](../../../data/src/main/java/com/m3u/data/extension), [`data/repository/extension`](../../../data/src/main/java/com/m3u/data/repository/extension) |
| Background tasks | Reconcile periodic declarations and invoke the task Hook from WorkManager | [`ExtensionBackgroundTaskScheduler`](../../../data/src/main/java/com/m3u/data/worker/ExtensionBackgroundTaskScheduler.kt), [`ExtensionBackgroundTaskWorker`](../../../data/src/main/java/com/m3u/data/worker/ProviderWorker.kt) |

## What happens during one Hook call

1. A feature repository chooses a `HookSpec`, extension ID, and request.
2. The runtime confirms that the extension is enabled and declares that Hook.
3. The runtime checks API and Hook schema versions.
4. `CapabilityPolicy` computes the user's grants. The runtime keeps only capabilities declared by
   this Hook. Capabilities declared by another Hook never enter the call.
5. The runtime applies payload, concurrency, and timeout limits.
6. If an external Hook declares and receives `network`, the broker scope provider may open one
   short-lived scope.
7. A built-in handler runs directly, or the external request crosses the Android transport.
8. The runtime decodes the result, closes the broker scope, and records health.
9. The feature repository checks ownership and applies the result.

A missing broker scope does not grant a fallback network path. The Hook can still return an offline
result, but broker operations fail.

## How network scope is chosen

The external broker supports provider Validate/Refresh/Resolve/Close, settings, search, metadata,
EPG, and background tasks. Provider `Discover` is always offline.

| Request | Scope source |
| --- | --- |
| Provider `Validate` | Authentication scope created from the submitted provider origin. |
| Provider `Refresh`, `ResolvePlayback`, `ClosePlayback` | Account scope created by the provider repository. |
| Search, metadata, or EPG with `account + credential` | Account scope created from the matching stored provider account. |
| Settings, background, or search/metadata/EPG without an account | Hook scope created from approved manifest and setting origins. |

For a general Hook scope, the host combines:

- fixed `manifest.networkOrigins` that were approved for the trusted extension; and
- current text settings marked `networkOrigin` that the user explicitly saved.

The trust store keeps only approved fixed origins. Reconnecting an extension does not approve a new
origin. A certificate repin keeps the intersection of old and current origins. A network-origin
setting has no default; saving it grants the current value, clearing it revokes the value, and a
settings schema change requires another save.

Every scope binds the external principal, Hook, approved origins, and a short lifetime. Account
scopes also bind the account. The scope closes after normal return, failure, timeout, or
cancellation. `HostNetworkBrokerImpl` checks the first URL and every redirect against the exact
scheme, host, and port.

Credential handles enter a scope only when the current Hook declares `credential.read` and the user
approved it. The broker resolves `SecretReference` and `ContextReference` only while constructing a
request. It never serializes their resolved value directly back to the extension.

## Provider authentication and refresh

External provider authentication uses a separate one-time flow:

```text
Validate Hook
  -> broker.authenticate sends the login exchange
  -> broker captures the credential and selected account fields
  -> extension receives a one-time receipt
  -> provider repository consumes the receipt
  -> vault stores encrypted credential material
```

The extension does not receive the login response body. The built-in Emby/Jellyfin implementation
is trusted host code, so it returns `ProviderValidationEvidence.TrustedDirect`. An external provider
must return `ProviderValidationEvidence.HostBrokerReceipt`.

Refresh then follows the normal product path:

```text
ProviderWorker or user refresh
  -> SubscriptionProviderRepositoryImpl
  -> SubscriptionHookSpecs.Refresh
  -> ExtensionRuntime
  -> provider handler
  -> SubscriptionProviderImporter
  -> Room
  -> metadata and EPG contribution Hooks
```

The importer updates only the current account and preserves host-owned local channel state. An
external provider uses the same repository and importer as Emby/Jellyfin; only the handler call
crosses Android IPC.

The broker prevents direct host-side credential disclosure. It cannot stop a malicious extension
from colluding with an origin that the user approved. That remaining threat is one reason external
extensions stay behind the developer switch.

## Background task path

An extension declares periodic jobs in `manifest.backgroundTasks`.

```text
enable, reauthorize, or restore extension
  -> ExtensionBackgroundTaskScheduler.reconcile
  -> WorkManager stores or updates periodic work
  -> ExtensionBackgroundTaskWorker restores enabled plugins
  -> ExtensionRuntime invokes HostHookSpecs.BackgroundTask
```

The scheduler removes stale declarations and cancels all jobs when the extension is disabled or
loses a required capability. A declaration with `requiresNetwork = true` receives a connected
network constraint. The Worker retries only recoverable failures and stops after the bounded retry
count.

## External extension lifecycle

```text
discover service
  -> inspect manifest and identity; issue a short-lived review token
  -> user approves identity, capabilities, and fixed origins
  -> repository consumes that token and records trust
  -> transport registers with the runtime
```

Enable and reauthorization must consume the token created for the details shown to the user. The
token is single-use, expires after five minutes, and is bound to the discovered service and
certificate. If it is missing, expired, or belongs to different details, the user must review the
extension again.

Settings use the same boundary at field level. Rendering a configuration issues a short-lived,
single-use edit token for each field. Saving succeeds only against that displayed section schema;
if the schema changed, the host rejects the edit and reloads the form.

Keep these states separate:

- **Discovered:** the service is visible to the host.
- **Enabled:** the user allows calls and required grants are present.
- **Registered:** this host process currently has a working transport.

After process restart, the repository restores trusted, enabled extensions. After an update or
Binder disconnect, a restore or plugin-list refresh rebuilds registration. The runtime does not own
Android Service lifecycle.

## Rules that should remain true

- The runtime does not write Room and does not update UI.
- A result importer changes data only inside the current request and owner scope.
- One extension's failure does not remove another extension's data or its own last valid result.
- The plugin repository owns trust, enablement, and grants.
- The vault owns secrets. Contracts carry opaque handles and one-time receipts.

Before editing, continue with [Change by task](change-guide.md).
