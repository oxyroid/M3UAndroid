# Playbook: Playback Pipeline

## When to use this playbook

Use this when the task involves media playback, Media3/ExoPlayer integration, playback controls, casting, video overlays, lifecycle handling, playback persistence, or stream selection.

## Required context

Read these first:

- `AGENTS.md`
- Relevant `app/AGENTS.md`, `business/AGENTS.md`, and `data/AGENTS.md`
- Nearby playback UI, state holder, repository, service/adapter, and tests

## Safe change scope

The agent may modify:

- Playback UI and controls for the affected surface
- Playback state mapping, lifecycle glue, and adapters directly related to the task
- Tests or manual checklist notes for changed behavior

The agent should avoid modifying:

- Parser, persistence, or playlist logic unless playback requires it
- TV focus behavior as a side effect without TV validation
- Broad engine rewrites for localized UI/control bugs

## Architecture rules

- Keep UI state, playback engine state, and persistence concerns separated.
- Handle lifecycle events explicitly and clean up playback resources.
- Keep video overlays readable without obscuring primary playback.
- Treat autoplay, resume, casting, and stream-selection behavior as user-visible compatibility areas.

## Common mistakes

- Starting playback work directly from composition without effect/lifecycle control.
- Persisting transient playback state as durable user data.
- Breaking TV remote control paths while fixing phone controls.
- Changing buffering or retry semantics without PR notes.

## Validation

Run:

```bash
./gradlew :app:smartphone:compileDebugKotlin
```

For TV playback surfaces, also run:

```bash
./gradlew :app:tv:compileDebugKotlin
```

Run playback-related tests or manual lifecycle checks when available.

If the command is not available or cannot be run, explain why.

## PR notes

The PR should mention:

- Playback behavior, lifecycle, or controls changed
- Casting, resume, retry, or overlay risks
- Phone/TV validation coverage
