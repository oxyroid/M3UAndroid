# Report Template

Write the audit report to `COMPOSE-AUDIT-REPORT.md` using this structure.

**Citation rule:** every finding (Critical Findings *and* per-category Evidence bullets) must include a `References:` line with at least one URL pointing to the official documentation rule the code violates. Use the URLs in `references/canonical-sources.md` and `references/scoring.md`. A finding without a citation should not appear in the report — that's the credibility lever this audit relies on.

```markdown
# Jetpack Compose Audit Report

Target: [repo path or module path]
Date: [YYYY-MM-DD]
Scope: [modules or directories audited]
Excluded from scoring: [paths or globs treated as samples / tests / previews]
Confidence: [High | Medium | Low]
Overall Score: [X/100]

## Scorecard

| Category | Score | Weight | Status | Notes |
|----------|-------|--------|--------|-------|
| Performance | X/10 | 35% | [fail / needs work / solid / excellent] | [short note] |
| State management | X/10 | 25% | [fail / needs work / solid / excellent] | [short note] |
| Side effects | X/10 | 20% | [fail / needs work / solid / excellent] | [short note] |
| Composable API quality | X/10 | 20% | [fail / needs work / solid / excellent] | [short note] |

## Critical Findings

List the most important findings first. Each finding should include:

- severity
- why it matters
- 2-4 concrete file examples
- the likely fix direction

Example format:

1. **Performance: repeated expensive work happens inside composition**
   - Why it matters: [brief reason]
   - Evidence: `path/a.kt:42`, `path/b.kt:117`
   - Fix direction: [brief recommendation]
   - References: <https://developer.android.com/develop/ui/compose/performance/bestpractices>

## Category Details

### Performance — [X/10]

**What is working**

- [positive evidence]

**What is hurting the score**

- [problem 1]
- [problem 2]

**Evidence**

- `path/to/file1.kt:LL` — [brief reason] · References: <https://developer.android.com/...>
- `path/to/file2.kt:LL` — [brief reason] · References: <https://developer.android.com/...>

### State Management — [X/10]

**What is working**

- [positive evidence]

**What is hurting the score**

- [problem 1]
- [problem 2]

**Evidence**

- `path/to/file1.kt:LL` — [brief reason] · References: <https://developer.android.com/...>
- `path/to/file2.kt:LL` — [brief reason] · References: <https://developer.android.com/...>

### Side Effects — [X/10]

**What is working**

- [positive evidence]

**What is hurting the score**

- [problem 1]
- [problem 2]

**Evidence**

- `path/to/file1.kt:LL` — [brief reason] · References: <https://developer.android.com/...>
- `path/to/file2.kt:LL` — [brief reason] · References: <https://developer.android.com/...>

### Composable API Quality — [X/10]

**What is working**

- [positive evidence]

**What is hurting the score**

- [problem 1]
- [problem 2]

**Evidence**

- `path/to/file1.kt:LL` — [brief reason] · References: <https://developer.android.com/...>
- `path/to/file2.kt:LL` — [brief reason] · References: <https://developer.android.com/...>

## Prioritized Fixes

1. [Highest leverage fix]
2. [Second fix]
3. [Third fix]
4. [Optional follow-up]

## Notes And Limits

- [state if only part of the repo was audited]
- [state if confidence is medium/low]
- [state if some categories had limited surface area]
- Weight choice: [default 35/25/20/20, or note any deviation and why]
- Renormalization: [list any N/A categories and the renormalized weights]
- Compiler diagnostics used: [yes / no — link to the Compose Compiler reports if generated; "no" means stability claims are inferred from source, not measured]

## Suggested Follow-Up

- Run `material-3` audit if the repo also shows likely design-system or Material 3 problems.
```

## Tone

- Keep the tone strict and direct.
- Avoid filler praise.
- Give credit only where the codebase actually demonstrates good patterns.
- Prefer a few strong findings over dozens of weak bullets.
