# Playbook: Room Migration

## When to use this playbook

Use this when the task changes Room entities, tables, indices, DAOs, schema JSON, database versioning, or migration logic.

## Required context

Read these first:

- `AGENTS.md`
- `data/AGENTS.md`
- Current entity, DAO, database, migration, schema, and migration test files

## Safe change scope

The agent may modify:

- Room entities, DAOs, database version, migration code, and schema artifacts for the requested change
- Migration tests and fixtures
- Repository mapping code directly affected by schema changes

The agent should avoid modifying:

- UI and business logic unless required by a changed data contract
- Generated schema files manually without using the established Room workflow when available
- Destructive migrations unless explicitly requested and documented

## Architecture rules

- Update version, migrations, schema JSON, and preservation logic together.
- Keep data preservation explicit.
- Do not silently change table or column semantics.
- Keep Room entities and low-level persistence details out of UI.

## Common mistakes

- Changing an entity without adding a migration.
- Forgetting schema artifact updates.
- Dropping user data accidentally during migration.
- Missing index, foreign key, or default-value behavior in tests.

## Validation

Run:

```bash
./gradlew :data:testDebugUnitTest
```

Verify schema diffs and migration tests for every affected version path.

If the command is not available or cannot be run, explain why.

## PR notes

The PR should mention:

- Database version and schema changes
- Data preservation strategy
- Migration validation results
- Any downgrade or import/export risk
