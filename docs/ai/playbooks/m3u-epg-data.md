# Playbook: M3U and EPG Data

## When to use this playbook

Use this when the task involves M3U parsing, EPG parsing, channel-to-programme matching, playlist associations, Xtream data, or guide fallback behavior.

## Required context

Read these first:

- `AGENTS.md`
- `data/AGENTS.md`
- Relevant repository, DAO, parser, model, and test files
- Mock server fixtures under `testing/` when sample data is relevant

## Safe change scope

The agent may modify:

- Parser logic and parser tests
- Repository matching or fallback behavior
- DAO queries directly related to channel/programme results
- Sample fixtures needed for regression coverage

The agent should avoid modifying:

- UI behavior unless the data contract intentionally changes
- Database schema without the Room migration playbook
- Import/export formats without compatibility notes

## Architecture rules

- Keep parser behavior platform-neutral where practical.
- Do not leak parser internals or Room entities into UI.
- Preserve channels with no programmes unless the task explicitly changes fallback behavior.
- Treat timezone, time ranges, duplicates, and empty guide data as compatibility-sensitive.

## Common mistakes

- Joining EPG tables in a way that drops channels without programmes.
- Matching only by display name when stable IDs are available.
- Ignoring duplicate programmes or overlapping time ranges.
- Changing empty EPG fallback behavior without tests or PR notes.

## Validation

Run:

```bash
./gradlew :data:testDebugUnitTest
```

Also run parser regression tests or sample fixture checks that cover the changed M3U/EPG behavior.

If the command is not available or cannot be run, explain why.

## PR notes

The PR should mention:

- Matching and fallback behavior changes
- Duplicate programme or timezone handling
- Compatibility risks for existing playlists/imports
- Validation results
