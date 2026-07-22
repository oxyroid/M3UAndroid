# Maintain the extension system

[简体中文](README.zh-CN.md)

This guide is for M3UAndroid maintainers changing the extension platform. It covers extension code only.

## The architecture in one minute

All extensions, built-in or external, pass through one runtime:

```text
built-in Kotlin adapter ----\
                            > ExtensionRuntime -> typed hook -> host importer -> Room/UI/player
external APK -> Android IPC /
```

This shape enforces two rules:

1. transport changes how a request reaches an extension, not what the request means;
2. an extension returns a proposal; host code validates and applies it.

Never add an app-to-plugin shortcut or let a plugin call a DAO. If a feature cannot fit this flow, change the typed contract and host importer deliberately.

## Where code belongs

| Module | Owns | Must not own |
| --- | --- | --- |
| `:extension:api` | Serializable models, manifests, hook specs, settings, errors, credential/broker contracts | Android APIs or host persistence |
| `:extension:runtime` | Registration, negotiation, policy, invocation limits, health, isolation | APK discovery or UI |
| `:extension:transport-android` | Discovery, explicit bind, AIDL, PFD streams, Binder death | Hook meaning or data import |
| `:extension:sdk-android` | The service base and APK-side broker adapter | Host credentials or database access |
| `data` | Built-in registration, trust store, vault, broker, workers, Room, host importers | Permission presentation |
| phone / TV apps | Trust, permission details, management and declarative settings UI | Transport or DAO logic |
| `:testing:extension-reference` | Executable external fixture | Production-only behavior |

`:extension:api` stays Android-free and KMP-compatible. Built-in and APK extensions must use the same `HookSpec`, capability checks, timeouts, size/concurrency limits, cancellation, health, and error envelope.

## Follow one invocation

When diagnosing a hook, follow this order:

1. The host chooses a typed `HookSpec<Request, Result>`.
2. `ExtensionCatalog` finds enabled implementations compatible with its schema version.
3. `CapabilityPolicy` checks host support, manifest declarations, and user grants.
4. `InvocationPolicy` applies timeout, payload, and concurrency limits.
5. The built-in adapter or Android transport executes the request.
6. The runtime decodes the typed result and records success/failure health.
7. A host-owned importer validates references, sizes, and allowed fields before persistence.

Callers must not pass an ad-hoc “granted capabilities” set. Plugins must not return Room entities, `DataSource`, player objects, or raw credentials.

## Changing a contract safely

Each hook has its own positive schema version. The extension API also has a major version.

Compatible changes usually add an optional serialized field with a default. Breaking field changes require a new hook schema; a platform-wide break requires a new API major. Open-ended values such as refresh and close reasons remain strings so newer values do not break older plugins.

For every contract change:

1. update the API model, catalog, and serializers;
2. add/update golden JSON and negotiation tests;
3. test the same behavior through built-in and Android transports;
4. add or update the host importer/call site;
5. state clearly whether the hook is only defined or actually connected;
6. update both developer guides and both maintainer guides.

Reject an API major mismatch, unknown required capability, or unsupported required hook schema. Ignore unknown optional JSON fields. Do not restore arbitrary runtime payload casts.

## External plugin lifecycle

The normal lifecycle is:

```text
installed -> discovered -> inspected -> user trusts -> enabled
                                      \-> rejected / incompatible
enabled -> disabled -> enabled
enabled -> repeated failures -> unhealthy
signer or stable ID changes -> disabled -> new trust decision
```

Discovery queries only `com.m3u.extension.action.BIND_EXTENSION`. It explicitly binds the resolved component, which must require `com.m3u.permission.BIND_EXTENSION_HOST`. Never add `QUERY_ALL_PACKAGES`, `DexClassLoader`, or in-process APK loading.

External plugins remain behind `PreferencesKeys.EXTERNAL_EXTENSIONS`. Turning the switch off closes and unregisters external transports but preserves trust. Lifecycle changes are serialized in the repository so UI, restoration, refresh, and workers cannot register the same plugin concurrently.

Trust is pinned to package/component identity and signing certificate. First enable shows identity and every requested capability. On restore, intersect old grants with the new manifest: new optional capabilities stay ungranted; a new required capability blocks restore. A changed signer or extension ID requires a new trust decision.

Explicit reauthorization first rechecks signer and extension ID, then updates only currently requested, host-supported capabilities. It does not duplicate runtime registration and must leave an already disabled plugin disabled. Phone authorization content scrolls; TV uses a dedicated DPad screen so no capability reason is truncated. Rejection must be visible to the user.

## Failure isolation and diagnostics

Cancellation propagates through runtime and transport. Multi-plugin contribution points use supervisor behavior: preserve cancellation, but turn an ordinary single-plugin failure into an empty/failed contribution. Repeated failures can move only that plugin to `UNHEALTHY`.

Valid states are `ENABLED`, `DISABLED`, `INCOMPATIBLE`, and `UNHEALTHY`.

Never log complete envelopes or response bodies. Diagnostic export uses a positive whitelist: host API, package/service identity, pinned certificate, version/state, capability and hook IDs, failure count, and aggregate settings counts. It excludes free-form metadata, setting keys/values, payloads, broker traffic, response bodies, and exception text.

**Clear data** removes extension settings, encrypted secret handles, and only EPG sources under `m3u-extension-epg://<extension-id>/`. It preserves subscriptions, channels, favorites, hidden state, and playback history.

## Credentials and network

`CredentialVault` encrypts secrets with Android Keystore-backed AES-GCM and exposes opaque handles. Database rows and backups never contain plaintext tokens. If a key is lost or restored ciphertext cannot be decrypted, delete the unusable credential and require reauthentication.

Every external network request goes through `HostNetworkBroker`. Preserve all of these checks:

- target is the account origin or a user-approved additional origin;
- plugin-supplied authentication headers are stripped;
- a host-held secret is injected by reference;
- every redirect target is checked again;
- timeout, response size, and concurrency are bounded;
- login capture writes a header/JSON-pointer value to the vault and returns redacted data plus a handle.

Do not add a raw-secret escape hatch.

## Host-owned importers

The importer is the security boundary after decoding. It checks stable references, result counts/sizes, allowed fields, and ownership before writing anything.

- Provider subscriptions store generic provider/account identity and credential handles. Never hard-code Emby IDs in UI/repositories.
- Search maps opaque references to existing, non-hidden channels. Unknown references are dropped.
- Metadata can update only host-approved fields such as title/category through narrow data methods.
- EPG validates references and time/size bounds, then writes an extension-isolated source.
- Settings are namespaced by section; changing its schema version removes stale values and secrets.
- Provider refresh runs in WorkManager with network constraints, retry, progress, cancellation, and quotas.
- Playback sessions are persisted immediately, deleted on normal close, and cleaned idempotently after restart.

Backups may contain account metadata and stable playback references, never tokens. Restored provider playlists require reauthentication.

## What is connected today

| Area | Status |
| --- | --- |
| Built-in Emby/Jellyfin subscribe, refresh, playback, close | Connected |
| Generic provider persistence, WorkManager refresh, session recovery | Connected |
| APK discovery, trust, IPC, cancellation, health, broker, restore | Connected behind developer switch |
| Phone/TV plugin management and declarative settings | Connected |
| Smartphone search contribution | Connected |
| Metadata and EPG during generic provider refresh | Connected |
| Metadata/EPG in every legacy M3U/Xtream ingestion path | Not yet complete |
| External provider flow as a generally supported public feature | Not yet released |

Keep this table honest. A serialized type in `:extension:api` is not proof of a production host call site.

## Release gate

Before removing the developer switch, require evidence for:

- golden serialization, negotiation, bad types, size/time/concurrency limits, cancellation, capability denial, quarantine, and redaction;
- Room migrations, credential loss, backup/restore reauthentication, idempotent imports, background refresh, and restart cleanup;
- denied origins/redirects, stripped auth headers, signer mismatch, and denied capabilities;
- installed reference APK discovery, binding, handshake, large PFD result, cancellation, Binder death, crash, and incompatible version;
- phone and TV discovery, authorization details, DPad/full-row interaction, enable/disable, reauthorization, restore, and visible errors;
- M3U, EPG, Xtream, ordinary playback, and DLNA regressions.

The platform is ready only when an independently installed compatible APK can be safely discovered and enabled, every exposed hook has a host-owned importer, and plugin crash, malicious requests, or credential failure cannot corrupt host data or terminate the host process.
