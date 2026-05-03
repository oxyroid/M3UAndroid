# AGENTS.md

This file applies to the entire repository. More specific `AGENTS.md` files in subdirectories take precedence for their subtree. Start here for global rules, then read the nested file nearest to the code you are changing.

## Project Overview

M3UAndroid is a Kotlin Android IPTV player for phones, tablets, and Android TV. It supports M3U playlists, Xtream API, DLNA casting, Room persistence, WorkManager background sync, Media3/ExoPlayer playback, extensions, benchmark tooling, and multilingual resources.

## Progressive Guidance Map

- `app/AGENTS.md`: app modules, Compose UI, navigation, Hilt entry points, permissions, and platform presentation.
- `app/tv/AGENTS.md`: Android TV layouts, DPad focus, couch-distance readability, and video overlays.
- `business/AGENTS.md`: feature state, user actions, workflow logic, and KMP-friendly business rules.
- `core/AGENTS.md`: lightweight shared helpers, foundation UI primitives, contracts, and extension integration.
- `data/AGENTS.md`: Room, repositories, parsers, networking, playback coordination, migrations, and workers.
- `i18n/AGENTS.md`: localized strings, key naming, fallback behavior, and resource validation.
- `testing/AGENTS.md`: benchmarks, mock server, device tests, and test-scoped validation.

If a directory does not have a nested instruction file, use this root file plus the nearest parent guidance.

## Global Principles

- The long-term direction is Kotlin Multiplatform. Prefer code that can move toward shared KMP modules when adding reusable logic.
- Avoid Android platform APIs in business rules, parsers, models, reducers, validation, and other reusable logic. If Android APIs are required, isolate them behind app or data adapters.
- Keep requested changes narrowly scoped. Do not mix broad rewrites, unrelated refactors, or formatting churn into narrow tasks.
- Inspect nearby modules and existing helpers before changing code. Prefer established local patterns over new abstractions unless the abstraction removes real duplication or complexity.
- Use Kotlin only. Do not add Java.
- Package-qualified references belong in imports, not code bodies. Use import aliases for conflicts.
- Add dependencies through the version catalog and existing repositories only. Do not add jar files, unknown Maven repositories, or inline dependency versions.
- Do not use star imports.

## Architecture Boundaries

- Keep dependency direction clear: app depends on business/core/data/i18n; business depends on core/data; core stays independent from app and business.
- UI modules own Compose routes, screens, permission prompts, navigation wiring, and platform-specific presentation.
- Business modules own feature state, user actions, and reusable workflow logic.
- Data modules own persistence, networking, parsers, migrations, repositories, background work, and playback integration.
- Core modules should stay lightweight, reusable, and as platform-neutral as practical.
- UI must not directly access DAOs, databases, parser internals, or low-level data sources.

## Build And Validation

- Use the repository Gradle wrapper for builds and validation.
- Validate the smallest relevant module first, then broaden when changes affect user-facing flows, shared contracts, playback, sync, database, permissions, background work, or CI release paths.
- For documentation-only or small resource-only changes, at least run a diff whitespace check.
- Do not change Room entities, tables, or schemas without updating migrations and schema artifacts together.
