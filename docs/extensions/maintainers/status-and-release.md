# Status and release gates

[简体中文](status-and-release.zh-CN.md) · [Maintainer guide](README.md)

This page defines what may ship from the current branch. Implementation instructions belong in the [extension developer guide](../developers/README.md).

## Release boundary

- The built-in Emby/Jellyfin extension follows the normal smartphone product release gate for
  phone and tablet layouts.
- External APK extensions remain an opt-in developer preview behind the developer switch.
- TV exposes and invokes no extension capability; it supports only M3U and Xtream.
- Keep the switch until the external-opening gates are green and every unresolved decision in the
  published [external APK threat model](threat-model.md) has a recorded outcome.

## Connected paths

| Area | Current behavior | Evidence |
| --- | --- | --- |
| Contract and runtime | Typed, versioned Hook contracts; each call receives only the current Hook's declared and approved capabilities; per-extension and host-wide admission caps; one deadline across preparation, queueing, execution, response validation, and broker requests; cumulative broker request-count, encoded request-byte, and encoded response-byte limits; cancellation, health, and failure isolation | `WireGoldenFixtureTest`, `ExtensionContractTest`, `ExtensionRuntimeTest`, `InvocationBudgetPropagationTest`, and `ExtensionHostBridgeTest`. One serialized conformance suite runs against the built-in runtime, SDK backend, and standalone reference APK. |
| SDK distribution | `1.0.0-alpha01` publishes API, Android protocol, typed SDK, sources, conformance code, and golden fixtures into a versioned Maven repository zip with a SHA-256 file. Hello is an independent Gradle consumer project that resolves the SDK group only from that repository. | The signed release job runs `verifyExtensionSdkBundle` and uploads the zip and checksum. `testing/bin/verify-extension-sdk-distribution.sh` remains the explicit pre-release check for the independent Hello consumer. The fast workflow intentionally packages only debug APKs. |
| Built-in provider | Emby and Jellyfin are selectable variants of one built-in extension in the smartphone app | `EmbyCompatibleProviderIntegrationTest`, `EmbyCompatibleProviderLocalizationTest`, and `SubscriptionProviderRepositoryIntegrationTest` |
| Complete provider contract | Every built-in and external provider implements `Discover`, `Validate`, `Refresh`, `Browse`, `ResolvePlayback`, `UpdatePlayback`, and `ClosePlayback`. `Browse` exposes bounded root/child pages with extensible media kinds and stable references. `UpdatePlayback` reports bounded session events. | `SubscriptionProviderContractsTest`, `ExtensionContractTest`, `ExtensionNetworkOriginContractTest`, `WireGoldenFixtureTest`, and provider product-flow tests |
| Provider credentials | External login returns a one-time host receipt. Post-validation scopes resolve references only into requests for the approved origin; the host does not directly serialize resolved values back to the extension. | `HostNetworkBrokerSecurityTest`, `ExtensionHostBridgeTest`, `ProviderBrokerScopeStoreTest`, and `CredentialVaultTest` |
| General Hook network access | Settings, search, metadata, EPG, and background Hooks can use the host broker when that Hook declares and receives `network`. Search/metadata/EPG use an account scope when their request has an account; other calls use approved manifest and explicitly saved setting origins. Discover stays offline. | `ExtensionNetworkOriginContractTest`, `ExtensionBrokerScopeRuntimeTest`, `ExtensionHookBrokerScopeStoreTest`, and `ExtensionHostBridgeTest` |
| Provider persistence | New and restored subscriptions use `DataSource.Provider`; generic provider accounts, backup without tokens, reauthentication state, WorkManager refresh, and restart session cleanup share one path | Migration, provider repository, worker, restore, and session cleanup tests |
| External lifecycle | Discovery, identity and certificate trust, review-bound enable/reauthorize tokens, enable/disable, capability and fixed-origin authorization, reconnect, clear data, diagnostics, file-backed large-payload transfer, and cancellation | Transport tests, `ExtensionPluginRepositoryLifecycleTest`, and `ExternalExtensionIpcTest` |
| Extension settings | Manifest and dynamic schemas, ordinary values, encrypted secret handles, network-origin approval, and review-bound field edits. Dynamic state is bound to one runtime registration and stays hidden after failure, disablement, or replacement until the current registration verifies it. | `ExtensionSettingsRepositoryTest`, `ExtensionPluginRepositoryLifecycleTest`, and `ExtensionHookBrokerScopeProviderTest` |
| External reference provider | A standalone reference APK crosses Binder/PFD for rejected and successful login, subscription, Room import, WorkManager refresh, media browse, credential-backed playback resolve/update, real PlayerManager/Media3 readiness, and session close. It uses the same repository as built-in providers. | `ExternalProviderEndToEndTest` |
| Provider UI | The smartphone app's phone and tablet layouts use descriptor-driven provider lists and forms; Emby and Jellyfin remain separate choices, while external choices retain visible provider identity | `SubscriptionSourceSelectionTest` and `ResourceContractTest`; connected UI tests currently require an explicit device run |
| Other Hooks | Settings, search, metadata enrichment, and EPG refresh have typed SDK handlers and product callers | SDK, contribution repository/importer, and IPC tests |
| Background task | Manifest task declarations are reconciled into periodic WorkManager jobs when an extension is enabled, reauthorized, or restored. Disablement or missing grants cancels them; network tasks use a connected constraint. | `ExtensionBackgroundTaskSchedulerTest`, Worker tests, and `ExtensionPluginRepositoryLifecycleTest` |

## How to read the evidence

`.github/workflows/android.yml` is a build and release workflow, not a functional-test gate. Pull
requests and manual non-publishing runs compile the three unsigned Release APKs. A publishing run
on `master` builds the signed app, TV, reference-plugin, and SDK artifacts, then checks their
certificates, default-library contents, and 16 KB packaging before upload. The manual fast workflow
only builds the three debug APKs, uploads them, and sends them to Telegram.

Unit, managed-device, and phone/tablet UI tests are explicit maintainer validation commands outside
these packaging workflows. The recorded results below are evidence from those runs.
`ResourceContractTest` checks resource structure, not native-language quality.

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
- Not covered: cold-start recovery of an open persisted playback session.

Latest connected phone run, 2026-07-30:

- Device and profile: Pixel_6_Pro API 36 on `emulator-5558`, using the runner's
  `phone` profile.
- Results: `compact-ltr` passed 20/20, `compact-narrow-ltr` passed 2/2,
  `compact-height-zh-cn-dark-three-button` passed 2/2, `compact-599-en-xa` passed
  3/3, and `compact-rtl-large` passed 11/11.
- Extension coverage includes descriptor-driven provider forms, the complete reference-plugin
  management lifecycle, and distinct Loading, retryable Failure, Missing, and Content states.
- Accessibility coverage verifies one action owner per plugin row, mirrored 48dp leading and
  trailing slots, non-duplicated status semantics, complete technical identity, and natural
  wrapping for long setting choices and single error announcements in RTL at 200% text. The run
  also covers the 599dp compact boundary, a 480dp-high Simplified Chinese IME case, `en-XA`,
  light/dark themes, and gesture/three-button navigation.

Latest connected tablet run, 2026-07-30:

- Device and profile: `6GB_RAM_Device` API 36 on `emulator-5554`, using the runner's
  `tablet` profile.
- Results: `medium-600-ltr` passed 1/1 at 600dp, `medium-839-ltr` passed 1/1 at
  839dp, and dark-theme `expanded-840-ltr-dark` passed 6/6 at 840dp.
- Extension-specific coverage verifies the descriptor-driven provider form and the complete
  external-plugin management lifecycle with the Settings side rail present and selected.
  The medium boundary cases also verify one contextual heading, one back action, single-pane
  navigation, and the 48dp back touch target.

Repeat the phone matrix on a disposable, booted API 33 or newer phone emulator with:

```shell
testing/bin/run-smartphone-provider-ui-matrix.sh emulator-5558 phone
```

The `phone` profile runs the complete compact English LTR group, the targeted compact-narrow
English LTR group, a 360 × 480dp Simplified Chinese dark/three-button IME case, the 599dp `en-XA`
boundary case, and the compact `ar-XB` RTL group at 320dp width and 200% text. Run large-window
cases separately on the dedicated tablet emulator:

```shell
testing/bin/run-smartphone-provider-ui-matrix.sh emulator-5554 tablet
```

The `tablet` profile runs exact 600, 839, and 840dp cases and never runs a phone-width case. Each
case verifies its display, font, theme, navigation, and app-locale configuration before
instrumentation. The script restores every changed display, developer, theme, and navigation
setting and removes the test packages when it finishes.

## Before shipping the built-in provider path

- Run the complete migration chain from every supported starting schema through the current
  database version, currently 21→22.
- Run M3U, EPG, Xtream, ordinary playback, and DLNA regressions after provider or playback changes.
- Run the phone and tablet connected UI checks with the requested configuration asserted by the
  test: LTR and RTL, large text, compact and ≥600dp layouts, and provider selection/forms. Record
  the device or AVD, locale, font scale, width, command, and result.
- Treat `ResourceContractTest` as a structural gate for keys, placeholders, plurals, and bidi
  controls. Native-speaker review of provider, sign-in, authorization, error, and destructive
  action copy is still required before a locale is called complete.
- Keep the database schema artifact and every manual migration in the same change.

## Before opening external extensions

- Resolve and record every open decision in the
  [external APK threat model](threat-model.md#what-remains-open), including HTTP/LAN policy,
  resolved-address handling, approved-server cooperation, Hook disclosure, and package admission.
- Keep `ExternalProviderEndToEndTest` green for rejected login, subscription, WorkManager refresh,
  media browse, playback updates, real-player readiness, and session close.
- Add a cold-start device test that leaves an external playback session open, restarts the host,
  and verifies idempotent remote close plus local session removal.
- Add CI-runnable smartphone UI automation for external authorization, reauthorization, settings,
  error states, and destructive confirmations on phone and tablet layouts.
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
