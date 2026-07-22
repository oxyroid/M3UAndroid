# Extension system maintainer guide

[简体中文](README.zh-CN.md)

This document records the extension-specific architecture and release invariants for M3UAndroid maintainers. It does not define unrelated app architecture.

## Architecture boundary

The extension stack is intentionally layered:

- `:extension:api` owns KMP-compatible, `kotlinx.serialization` contracts, hook specs, manifests, settings models, errors, credential handles, and broker contracts. It must not depend on Android.
- `:extension:runtime` owns catalog registration, compatibility negotiation, capability and invocation policy, typed serialization, limits, health, and fault isolation.
- `:extension:transport-android` owns service discovery, explicit binding, AIDL control, `ParcelFileDescriptor` JSON transfer, Binder death, and Android transport errors.
- `:extension:sdk-android` is the external APK-facing service and network-broker adapter.
- `data` owns host adapters: built-in provider registration, plugin trust persistence, credential vault, network broker implementation, Room migration, workers, session recovery, and repositories that invoke hooks.
- phone and TV app modules own permission/trust presentation and plugin management UI.
- `:testing:extension-reference` is the external process reference fixture.

Built-in extensions and external APK transports must enter the same runtime and obey the same typed hook, capability, timeout, payload, concurrency, cancellation, health, and error rules. Do not add a direct app-to-plugin shortcut.

## Contract evolution

Every hook has a `HookSpec<Request, Result>` and an independent positive schema version. Add serializable optional fields with defaults for compatible evolution. Use extensible strings for open-ended wire values such as refresh and close reasons. Do not introduce runtime casts to an arbitrary payload.

An API major mismatch is incompatible. Within one major, negotiate the hook schema declared in the manifest. Reject unknown required capabilities and unsupported required hook versions. Ignore unknown optional JSON fields.

Before freezing or changing a contract:

1. update the API catalog and typed serializers;
2. add or update golden JSON fixtures and version-negotiation tests;
3. run the same behavior through built-in and Android transports;
4. update both developer-language documents and both maintainer-language documents in this directory tree;
5. describe whether the contract is merely defined or has a production host call site.

## Runtime policy and isolation

Callers request an extension and typed hook; they do not supply an ad-hoc set of granted capabilities. `CapabilityPolicy` computes grants from host support, manifest declarations, hook requirements, trust, and user approval. `InvocationPolicy` supplies timeout, concurrency, and payload limits.

Cancellation must propagate to the runtime and Android transport. Ordinary extension failure must remain local to that extension. Multi-extension contribution points use supervisor semantics, preserve cancellation, and convert other per-extension exceptions into an empty/failure contribution. Repeated failures update health and can quarantine the extension as `UNHEALTHY`; valid lifecycle states are `ENABLED`, `DISABLED`, `INCOMPATIBLE`, and `UNHEALTHY`.

Never log serialized payloads blindly. Runtime and transport diagnostics must redact credentials, authorization headers, secret settings, captured values, and other known sensitive fields.

## External APK lifecycle

Discovery uses an intent query for `com.m3u.extension.action.BIND_EXTENSION`; do not add `QUERY_ALL_PACKAGES`. Bind explicitly to the resolved component and require `com.m3u.permission.BIND_EXTENSION_HOST`. Never load APK code into the host process.

External extensions remain behind `PreferencesKeys.EXTERNAL_EXTENSIONS`. Turning the feature off unregisters and closes external transports but preserves trust records. Turning it back on restores eligible enabled plugins. The repository serializes lifecycle changes so a settings observer, worker, refresh, and UI action cannot race registration.

First enable is a security decision. Phone and TV must show package, developer, certificate SHA-256, version, and requested capabilities before accepting. Trust is keyed to package/component identity and pinned signer. A signer change disables the plugin and requires explicit reauthorization. “Forget trust” removes the persisted decision; it is not equivalent to merely disabling the extension.

Restore must never call the user-authorization path. Reconcile saved grants against the current manifest: drop removed capabilities, leave new optional capabilities ungranted, and disable restoration when any new required capability is missing. A component that changes its extension ID also requires explicit reauthorization.

## Credentials and network

The host owns secrets. `CredentialVault` stores AES-GCM ciphertext protected by Android Keystore and exposes opaque handles. Database and backup payloads must never contain plaintext tokens. If a key is missing or restored ciphertext cannot be decrypted, remove the unusable credential and mark the provider for reauthentication.

External network activity goes through `HostNetworkBroker`. Preserve these invariants:

- target origin is the account base origin or an explicitly user-approved additional origin;
- extension-provided authentication headers are stripped;
- host-held secrets are injected by reference;
- redirects are revalidated by origin;
- time, response size, and concurrency are bounded;
- login capture stores a header/JSON-pointer secret in the vault and returns only redacted content plus a handle.

Do not add a raw-secret escape hatch for compatibility.

## Host call-site rule

An extension returns declarative results; a host importer validates and persists them. UI, business logic, and plugins must not manipulate DAOs or construct provider-specific `DataSource` branches.

For provider subscriptions, persist generic provider/account identity and credential handles. Resolve `providerId` and `providerKind` from the provider account rather than hard-coding Emby IDs. Backups include account metadata and stable playback references, never tokens; restored provider playlists require reauthentication.

For search, the host invokes enabled contributors concurrently and maps opaque stable references back to existing, non-hidden host channels. Unmapped plugin items are not converted into playable objects. Apply the same ownership rule to future settings, EPG, metadata, and background contributions: the corresponding host renderer/importer remains authoritative.

Provider refresh belongs in WorkManager with network constraints, cancellation, retries, progress, and bounded quotas. Playback sessions are persisted immediately after opening, deleted after normal close, and cleaned idempotently after process restart.

## Current integration status

Production host paths currently exist for built-in Emby/Jellyfin discovery, validation, refresh, playback resolution, close, provider persistence, background refresh, and playback-session recovery. External APK discovery, trust, enable/disable, signer pinning, handshake, invocation, cancellation, health, broker mediation, process restoration, and background tasks are implemented behind the developer feature. Smartphone search contributions use host-owned stable-reference resolution. Generic provider refresh invokes metadata and EPG contributors in bounded batches; metadata can update only title/category through a narrow DAO query, and EPG is replaced in extension-specific host sources after reference/time/size validation.

Metadata and EPG importers are not yet connected to every legacy M3U/Xtream ingestion path. Typed settings persistence and invocation context are connected: values are namespaced by section, schema-version reconciliation removes stale data, and secret fields are encrypted while only opaque handles enter invocation envelopes. Phone and TV settings renderers remain in progress. Do not describe those incomplete UI paths as production integrations until their call sites and tests land.

## Release gates

At minimum, validate:

- API/runtime golden serialization, negotiation, wrong types, size limits, timeout, cancellation, concurrency, capability denial, quarantine, and redaction;
- Room migrations, credential loss, backup/restore reauthentication, idempotent import, background refresh, and restart session cleanup;
- cross-origin and redirect denial, auth-header stripping, signer mismatch, and unavailable capabilities;
- installed reference discovery, binding, handshake, large PFD result, cancellation, Binder death, process crash, and incompatible versions;
- phone and TV discovery, full-row/DPad interaction, trust detail, enable/disable, reauthorization, feature-toggle restoration, and error states;
- M3U, EPG, Xtream, normal playback, and DLNA regression paths.

The platform is not ready to leave the developer switch until an independently installed compatible APK can be safely discovered and enabled, all exposed hooks have host-owned importers, and plugin crash, malicious requests, or credential failure cannot corrupt host data or terminate the host process.
