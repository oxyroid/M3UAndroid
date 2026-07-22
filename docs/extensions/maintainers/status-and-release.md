# Status and release gates

[简体中文](status-and-release.zh-CN.md) · [Maintainer guide](README.md)

This is a lookup and release-review page, not a getting-started tutorial. Find the relevant capability under Current support first; read every blocker only when deciding whether external extensions can be opened. Update this page when a host call site, UI path, security boundary, or end-to-end test changes.

## Current support

| Area | Current evidence | Status |
| --- | --- | --- |
| Built-in Emby/Jellyfin validate, refresh, playback, and close | Strict mock-server device integration tests cover both kinds and repository persistence | Connected |
| Generic provider Room model, encrypted credential, WorkManager refresh, open-session recovery | Data integration paths exist | Connected for built-in provider flow |
| APK discovery, handshake, PFD payloads, typed invocation, settings, and cancellation | SDK unit tests cover typed handlers; Hello renders static and dynamic settings; `ExternalExtensionIpcTest` completes 1/1 | Developer preview |
| Invocation-scoped host broker | Host checks caller UID and grants, revokes retained bridges, bounds concurrent calls, and rejects APKs with direct network access; the reference APK completes a broker probe | Transport boundary connected; provider login not connected |
| Phone plugin authorization, enable/disable, settings, reauthorization, diagnostics, and clear data | Product UI exists; manual device path works | Developer preview; UI automation incomplete |
| TV plugin management and declarative settings | Product UI exists | Developer preview; DPad automation absent |
| Declarative extension settings | Phone, TV, repository, encrypted secret storage, Hello sample, and reference fixture | Most complete external hook |
| Smartphone extension search | Maps stable references to existing visible channels | Partial |
| Metadata and EPG contributions | Run after generic provider refresh; EPG replacement is isolated per successful extension | Partial; not connected to M3U/Xtream ingestion |
| External provider discovery | Smartphone can show returned descriptors | Partial; reference provider cannot subscribe |
| External provider validate/refresh/playback/close | No complete reference APK or pre-account login path | Not connected |
| Dynamic provider subscription on TV | No provider list or form state | Not connected |
| Provider reauthentication after restore/key loss | State exists, but no user repair flow | Not connected |
| Background task | Contract, Worker, and direct transport test exist; nothing schedules the Worker | Contract only |

## Release blockers

### 1. Isolate the Android process boundary

- Move long-running work behind short asynchronous control calls and host-owned result pipes.
- Apply one host-wide executor budget across transports; synchronous Binder calls can still occupy the per-transport budget until the remote process returns.
- Handshake, manifest, invoke, cancel, health, and broker failures use one stable error envelope.

### 2. Complete credential and login ownership

- Key trust, grants, settings, credentials, and provider ownership by package, service, certificate set, UID, and extension ID.
- Add a provisional provider-auth session for credentials used before an account exists.
- Unify provider credentials, captured login output, and extension secret settings under scoped host-owned handles.
- Define authentication headers, capture rules, and returned fields in host-approved schema. Authentication responses expose only allowlisted fields.
- Add one end-to-end broker/HTTP deadline and user-approved additional-origin policy.

### 3. Make every importer preserve ownership and valid data

- Validate contributor/provider identity, kind, remote IDs, result counts, field lengths, maps, URLs, schemes, headers, and playback sessions.
- Isolate each provider discovery failure and always retain built-in providers.
- Persist or remove currently unused sync metadata, diagnostics, expiry, continuation, and retry fields before wire freeze.
- Run common metadata/EPG contribution logic after M3U and Xtream ingestion where supported.

### 4. Finish the provider product flow

- Make the independently installed reference APK implement discover, validate, refresh, resolve, and close with a real settings schema.
- Exercise the reference extension through repository, broker, Room, WorkManager, player, and session close.
- Add dynamic provider subscription to TV.
- Add visible reauthentication for restored or undecryptable provider accounts.
- Provider descriptors and schemas are the only provider-specific UI input; app and business code remain independent of Emby/Jellyfin kinds.
- Show a visible error for a missing or empty provider settings schema and for failed subscription.

### 5. Freeze a smaller, enforceable public contract

- Add host-defined minimum capabilities for every hook.
- Decide whether unused public fields have a tested consumer or should be removed before 1.0.
- Publish golden JSON fixtures for every request, result, and error.
- Publish the SDK artifact and same-major compatibility policy.

### 6. Build release evidence

- Run one parameterized conformance suite through built-in and Android transports.
- Add hostile fixture APKs for hang, ignored cancel, death, malformed/oversized output, retained broker, signer change, and extension-ID collision.
- Add stable phone tests for Jellyfin selection, dynamic provider forms, plugin authorization, reauthorization, settings, and visible errors.
- Add TV DPad tests for plugin management, authorization, settings, and provider subscription.
- Regress M3U, EPG, Xtream, ordinary playback, and DLNA.

## Ready-to-open definition

The developer switch can be removed when all of the following are true:

- a new provider needs no app/business/data branch keyed to its concrete kind;
- an independently installed APK can complete its declared product flow on phone and TV;
- every exposed hook has a real host caller, bounded importer/renderer, and end-to-end test;
- plugin hang, crash, malformed data, retained broker access, signer change, and credential loss leave host data and the host process safe;
- compatibility fixtures and the public SDK version policy are published.
