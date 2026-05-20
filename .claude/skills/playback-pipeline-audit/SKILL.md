---
name: playback-pipeline-audit
description: Audit M3UAndroid playback flows for UI/engine/persistence boundaries, lifecycle cleanup, Media3 integration, casting, overlays, resume/autoplay behavior, and TV control risks.
allowed-tools: Read, Glob, Grep, Bash, Agent
argument-hint: "[repo path or playback-related module path]"
---

# Playback Pipeline Audit

Audit playback changes with evidence from UI, state, data adapters, and lifecycle paths.

## When To Use

Use when a task changes or reviews:

- Media3/ExoPlayer setup or adapters
- Playback controls, overlays, or TV remote behavior
- Casting, resume, retry, stream selection, or persistence
- Lifecycle handling around playback resources

## Required Context

Read first:

- `AGENTS.md`
- Relevant `app/AGENTS.md`, `business/AGENTS.md`, and `data/AGENTS.md`
- `docs/ai/playbooks/playback-pipeline.md`
- Nearby playback UI, state holders, adapters, repositories, and tests

## Audit Checks

Verify with file evidence:

- UI state, playback engine state, and persistence concerns remain separated.
- Playback resources are created, observed, and released with lifecycle awareness.
- Side effects are not launched directly from composition without effect control.
- Playback overlays remain readable and do not block primary controls.
- TV remote and focus paths are preserved for TV playback surfaces.
- Resume, autoplay, retry, casting, and stream-selection changes are documented.

## Process

1. Map playback entry points, state holders, adapters, and UI surfaces.
2. Trace one start, one stop/release, and one error/retry path.
3. Inspect Compose effects and lifecycle observers involved in playback.
4. Check phone and TV surfaces separately when both are affected.
5. Cite file paths and line numbers for each finding.

## Validation

Recommend:

```bash
./gradlew :app:smartphone:compileDebugKotlin
```

For TV playback changes also recommend:

```bash
./gradlew :app:tv:compileDebugKotlin
```

If manual playback checks cannot be run, state the remaining behavior risk.

## Output

Return a concise Markdown report with:

- Scope and behavior paths reviewed
- Pass/fail table for each audit check
- Evidence-backed findings with file paths
- Behavior and lifecycle risks for PR notes
- Validation run or not run, with reason
