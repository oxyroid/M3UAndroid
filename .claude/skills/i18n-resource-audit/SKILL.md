---
name: i18n-resource-audit
description: Audit M3UAndroid localization resources for missing default strings, placeholder consistency, key naming, hardcoded user text, fallback behavior, and locale safety.
allowed-tools: Read, Glob, Grep, Bash, Agent
argument-hint: "[repo path or i18n module path]"
---

# i18n Resource Audit

Audit localization changes with concrete resource and call-site evidence.

## When To Use

Use when a task changes or reviews:

- String resources, plurals, arrays, or locale files
- Resource key renames/removals
- User-facing text in app or business code
- Placeholder or formatting behavior

## Required Context

Read first:

- `AGENTS.md`
- `i18n/AGENTS.md`
- `docs/ai/playbooks/i18n-resources.md`
- Relevant default resources, locale resources, and Kotlin call sites

## Audit Checks

Verify with file evidence:

- Every referenced user-facing string exists in the default locale.
- Placeholder names, counts, types, and positional markers match across locales.
- Resource keys are meaningful and aligned with existing prefixes.
- Hardcoded user-facing strings are not introduced in app/business code.
- Non-translatable literals are marked or kept out of translation resources.
- Locale fallback behavior is intentional for missing translations.

## Process

1. Map changed resource keys and all Kotlin/XML call sites.
2. Compare default and locale-specific entries for placeholders and escaping.
3. Search the touched area for hardcoded user-facing strings.
4. Cite file paths and line numbers for each finding.

## Validation

Recommend:

```bash
./gradlew :i18n:compileDebugKotlin
```

Compile affected app variants when resource usage changes. If unavailable, state resource-risk remaining.

## Output

Return a concise Markdown report with:

- Scope and locales reviewed
- Pass/fail table for each audit check
- Evidence-backed findings with file paths
- Placeholder or fallback risks for PR notes
- Validation run or not run, with reason
