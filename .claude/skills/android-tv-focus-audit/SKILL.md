---
name: android-tv-focus-audit
description: Audit M3UAndroid Android TV screens for DPad focusability, focus order, initial focus, visual focus states, touch assumptions, and couch-distance readability. Use when reviewing TV UI or changing focus behavior.
allowed-tools: Read, Glob, Grep, Bash, Agent
argument-hint: "[repo path or TV module path]"
---

# Android TV Focus Audit

Audit Android TV UI with measurable evidence, not broad design opinion.

## When To Use

Use when a task changes or reviews:

- `app/tv` screens, rows, cards, overlays, or dialogs
- DPad navigation, focus requesters, focus properties, or remote-control behavior
- TV playback controls or overlays

## Required Context

Read first:

- `AGENTS.md`
- `app/AGENTS.md`
- `app/tv/AGENTS.md`
- `docs/ai/playbooks/android-tv-ui.md`

## Audit Checks

Verify with file evidence:

- Key interactive elements are focusable and reachable by DPad.
- Focus order is predictable for up, down, left, and right movement.
- Initial focus is explicitly defined for screens, dialogs, and overlays when needed.
- Focused and selected states are visually distinct from default state.
- The UI does not rely on touch-only gestures or pointer hover.
- Text, icons, spacing, and contrast are suitable for couch-distance viewing.
- Playback overlays do not obscure important video content or focus targets.

## Process

1. Map the TV screens and shared TV components in scope.
2. Identify focusable controls, focus requesters, focus properties, and click handlers.
3. Read representative files before judging each issue.
4. Record specific file paths and line numbers for every finding.
5. Prefer multiple examples for systemic issues.
6. Separate focus defects from general visual polish.

## Validation

Recommend:

```bash
./gradlew :app:tv:compileDebugKotlin
```

If runtime focus cannot be manually checked, state the remaining risk.

## Output

Return a concise Markdown report with:

- Scope and confidence
- Pass/fail table for each audit check
- Evidence-backed findings with file paths
- Top fixes prioritized by focus-blocking severity
- Validation run or not run, with reason
