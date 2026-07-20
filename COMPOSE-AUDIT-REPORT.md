# Jetpack Compose Audit Report

Target: oxyroid/M3UAndroid (repository root)
Date: 2026-06-11
Scope: `app/smartphone`, `app/tv`, `app/extension`, `business/*`, `core/foundation` (Compose surfaces)
Excluded from scoring: `testing/*`, `baselineprofile/*` (cited as positive evidence only), `docs/`, `.claude/`, `lint/`, `data/` (no Compose UI)
Confidence: Medium
Overall Score: 68/100

## Scorecard

| Category | Score | Weight | Status | Notes |
|----------|-------|--------|--------|-------|
| Performance | 7/10 | 35% | solid | Strong stability hygiene; a few unkeyed lazy lists and in-composition allocations. Capped at 7 (no compiler reports). |
| State management | 7/10 | 25% | solid | Exemplary lifecycle-aware collection and `stateIn`; `MutableState` leaks into ViewModels and the business layer. |
| Side effects | 7/10 | 20% | solid | Disciplined effect usage overall; `LaunchedEffect(Unit)` captures changeable state in the player screen. |
| Composable API quality | 6/10 | 20% | needs work | Good modifier/i18n conventions; zero `@Preview`, hardcoded colors, giant composables, awkward parameter ordering. |

`overall = (7×0.35 + 7×0.25 + 7×0.20 + 6×0.20) × 10 = 68`

## Critical Findings

1. **API quality: zero `@Preview` coverage across 124 Compose UI files**
   - Why it matters: reusable components (`core/foundation`, `app/smartphone/ui/material/components`) cannot be verified in isolation; hidden ambient dependencies (e.g. `LocalHelper`, which `error()`s without a provider) go undetected until full-app runs.
   - Evidence: `grep -rn '@Preview' app business core` → 0 matches; e.g. `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/components/TextFields.kt`, `core/foundation/src/main/java/com/m3u/core/foundation/components/CircularProgressIndicator.kt`
   - Fix direction: add `@Preview` configurations to shared components first (material/components, core/foundation), then to screen-level pieces.
   - References: <https://developer.android.com/develop/ui/compose/tooling/previews>

2. **Performance: lazy lists without stable keys; `contentType` never used**
   - Why it matters: item moves/removals lose state and force full item recomposition; selection tracking in track/device sheets is identity-fragile. `contentType` appears 0 times in the repo.
   - Evidence: `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/components/DlnaDevicesBottomSheet.kt:118` (`items(devices)`), `.../components/FormatsBottomSheet.kt:101` (`items(currentTracks)`), `.../components/PlayerPanel.kt:401` (`items(value.episodes)`)
   - Fix direction: add `key = { ... }` from stable IDs (device UDN, track group+index, episode id) and `contentType` on heterogeneous lists.
   - References: <https://developer.android.com/develop/ui/compose/lists>

3. **State management: Compose `MutableState` used as ViewModel/business-layer state**
   - Why it matters: couples ViewModels and the `business/setting` module to the Compose runtime, hurts testability, and contradicts the repo's own KMP direction (AGENTS.md: business logic should avoid Android/Compose framework APIs). The repo otherwise uses `StateFlow` + `stateIn` consistently, so this is an inconsistency, not the norm.
   - Evidence: `app/smartphone/src/main/java/com/m3u/smartphone/ui/AppViewModel.kt:69` (`var searchQuery = mutableStateOf("")`), `:83-86` (`code`, `isConnectSheetVisible`, `connectedTv`, `connectionToTvValue`); `business/setting/src/main/java/com/m3u/business/setting/SettingProperties.kt:8-19` (10 `MutableState` fields exposed as a bundle)
   - Fix direction: replace with `MutableStateFlow` exposed as `StateFlow`; turn `SettingProperties` into an immutable UI-state data class with update events.
   - References: <https://developer.android.com/develop/ui/compose/architecture>

4. **Side effects: `LaunchedEffect(Unit)` gating long-lived collectors on changeable state**
   - Why it matters: `isSupportBrightnessGesture` is a `derivedStateOf` (`brightness != -1f`) evaluated once at effect launch; if support status changes, the brightness collector never starts (or keeps running). The neighbouring `DisposableEffect(Unit)` inside `if (brightnessGestureEnabled)` captures `prev` only on first entry.
   - Evidence: `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/ChannelScreen.kt:182-205`, `:207-213`
   - Fix direction: key the effect on `isSupportBrightnessGesture` (or move the condition inside the snapshotFlow), and key the `DisposableEffect` on `brightnessGestureEnabled`.
   - References: <https://developer.android.com/develop/ui/compose/side-effects>

5. **API quality: hardcoded colors in shared components break theming/dark mode**
   - Why it matters: the favourite-star gold is duplicated in two files, and a `core/foundation` defaults object hardcodes `Color.Gray` — every consumer inherits a color that ignores `MaterialTheme.colorScheme` and dark mode.
   - Evidence: `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/ChannelMask.kt:306` (`Color(0xffffcd3c)`), `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/playlist/components/ChannelItem.kt:96` (same literal), `core/foundation/src/main/java/com/m3u/core/foundation/components/CircularProgressIndicator.kt:60` (`val color = Color.Gray`), `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/brush/Scrim.kt:10-11`
   - Fix direction: route through `MaterialTheme.colorScheme` (or one named theme token for the star color).
   - References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>, <https://developer.android.com/develop/ui/compose/designsystems/material3>

## Category Details

### Performance — 7/10

**Performance ceiling check**

```
Compiler reports: NOT generated (build fails: git submodules `native-load-gradle-plugin`
and `parser` cannot be cloned in this sandbox, so the `dev.oxyroid.native-load`
included-build plugin is unresolvable).
Rule applied: without compiler diagnostics, cap Performance at 7.
qualitative score: 7
applied score: 7 (at cap)
```

**What is working**

- `compose_compiler_config.conf` marks third-party types (`Uri`, Media3 `Format`/`Download`, `kotlinx.datetime.LocalDateTime`, …) stable — rare, deliberate stability hygiene. · References: <https://developer.android.com/develop/ui/compose/performance/stability/fix>
- ~50 `@Immutable`/`@Stable` annotations across `core` and UI models (`MaskState` is `@Stable`). · References: <https://developer.android.com/develop/ui/compose/performance/stability>
- Typed state factories used (`mutableFloatStateOf`, `mutableIntStateOf` — `ChannelScreen.kt:122-124`, `CanvasBottomSheet.kt:61`). · References: <https://developer.android.com/develop/ui/compose/state>
- High-traffic lists are keyed: `ChannelGallery.kt:385` and `PlayerPanel.kt:385` use `channels.itemKey { it.id }`; `PlaylistGallery.kt:96` keys on `playlist.url`. · References: <https://developer.android.com/develop/ui/compose/lists>
- Release hygiene: `app/smartphone/build.gradle.kts:29` `isMinifyEnabled = true`; dedicated `baselineprofile/smartphone` and `baselineprofile/tv` modules. · References: <https://developer.android.com/develop/ui/compose/performance/baseline-profiles>

**What is hurting the score**

- Three unkeyed `items(...)` calls and zero `contentType` usage repo-wide (Critical Finding 2).
- `Map.entries.toList()` allocated directly in composition/lazy builders.
- A four-deep `derivedStateOf` chain whose inputs change exactly as often as outputs.

**Evidence**

- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/components/DlnaDevicesBottomSheet.kt:118` — `items(devices)` without `key` · References: <https://developer.android.com/develop/ui/compose/lists>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/components/FormatsBottomSheet.kt:101` — `items(currentTracks)` without `key`; selection matched by field comparison instead · References: <https://developer.android.com/develop/ui/compose/lists>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/foryou/components/PlaylistGallery.kt:95` — `playlists.entries.toList()` re-allocated every recomposition (same pattern in `PlaylistScreen.kt:414`, `EpgManifestGallery.kt:52`) · References: <https://developer.android.com/develop/ui/compose/performance/bestpractices>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/setting/components/CanvasBottomSheet.kt:83-90` — chained `derivedStateOf` for `red`/`green`/`blue` derived from a value that changes at the same rate; pure overhead · References: <https://developer.android.com/develop/ui/compose/side-effects>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/components/ProgrammeGuide.kt:495-505` — 1-second ticking `produceState` recomposes the guide continuously while expanded; consider deferring the read · References: <https://developer.android.com/develop/ui/compose/performance/phases>
- Stability of shared model classes (e.g. `Channel`, `Playlist` Room entities passed into composables) is **inferred from source, not verified against compiler reports** — see Notes And Limits.

### State Management — 7/10

**What is working**

- 63 `collectAsStateWithLifecycle()` call sites vs. a single bare `collectAsState(` — effectively 100% lifecycle-aware collection. · References: <https://developer.android.com/develop/ui/compose/state>
- ViewModels consistently expose `StateFlow` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)` (e.g. `business/setting/src/main/java/com/m3u/business/setting/SettingViewModel.kt:71-85`). · References: <https://developer.android.com/develop/ui/compose/architecture>
- `rememberSaveable` used for recreate-surviving UI state (sort sheet, focus category in `PlaylistScreen.kt`, `FavouriteScreen.kt`). · References: <https://developer.android.com/develop/ui/compose/state>
- `remember` correctly keyed where inputs change: `ForyouScreen.kt:186` keys on `configuration.orientation`. · References: <https://developer.android.com/develop/ui/compose/state>

**What is hurting the score**

- `mutableStateOf` as ViewModel state and `MutableState`-bundle in the business layer (Critical Finding 3). The official guidance treats ViewModel `mutableStateOf` as an acceptable team tradeoff, but here it contradicts the repo's own architecture rules and its otherwise-consistent `StateFlow` pattern.
- String-based navigation routes (`Destination.Foryou.name` as `startDestination`, `business/playlist/PlaylistNavigation.kt` route constants) on a Navigation Compose 2.8+ project where type-safe `@Serializable` routes are available.

**Evidence**

- `app/smartphone/src/main/java/com/m3u/smartphone/ui/AppViewModel.kt:69,83-86` — five `mutableStateOf` properties as ViewModel state; `:44` builds paging off `snapshotFlow { searchQuery.value }` instead of a `MutableStateFlow` · References: <https://developer.android.com/develop/ui/compose/architecture>
- `business/setting/src/main/java/com/m3u/business/setting/SettingProperties.kt:8-19` — business module exposes ten raw `MutableState` fields; split ownership between ViewModel and UI · References: <https://developer.android.com/develop/ui/compose/state-hoisting>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/common/AppNavHost.kt:35` — string route (`Destination.Foryou.name`) instead of type-safe routes · References: <https://developer.android.com/develop/ui/compose/navigation>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/ChannelScreen.kt:122` — `remember { mutableFloatStateOf(helper.brightness) }` captures a stale initial value if `helper.brightness` changes externally; tolerable for transient gesture state but fragile · References: <https://developer.android.com/develop/ui/compose/state>

### Side Effects — 7/10

**What is working**

- Navigation, snackbar, and repository calls live in event handlers/callbacks — no `navigate(...)` in composition bodies was found. · References: <https://developer.android.com/develop/ui/compose/navigation>
- `snapshotFlow { … }` is collected from inside `LaunchedEffect` (the canonical pattern), e.g. `ChannelScreen.kt:184-204`. · References: <https://developer.android.com/develop/ui/compose/side-effects>
- 26 `rememberCoroutineScope()` sites are all event-driven work; no lifecycle-reinvention found. · References: <https://developer.android.com/develop/ui/compose/side-effects>
- `rememberUpdatedState` used to avoid stale captures in long-lived gesture effects (`ChannelScreen.kt:402-404`). · References: <https://developer.android.com/develop/ui/compose/side-effects>
- `DisposableEffect` cleanup restores prior system brightness (`ChannelScreen.kt:207-213`). · References: <https://developer.android.com/develop/ui/compose/side-effects>

**What is hurting the score**

- `LaunchedEffect(Unit)` bodies that branch on changeable state without keys or `rememberUpdatedState` (Critical Finding 4). This is the dominant effect smell, concentrated in the player screen (8 of ~14 repo-wide `LaunchedEffect(Unit)` sites are in `ChannelScreen.kt`).
- `DisposableEffect(Unit)` whose captured `prev` value belongs to a condition (`brightnessGestureEnabled`) that is not a key.

**Evidence**

- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/ChannelScreen.kt:182-187` — `if (isSupportBrightnessGesture)` evaluated once inside `LaunchedEffect(Unit)`; collector never starts/stops when support changes · References: <https://developer.android.com/develop/ui/compose/side-effects>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/ChannelScreen.kt:207-213` — `DisposableEffect(Unit)` should be keyed on `brightnessGestureEnabled` so `prev` re-captures on re-entry · References: <https://developer.android.com/develop/ui/compose/side-effects>

### Composable API Quality — 6/10

**What is working**

- Shared components consistently expose `modifier: Modifier = Modifier` in the conventional position. · References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- User-facing strings route through the dedicated `i18n` module via `stringResource`; no hardcoded UI text found. · References: <https://developer.android.com/develop/ui/compose/resources>
- `Modifier.composed { }` used only where CompositionLocal access genuinely requires it (4 sites, e.g. `ui/material/ktx/Modifier.kt:16`). · References: <https://developer.android.com/develop/ui/compose/custom-modifiers>
- `ComponentDefaults`-style objects exist (`TextFieldDefaults`, `CircularProgressIndicatorDefaults`). · References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- Theme tokens (`LocalSpacing`, `LocalDuration`, `LocalGradientColors`) are legitimate tree-scoped data with defaults.

**What is hurting the score**

- Zero `@Preview` annotations in 124 Compose UI files (Critical Finding 1).
- Hardcoded colors in shared components, including a `core/foundation` defaults object (Critical Finding 5).
- Parameter ordering violations in widely used internal APIs.
- Monolithic composables: `ChannelMask.kt` (755 lines), `PlayerPanel.kt` (539), `ChannelScreen.kt` (523), `ProgrammeGuide.kt` (505).
- `LocalHelper` (`staticCompositionLocalOf<Helper> { error(...) }`) carries an app-scoped service object through the tree with no sensible default — component-specific configuration via a Local.

**Evidence**

- `core/foundation/src/main/java/com/m3u/core/foundation/components/CircularProgressIndicator.kt:60` — foundation default `Color.Gray`, ignores theme/dark mode · References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/components/PlayerPanel.kt:90-105` — `modifier` buried after 10 required params, with more required callbacks after it · References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/ChannelMask.kt:116-755` — single composable owns controls, gestures, animation, and state · References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/common/helper/Helper.kt:168` — `LocalHelper` with `error()` default; service-object via CompositionLocal · References: <https://developer.android.com/develop/ui/compose/compositionlocal>

## Prioritized Fixes

1. **Add stable `key =` (and `contentType`) to the three unkeyed lazy lists** — `DlnaDevicesBottomSheet.kt:118`, `FormatsBottomSheet.kt:101`, `PlayerPanel.kt:401`. Cheap, mechanical, removes the only crash/state-loss-class list issue. (<https://developer.android.com/develop/ui/compose/lists>)
2. **Migrate `AppViewModel` `mutableStateOf` fields and `SettingProperties` to `StateFlow`/immutable UI state** — `AppViewModel.kt:69,83-86`, `SettingProperties.kt:8-19`. Aligns with the repo's KMP direction and its own `stateIn(WhileSubscribed(5_000))` convention. (<https://developer.android.com/develop/ui/compose/architecture>)
3. **Key the player gesture effects on their gating state** — `ChannelScreen.kt:182-205` (`LaunchedEffect`) and `:207-213` (`DisposableEffect`) keyed on `isSupportBrightnessGesture` / `brightnessGestureEnabled`. Fixes a real behavioral bug class (brightness collector frozen at first composition). (<https://developer.android.com/develop/ui/compose/side-effects>)
4. Replace hardcoded colors with theme tokens (`ChannelMask.kt:306`, `ChannelItem.kt:96`, `CircularProgressIndicator.kt:60`, `Scrim.kt:10-11`) and start adding `@Preview` to `core/foundation` and `ui/material/components`.

## Notes And Limits

- **Compiler diagnostics used: no.** The Gradle build with the audit init script fails before configuration: the repo depends on two git submodules (`native-load-gradle-plugin` included build providing the `dev.oxyroid.native-load` plugin, and `parser`) that could not be cloned in this sandbox (network-restricted). All stability/skippability claims are **inferred from source, not measured**; Performance is capped at 7 accordingly and overall confidence reduced from High to Medium.
- `data/` module and TV module (`app/tv`) were sampled more lightly than `app/smartphone`; TV-specific focus behavior is out of scope for this rubric (a separate `android-tv-focus-audit` exists in this repo).
- Accompanist **permissions** usage (4 files) was *not* deducted: unlike pager/swiperefresh/flowlayout/systemuicontroller, there is no first-party Compose replacement for it.
- Accessibility, test coverage, and Material 3 token compliance were not scored (v1 scope).
- Weight choice: default 35/25/20/20.
- Renormalization: none — no N/A categories.

## Suggested Follow-Up

- Run a `material-3` audit: the app is fully Material 3 but the hardcoded-color findings and 6 CompositionLocal theme channels suggest token-compliance gaps worth a dedicated pass.
- Re-run this audit with submodules initialized (CI or local) so compiler reports can verify skippability of `Channel`/`Playlist` entity params passed into composables.
