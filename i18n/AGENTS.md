# AGENTS.md

This file applies to `i18n/`. Use it together with the root guidance.

## Localization Scope

- Put user-visible strings in this module and consume them through existing resource-loading conventions.
- Keep the default `values/` resources complete for every string referenced by code.
- Locale-specific files may rely on Android fallback behavior when a translation is intentionally missing.
- Do not hard-code translatable business text in app or business modules.

## Key Naming And Organization

- Use clear prefixes that reflect ownership: `ui_*` for shared UI, `feat_*` for feature text, `data_*` for data/source text, and app-specific prefixes only for app metadata.
- Move shared text out of a feature file when it becomes cross-screen or cross-module UI.
- Keep new resources grouped with nearby feature resources.
- Avoid mass reordering locale files unless the task explicitly asks for resource normalization.

## Translation Quality

- Preserve format placeholders and use positional placeholders such as `%1$s` when multiple arguments are present.
- Mark non-translatable values, references, URLs, product names, or literals with the existing Android resource mechanisms when appropriate.
- Add new strings to the default locale first, then update relevant translations when the feature directly affects those users.

## Validation

- For i18n-only changes, run at least a resource compile or diff whitespace check depending on risk.
- For key renames, update all Kotlin references and verify old keys are removed.