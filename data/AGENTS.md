# AGENTS.md

This file applies to `data/`. Use it together with the root guidance.

## Data Layer Scope

- Data modules own persistence, network access, parsers, migrations, repositories, background work, and playback integration.
- Keep complex SQL in DAOs and business composition in repositories or ViewModels.
- Follow the repository's existing Flow error-handling style and avoid inventing new fallback conventions.
- Do not expose low-level data sources directly to UI modules.

## Room And Migrations

- When changing the database schema, update the version, migrations, schema JSON, and preservation logic together.
- Do not change Room entities, tables, or schemas without migrations.
- Keep migration behavior explicit and covered by the smallest relevant validation available.

## Networking, Parsers, And Playback

- Keep parsers and reusable models platform-neutral where practical.
- Isolate Android-specific file, network, service, or playback APIs behind narrow data-layer adapters.
- For playback, sync, database, permissions, or background-work changes, run the relevant Kotlin compile or assemble task.

## Background Work

- Workers should use the existing application-level WorkManager setup.
- Keep scheduling policy, constraints, and retry behavior consistent with nearby workers.