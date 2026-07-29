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
| Contract and runtime | Typed, versioned Hook contracts; each call receives only the current Hook's declared and approved capabilities; per-extension and host-wide admission caps; one deadline across preparation, queueing, execution, response validation, and broker requests; cumulative broker request-count, encoded request-byte, and encoded response-byte limits; cancellation, health, and failure isolation | `WireGoldenFixtureTest`, `ExtensionContractTest`, `ExtensionRuntimeTest`, `InvocationBudgetPropagationTest`, and `ExtensionHostBridgeTest`. CI runs the API golden, runtime, SDK, and transport unit tests; the broker bridge uses connected device evidence. |
| Built-in provider | Emby and Jellyfin are selectable variants of one built-in extension; the hidden automatic kind remains only as a compatibility value for existing accounts and is not offered for new subscriptions | `EmbyCompatibleProviderIntegrationTest`, `EmbyCompatibleProviderLocalizationTest`, and `SubscriptionProviderRepositoryIntegrationTest` |
| Provider credentials | External login returns a one-time host receipt. Post-validation scopes resolve references only into requests for the approved origin; the host does not directly serialize resolved values back to the extension. | `HostNetworkBrokerSecurityTest`, `ExtensionHostBridgeTest`, `ProviderBrokerScopeStoreTest`, and `CredentialVaultTest` |
| General Hook network access | Settings, search, metadata, EPG, and background Hooks can use the host broker when that Hook declares and receives `network`. Search/metadata/EPG use an account scope when their request has an account; other calls use approved manifest and explicitly saved setting origins. Discover stays offline. | `ExtensionNetworkOriginContractTest`, `ExtensionBrokerScopeRuntimeTest`, `ExtensionHookBrokerScopeStoreTest`, and `ExtensionHostBridgeTest` |
| Provider persistence | New and restored subscriptions use `DataSource.Provider`; generic provider accounts, backup without tokens, reauthentication state, WorkManager refresh, and restart session cleanup share one path | Migration, provider repository, worker, restore, and session cleanup tests |
| External lifecycle | Discovery, identity and certificate trust, review-bound enable/reauthorize tokens, enable/disable, capability and fixed-origin authorization, reconnect, clear data, diagnostics, streamed payloads, and cancellation | Transport tests, `ExtensionPluginRepositoryLifecycleTest`, and `ExternalExtensionIpcTest` |
| Extension settings | Manifest and dynamic schemas, ordinary values, encrypted secret handles, network-origin approval, and review-bound field edits | `ExtensionSettingsRepositoryTest` and `ExtensionPluginRepositoryLifecycleTest` |
| External reference provider | Discover, host-managed login, initial and later refresh, Room import, credential-backed playback resolve, header resolution, and session close cross Binder and use the same repository as built-in providers. | `ExternalProviderEndToEndTest` |
| Provider UI | Phone and TV use descriptor-driven provider lists and forms; Emby and Jellyfin remain separate choices, while external choices retain visible provider identity | `SubscriptionSourceSelectionTest`, `TvProviderAccessibilityTest`, and `ResourceContractTest`; connected UI tests currently require an explicit device run |
| Other Hooks | Settings, search, metadata enrichment, and EPG refresh have typed SDK handlers and product callers | SDK, contribution repository/importer, and IPC tests |
| Background task | Manifest task declarations are reconciled into periodic WorkManager jobs when an extension is enabled, reauthorized, or restored. Disablement or missing grants cancels them; network tasks use a connected constraint. | `ExtensionBackgroundTaskSchedulerTest`, Worker tests, and `ExtensionPluginRepositoryLifecycleTest` |

## How to read the evidence

A CI gate is run by `.github/workflows/android.yml`. A connected UI check is repeatable, but
currently needs an explicit device run. A device check is a recorded one-off run.
`ResourceContractTest` validates resource structure, not native-language quality.
CI syntax-checks the phone matrix runner and compiles the data, phone, and TV connected-test
harnesses; it does not execute either device matrix.

Latest connected phone run, 2026-07-29:

- Device and profile: Pixel_6_Pro API 36 on `emulator-5558`, using the runner's
  `phone` profile.
- Results: `compact-ltr` passed 16/16, `compact-narrow-ltr` passed 2/2, and
  `compact-rtl-large` passed 7/7 with `ar-XB` at 320dp width and 200% text.
- Coverage includes provider selection and forms plus the new plugin-detail Loading,
  Failure with retry, Missing, and Content states. It also verifies correct action-target
  ownership, non-duplicated live-region semantics, non-overlapping 48dp action targets,
  and complete version, package, service, and certificate information.

This run validates only the phone profile. Tablet validation remains pending as a separate
connected run. The latest TV evidence remains the 2026-07-28 API 34 run at 1280×720:
`TvProviderAccessibilityTest` passed 1/1 in English LTR and 1/1 in actual `ar-XB` RTL with
the rail on the right, including DPad entry, provider-form open/close, accessible naming,
and focus return to Emby. TV was not rerun as part of the phone command below.

Repeat the phone matrix on a disposable, booted API 33 or newer phone emulator with:

```shell
testing/bin/run-smartphone-provider-ui-matrix.sh emulator-5558 phone
```

The `phone` profile runs the complete compact English LTR group, the targeted compact-narrow
English LTR group, and the compact `ar-XB` RTL group at 320dp width and 200% text. Each run
passes a required named instrumentation case; the test fails if the argument, profile, app
locale, or actual device configuration does not match. The script restores the emulator display
settings and removes the test packages when it finishes.

## Before shipping the built-in provider path

- Run the complete migration chain from every supported starting schema through the current
  database version, currently 21→26.
- Run M3U, EPG, Xtream, ordinary playback, and DLNA regressions after provider or playback changes.
- Run the phone, tablet, and TV connected UI checks with the requested configuration asserted by
  the test: LTR and RTL, large text, compact and ≥600dp layouts, provider selection/forms, and TV
  DPad return focus. Record the device or AVD, locale, font scale, width, command, and result.
- Treat `ResourceContractTest` as a structural gate for keys, placeholders, plurals, and bidi
  controls. Native-speaker review of provider, sign-in, authorization, error, and destructive
  action copy is still required before a locale is called complete.
- Keep the database schema artifact and every manual migration in the same change.

## Before opening external extensions

- Decide the published threat model. The current broker does not protect a token from a malicious extension colluding with its approved server. A stronger guarantee requires host-owned protected-response parsing and import.
- Run the complete external provider flow on TV, through WorkManager, and through the real player rather than only the repository-level device test.
- Add CI-runnable connected UI automation for external authorization, reauthorization, settings,
  error states, destructive confirmations, and TV focus restoration. The built-in provider DPad
  test does not satisfy this gate.
- Add process-level hostile fixtures for a blocked call, ignored cancellation, process death, malformed or oversized output, retained broker access, signer change, and extension-ID collision.
- Run the same published conformance suite against built-in and external transports.
- Publish the SDK artifact together with the checked-in golden fixtures and compatibility policy.

## Decision rule

The built-in provider path may ship when its regression list is green. External extensions may remain available as a developer preview, but the default feature switch may be removed only after the external-opening list above is green and failures leave host data and the host process safe.
