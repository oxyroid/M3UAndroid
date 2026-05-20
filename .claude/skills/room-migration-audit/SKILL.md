---
name: room-migration-audit
description: Audit M3UAndroid Room schema and migration changes for versioning, schema artifacts, data preservation, indices, defaults, DAO compatibility, and migration validation.
allowed-tools: Read, Glob, Grep, Bash, Agent
argument-hint: "[repo path or data module path]"
---

# Room Migration Audit

Audit Room database changes for complete migration and data-preservation evidence.

## When To Use

Use when a task changes or reviews:

- Room entities, DAOs, database versions, migrations, or schema JSON
- Persistence models or query behavior that changes stored data semantics

## Required Context

Read first:

- `AGENTS.md`
- `data/AGENTS.md`
- `docs/ai/playbooks/room-migration.md`
- Relevant entity, DAO, database, migration, schema, and test files

## Audit Checks

Verify with file evidence:

- Database version, migration code, and schema artifacts are updated together.
- Migration preserves existing user data or documents intentional loss.
- Added columns have defaults or nullable semantics compatible with existing rows.
- Indices, foreign keys, and constraints match entity/query expectations.
- DAO/repository behavior remains compatible with upper layers.
- Migration tests cover every affected version path when available.

## Process

1. Map changed entities, database version, migration objects, schema files, and tests.
2. Compare old and new schema semantics.
3. Trace how repositories and DAOs consume changed columns/tables.
4. Check data preservation and default values.
5. Cite file paths and line numbers for every finding.

## Validation

Recommend:

```bash
./gradlew :data:testDebugUnitTest
```

If schema generation or migration tests are unavailable, state the remaining migration risk.

## Output

Return a concise Markdown report with:

- Scope and affected versions
- Pass/fail table for each audit check
- Evidence-backed findings with file paths
- Required migration or test fixes
- Validation run or not run, with reason
