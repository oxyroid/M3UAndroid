# AGENTS.md

This file applies to the entire repository. If a nested `AGENTS.md` is added later, the more specific file takes precedence for that subtree.

## Project Overview

M3UAndroid is a Kotlin Android IPTV player for phones, tablets, and Android TV. It supports M3U playlists, Xtream API, DLNA casting, Room persistence, WorkManager background sync, Media3/ExoPlayer playback, extensions, and multilingual resources.

Main modules currently enabled in `settings.gradle.kts`:

- `:app:smartphone`: Main phone application. Contains the Hilt application, activities, Compose navigation, player entry points, Glance widget, ACRA, and startup setup.
- `:app:extension`: Example/extension application using `m3u-extension` annotations and KSP.
- `:core`: Shared architecture, preferences, wrappers, utilities, and contracts.
- `:core:foundation`: Lightweight Compose foundation components and UI helpers.
- `:core:extension`: Extension remote service, AIDL, Wire, and ServiceLoader integration.
- `:data`: Room database, DAOs, repository implementations, parsers, Media3/PlayerManager, WorkManager workers, and network APIs.
- `:business:*`: Business ViewModels and reusable feature logic, including foryou, favorite, setting, playlist, playlist-configuration, channel, and extension.
- `:i18n`: Translatable string resources.
- `:baselineprofile:smartphone`: Smartphone baseline profile generation.
- `:lint:annotation` / `:lint:processor`: Project KSP annotations and processors such as `@Likable` and `@Exclude`.

`app/tv` and `baselineprofile/tv` exist in the tree, but are currently commented out in `settings.gradle.kts`. Before changing TV-related code, confirm whether the task also requires enabling those modules and validating their build.

## Architecture Boundaries

- UI code lives under `app/smartphone/src/main/java/com/m3u/smartphone/ui/**` or `app/tv/**`, using Jetpack Compose Routes, Screens, and components.
- Business state and user actions usually belong in `business/*` ViewModels, using `@HiltViewModel`, `viewModelScope`, `StateFlow`, and `Flow`.
- Data access goes through interfaces and implementations in `data/repository/**`. UI must not directly access DAOs, the Room database, or parser implementations.
- `data/database/**` is for Room entities, DAOs, database setup, migrations, and converters. Database version changes must update migrations and `data/schemas/...`.
- Playback control is centralized in `data/service/PlayerManager` and its implementation. Business code should coordinate playback through `MediaCommand` and `PlayerManager`.
- Preferences use `core/architecture/preferences`: `Settings`, `PreferencesKeys`, `preferenceOf`, `mutablePreferenceOf`, and `Settings.flowOf`.
- One-shot UI events follow `core.wrapper.Event`, `eventOf`, and `handledEvent`.
- Keep dependency direction clear: app -> business/core/data/i18n, business -> core/data, data -> core. Core must not depend back on app or business modules.

## Build And Validation

- Use the repository Gradle wrapper: `./gradlew ...`.
- Common validation commands:
  - `./gradlew :app:smartphone:assembleDebug`
  - `./gradlew :app:smartphone:compileDebugKotlin`
  - `./gradlew :data:compileDebugKotlin`
  - `./gradlew :baselineprofile:smartphone:generateBaselineProfile`
- For documentation or small resource-only changes, at least run `git diff --check`.
- When adding or changing dependencies, prefer `gradle/libs.versions.toml`; do not scatter hard-coded versions across module build files.
- The repository is configured for `google()`, `mavenCentral()`, `jitpack.io`, and the Gradle plugin portal. Do not add jar libraries or unknown private repositories.

## Kotlin And Compose Style

- Always use Kotlin. Do not add Java.
- Use Kotlin official code style. The JVM target is 17.
- Do not use star imports.
- Do not add view-based XML UI. Use Compose for normal UI; traditional Views are allowed only inside interop surfaces such as `AndroidView`.
- Most composable functions without return values should remain restartable and skippable. Avoid creating unstable large objects or running side effects directly inside composables.
- Use the appropriate Compose side-effect APIs: `LaunchedEffect`, `DisposableEffect`, `LifecycleResumeEffect`, `rememberUpdatedState`, `rememberCoroutineScope`, and similar existing patterns.
- Prefer `collectAsStateWithLifecycle`, `remember`, `rememberSaveable`, and `derivedStateOf` for Compose state. Do not keep long-lived business coroutines in UI code.
- Do not blindly chain many Flows into complex reactive pipelines. If the recomputation cost is high or the extra reactivity has little user value, prefer a simpler `suspend` call, explicit refresh trigger, cached state, or a narrower observed source.
- `List`, `Map`, and `Set` are allowed in composable parameters because they are listed in `compose_compiler_config.conf`.
- Use `ImageVector.vectorResource` for drawable vectors. Do not use `Painter` to inflate drawable resources.
- If UI code needs `Context`, use `LocalContext` or existing helpers. ViewModels must not extend `AndroidViewModel`. If application context is needed, inject `@ApplicationContext Context` with Hilt.
- To change system bar visibility, use the existing helpers: `Helper#statusBarsVisibility` and `Helper#navigationBarsVisibility`.

## Data, Room, And Background Work

- Repository implementations usually follow `internal class XxxRepositoryImpl @Inject constructor(...) : XxxRepository`, then bind the interface in `RepositoryModule` with `@Binds`.
- DAOs return `Flow`, `PagingSource`, or `suspend` results. Keep complex SQL in DAOs, and keep business composition in repositories or ViewModels.
- Follow the existing Flow error-handling style. Repository flows may use `.catch { emit(...) }` to provide safe defaults.
- Room entities commonly use `@Immutable` and `@Serializable`. Use `@Likable` for generated similarity helpers, and mark fields excluded from similarity with `@Exclude`.
- When changing the database schema:
  - Bump the `M3UDatabase` version.
  - Add an `AutoMigration` or a manual migration.
  - Update `data/schemas/com.m3u.data.database.M3UDatabase/`.
  - Consider preserving favorites, hidden channels, playback history, and other persistent fields.
- Workers should follow the existing Hilt/WorkManager setup. Do not bypass `M3UApplication.workManagerConfiguration`.

## Resources And Localization

- Add user-visible text to the `:i18n` module, then use `import com.m3u.i18n.R.string` and `stringResource(string.xxx)`.
- Do not add translatable business text to app modules. App resources should stay limited to application-level values such as `app_name`.
- New strings must at least be added to `i18n/src/main/res/values`. If the change is directly relevant to Chinese users, also update `values-zh-rCN`; for larger features, update all existing locales or clearly document the localization gap.
- Keep resource names grouped by the existing files: `app.xml`, `ui.xml`, `feat_playlist.xml`, `feat_setting.xml`, and similar feature files.

## Dependency Injection And Registration

- Use Hilt: `@HiltAndroidApp` for the Application, `@AndroidEntryPoint` for activities, and `@HiltViewModel` for ViewModels.
- Put singleton providers or interface bindings in the appropriate `*Module.kt`, following the existing `@InstallIn(SingletonComponent::class)` pattern.
- When adding KSP or annotation processor dependencies, update the relevant module `build.gradle.kts`; do not configure them only in the app module.
- Extension features involve `m3u-extension`, `ServiceLoader`, AIDL, and manifest metadata. Read the existing patterns in `core/extension` and `app/extension` before changing them.

## Navigation And Feature UI

- Navigation routes usually live in a feature module's `*Navigation.kt`, while the app NavHost calls extension functions to register screens.
- Route composables connect ViewModels, permissions, navigation, and helpers. Screen/components should stay mostly parameter-driven.
- Do not scatter player launch logic. On smartphone, playback usually flows through `MediaCommand` + `helper.play(...)` + `PlayerActivity`.
- Permission requests should follow the existing Accompanist permissions and `checkPermissionOrRationale` helpers.
- TV UI uses `androidx.tv.material3` and focus-related helpers. Do not directly copy phone layouts into TV code.

## Do

- Inspect nearby modules and existing helpers before changing code.
- Keep module boundaries clear, and place new behavior in app, business, data, or core deliberately.
- Add dependencies through the version catalog, and verify that the artifact is available from existing repositories.
- Put new user-visible strings in `:i18n`.
- Update Room migrations and schemas together with database changes.
- Use coroutines and Flow for asynchronous work, following project patterns such as `SharingStarted.WhileSubscribed(5_000)`.
- For high-risk changes involving playback, sync, database, permissions, or background work, run the relevant Kotlin compile or assemble task.

## Do Not

- Do not add Java.
- Do not use star imports.
- Do not add view-based XML UI.
- Do not use `AndroidViewModel`.
- Do not let UI code directly access DAOs, the database, or parser implementations.
- Do not build large Flow chains by default when a simpler data path is cheaper and clear enough.
- Do not hard-code user-visible business text in composables.
- Do not use `Painter` to inflate drawable resources; use `ImageVector.vectorResource`.
- Do not casually enable the currently commented-out TV or baselineprofile TV modules unless the task explicitly requires it.
- Do not add jar files, unknown Maven repositories, or dependency versions outside `libs.versions.toml`.
- Do not change Room entities, tables, or schemas without migrations.
- Do not reformat unrelated code, reorder unrelated files, or remove user changes.
