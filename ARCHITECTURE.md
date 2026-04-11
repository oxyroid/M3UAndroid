# M3UAndroid — Architecture Documentation

## Table of Contents

- [Overview](#overview)
- [Module Dependency Graph](#module-dependency-graph)
- [Module Breakdown](#module-breakdown)
  - [App Modules](#app-modules)
  - [Core Modules](#core-modules)
  - [Data Module](#data-module)
  - [Business Modules](#business-modules)
  - [Support Modules](#support-modules)
- [Smartphone Architecture](#smartphone-architecture)
  - [Navigation](#smartphone-navigation)
  - [UI Design System](#smartphone-ui-design-system)
  - [Player](#smartphone-player)
- [TV Architecture](#tv-architecture)
  - [Navigation](#tv-navigation)
  - [Focus & Input](#tv-focus--input)
- [Extension Architecture](#extension-architecture)
- [Data Layer](#data-layer)
  - [Database](#database)
  - [Repositories](#repositories)
  - [Parsers](#parsers)
  - [Player Manager](#player-manager)
  - [Background Workers](#background-workers)
- [Business Logic Layer](#business-logic-layer)
  - [ViewModels](#viewmodels)
- [Dependency Injection](#dependency-injection)
- [Key Design Patterns](#key-design-patterns)
- [Tech Stack Summary](#tech-stack-summary)

---

## Overview

M3UAndroid is a multi-module, multi-platform Android streaming media player supporting both smartphones and Android TV. The project follows MVVM architecture with a clean separation between platform-specific UI, shared business logic, and a unified data layer.

```
┌─────────────────────────────────────────────────────────────┐
│                     App Layer (UI)                          │
│  ┌──────────────┐  ┌──────────┐  ┌───────────────────────┐ │
│  │ app:smartphone│  │  app:tv  │  │    app:extension      │ │
│  └──────┬───────┘  └────┬─────┘  └───────────┬───────────┘ │
├─────────┼───────────────┼────────────────────┼──────────────┤
│         │    Business Logic Layer             │              │
│  ┌──────┴────────────────┴───────────────────┴──────────┐   │
│  │  business:foryou  │ business:playlist │ business:channel│ │
│  │  business:favorite│ business:setting  │ business:ext.  │  │
│  │  business:search  │ business:playlist-configuration   │  │
│  └──────────────────────────┬────────────────────────────┘  │
├─────────────────────────────┼───────────────────────────────┤
│                     Data Layer                              │
│  ┌──────────────────────────┴────────────────────────────┐  │
│  │                      data                             │  │
│  │  Database │ Repositories │ Parsers │ PlayerManager    │  │
│  │  Workers  │ API clients  │ Services                   │  │
│  └──────────────────────────┬────────────────────────────┘  │
├─────────────────────────────┼───────────────────────────────┤
│                     Core Layer                              │
│  ┌──────────┐  ┌────────────────┐  ┌──────────────────┐    │
│  │   core   │  │ core:foundation│  │  core:extension   │    │
│  └──────────┘  └────────────────┘  └──────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│  ┌──────┐  ┌──────────────────────┐                         │
│  │ i18n │  │ lint:annotation/proc │                         │
│  └──────┘  └──────────────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

## Module Dependency Graph

```
app:smartphone ──┬──► business:foryou ──────────► data ──► core ──► i18n
                 ├──► business:favorite                    │
                 ├──► business:setting                     ├──► core:foundation
                 ├──► business:playlist                    │
                 ├──► business:channel                     └──► core:extension ──► data
                 ├──► business:playlist-configuration
                 ├──► business:search
                 ├──► business:extension
                 ├──► data
                 ├──► core
                 ├──► core:foundation
                 └──► core:extension

app:tv ──────────┬──► business:foryou
                 ├──► business:favorite
                 ├──► business:setting
                 ├──► business:playlist
                 ├──► business:channel
                 ├──► business:playlist-configuration
                 ├──► data
                 └──► core, core:foundation

app:extension ───┬──► m3u-extension-api (external)
                 └──► m3u-extension-annotation + processor
```

## Module Breakdown

### App Modules

| Module | Package | Description |
|--------|---------|-------------|
| `app:smartphone` | `com.m3u.smartphone` | Phone/tablet app with Material 3, NavigationSuiteScaffold, search bar, Glance widgets |
| `app:tv` | `com.m3u.tv` | Android TV app with TV Material 3, D-pad navigation, focus management |
| `app:extension` | `com.m3u.extension` | Standalone extension APK for custom playlist parsers via AIDL |

### Core Modules

| Module | Package | Description |
|--------|---------|-------------|
| `core` | `com.m3u.core` | Preferences (DataStore), Publisher, FileProvider, wrapper types (`Event`, `Message`, `Resource`, `Sort`), utility extensions |
| `core:foundation` | `com.m3u.core.foundation` | Compose foundation utilities shared across platforms |
| `core:extension` | `com.m3u.core.extension` | Extension host runtime — AIDL bindings, extension discovery, communication with extension APKs |

### Data Module

| Module | Package | Description |
|--------|---------|-------------|
| `data` | `com.m3u.data` | Room database, repositories, parsers (M3U/Xtream/EPG), PlayerManager (Media3), WorkManager workers, Ktor server for remote control, Retrofit API clients |

### Business Modules

| Module | ViewModel | Responsibilities |
|--------|-----------|-----------------|
| `business:foryou` | `ForyouViewModel` | Playlist listing with counts, recommendation engine (recently played, unseen favorites), subscription status tracking, series episode loading |
| `business:favorite` | `FavouriteViewModel` | Favorite channel management with sorting and paging |
| `business:playlist` | `PlaylistViewModel` | Channel browsing by category with paging, sorting, search, pinning/hiding categories, shortcut creation, zapping mode, thumbnail sync |
| `business:channel` | `ChannelViewModel` | Active playback state, DLNA device discovery/casting, volume control, track selection (audio/video/subtitle), EPG programme guide, adjacent channel navigation, programme reminders, video recording |
| `business:setting` | `SettingViewModel` | App preferences, playlist management, backup/restore, hidden channels, EPG configuration, appearance settings |
| `business:playlist-configuration` | `PlaylistConfigurationViewModel` | Per-playlist settings: EPG manifest URLs, Xtream panel config, auto-sync, user agent |
| `business:extension` | `ExtensionViewModel` | Extension APK discovery, installation, management |
| `business:search` | `GlobalSearchViewModel` | Universal cross-playlist search with debounced queries, category/channel/live/VOD result grouping, playlist URL resolution for category navigation |

### Support Modules

| Module | Description |
|--------|-------------|
| `i18n` | String resources for 12+ languages |
| `lint:annotation` | Custom lint annotation definitions |
| `lint:processor` | KSP-based lint rule processor |
| `baselineprofile:smartphone` | Baseline profile generation for smartphone app |
| `baselineprofile:tv` | Baseline profile generation for TV app |

---

## Smartphone Architecture

### Smartphone Navigation

The smartphone app uses a two-level navigation structure:

```
MainActivity
  └── App (Composable)
        ├── NavigationSuiteScaffold (bottom/rail nav)
        │     ├── Foryou tab (app header banner, no search bar)
        │     ├── Favorite tab (TopSearchBar as filter)
        │     ├── Search tab (TopSearchBar as global search)
        │     ├── Extension tab
        │     └── Setting tab
        ├── Smart TopSearchBar (context-aware, hidden on Foryou)
        └── AppNavHost (NavHost)
              ├── rootGraph (tab destinations)
              │     ├── Foryou → ForyouRoute (empty state when no playlists)
              │     ├── Favorite → FavoriteRoute (search bar filters favorites)
              │     ├── Search → GlobalSearchRoute (cross-playlist search)
              │     ├── Extension → ExtensionRoute
              │     └── Setting → SettingRoute
              ├── playlistScreen → PlaylistScreen (search bar filters in-playlist, category navigation with auto-select)
              └── playlistConfigurationScreen → PlaylistConfigurationScreen

PlayerActivity (separate Activity, ComposeView with DisposeOnViewTreeLifecycleDestroyed)
  └── ChannelRoute → ChannelScreen + ChannelMask
        ├── Player (Media3 PlayerView)
        ├── Mask overlay (controls, gestures)
        ├── PlayerPanel (track selection, EPG)
        ├── DlnaDevicesBottomSheet
        └── FormatsBottomSheet
```

Key navigation patterns:
- `NavigationSuiteScaffold` adapts between bottom bar (phone) and navigation rail (tablet)
- Smart search bar is context-aware: app header on Foryou, filter on Favorite/Playlist, global search on Search tab. Search text is saved/restored on tab switches.
- `PlayerActivity` uses manual `ComposeView` with `DisposeOnViewTreeLifecycleDestroyed` strategy to prevent memory leaks during PiP window re-attach cycles
- Category navigation: tapping a category in global search navigates to the playlist with that category auto-selected via `initialCategory` parameter
- Deep linking via URL-encoded playlist URLs: `playlist_route/{url}?category={category}`
- Playlist configuration: `playlist_configuration_route/{url}`

### Smartphone UI Design System

Located under `app/smartphone/.../ui/material/`:

```
ui/material/
├── components/
│   ├── mask/           # Player overlay system
│   │   ├── Mask.kt              — AnimatedVisibility container with focus management
│   │   ├── MaskState.kt         — Visibility state with auto-hide timer
│   │   ├── MaskButton.kt        — Icon buttons for mask controls
│   │   ├── MaskCircleButton.kt  — Circular play/pause button
│   │   ├── MaskPanel.kt         — Expandable info panel
│   │   ├── MaskDefaults.kt      — Default timing/behavior config
│   │   └── MaskInterceptor.kt   — Touch event interception
│   ├── Player.kt           — Media3 PlayerView wrapper with ClipMode (adaptive/clip/stretched)
│   ├── BottomSheet.kt      — Modal bottom sheet wrapper
│   ├── Preferences.kt      — Settings list item components
│   ├── Selections.kt       — Selection/picker components
│   ├── SortBottomSheet.kt  — Sort option picker
│   ├── ThemeSelection.kt   — Color theme picker
│   ├── Destination.kt      — Navigation tab definitions (Foryou, Favorite, Search, Extension, Setting)
│   ├── Backgrounds.kt      — Background container with color
│   ├── Brushes.kt          — Custom gradient brushes
│   ├── Images.kt           — Coil image loading wrappers
│   ├── Badges.kt           — Badge/chip components
│   ├── TextFields.kt       — Styled text input fields
│   ├── FontFamilies.kt     — Google Sans, JetBrains Mono, Lexend Exa
│   ├── Lotties.kt          — Lottie animation wrappers
│   ├── PullPanelLayout.kt  — Pull-to-reveal panel layout
│   ├── MediaSheet.kt       — Media info bottom sheet
│   ├── EpisodesBottomSheet.kt — Series episode picker
│   ├── EventHandler.kt     — One-shot event consumer
│   └── SnackHost.kt        — Snackbar host
├── model/
│   ├── Theme.kt            — Material 3 dynamic/static color scheme setup
│   ├── Spacing.kt          — Spacing design tokens
│   ├── Duration.kt         — Animation duration tokens
│   ├── GradientColors.kt   — Gradient color definitions
│   └── LocalHazeState.kt   — CompositionLocal for Haze blur state
├── ktx/                    — Compose extension functions
│   ├── Blurs.kt            — Edge blur modifiers (Haze library)
│   ├── Colors.kt           — Color manipulation utilities
│   ├── Permissions.kt      — Runtime permission helpers
│   ├── Pager.kt            — HorizontalPager extensions
│   ├── Effects.kt          — Side-effect utilities
│   ├── LifecycleEffect.kt  — Lifecycle-aware effects
│   ├── InterceptEvent.kt   — Pointer input interception
│   ├── Interaction.kt      — Interaction source utilities
│   ├── Modifier.kt         — Custom modifier extensions
│   ├── PaddingValues.kt    — PaddingValues arithmetic
│   ├── ScrollableState.kt  — Scroll state extensions
│   └── Unspecified.kt      — Sentinel value helpers
├── transformation/
│   ├── BlurTransformation.kt       — Coil image blur transformation
│   └── ColorCombineTransformation.kt — Coil color overlay transformation
├── texture/
│   ├── Textures.kt         — Texture pattern definitions
│   └── TextureContainer.kt — Texture-backed container
├── brush/
│   └── Scrim.kt            — Gradient scrim brushes
├── effects/
│   └── BackStack.kt        — Navigation back stack effects
├── M3UHapticFeedback.kt    — Custom haptic feedback types
└── RecomposeHighlighter.kt — Debug recomposition visualizer
```

### Smartphone Player

The player screen (`PlayerActivity` → `ChannelScreen`) features:

- Full-screen `Player` composable wrapping Media3's `PlayerView`
- `ChannelMask` overlay with auto-hide behavior (tap to show/hide)
- Gesture areas:
  - Left vertical swipe → brightness control
  - Right vertical swipe → volume control
  - Horizontal swipe → seek (when supported)
- `PlayerPanel` — expandable bottom panel with:
  - Audio/video/subtitle track selection
  - EPG programme guide (ProgrammeGuide)
  - Adjacent channel list
- DLNA casting via `DlnaDevicesBottomSheet` (uses mm2d-upnp library)
- PiP mode support with `isInPipMode` static tracking
- Zapping mode — keeps player alive while browsing playlists

---

## TV Architecture

### TV Navigation

The TV app uses a nested navigation structure optimized for D-pad/remote control:

```
MainActivity
  └── MaterialTheme(darkColorScheme)
        └── App (Composable)
              └── NavHost (root)
                    ├── Dashboard (start)
                    │     ├── DashboardTopBar (tab row)
                    │     │     ├── Foryou tab
                    │     │     ├── Favorite tab
                    │     │     ├── Search tab
                    │     │     └── Profile tab (+ dynamic playlist tabs)
                    │     └── Body (nested NavHost)
                    │           ├── Foryou → ForyouScreen (carousel, top-10 lists)
                    │           ├── Favorite → FavoriteScreen
                    │           ├── Search → SearchScreen
                    │           └── Profile → ProfileScreen (settings, language, about)
                    ├── Playlist/{url} → PlaylistScreen
                    ├── Channel → ChannelScreen (full-screen player)
                    └── ChannelDetail/{id} → ChannelDetailScreen
```

Key differences from smartphone:
- Uses `androidx.tv.material3` (TV Material) instead of standard Material 3
- Always dark color scheme (`darkColorScheme()`)
- `DashboardTopBar` with animated show/hide based on scroll position
- `DashboardKey` sealed class supports dynamic tabs (playlists appear as tabs)
- `BackPressHandledArea` intercepts `Key.Back` for custom back navigation
- `FeaturedMoviesCarousel` — auto-scrolling content carousel
- `Top10MoviesList` — numbered horizontal list

### TV Focus & Input

The TV app is built around focus-based navigation:

- `FocusRequester` instances stored in `TopBarFocusRequesters` array for programmatic focus control
- `BringIntoViewIfChildrenAreFocused` — scrolls parent when child receives focus
- `onPreviewKeyEvent` handlers for D-pad key interception
- `isTopBarVisible` state controls top bar show/hide with animated offset
- `isComingBackFromDifferentScreen` flag manages focus restoration after navigation
- `GradientBg` — gradient background effects for TV visual style

TV Player components (`screens/player/components/`):
- `VideoPlayerOverlay` — full-screen overlay with controls
- `VideoPlayerControls` — play/pause, seek, channel info
- `VideoPlayerSeeker` — seek bar with thumbnail preview
- `VideoPlayerPulse` — visual feedback for fast-forward/rewind
- `VideoPlayerIndicator` — buffering/loading indicator
- `VideoPlayerState` — centralized player state management
- `RememberPlayer` — composable player lifecycle management

---

## Extension Architecture

The extension system allows third-party playlist parsers via separate APKs:

```
app:extension (standalone APK)
  ├── AndroidManifest.xml (declares extension metadata via manifestPlaceholders)
  │     ├── description, version, mainClass
  │     └── Extension discovery via PackageManager
  └── Uses m3u-extension-api + m3u-extension-annotation + KSP processor

core:extension (host-side runtime)
  ├── AIDL bindings for cross-process communication
  ├── Extension discovery and lifecycle management
  └── Wire protobuf for serialization
```

Extensions are discovered at runtime, communicate via AIDL, and provide custom parsing logic for playlist formats beyond M3U and Xtream.

---

## Data Layer

### Database

`M3UDatabase` — Room database at version 20 with auto-migrations:

| Entity | Table | Description |
|--------|-------|-------------|
| `Channel` | `streams` | Playable stream entries with URL, category, cover, DRM license info, favorite/hidden/seen state, EPG relation ID |
| `Playlist` | `playlists` | Subscription sources (M3U URL, Xtream credentials, EPG). Tracks pinned/hidden categories, user agent, auto-refresh |
| `Programme` | — | EPG programme data (title, start/end times, description) linked to channels via relation ID |
| `Episode` | — | Series episode metadata for Xtream VOD/Series content |
| `ColorScheme` | — | User-defined color scheme overrides |

DAOs: `ChannelDao` (includes global search queries: `searchCategories`, `searchByPrefix`, `searchByPlaylistUrls`, `findPlaylistUrlByCategory`), `PlaylistDao`, `ProgrammeDao`, `EpisodeDao`, `ColorSchemeDao`

Data sources supported:
- `DataSource.M3U` — Standard M3U/M3U8 playlist files
- `DataSource.Xtream` — Xtream Codes API (live, VOD, series subtypes)
- `DataSource.EPG` — Electronic Programme Guide XML feeds
- `DataSource.Emby` — Emby media server (planned)
- `DataSource.Dropbox` — Dropbox integration (planned)

### Repositories

| Repository | Key Operations |
|------------|---------------|
| `PlaylistRepository` | `m3uOrThrow()`, `xtreamOrThrow()` — subscribe to playlists; `refresh()` — re-fetch; `backupOrThrow()`/`restoreOrThrow()` — JSON backup to URI; `pinOrUnpinCategory()`, `hideOrUnhideCategory()` — category management; `readEpisodesOrThrow()` — load series episodes; `insertEpgAsPlaylist()` — add EPG source |
| `ChannelRepository` | `pagingAllByPlaylistUrl()` — paged channel queries with category/sort/search; `favouriteOrUnfavourite()`, `hide()`, `reportPlayed()` — channel state; `observeAdjacentChannels()` — prev/next channel for navigation; `observeAllUnseenFavorites()` — recommendation engine input; `searchCategories()`, `searchByPrefix()`, `searchByPlaylistUrls()` — global search queries; `findPlaylistUrlForCategory()` — category-to-playlist resolution |
| `ProgrammeRepository` | `pagingProgrammes()` — paged EPG data; `checkOrRefreshProgrammesOrThrow()` — EPG sync with cache; `getProgrammeCurrently()` — what's on now; `observeProgrammeRange()` — time range for EPG grid |
| `MediaRepository` | `savePicture()` — download channel cover; `loadDrawable()` — load image as Drawable; `installApk()` — install extension APK from byte channel |

### Parsers

```
data/parser/
├── m3u/
│   ├── M3UParser.kt      — Interface: InputStream → Flow<M3UData>
│   ├── M3UParserImpl.kt  — Line-by-line M3U/M3U8 parsing (#EXTINF, #EXTVLCOPT, etc.)
│   └── M3UData.kt        — Parsed M3U entry data class
├── xtream/
│   ├── XtreamParser.kt   — Interface: XtreamInput → Flow<XtreamData>
│   ├── XtreamParserImpl.kt — Xtream Codes API client (categories + streams)
│   ├── XtreamInput.kt    — Credentials (basicUrl, username, password, type)
│   ├── XtreamData.kt     — Parsed stream data
│   ├── XtreamInfo.kt     — Server info response
│   ├── XtreamOutput.kt   — Combined output
│   └── XtreamChannelInfo.kt — Detailed channel/series info with episodes
├── epg/
│   ├── EpgParser.kt      — Interface: InputStream → Flow<EpgProgramme>
│   ├── EpgParserImpl.kt  — XML pull parser for XMLTV format
│   └── EpgData.kt        — Parsed programme data
└── ParserModule.kt        — Hilt module binding parser implementations
```

### Player Manager

`PlayerManager` wraps Media3/ExoPlayer with a reactive API:

```kotlin
interface PlayerManager {
    val player: StateFlow<Player?>           // Current ExoPlayer instance
    val channel: StateFlow<Channel?>         // Currently playing channel
    val playlist: StateFlow<Playlist?>       // Current playlist context
    val playbackState: StateFlow<Int>        // Player.STATE_* constants
    val playbackException: StateFlow<PlaybackException?>
    val isPlaying: StateFlow<Boolean>
    val tracksGroups: StateFlow<List<Tracks.Group>>  // Available audio/video/subtitle tracks
    val size: StateFlow<Rect>                // Video dimensions
    val cwPosition: SharedFlow<Long>         // Continue-watching position updates

    suspend fun play(command: MediaCommand)  // Start playback
    fun chooseTrack(group: TrackGroup, index: Int)  // Select specific track
    fun clearTrack(type: Int)                // Reset track selection
    fun pauseOrContinue(value: Boolean)
    fun updateSpeed(race: Float)
    suspend fun recordVideo(uri: Uri)        // Record to file
    suspend fun reloadThumbnail(channelUrl: String): Uri?  // Cached thumbnail
    suspend fun syncThumbnail(channelUrl: String): Uri?    // Fresh thumbnail
}
```

Supported protocols (via Media3 extensions):
- HLS, DASH, SmoothStreaming, RTSP, RTMP
- FFmpeg decoder support via `nextlib-media3ext`
- DRM: Widevine, ClearKey, PlayReady
- OkHttp data source for custom HTTP handling

### Background Workers

| Worker | Purpose |
|--------|---------|
| `SubscriptionWorker` | Subscribes to M3U/Xtream/EPG playlists in background with progress notifications. Supports cancel/retry actions. Uses `OneTimeWorkRequest` with network constraint. |
| `BackupWorker` | Exports playlists + channels to JSON file at user-specified URI |
| `RestoreWorker` | Imports backup JSON, recreating playlists and channels |
| `ProgrammeReminder` | Schedules notifications for upcoming EPG programmes |

---

## Business Logic Layer

### ViewModels

All ViewModels use `@HiltViewModel` with constructor injection and expose state via `StateFlow`/`Flow`:

```
ForyouViewModel
├── playlists: StateFlow<Map<Playlist, Int>>          — All playlists with channel counts (empty state shown when map is empty)
├── subscribingPlaylistUrls: StateFlow<List<String>>  — Currently syncing playlists
├── refreshingEpgUrls: Flow<List<String>>             — Currently refreshing EPG sources
├── specs: StateFlow<List<Recommend.Spec>>            — Recommendation cards (recently played, unseen favorites)
├── episodes: StateFlow<Resource<List<Episode>>>      — Series episodes for selected series
└── query: MutableStateFlow<String>                   — Search query

PlaylistViewModel
├── playlist: StateFlow<Playlist?>                    — Current playlist metadata
├── channels: StateFlow<Map<String, Flow<PagingData<Channel>>>>  — Channels grouped by category, paged
├── categories: StateFlow<List<String>>               — Available categories (debounced)
├── sort: StateFlow<Sort>                             — Current sort order (UNSPECIFIED, ASC, DESC, RECENTLY, MIXED)
├── pinnedCategories: StateFlow<List<String>>          — User-pinned categories
├── categoryPrefixes: StateFlow<List<String>>          — Two-stage category prefix filters
├── prefixFilter: StateFlow<String?>                  — Currently selected prefix filter
├── zapping: StateFlow<Channel?>                      — Currently zapping channel
├── subscribingOrRefreshing: StateFlow<Boolean>       — Sync status
└── series/episodes                                   — Series episode management

GlobalSearchViewModel
├── query: StateFlow<String>                              — Current search query
├── state: StateFlow<GlobalSearchState>                   — Combined results (categories, channels, liveStreams, vod, playlistTitles)
├── isSearching: StateFlow<Boolean>                       — True when query ≥ 3 characters
└── findPlaylistUrlForCategory(category) → String?        — Resolves category to playlist URL for navigation

ChannelViewModel
├── playerState: StateFlow<PlayerState>               — Combined player state (playback, size, errors, isPlaying)
├── channel/playlist: StateFlow                       — Current channel and playlist
├── adjacentChannels: StateFlow<AdjacentChannels?>    — Previous/next channel for navigation
├── tracks: Flow<Map<TrackType, List<Format>>>        — Available media tracks
├── currentTracks: Flow<Map<TrackType, Format?>>      — Selected tracks
├── volume: StateFlow<Float>                          — System volume (0.0–1.0)
├── programmes: Flow<PagingData<Programme>>           — EPG data for current channel
├── programmeRange: StateFlow<ProgrammeRange>         — EPG time window
├── devices: List<Device>                             — Discovered DLNA devices
├── isDevicesVisible/searching: StateFlow<Boolean>    — DLNA UI state
└── programmeReminderIds: StateFlow<List<Int>>        — Scheduled reminders
```

---

## Dependency Injection

Hilt modules are organized by layer:

| Module | Location | Provides |
|--------|----------|----------|
| `AppModule` | `app:smartphone`, `app:tv` | `Publisher`, `WorkManager`, `NotificationManager`, `AudioManager` |
| `DatabaseModule` | `data` | `M3UDatabase`, all DAOs |
| `RepositoryModule` | `data` | Repository interface → implementation bindings |
| `ParserModule` | `data` | Parser interface → implementation bindings |
| `ServicesModule` | `data` | `PlayerManager`, `Messager`, `FileProvider` |
| `ApiModule` | `data` | Retrofit instances, OkHttp client |

---

## Key Design Patterns

1. **Reactive state management** — All data flows through `StateFlow`/`Flow` from database to UI. Room DAOs return `Flow<T>` for live queries, repositories transform and combine them, ViewModels expose them to Compose via `stateIn()`.

2. **Paging 3** — Channel lists use `PagingSource` → `Pager` → `PagingData<Channel>` for efficient large-list rendering with category-based grouping.

3. **Resource wrapper** — `Resource<T>` sealed class (`Loading`, `Success`, `Failure`) wraps async operations for consistent loading/error state handling.

4. **Event wrapper** — `Event<T>` ensures one-shot events (navigation, snackbars) are consumed exactly once.

5. **Platform-specific UI, shared logic** — Business modules contain ViewModels used by both smartphone and TV apps. Each app module provides its own Compose UI consuming the same ViewModel state.

6. **Composition Locals** — `LocalHelper`, `LocalSpacing`, `LocalHazeState` provide ambient values through the Compose tree without explicit parameter passing.

7. **WorkManager for background sync** — Playlist subscription, EPG refresh, backup/restore all run as `CoroutineWorker` instances with foreground notifications and cancel/retry support.

8. **DataStore preferences** — Type-safe preferences via `PreferencesKeys` with composable `preferenceOf()` / `mutablePreferenceOf()` for direct Compose integration.

---

## Tech Stack Summary

| Category | Technology |
|----------|-----------|
| Language | Kotlin (100%), JVM 17, context parameters enabled |
| UI Framework | Jetpack Compose (smartphone: Material 3, TV: TV Material 3) |
| Architecture | MVVM with Hilt DI |
| Navigation | Jetpack Navigation Compose (multi-NavHost) |
| Database | Room (v20, auto-migrations, KSP) |
| Networking | Retrofit + OkHttp, Ktor Server (remote control) |
| Media | Media3 ExoPlayer + FFmpeg (nextlib), HLS/DASH/RTSP/RTMP |
| Async | Kotlin Coroutines + Flow |
| Paging | Paging 3 with Compose integration |
| Image Loading | Coil with custom transformations (blur, color combine) |
| Serialization | Kotlinx Serialization (JSON), Wire (protobuf) |
| Background | WorkManager (subscription, backup, EPG sync, reminders) |
| Preferences | DataStore Preferences |
| DLNA | mm2d-upnp |
| Visual Effects | Haze (blur), Lottie (animations) |
| DRM | Widevine, ClearKey, PlayReady |
| Lint | Custom KSP-based lint rules (`@Exclude`, `@Likable`) |
| Performance | Baseline Profiles, LeakCanary (debug), Compose metrics |
| Crash Reporting | ACRA (notification + email) |
| Build | Gradle 8.11, Version Catalog (`libs.versions.toml`) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 33 (smartphone), 35 (extension) |
| Compile SDK | 36 |
| Distribution | GitHub Releases, F-Droid, IzzyOnDroid, Telegram |
| License | GPL 3.0 |
