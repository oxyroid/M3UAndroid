---
name: apk-size-audit
description: Audit M3UAndroid APK/AAB size and packaging changes for dependency growth, native libraries, resources, R8/minification, generated assets, baseline profiles, and artifact comparison evidence.
allowed-tools: Read, Glob, Grep, Bash, Agent
argument-hint: "[repo path or app module path]"
---

# APK Size Audit

Audit packaging and size-sensitive changes with measurable artifact and configuration evidence.

## When To Use

Use when a task changes or reviews:

- Dependencies, native libraries, packaging options, or R8/minification
- Resources, generated assets, baseline profiles, or release artifacts
- Native-load packaging and runtime loading behavior

## Required Context

Read first:

- `AGENTS.md`
- Relevant module `AGENTS.md` files for touched build logic
- `docs/ai/playbooks/apk-size.md`
- `docs/native-load-yaml.md` when native packs are affected
- Relevant Gradle, version catalog, packaging, and benchmark files

## Audit Checks

Verify with file evidence:

- Dependencies are added through the version catalog with justification.
- Native library packaging and runtime loading remain aligned.
- R8/minification/resource shrinking changes are intentional and variant-scoped.
- Generated assets are not committed or modified manually unless required.
- Artifact size comparisons use the same variant/build type.
- Baseline profile or benchmark changes do not hide runtime regressions.

## Process

1. Map changed Gradle, version catalog, packaging, native-load, and generated-asset files.
2. Identify artifact variants affected by the change.
3. Compare dependency/resource/native-library deltas when data is available.
4. Cite file paths and line numbers for each finding.
5. Avoid claiming size wins without measured artifact evidence.

## Validation

Recommend:

```bash
./gradlew :app:smartphone:assembleRelease
```

Use the affected TV or extension variant when applicable. If artifact comparison cannot be run, state the remaining size risk.

## Output

Return a concise Markdown report with:

- Scope and artifacts reviewed
- Pass/fail table for each audit check
- Evidence-backed findings with file paths
- Measured size deltas or reason measurements are unavailable
- Validation run or not run, with reason
