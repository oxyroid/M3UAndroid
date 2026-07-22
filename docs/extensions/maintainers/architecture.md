# Extension platform architecture

[简体中文](architecture.zh-CN.md) · [Maintainer guide](README.md)

This page explains the current system shape. It does not imply that every path is ready for external plugins; see [Status and release gates](status-and-release.md) for that assessment.

## Mental model

Every extension reaches the same typed runtime, then a host-owned consumer decides what to do with the result.

```text
built-in extension --------------------\
                                        > ExtensionRuntime
external APK -> Android transport -----/         |
                                                  v
                                      HookSpec<Request, Result>
                                                  |
                                                  v
                                      host importer / renderer
                                                  |
                                                  v
                                        Room / UI / player
```

Emby/Jellyfin support is implemented by one **built-in extension**. Its adapter is registered directly by the host and runs in the host process. An external extension is a separately installed APK reached through Android IPC. Transport changes how a call crosses the boundary; the hook meaning stays the same.

## Vocabulary

| Term | Meaning |
| --- | --- |
| Extension manifest | Stable identity, version range, hooks, capability requests, settings, and diagnostics metadata |
| Hook spec | One typed request/result pair with an independent schema version |
| Catalog | Registered implementations that declare support for a hook |
| Runtime | API/schema compatibility validation, serialization, invocation limits, cancellation, health, and failure tracking |
| Transport | Built-in call adapter or Android process-boundary adapter |
| Importer/renderer | Host code that validates and applies a returned contribution |

## Module ownership

| Module | Owns |
| --- | --- |
| `:extension:api` | Android-free serializable contracts, manifests, hook specs, settings, errors, credential and broker types |
| `:extension:runtime` | Catalog, registration, API/schema compatibility validation, invocation policy, concurrency, cancellation, and health |
| `:extension:transport-android` | APK discovery, explicit service binding, AIDL, PFD payloads, package identity, and trust persistence |
| `:extension:sdk-android` | APK-side `ExtensionService` and host bridge adapter |
| `data` | Built-in registration, plugin lifecycle repository, vaults, broker, workers, Room, and host importers |
| phone and TV apps | Preview switch, plugin authorization, management, and settings; smartphone currently owns the dynamic provider form |
| `:testing:extension-reference` | Installed APK fixture used to exercise the external boundary |

`:extension:api` stays Kotlin Multiplatform-compatible. Android discovery, Binder, Keystore, WorkManager, and Room remain in Android-facing modules.

## One invocation

1. Host code selects a `HookSpec<Request, Result>` and creates a typed request.
2. Host code queries `ExtensionCatalog` and selects registered implementations; the runtime rejects disabled, unhealthy, or incompatible entries.
3. `CapabilityPolicy` supplies the currently granted manifest capabilities.
4. `InvocationPolicy` applies request/result size, logical timeout, and per-extension concurrency limits.
5. The built-in adapter or Android transport invokes the extension.
6. The runtime decodes the result and records success or failure for that extension.
7. A host importer or renderer validates the contribution before applying it.

The runtime currently enforces the capabilities named by each extension's hook declaration. It does not yet impose a host-defined minimum capability set for every public hook. Adding that host-owned mapping is a release requirement.

## Built-in extensions

`EmbyCompatibleProvider` publishes one **Emby Compatible** descriptor whose supported kinds are Emby, Jellyfin, and automatic detection. It implements the five provider hooks and uses the same contract models as an external extension. Built-in code can use host services through adapters, but it still returns public contract results rather than Room entities or player objects.

This is the reference path for provider semantics, not proof that the external provider lifecycle is complete.

## External APK lifecycle

The host discovers services that declare the extension service action, resolves an explicit component, reads its package and signing certificate, performs a handshake, and reads the manifest. The user then reviews identity and requested capabilities before enablement.

```text
installed -> discovered -> inspected -> trusted -> enabled
                                      \-> rejected / incompatible
enabled -> disabled -> enabled
enabled -> repeated failures -> unhealthy
identity change -> new trust decision
```

The platform-visible extension states are `ENABLED`, `DISABLED`, `INCOMPATIBLE`, and `UNHEALTHY`.

The present trust implementation pins the inspected signer for a package/service. Grants, settings, and provider ownership are not yet consistently keyed by the complete package/service/certificate identity; the status page tracks this as a release blocker.

## Android transport

The control plane uses AIDL. JSON envelopes move through `ParcelFileDescriptor` so results can exceed Binder's normal inline transaction size. Handshake, manifest, invoke, cancel, and health are exposed by the service.

The current `invoke` transaction is synchronous even though its payload uses a PFD. A coroutine timeout does not terminate a Binder transaction that never returns. Production isolation therefore requires an asynchronous, bounded session protocol and hostile-plugin tests.

## Host-owned data application

Extensions do not receive DAOs or write Room entities. Each contribution path needs a narrow consumer:

- provider discovery validates contributor identity, descriptor count, kinds, and field bounds;
- provider refresh validates ownership, unique remote IDs, limits, and playback references;
- search resolves a stable reference to an existing visible channel;
- metadata applies only approved fields to request-owned channels;
- EPG replaces data for the successful contributing extension without deleting data after another extension fails;
- playback validates URLs, schemes, headers, expiry, and session ownership before reaching the player.

Several of these checks are incomplete today. Keep the intended boundary here and the concrete gaps on the [status page](status-and-release.md).

## Credentials and broker

Provider credentials and extension secret settings are encrypted with Android Keystore-backed AES-GCM stores. Contract requests carry `CredentialHandle` values rather than plaintext secrets.

The current `HostNetworkBroker` can resolve credentials for an already persisted provider account. It checks account ownership, restricts the target to the account origin, rechecks redirects, and bounds the response body. It does not yet support pre-account login, secret-setting credentials, user-approved additional origins, a revocable per-invocation broker session, or a safe allowlisted authentication response.

The production design needs one host credential registry with explicit owner, purpose, scope, and approved origin. A provisional login session must be promoted to a persisted account only after validation succeeds.

## Persistence and recovery

Provider accounts, generic provider playlists, playback references, and open playback sessions are host-owned data. Tokens are excluded from backup. A restored provider account must be reauthenticated before refresh or playback.

Open playback sessions are recorded before playback proceeds, removed after a normal close, and eligible for idempotent recovery cleanup after restart. WorkManager owns long-running provider refresh and recovery work.

Next: [Change the platform safely](change-guide.md).
