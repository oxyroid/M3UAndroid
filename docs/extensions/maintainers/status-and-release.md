# Status and release gates

[简体中文](status-and-release.zh-CN.md) · [Maintainer guide](README.md)

This page defines what may ship from the current branch. Implementation instructions belong in the [extension developer guide](../developers/README.md).

## Release boundary

- The built-in Emby/Jellyfin extension is on the normal product path.
- External extensions remain behind the developer feature switch.
- The switch must stay in place until every item under **Before opening external extensions** is complete.

## Connected paths

| Area | Current behavior | Evidence |
| --- | --- | --- |
| Contract and runtime | Typed, versioned Hook contracts; singular provider discovery in schema 3; title-free refresh source in schema 4; each call receives only the current Hook's declared and approved capabilities; payload, timeout, concurrency, cancellation, health, and failure isolation | `ExtensionContractTest`, `SubscriptionProviderContractsTest`, `ExtensionRuntimeTest`, and transport conformance tests |
| Built-in provider | Emby and Jellyfin are variants of one built-in extension; discover, validate, refresh, playback resolve, and session close share the provider path | `EmbyCompatibleProviderIntegrationTest` and `SubscriptionProviderRepositoryIntegrationTest` |
| Provider credentials | External login returns a one-time host receipt. Post-validation scopes resolve references only into requests for the approved origin; the host does not directly serialize resolved values back to the extension. | `HostNetworkBrokerSecurityTest`, `ExtensionHostBridgeTest`, `ProviderBrokerScopeStoreTest`, and `CredentialVaultTest` |
| General Hook network access | Settings, search, metadata, EPG, and background Hooks can use the host broker when that Hook declares and receives `network`. Search/metadata/EPG use an account scope when their request has an account; other calls use approved manifest and explicitly saved setting origins. Discover stays offline. | `ExtensionNetworkOriginContractTest`, `ExtensionBrokerScopeRuntimeTest`, `ExtensionHookBrokerScopeStoreTest`, and `ExtensionHostBridgeTest` |
| Provider persistence | Generic provider accounts, database migrations, backup without tokens, reauthentication state, WorkManager refresh, and restart session cleanup | Migration, provider repository, worker, restore, and session cleanup tests |
| External lifecycle | Discovery, identity and certificate trust, review-bound enable/reauthorize tokens, enable/disable, capability and fixed-origin authorization, reconnect, clear data, diagnostics, streamed payloads, and cancellation | Transport tests, `ExtensionPluginRepositoryLifecycleTest`, and `ExternalExtensionIpcTest` |
| Extension settings | Manifest and dynamic schemas, ordinary values, encrypted secret handles, network-origin approval, and review-bound field edits | `ExtensionSettingsRepositoryTest` and `ExtensionPluginRepositoryLifecycleTest` |
| External reference provider | Discover, host-managed login, initial and later refresh, Room import, credential-backed playback resolve, header resolution, and session close cross Binder and use the same repository as built-in providers. | `ExternalProviderEndToEndTest` |
| Provider UI | Phone and TV use descriptor-driven provider lists and forms; both expose reauthentication state; Emby and Jellyfin remain separate choices | `SubscriptionSourceSelectionTest` plus phone and TV device checks |
| Other Hooks | Settings, search, metadata enrichment, and EPG refresh have typed SDK handlers and product callers | SDK, contribution repository/importer, and IPC tests |
| Background task | Manifest task declarations are reconciled into periodic WorkManager jobs when an extension is enabled, reauthorized, or restored. Disablement or missing grants cancels them; network tasks use a connected constraint. | `ExtensionBackgroundTaskSchedulerTest`, Worker tests, and `ExtensionPluginRepositoryLifecycleTest` |

## Before shipping the built-in provider path

- Run migration tests from database versions 21, 22, and 23.
- Run M3U, EPG, Xtream, ordinary playback, and DLNA regressions after provider or playback changes.
- Verify the phone and TV provider forms with real input and focus movement.
- Keep the database schema artifact and every manual migration in the same change.

## Before opening external extensions

- Decide the published threat model. The current broker does not protect a token from a malicious extension colluding with its approved server. A stronger guarantee requires host-owned protected-response parsing and import.
- Run the complete external provider flow on TV, through WorkManager, and through the real player rather than only the repository-level device test.
- Add repeatable UI automation for authorization, reauthorization, settings, errors, and TV focus.
- Add process-level hostile fixtures for a blocked call, ignored cancellation, process death, malformed or oversized output, retained broker access, signer change, and extension-ID collision.
- Apply one host-wide invocation budget and one deadline across a Hook and all of its broker requests.
- Run the same published conformance suite against built-in and external transports.
- Publish golden wire fixtures, the SDK artifact, and the same-major compatibility policy.

## Decision rule

The built-in provider path may ship when its regression list is green. External extensions may remain available as a developer preview, but the default feature switch may be removed only after the external-opening list above is green and failures leave host data and the host process safe.
