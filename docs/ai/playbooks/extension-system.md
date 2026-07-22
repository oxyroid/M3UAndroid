# Playbook: Formal Extension System

## When to use this playbook

Use this playbook for extension contracts, host runtime hooks, built-in providers,
external plugin transports, provider imports, dynamic playback resolution, or
extension-contributed metadata, EPG, settings, search, and background work.

The removed RPC and package-scanning code was an experimental implementation.
The formal extension system is a separate runtime-hook platform, not a versioned
migration of that experiment.

## Required context

Read these before making changes:

- `AGENTS.md`
- The nearest module-specific `AGENTS.md`
- `docs/ai/architecture/extension-runtime.md`
- `settings.gradle.kts`
- The hook contract and its host call site

Initialize repository submodules before validation:

```bash
git submodule update --init --recursive
```

## Module boundaries

- `extension/api` owns platform-neutral identity, manifest, hook, capability,
  invocation, payload, outcome, and error contracts.
- `extension/runtime` owns host-side registration, lookup, API-range checks,
  capability enforcement, invocation identifiers, and structured failures.
- `data` owns network clients, credentials, persistence, provider imports,
  transactions, migrations, and playback adapters.
- `business` owns feature state and provider workflows.
- `app` owns provider screens, navigation, permission prompts, and playback UI.

`extension/api` must not depend on app, business, data, Room, DAO, Compose,
Android activities or services, player implementations, parsers, or repositories.

Providers return declarative results. They do not write databases or call
arbitrary host APIs. Data-layer importers decide how to preserve favorites,
hidden state, ordering, recent playback, and other local state.

## Adding a hook

1. Define a stable hook identifier in `ExtensionHookIds`.
2. Define platform-neutral request and result payloads.
3. Declare the hook and every required capability in the provider manifest.
4. Bind exactly one implementation in the provider entrypoint.
5. Invoke it through `ExtensionRuntime` at an explicit host lifecycle point.
6. Consume the result in the owning host layer.
7. Cover success, denied capability, malformed input, and provider failure.

Keep request-response hooks as `suspend` calls. Introduce streaming or callbacks
only when a concrete hook cannot be represented as a bounded request and result.

## Emby-compatible providers

Emby and Jellyfin are separate user-facing server kinds backed by one shared
`EmbyCompatibleProvider`. The provider should implement discovery, account
validation, Live TV refresh, playback source resolution, and playback session
close hooks.

Persist stable provider, account, item, and media-source references. Resolve the
actual URL and headers immediately before playback. If resolution opens a server
session, close it on stop, channel change, or playback failure. Do not persist a
short-lived URL as the channel's permanent URL.

The built-in data adapter stores accounts, credentials, and channel playback
references in `provider_accounts`, `provider_credentials`, and
`channel_playback_references`. Provider channels use the non-network
`Channel.URL_DYNAMIC` marker; only the stable reference is durable. Schema 22 and
the 21-to-22 migration introduce these tables.

Use the Emby authorization headers for Emby servers. Jellyfin requests use the
current `Authorization: MediaBrowser …` scheme and do not send deprecated token
headers. Access tokens belong in authenticated request headers, never persisted
playback URLs, logs, or diagnostics. Provider authentication and resolved
playback use the platform TLS trust store independently from ordinary playlist
transport compatibility settings.

## Validation

Use JDK 17 and run the smallest relevant checks first:

```bash
./gradlew --configure-on-demand :extension:api:test :extension:runtime:test
./gradlew :data:connectedDebugAndroidTest
./gradlew :app:smartphone:assembleDebug :app:tv:assembleDebug
./gradlew :app:smartphone:connectedDebugAndroidTest :app:tv:connectedDebugAndroidTest
```

Also compile the TV app when a shared playback or provider flow affects it. When
Room changes, update the database version, migrations, schema artifacts, and
migration tests together.

Search the repository for removed module coordinates, package names, RPC/service
types, package-scanning permissions, sample feature metadata, and old dependency
aliases. The expected result is no source, build, manifest, or documentation
match. Generated baseline profiles are excluded unless they are being rebuilt.

## PR notes

Document:

- Removed experimental modules, UI, service, permission, and dependencies.
- Added standalone extension API and runtime modules.
- Hook, capability, manifest, invocation, result, and error boundaries.
- Confirmation that providers cannot access host persistence directly.
- Emby-compatible playback URL and session lifecycle behavior, when affected.
- Exact test, compile, migration, and mock-server validation results.
