# Playbook: i18n Resources

## When to use this playbook

Use this when the task adds, renames, removes, or translates user-facing strings, plurals, resource keys, placeholders, or locale files.

## Required context

Read these first:

- `AGENTS.md`
- `i18n/AGENTS.md`
- App or feature instructions for code consuming the resources
- Relevant default and locale-specific resource files

## Safe change scope

The agent may modify:

- Default and locale-specific resource entries
- Kotlin references to renamed resource keys
- Tests or resource compile targets for affected modules

The agent should avoid modifying:

- Large locale reordering or formatting-only churn
- User-facing hardcoded strings in app/business code
- Placeholder semantics without checking all locales

## Architecture rules

- Add new user-visible strings to the default locale first.
- Preserve placeholders, escaping, and positional format arguments.
- Keep key names meaningful and stable.
- Use Android fallback behavior intentionally for missing translations.

## Common mistakes

- Adding a string only to a translated locale.
- Changing `%s` or `%d` placeholders inconsistently across locales.
- Renaming keys without updating all Kotlin references.
- Moving business text into app modules instead of i18n.

## Validation

Run:

```bash
./gradlew :i18n:compileDebugKotlin
```

For app-visible resource usage, compile the affected app variant.

If the command is not available or cannot be run, explain why.

## PR notes

The PR should mention:

- Added, renamed, or removed keys
- Locale coverage and fallback behavior
- Placeholder or formatting risks
- Validation results
