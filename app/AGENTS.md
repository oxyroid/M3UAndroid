# AGENTS.md

This file applies to `app/`. Use it together with the root guidance. More specific files, such as `app/tv/AGENTS.md`, take precedence inside their subtree.

## App Module Scope

- App modules own Compose routes, screens, permission prompts, navigation wiring, Hilt entry points, activities, platform adapters, and app-specific presentation.
- Keep lower-level screen composables parameter-driven. Route-level composables should connect ViewModels, permissions, navigation, and platform helpers.
- Do not directly access DAOs, databases, parser internals, or low-level data sources from UI code.
- Keep playback launch, remote-control entry points, and permission flows consistent with existing app patterns.

## Compose And UI Style

- Keep UI in Compose unless existing code uses interop for a narrow platform surface.
- Keep composables stable, lightweight, and mostly parameter-driven. Put long-lived work in ViewModels or business logic.
- Use Compose state and side-effect APIs deliberately, following nearby code patterns.
- Do not perform state mutation or network/device side effects from draw lambdas such as `Canvas` rendering.
- Use existing UI helpers and resource-loading conventions instead of introducing parallel patterns.
- If text is truncated on a real device or emulator, fix layout behavior first: constraints, wrapping, max lines, scaling strategy, overflow handling, adaptive spacing, or component structure. Do not shorten correct copy as the primary fix.

## Resources

- Do not hard-code user-visible business text in composables.
- Put new user-visible strings in `i18n` and consume them through existing Compose resource conventions.
- Keep translatable business text out of app modules unless it is truly app-level metadata.

## Dependency Injection

- Use Hilt and the repository's existing injection patterns for applications, activities, ViewModels, providers, and bindings.
- Register dependencies in the module that owns them. Do not configure processors or generated-code dependencies only from an app module.