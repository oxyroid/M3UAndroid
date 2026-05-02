# AGENTS.md

This file applies to the entire repository. If a nested `AGENTS.md` is added later, the more specific file takes precedence for that subtree.

## Project Overview

M3UAndroid is a Kotlin Android IPTV player for phones, tablets, and Android TV. It supports M3U playlists, Xtream API, DLNA casting, Room persistence, WorkManager background sync, Media3/ExoPlayer playback, extensions, and multilingual resources.

Main enabled areas:

- App modules for smartphone, Android TV, and extensions.
- Shared core modules for architecture helpers, UI foundation pieces, contracts, and extension integration.
- Data and business modules for persistence, networking, playback coordination, repositories, and feature logic.
- Localization, baseline profile, testing, and lint helper modules.

## Guiding Principles

- The long-term direction of this project is Kotlin Multiplatform. When adding features or fixing bugs, prefer code that can move toward shared KMP modules over Android-only implementations.
- Avoid platform APIs in business rules, parsers, models, state reducers, validation, and other reusable logic. If Android APIs are truly needed, isolate them behind narrow app/data adapters or expect/actual-ready boundaries.
- Unless the prompt explicitly asks for a broad project rewrite, keep new features and bug fixes as small and local as practical. Do not fold unrelated refactors, mass moves, or architectural rewrites into narrow tasks.
- If text is truncated on a real device or emulator, fix the display behavior first: layout constraints, wrapping, max lines, scaling strategy, overflow handling, adaptive spacing, or component structure. Do not shorten copy as the primary fix unless the text itself is wrong or the user asks for wording changes.
- Inspect nearby modules and existing helpers before changing code. Prefer the repository's established patterns over new abstractions unless the new abstraction removes real duplication or complexity.

## Architecture Boundaries

- Keep dependency direction clear: app depends on business/core/data/i18n, business depends on core/data, and core stays independent from app and business.
- UI modules own Compose routes, screens, permission prompts, navigation wiring, and platform-specific presentation details.
- Business modules own feature state, user actions, and reusable workflow logic.
- Data modules own persistence, network access, parsers, migrations, repositories, background work, and playback integration.
- Core modules should stay lightweight, reusable, and as platform-neutral as practical.
- UI must not directly access DAOs, databases, parser internals, or low-level data sources.

## Kotlin And Compose Style

- Always use Kotlin and the repository's existing Kotlin style.
- Keep UI in Compose unless the existing code explicitly uses interop for a narrow platform surface.
- Keep composables stable, lightweight, and mostly parameter-driven. Put long-lived work in ViewModels or business logic rather than directly in UI.
- Use Compose state and side-effect APIs deliberately, following nearby code patterns.
- Avoid overly complex reactive pipelines when an explicit refresh, cached state, or narrower observed source would be clearer and cheaper.
- Use existing UI helpers and resource-loading conventions instead of introducing parallel patterns.

## Navigation And Feature UI

- Keep navigation registration close to the feature that owns the screen.
- Route-level composables should connect ViewModels, permissions, navigation, and platform helpers; lower-level screen components should stay mostly parameter-driven.
- Keep playback launch and permission flows consistent with the existing app patterns instead of scattering new entry points.
- Treat TV UI as a focus-first experience. Do not directly copy phone layouts into TV code.

## Data, Room, And Background Work

- Keep repository, DAO, migration, and worker changes inside the data layer unless a higher layer only needs to consume an interface.
- Keep complex SQL in DAOs and business composition in repositories or ViewModels.
- Follow the repository's existing Flow error-handling style and avoid inventing new fallback conventions.
- When changing the database schema, update the version, migrations, schemas, and preservation logic together.
- Workers should use the existing application-level WorkManager setup.

## Dependency Injection And Registration

- Use Hilt and the repository's existing injection patterns for applications, activities, ViewModels, providers, and bindings.
- Register dependencies in the module that owns them; do not configure processors or generated-code dependencies only from an app module.
- Extension features rely on several registration mechanisms. Read the existing extension modules before changing that flow.

## Resources And Localization

- Put user-visible strings in the localization module and consume them through Compose resources.
- Keep translatable business text out of app modules unless it is truly app-level metadata.
- Add new strings to the default locale and update relevant translations when the feature directly affects those users.
- Keep new resources grouped with nearby feature resources.

## Build And Validation

- Use the repository Gradle wrapper for builds and validation.
- Validate the smallest relevant module first, then broaden to app builds when the change affects user-facing flows, shared contracts, playback, sync, database, permissions, or background work.
- For documentation or small resource-only changes, at least run a diff whitespace check.
- Add or update dependencies through the version catalog and existing repositories only.

## Do

- Keep module boundaries clear, and place new behavior in app, business, data, or core deliberately.
- Prefer KMP-friendly designs for new reusable logic, keeping Android-specific APIs at module edges.
- Keep requested changes narrowly scoped unless the user explicitly asks for a larger migration or rewrite.
- Add dependencies through the version catalog, and verify that the artifact is available from existing repositories.
- Put new user-visible strings in the localization module.
- Update Room migrations and schemas together with database changes.
- Use coroutines and Flow for asynchronous work, following nearby project patterns.
- For high-risk changes involving playback, sync, database, permissions, or background work, run the relevant Kotlin compile or assemble task.

## Do Not

- Do not add Java.
- Do not use star imports.
- Do not add view-based XML UI.
- Do not put Android framework dependencies into ViewModels or reusable business logic.
- Do not let UI code directly access DAOs, the database, or parser implementations.
- Do not build large Flow chains by default when a simpler data path is cheaper and clear enough.
- Do not hard-code user-visible business text in composables.
- Do not use Android platform APIs in reusable business/domain logic when a KMP-friendly standard Kotlin approach is practical.
- Do not solve device/emulator text truncation by merely shortening correct user-facing copy; fix layout and adaptive rendering first.
- Do not introduce parallel resource-loading patterns when an existing project convention already covers the need.
- Do not add jar files, unknown Maven repositories, or dependency versions outside the version catalog.
- Do not change Room entities, tables, or schemas without migrations.
- Do not reformat unrelated code, reorder unrelated files, or remove user changes.
