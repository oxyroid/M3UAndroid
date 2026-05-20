# AGENTS.md

This file applies to the entire repository. More specific `AGENTS.md` files in subdirectories take precedence for their subtree. Start here for global rules, then route by task type before reading the nearest directory instructions.

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

## Task Routing Guide

Before modifying code, identify the task type and read the relevant instructions. Use the task playbooks in `docs/ai/playbooks/` for high-risk areas.

### Android Phone UI changes

Read:

- `AGENTS.md`
- `app/AGENTS.md`
- The nearest feature-level `AGENTS.md`, if present

Rules:

- Keep UI state in ViewModel or feature state holders.
- Do not access DAO, database, parser internals, or low-level data sources directly from UI.
- Prefer small, focused composables.
- Avoid large unrelated UI refactors.

### Android TV UI changes

Read:

- `AGENTS.md`
- `app/AGENTS.md`
- `app/tv/AGENTS.md`
- `docs/ai/playbooks/android-tv-ui.md`

Rules:

- TV UI must be DPad-first.
- Every interactive element must have a clear focus state.
- Initial focus must be intentional.
- Avoid touch-only interaction assumptions.
- Consider long-distance readability.

### Data layer changes

Read:

- `AGENTS.md`
- `data/AGENTS.md`
- Relevant data playbooks under `docs/ai/playbooks/`

Rules:

- Repositories should expose stable APIs to upper layers.
- Do not leak Room entities or parser internals into UI.
- Migration changes must include validation.
- Parser behavior changes should include sample or regression tests when possible.

### Business/domain logic changes

Read:

- `AGENTS.md`
- `business/AGENTS.md`

Rules:

- Business logic should not depend on Android framework UI APIs.
- Keep workflow logic testable.
- Do not move UI-specific assumptions into business modules.

### Core module changes

Read:

- `AGENTS.md`
- `core/AGENTS.md`

Rules:

- Keep core lightweight.
- Do not add dependencies on app or business modules.
- Avoid Android framework dependencies unless the module explicitly allows them.

### i18n/resource changes

Read:

- `AGENTS.md`
- `i18n/AGENTS.md`
- `docs/ai/playbooks/i18n-resources.md`

Rules:

- Do not hardcode user-facing strings.
- Keep resource names stable and meaningful.
- Validate affected locales when possible.

### Extension system changes

Read:

- `AGENTS.md`
- `core/AGENTS.md`
- Relevant app, business, or data instructions for touched modules
- `docs/ai/playbooks/extension-system.md`
- Any extension-related design documents near the changed code

Rules:

- Do not break host/plugin API compatibility casually.
- Keep API surfaces stable.
- Be careful with classloader boundaries.
- Do not introduce direct host implementation dependencies into extension APIs.
- Mention compatibility risks in the PR.

### Playback-related changes

Read:

- `AGENTS.md`
- Relevant app, business, and data instructions
- `docs/ai/playbooks/playback-pipeline.md`
- Any playback-related design documents near the changed code

Rules:

- Avoid mixing UI state, playback engine state, and persistence concerns.
- Be careful with lifecycle handling.
- Mention behavior changes clearly in the PR.

### Build, release, benchmark, or APK size changes

Read:

- `AGENTS.md`
- Relevant module instructions for touched Gradle files
- `testing/AGENTS.md` when benchmarks or device tests are affected
- `docs/ai/playbooks/apk-size.md` for packaging or size work

Rules:

- Keep Gradle changes narrowly scoped and use the version catalog for dependencies.
- Do not add repositories, inline dependency versions, or generated artifacts without justification.
- Mention release, size, or benchmark risks in the PR.

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
- Use `docs/ai/VALIDATION_MATRIX.md` to choose the smallest relevant validation first, then broaden when changes affect user-facing flows, shared contracts, playback, sync, database, permissions, background work, or CI release paths.
- For documentation-only or small resource-only changes, at least run a diff whitespace check.
- Do not change Room entities, tables, or schemas without updating migrations and schema artifacts together.
- If validation cannot be run, explicitly state the command not run, why it was not run, and what risk remains.

## Agent Safety Rules

Agents must avoid the following unless explicitly requested:

- Large unrelated refactors
- Formatting-only changes across many files
- Moving logic across architectural layers without explanation
- Adding new dependencies without justification
- Changing public APIs without compatibility notes
- Modifying generated files manually
- Silently changing database schema
- Silently changing playback behavior
- Silently changing TV focus behavior
- Replacing project-specific abstractions with generic ones
- Introducing direct data-layer access into UI
- Introducing app or business dependencies into core modules

When uncertain, prefer a smaller change with clear validation over a broad rewrite.

## Context Budget Guidance

Do not load the entire repository into context. Use progressive disclosure:

1. Read root `AGENTS.md`.
2. Identify the task type.
3. Read the relevant module-level `AGENTS.md`.
4. Read the smallest set of source files needed to understand the change.
5. Read tests or nearby call sites before changing behavior.
6. Only expand context when the first pass is insufficient.

Prefer precise local context over broad unrelated context.

## Compatibility-Sensitive Areas

The following areas require extra care:

- Extension API
- Plugin runtime
- AIDL/protobuf contracts
- Room schema
- M3U/EPG parser behavior
- Playback behavior
- Public user settings
- Import/export formats

Changes in these areas must mention compatibility impact in the PR.

## Example Workflow: Fix a TV Focus Bug

1. Read `AGENTS.md`.
2. Read `app/AGENTS.md`.
3. Read `app/tv/AGENTS.md`.
4. Read `docs/ai/playbooks/android-tv-ui.md`.
5. Locate the affected screen.
6. Identify focusable elements and initial focus behavior.
7. Make the smallest change.
8. Compile the TV variant.
9. In the PR, mention the focus path affected and validation result.

## Example Workflow: Change EPG Matching Logic

1. Read `AGENTS.md`.
2. Read `data/AGENTS.md`.
3. Read `docs/ai/playbooks/m3u-epg-data.md`.
4. Locate repository, DAO, parser, and tests.
5. Add or update regression coverage.
6. Avoid changing UI unless required.
7. Run relevant tests.
8. In the PR, mention fallback behavior and duplicate handling.
