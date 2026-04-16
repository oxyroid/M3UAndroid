---
name: jetpack-compose-audit
description: Audit Android Jetpack Compose repositories for performance, state management, side effects, and composable API quality. Scans source code, scores each category from 0-10, writes a strict markdown report, and summarizes the most important fixes. Use when reviewing a Compose codebase, rating repository quality, inspecting recomposition/state issues, or running a Compose audit.
allowed-tools: Read, Glob, Grep, Write, Bash, Agent
argument-hint: "[repo path or module path]"
---

# Jetpack Compose Audit

This skill audits Android Jetpack Compose repositories with a strict, evidence-based report.

**Rubric version:** v1 — current as of 2026-04-13. Compose track: Kotlin 2.0.20+ / Compose Compiler 1.5.4+ (Strong Skipping Mode default).

It is intentionally focused on four categories:

- Performance
- State management
- Side effects
- Composable API quality

This skill does **not** score design or Material 3 compliance in v1. If the audit surfaces likely design-system problems, recommend a follow-up audit with the `material-3` skill (reference implementation: <https://github.com/hamen/material-3-skill>).

## Out Of Scope In v1

Owned and deliberate scope choices — call out the limitation in the report rather than silently producing thin coverage:

- Material 3 compliance, theming, color/typography tokens — defer to the `material-3` skill.
- Accessibility scoring (`semantics`, content descriptions, touch-target sizing) — flag obvious gaps as a note, do not score.
- UI test coverage and Compose test rule patterns — note presence/absence, do not score.
- Compose Multiplatform-specific rules (`expect`/`actual`, target-specific code paths).
- Wear OS / TV / Auto / Glance surfaces.
- Build performance (incremental compilation, KSP/KAPT choice).

If the user explicitly asks for any of these, narrow the scope and state it in the report.

## When To Use

Use this skill when the user asks to:

- audit a Jetpack Compose repository
- review Compose architecture or quality
- rate a codebase with scores
- inspect recomposition, state, or effects issues
- identify Compose best-practice violations in an existing repo

Typical trigger phrases:

- "audit this Compose repo"
- "score this Jetpack Compose codebase"
- "review state hoisting and side effects"
- "check Compose performance"
- "rate this repository"

## Expected Output

Produce both:

- a repository report file named `COMPOSE-AUDIT-REPORT.md`
- a short chat summary with the overall score, category scores, worst issues, and the top fixes

## Audit Principles

- Be strict, but evidence-based.
- Do not score from search hits alone. Read representative files before judging a category.
- Cite concrete file paths in the report for every important deduction.
- **Cite an official documentation URL for every deduction.** No "trust me" findings — the rubric maps every rule to a canonical source in `references/canonical-sources.md`. The report template requires a `References:` line per finding.
- Prefer canonical Android guidance over folklore.
- Treat performance as important, but not as the only lens.
- Do not punish app code for failing public-library purity tests. Apply API-quality checks mainly to reusable internal components, design-system pieces, and shared UI building blocks.
- Reserve `0-3` scores for repeated or systemic problems, not isolated mistakes.
- Do not award `9-10` unless the repo is consistently strong across the category.

## Process

### 1. Confirm Scope

Identify the target path:

- If the user passed an explicit path (`[repo path or module path]`), use it.
- If no path was passed, default to the current working directory.
- If the path does not exist, ask the user to clarify.

Before mapping modules, confirm Compose is actually present (fast-fail):

- grep for `androidx.compose` in any `build.gradle*` or `libs.versions.toml`
- grep for `setContent {` or `@Composable` under `src/`

If neither shows up, stop and report that the target is out of scope. Do not run a full module map first.

If Compose is present *only* in `samples/`, `demos/`, or test sources (no production usage), narrow the scope to those directories, set confidence to `Low`, and state in the report that the audit is over sample code rather than production paths. Do not score production-quality categories against demo code.

### 2. Map The Repository

Before scoring, identify:

- Gradle modules
- Android app and feature modules
- likely Compose source roots
- shared UI/component packages
- theme or design-system packages
- state holder or ViewModel areas
- test and preview locations
- baseline-profile related modules or config if present

### 3. Build A Compose Surface Map

Look for:

- `@Composable` functions
- reusable UI components
- screens and routes
- `ViewModel` usage
- `remember`, `rememberSaveable`, `mutableStateOf`
- `collectAsStateWithLifecycle`, `collectAsState`
- `LaunchedEffect`, `DisposableEffect`, `SideEffect`, `rememberUpdatedState`, `produceState`
- `LazyColumn`, `LazyRow`, `items`, `itemsIndexed`

If the repo is large, audit by category or by module. If subagents are available, parallelize category scans by spawning `Explore`-type subagents (no write tools) and merge the findings.

### 4. Generate Compose Compiler Reports (Automatic)

Do **not** ask the user to edit `build.gradle` or run commands themselves. The skill runs the build with a bundled Gradle init script that injects `reportsDestination` / `metricsDestination` into every Compose module without modifying any file in the target repo. Before scoring, attempt this:

1. **Locate the init script** shipped with the skill: `scripts/compose-reports.init.gradle`. The absolute path is the skill's install location — in most installs that's `~/.claude/skills/jetpack-compose-audit/scripts/compose-reports.init.gradle`. If you cannot resolve the path, fall back to writing the script to `<target>/.compose-audit-reports.init.gradle` and delete it after the run.

2. **Check for a Gradle wrapper** in the target: `test -x <target>/gradlew`. If missing, skip to the fallback in step 6.

3. **Pick a compile target.** Prefer the cheapest task that still triggers Kotlin compilation for a Compose module:
   - find the first application module via `rg -l 'com\.android\.application' -g '*.gradle*'`
   - try in order: `:<app-module>:compileReleaseKotlinAndroid`, `:<app-module>:compileReleaseKotlin`, `assembleRelease`, `assembleDebug`
   - If the project is Compose-only on a library (`com.android.library`), use that module instead.

4. **Run the build.** Inform the user the build is starting (it may take several minutes).

   ```bash
   cd <target> && ./gradlew <task> \
       --init-script <path-to>/compose-reports.init.gradle \
       --no-daemon --quiet
   ```

   Use a 600-second timeout. If the task fails, try the next fallback task in step 3 once. Do **not** loop indefinitely.

5. **Collect the reports.**

   ```bash
   find <target> -path '*/build/compose_audit/*' \
       \( -name '*-classes.txt' -o -name '*-composables.txt' -o -name '*-composables.csv' -o -name '*-module.json' \)
   ```

   From these files, extract:
   - **unstable classes** (lines starting with `unstable class ` in `*-classes.txt`) used as composable parameters
   - **non-skippable but restartable named composables** (ignore zero-argument lambdas; focus on actual named functions in `*-composables.txt` or `*-composables.csv` where `isLambda == "0"`)
   - **module-wide skippability counts** from `*-module.json`, AND compute the **named-only skippability** from `*-composables.csv` (by filtering out rows where `isLambda == "1"` and calculating `sum(skippable) / sum(restartable)`). Cite both in the Performance section, noting that zero-argument lambdas can artificially anchor the module-wide metric.

6. **Fallback if the build fails or Gradle is unavailable.** Proceed with source-inferred stability findings, but:
   - set `Compiler diagnostics used: no` in the report's Notes And Limits and explain the failure reason briefly (wrapper missing, compile error, timeout)
   - reduce overall confidence by one level
   - state each stability-related deduction as "inferred from source — not verified against compiler reports"

Stability deductions from step 5 are measured evidence and should be weighted normally. Fallback deductions from step 6 are inferred and must be flagged as such in the report.

### 5. Audit The Four Categories

Use the scoring rubric in `references/scoring.md` and the heuristics in `references/search-playbook.md`.

#### Performance

Focus on:

- expensive work in composition
- avoidable recomposition
- lazy list keys
- bad state-read timing
- unstable or overly broad reads
- backwards writes
- obvious release-performance hygiene where visible

#### State Management

Focus on:

- hoisting correctness
- single source of truth
- reusable stateless seams
- correct use of `remember` vs `rememberSaveable`
- lifecycle-aware observable collection
- observable vs non-observable mutable state

#### Side Effects

Focus on:

- side effects incorrectly done in composition
- correct effect API choice
- effect keys
- stale lambda capture
- cleanup correctness
- lifecycle-aware effect behavior

#### Composable API Quality

Focus on reusable internal components, not every leaf screen.

Check:

- `modifier` presence and placement
- parameter order
- explicit over implicit configuration
- meaningful defaults
- avoiding `MutableState<T>` or `State<T>` parameters in reusable APIs where a better shape exists
- component purpose and layering

### 6. Verify Findings

Before deducting points:

- read the file where the smell appears
- make sure the pattern is real, not a false positive
- check whether the repo already has a compensating pattern elsewhere
- distinguish one-off mistakes from systemic patterns
- for stability findings (skippable / restartable / unstable params), use the compiler reports generated in Step 4. Cite the specific report line (e.g. `app/build/compose_audit/app_release-classes.txt:42`) as evidence. Only fall back to source-inferred stability claims if Step 4 failed, and label them as such.

### 7. Score

Assign each category a `0-10` score and a status:

- `0-3`: fail
- `4-6`: needs work
- `7-8`: solid
- `9-10`: excellent

Use the weights in `references/scoring.md` to compute the overall score.

**Measured ceilings are mandatory, not suggestive.** When Step 4 produced compiler reports, the Performance rubric in `references/scoring.md` defines a ceiling based on `skippable%` and unstable-param count. After arriving at a qualitative Performance score, you MUST apply the ceiling and lower the score if it exceeds the cap. Show the math in the report:

```
Performance ceiling check:
  skippable% = 186/273 = 68.1% → falls in 50-70% band → cap at 4
  qualitative score: 7
  applied score: 4 (ceiling lowered from 7)
```

Do not round `skippable%` up into a higher band. `68.1%` is not `≥70%`. If a qualitative score lands at or below the ceiling, no adjustment is needed — but the check itself must appear in the report so the reader can audit it.

If a category genuinely has too little auditable surface area, mark it `N/A`, explain why, and renormalize the remaining weights.

### 8. Write The Report

Use `references/report-template.md`.

The report must include:

- overall score
- category score table
- top critical findings
- category-by-category reasoning
- evidence file paths
- prioritized remediation list
- optional follow-up note to run `material-3` if design issues are suspected

Write the report to:

- `COMPOSE-AUDIT-REPORT.md` inside the audited target (the path the user passed), not the current working directory.

If `COMPOSE-AUDIT-REPORT.md` already exists at that path, do not overwrite it silently. Either confirm overwrite with the user, or write to `COMPOSE-AUDIT-REPORT-<YYYY-MM-DD>.md` alongside it.

### 9. Return A Short Summary

In chat, produce a summary that mirrors the report's `Prioritized Fixes` section — not a generic recap. The developer should be able to act on the summary alone without opening the report file.

Include:

- overall score (and the delta vs. any prior `COMPOSE-AUDIT-REPORT*.md` at the same path, if present)
- one-line judgment for each category, with the applied ceiling if any (e.g. "Performance 6/10 — capped by 79% skippability")
- compiler-report highlights when Step 4 succeeded: Strong Skipping on/off, `skippable%`, count of unstable shared types, any module that failed to build
- **top three actionable fixes**, each with:
  - the concrete change ("add `key = { it.id }` to `items(...)` in `feed/FeedList.kt:42`")
  - file path(s) and line numbers — the same ones listed in the report's Prioritized Fixes
  - one official doc URL from `references/canonical-sources.md`
  - expected impact ("unlocks skipping for `FeedItem`, should move `skippable%` from 79% → ~90%")
- whether a `material-3` audit is worth running next

The top-three fixes in the chat summary MUST be the same items as the report's `Prioritized Fixes` list (same file paths, same doc links). Do not add generic advice in chat that isn't in the written report.

## Evidence Rules

- Prefer multiple examples over one dramatic example.
- Use positive evidence too, not just failures.
- Do not infer runtime problems you cannot justify from the code.
- When a rule is based on official guidance but app-level tradeoffs may justify deviation, call it out as a tradeoff instead of pretending it is always wrong.

## Large Repo Strategy

For medium or large repositories:

1. Map modules first.
2. Pick representative files per module.
3. Parallelize category scans when possible.
4. Merge repeated findings into systemic issues instead of listing the same smell twenty times.

## What To Avoid

- Do not produce a generic checklist with no repository evidence.
- Do not turn the report into a public-library API lecture if the repo is an app.
- Do not inflate the performance score just because the app uses Compose.
- Do not over-penalize isolated experiments or sample files unless they are part of production paths.
- Do not score design in v1.
- Do not flag `LaunchedEffect(Unit)` or `LaunchedEffect(true)` on its own — the "run once" pattern is idiomatic. Only flag it when the body captures a value that may change without `rememberUpdatedState`.
- Do not deduct on Compose Multiplatform code paths for Android-only APIs (`collectAsStateWithLifecycle`, `lifecycle-runtime-compose`). Note the platform constraint as a tradeoff instead.
- Do not double-count the same root cause across categories. A stability problem typically surfaces in both Performance and State — pick the dominant category and cross-reference.

## References

- `references/scoring.md` — per-rule rubric with inline citations
- `references/search-playbook.md` — search patterns and red-flag heuristics
- `references/report-template.md` — required structure for `COMPOSE-AUDIT-REPORT.md`
- `references/canonical-sources.md` — the official URLs every deduction must cite
- `references/diagnostics.md` — copy-pasteable Gradle/code snippets for Compose Compiler reports, stability config, baseline profiles, and R8 checks
- `scripts/compose-reports.init.gradle` — Gradle init script the skill injects via `--init-script` in Step 4 to generate compiler reports automatically
