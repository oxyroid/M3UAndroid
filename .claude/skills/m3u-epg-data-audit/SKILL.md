---
name: m3u-epg-data-audit
description: Audit M3UAndroid M3U and EPG data flows for parser behavior, channel/programme matching, duplicate handling, fallback behavior, joins, and timezone/time-range correctness.
allowed-tools: Read, Glob, Grep, Bash, Agent
argument-hint: "[repo path or data module path]"
---

# M3U and EPG Data Audit

Audit playlist and guide data behavior with concrete parser, repository, DAO, and test evidence.

## When To Use

Use when a task changes or reviews:

- M3U, XMLTV, EPG, or Xtream parsing
- Channel-to-programme matching
- Playlist-to-EPG association behavior
- DAO queries or repositories that return channels/programmes
- Empty, malformed, duplicate, timezone, or time-range behavior

## Required Context

Read first:

- `AGENTS.md`
- `data/AGENTS.md`
- `docs/ai/playbooks/m3u-epg-data.md`
- Relevant parser, DAO, repository, model, fixture, and test files

## Audit Checks

Verify with file evidence:

- Channel/EPG matching uses stable IDs or documented fallback order.
- Duplicate programmes are handled deterministically.
- Playlist-to-EPG associations are preserved across refresh/import flows.
- Empty EPG data has an explicit fallback path.
- SQL joins do not accidentally exclude channels without programmes.
- Time ranges, date parsing, and timezone conversion are explicit and tested where possible.
- Parser errors follow existing Flow/error-handling conventions.

## Process

1. Map parser entry points, repositories, DAOs, and tests.
2. Trace one normal playlist with EPG and one empty/malformed guide path.
3. Inspect joins and filters that combine channels and programmes.
4. Check fixtures or tests for duplicates, missing guide data, and timezone offsets.
5. Cite file paths and line numbers for each finding.

## Validation

Recommend:

```bash
./gradlew :data:testDebugUnitTest
```

Also recommend targeted parser or fixture tests when present. If unavailable, state the untested data risk.

## Output

Return a concise Markdown report with:

- Scope and confidence
- Data-flow map
- Pass/fail table for each audit check
- Evidence-backed findings with file paths
- Regression tests or fixtures to add
- Validation run or not run, with reason
