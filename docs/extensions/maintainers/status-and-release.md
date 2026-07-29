# Status and release gates

[简体中文](status-and-release.zh-CN.md) · [Maintainer guide](README.md)

This page defines what may ship from the current branch. Implementation instructions belong in the [extension developer guide](../developers/README.md).

## Release boundary

- The built-in Emby/Jellyfin extension follows the normal product release gate.
- External APK extensions remain an opt-in developer preview behind the developer switch.
- Keep the switch until the external-opening gates are green and every unresolved decision in the
  published [external APK threat model](threat-model.md) has a recorded outcome.

## Connected paths

| Area | Current behavior | Evidence |
| --- | --- | --- |
| Contract and runtime | Typed, versioned Hook contracts; each call receives only the current Hook's declared and approved capabilities; per-extension and host-wide admission caps; one deadline across preparation, queueing, execution, response validation, and broker requests; cumulative broker request-count, encoded request-byte, and encoded response-byte limits; cancellation, health, and failure isolation | `WireGoldenFixtureTest`, `ExtensionContractTest`, `ExtensionRuntimeTest`, `InvocationBudgetPropagationTest`, and `ExtensionHostBridgeTest`. One serialized conformance suite runs against the built-in runtime, SDK backend, and standalone reference APK. |
| SDK distribution | `1.0.0-alpha01` publishes API, Android protocol, typed SDK, sources, conformance code, and golden fixtures into a versioned Maven repository zip with a SHA-256 file. Hello is an independent Gradle consumer project that resolves the SDK group only from that repository. | `verifyExtensionSdkBundle`, `verifyExtensionSdkRepository`, and `testing/bin/verify-extension-sdk-distribution.sh`. Both workflows are configured to upload the zip and checksum; the first Actions run is still required as remote evidence. |
| Built-in provider | Emby and Jellyfin are selectable variants of one built-in extension; the hidden automatic kind remains only as a compatibility value for existing accounts and is not offered for new subscriptions | `EmbyCompatibleProviderIntegrationTest`, `EmbyCompatibleProviderLocalizationTest`, and `SubscriptionProviderRepositoryIntegrationTest` |
| Provider credentials | External login returns a one-time host receipt. Post-validation scopes resolve references only into requests for the approved origin; the host does not directly serialize resolved values back to the extension. | `HostNetworkBrokerSecurityTest`, `ExtensionHostBridgeTest`, `ProviderBrokerScopeStoreTest`, and `CredentialVaultTest` |
| General Hook network access | Settings, search, metadata, EPG, and background Hooks can use the host broker when that Hook declares and receives `network`. Search/metadata/EPG use an account scope when their request has an account; other calls use approved manifest and explicitly saved setting origins. Discover stays offline. | `ExtensionNetworkOriginContractTest`, `ExtensionBrokerScopeRuntimeTest`, `ExtensionHookBrokerScopeStoreTest`, and `ExtensionHostBridgeTest` |
| Provider persistence | New and restored subscriptions use `DataSource.Provider`; generic provider accounts, backup without tokens, reauthentication state, WorkManager refresh, and restart session cleanup share one path | Migration, provider repository, worker, restore, and session cleanup tests |
| External lifecycle | Discovery, identity and certificate trust, review-bound enable/reauthorize tokens, enable/disable, capability and fixed-origin authorization, reconnect, clear data, diagnostics, file-backed large-payload transfer, and cancellation | Transport tests, `ExtensionPluginRepositoryLifecycleTest`, and `ExternalExtensionIpcTest` |
| Extension settings | Manifest and dynamic schemas, ordinary values, encrypted secret handles, network-origin approval, and review-bound field edits. Dynamic state is bound to one runtime registration and stays hidden after failure, disablement, or replacement until the current registration verifies it. | `ExtensionSettingsRepositoryTest`, `ExtensionPluginRepositoryLifecycleTest`, and `ExtensionHookBrokerScopeProviderTest` |
| External reference provider | A standalone reference APK crosses Binder/PFD for rejected and successful login, subscription, Room import, WorkManager refresh, credential-backed playback resolve, real PlayerManager/Media3 readiness, and session close. It uses the same repository as built-in providers. | `ExternalProviderEndToEndTest` |
| Provider UI | Phone and TV use descriptor-driven provider lists and forms; Emby and Jellyfin remain separate choices, while external choices retain visible provider identity | `SubscriptionSourceSelectionTest`, `TvProviderAccessibilityTest`, and `ResourceContractTest`; connected UI tests currently require an explicit device run |
| Other Hooks | Settings, search, metadata enrichment, and EPG refresh have typed SDK handlers and product callers | SDK, contribution repository/importer, and IPC tests |
| Background task | Manifest task declarations are reconciled into periodic WorkManager jobs when an extension is enabled, reauthorized, or restored. Disablement or missing grants cancels them; network tasks use a connected constraint. | `ExtensionBackgroundTaskSchedulerTest`, Worker tests, and `ExtensionPluginRepositoryLifecycleTest` |

## How to read the evidence

A CI gate is run by `.github/workflows/android.yml`. A connected UI check is repeatable, but
currently needs an explicit device run. A device check is a recorded one-off run.
`ResourceContractTest` validates resource structure, not native-language quality.
CI syntax-checks the phone matrix runner and compiles the data, phone, and TV connected-test
harnesses. Its external-extension gate starts and health-checks the local reference server, then
runs `HostileExternalExtensionIpcTest`, `ExternalExtensionConformanceIpcTest`,
`ExternalProviderEndToEndTest`, and `DebugDefaultLibraryBootstrapTest` with the standalone
reference APK on the `hostileApi34` build-managed device. The reference server supplies a
deterministic PCM WAV fixture for the real-player check. This gate does not run the phone, tablet,
or TV UI matrices.

Latest hostile IPC run, 2026-07-29:

- Device: Pixel 6 Pro API 36 on `emulator-5558`.
- Result: 3/3 passed with the fixture in the instrumentation APK, a different UID, and a dedicated
  `:hostile` process.
- Coverage: repeated malformed output, a valid oversized result, ignored cancellation and a late
  callback, the same host bridge working while scoped and rejecting use after revocation, process
  death, reconnect with a new PID, a stale persisted signer pin, reviewed certificate repinning,
  and rejection of a real process that claims an extension ID already owned by another trusted
  service.
- The signer case compares PackageManager's current certificate for the real discovered service
  with a seeded stale pin. It does not install a differently signed replacement APK.

Latest shared external conformance run, 2026-07-29:

- Device: Pixel 6 Pro API 36 on `emulator-5558`.
- Result: 1/1 passed with the reference extension installed as a standalone APK under a different
  UID from the host.
- Coverage: typed success with matching invocation, extension, Hook, and schema identifiers;
  request/settings/grant/budget context; rejection of a missing required capability and unsupported
  schema; typed request/result transfer over PFD JSON; and AIDL cancellation observed by the remote
  handler.

Latest external provider production-path run, 2026-07-29:

- Result: `ExternalProviderEndToEndTest` passed with the reference extension installed as a
  standalone APK; provider calls crossed Binder with PFD JSON payloads.
- Failure path: three rejected logins returned `provider.authentication_failed` without disabling
  the plugin.
- Data path: a successful subscription imported two channels, and a WorkManager background refresh
  completed with the same two-channel result.
- Playback path: the real `PlayerManager` and Media3 reached `STATE_READY` with the deterministic
  WAV fixture. Both explicit close and player release closed their server-side sessions.
- Not covered: the full external-provider flow on TV and cold-start recovery of an open persisted
  playback session.

Latest connected phone run, 2026-07-29:

- Device and profile: Pixel_6_Pro API 36 on `emulator-5558`, using the runner's
  `phone` profile.
- Results: `compact-ltr` passed 20/20, `compact-narrow-ltr` passed 2/2, and
  `compact-rtl-large` passed 11/11 with `ar-XB` at 320dp width and 200% text.
- Extension coverage includes descriptor-driven provider forms, the complete reference-plugin
  management lifecycle, and distinct Loading, retryable Failure, Missing, and Content states.
- Accessibility coverage verifies one action owner per plugin row, mirrored 48dp leading and
  trailing slots, non-duplicated status semantics, complete technical identity, and natural
  wrapping for long setting choices and single error announcements in RTL at 200% text.

Latest connected tablet run, 2026-07-29:

- Device and profile: `6GB_RAM_Device` API 36 on `emulator-5554`, using the runner's
  `tablet` profile.
- Results: `medium-ltr` passed 1/1 at 800dp and `wide-ltr` passed 6/6 at 1080dp,
  both in English LTR with normal text size.
- Extension-specific coverage verifies the descriptor-driven provider form and the complete
  external-plugin management lifecycle with the Settings side rail present and selected.
  The medium case also verifies the single-pane header, back navigation, and its 48dp touch target.

The latest TV evidence remains the 2026-07-28 API 34 run at 1280×720:
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

- Resolve and record every open decision in the
  [external APK threat model](threat-model.md#what-remains-open), including HTTP/LAN policy,
  resolved-address handling, approved-server cooperation, Hook disclosure, and package admission.
- Keep `ExternalProviderEndToEndTest` green for rejected login, subscription, WorkManager refresh,
  real-player readiness, and session close.
- Add the same complete external-provider flow on TV. The phone production-path gate does not prove
  the TV player and focus lifecycle.
- Add a cold-start device test that leaves an external playback session open, restarts the host,
  and verifies idempotent remote close plus local session removal.
- Add CI-runnable connected UI automation for external authorization, reauthorization, settings,
  error states, destructive confirmations, and TV focus restoration. The built-in provider DPad
  test does not satisfy this gate.
- Keep the process-level hostile fixture green for blocked or late calls, ignored cancellation,
  process death, malformed or oversized output, retained broker access, stale signer trust, and
  extension-ID collision.
- Keep the shared conformance suite green for the built-in runtime, SDK backend, and standalone APK.
- Keep the versioned SDK bundle and independent Hello consumer green. Before default opening,
  attach the bundle and checksums to a durable release channel rather than relying only on
  short-lived CI artifacts.

## Decision rule

The built-in provider may ship independently when its regression gates are green. External
extensions remain an opt-in developer preview; removing the switch requires every external-opening
gate and the published threat-model decision.
