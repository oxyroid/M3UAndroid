# M3UAndroid Refactoring Roadmap

This document tracks the refactoring effort announced for M3UAndroid. The project is entering a period of stabilisation: **no new features** until existing areas are reviewed, cleaned up, or removed. Every change must come as a small, focused PR that compiles and can be rolled back independently.

## Feature Status Matrix

| Feature | Module(s) | Status | Notes |
|---|---|---|---|
| M3U playlist subscribe & browse | `data/parser/m3u`, `business/playlist`, `app/smartphone`, `app/tv` | **stable** | Core IPTV experience — keep and stabilise |
| Xtream API (live / VOD / series) | `data/parser/xtream`, `data/repository/playlist` | **stable** | Keep; isolate cleanly behind repository boundary |
| Favourites | `business/favorite`, `data/database/dao/ChannelDao` | **stable** | Keep |
| EPG (programme guide) | `data/parser/epg`, `data/repository/programme`, `ProgrammeRepository` | **stable** | Keep basic display; defer complex UI work |
| ExoPlayer / Media3 playback | `data/service/internal/PlayerManagerImpl`, `business/channel` | **stable** | Keep; surface a cleaner interface to business layer |
| Smartphone UI | `app/smartphone` | **stable** | Keep; reduce screen composable parameter count |
| Android TV UI | `app/tv` | **stable** | Keep core playback and channel browsing; reduce phone-side coupling |
| Backup & restore | `data/worker/BackupWorker`, `data/worker/RestoreWorker`, `business/setting` | **experimental** | Verify end-to-end before promoting; keep behind a clear entry point |
| WebDAV data source | `DataSource.Dropbox` (placeholder), settings | **experimental** | Not functional; hide from stable UI |
| Codec pack / FFmpeg native load | `data/codec`, `native-load-gradle-plugin` | **experimental** | Keep capability; simplify UI exposure |
| DLNA casting | `data/tv` (NSD + HTTP server), smartphone connect UI | **experimental** | Freeze new work; evaluate complexity vs. value |
| Remote-control HTTP server | `data/tv/http`, `data/tv/nsd` | **experimental** | Freeze; high complexity, unclear user demand |
| Inter-process extension / plugin | `core/extension`, `business/extension`, `app/extension` | **experimental** | High risk; keep minimal entry point, no new APIs |
| Emby data source | `DataSource.Emby` | **deprecated** | Listed in model but not implemented; remove after confirming no persisted data |
| Auto codec-pack management UI | `data/codec/CodecPackRepository`, setting fragment | **deprecated** | Overengineered; simplify or remove |

**Status definitions**
- `stable` — production-ready, actively maintained, bugs are fixed promptly.
- `experimental` — partially implemented or insufficiently tested; visible to users but clearly labelled or behind a flag; new bug reports are tracked but may be deferred.
- `deprecated` — scheduled for removal in an upcoming refactoring step; UI entry points are hidden or removed first.
- `removed` — code deleted after confirming no remaining callers, migrations, or manifest entries.

---

## Refactoring Phases

### Phase 0 — Baseline stabilisation *(current)*

Goal: establish a reliable safety net before any structural change.

- [x] Document build commands:
  - `./gradlew :app:smartphone:assembleRelease` — primary smartphone build
  - `./gradlew :app:tv:assembleRelease` — TV build
  - Per-module: `./gradlew :<module>:compileDebugKotlin` for fast compile checks
- [ ] Add minimal unit tests for critical data-layer parsers (M3U, Xtream, EPG) and Room migrations
- [x] Confirm active modules in `settings.gradle.kts`
- [x] Add stable keys to all lazy lists (PR 2 — see below)

### Phase 1 — Product scope reduction

Goal: resolve fragmented experience by clarifying which features are in scope.

- [x] Publish feature status matrix (this document)
- [ ] Hide `experimental` and `deprecated` features behind explicit labels or `BuildConfig` flags in Settings
- [ ] Remove or stub `DataSource.Emby` and `DataSource.Dropbox` UI entry points

### Phase 2 — Architecture boundary alignment

Goal: make module responsibilities unambiguous.

Rules:
- `app/*` — Compose screens, routes, navigation, permissions, Activities, platform adapters. No DAO/parser access.
- `business/*` — ViewModels, UI state, user actions, lightweight workflow. No raw Android framework in state reducers.
- `data` — Room, Repositories, Parsers, Media3, Workers, TV/DLNA adapters. No direct exposure of DAOs to UI.
- `core/*` — lightweight shared models, UI primitives, extension contracts. No feature-specific logic.

Priorities:
1. `business/channel` ↔ `data/service/PlayerManager` playback state boundary
2. `business/playlist` ↔ `data/repository/playlist|channel` channel list data flow
3. `business/setting` ↔ backup/restore workers / codec / subscription

### Phase 3 — Playback and channel data flow refactor

Goal: single source of truth for playback state; no data loading inside item composition.

1. Define a clean `PlaybackSession` model (current playlist, channel, media source, state, formats, favourite/CW).
2. Move per-item programme/thumbnail loading from `ChannelGallery` `produceState` blocks into the ViewModel / repository layer.
3. Expose `Flow<PagingData<ChannelWithMetadata>>` from `PlaylistViewModel` rather than raw `Channel` paging.

### Phase 4 — Compose / UI refactor

Priorities based on the [Compose audit](COMPOSE-AUDIT-REPORT.md) (overall 59/100):

1. ~~Stable keys on all lazy lists~~ **done in Phase 0 / PR 2**
2. Introduce `kotlinx.collections.immutable` for composable collection parameters (after advisory-DB safety check)
3. Break up `PlaylistScreen` (23 parameters) into a state-model + smaller sections
4. Fix `Preference` composable — move `modifier` to last parameter
5. Apply `rememberSaveable` where `remember` is used for UI state that should survive config changes

### Phase 5 — Settings, sync, backup/restore governance

- Split Settings state into sections: appearance / playlist-subscription / backup-restore / codec / remote-control / experimental
- Standardise Worker scheduling policy, retry, constraints, user-visible failure messaging

### Phase 6 — Remove or freeze overengineered parts

Candidates (in priority order):
1. Inter-process extension / plugin system
2. Remote-control HTTP + NSD service
3. Auto codec-pack management
4. Emby / Dropbox data source stubs

Process: hide UI entry point → confirm no callers → remove after one release cycle.

### Phase 7 — Test and release gates

Every refactoring PR must satisfy:

| Change area | Required checks |
|---|---|
| Data layer | affected module compile + parser/repository unit tests + Room migration check |
| UI | affected app assemble + Compose compile |
| Playback | `:app:smartphone:assembleRelease` + `:app:tv:assembleRelease` + manual smoke test |
| Workers | task registration, constraints, failure path |
| i18n | all new string keys have English default + at least one translation fallback |

---

## PR Backlog

| # | Title | Status | Phase |
|---|---|---|---|
| 1 | Refactoring scope documentation | **done** | 0 / 1 |
| 2 | Add stable keys to all lazy lists | **done** | 0 / 4 |
| 3 | Move per-item data loading out of `ChannelGallery` | pending | 3 |
| 4 | Consolidate `PlaylistScreen` parameters into a state model | pending | 4 |
| 5 | Settings page experimental feature separation | pending | 5 |
| 6 | Playback state boundary: `ChannelViewModel` ↔ `PlayerManager` | pending | 2 / 3 |
| 7 | Freeze or hide extension/plugin system entry points | pending | 6 |
