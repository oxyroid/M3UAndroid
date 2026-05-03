# AGENTS.md

This file applies to `business/`. Use it together with the root guidance.

## Business Layer Scope

- Business modules own feature state, user actions, reusable workflow logic, and feature-level coordination.
- Prefer KMP-friendly Kotlin for reusable logic.
- Do not depend on Android framework APIs in state reducers, validation, models, or business workflows.
- Keep platform effects behind narrow app or data layer boundaries.

## State And Flow

- Use coroutines and Flow following nearby project patterns.
- Avoid overly complex reactive pipelines when an explicit refresh, cached state, or narrower observed source is clearer and cheaper.
- Keep business composition in repositories, use cases, or ViewModels as appropriate to the existing feature pattern.
- Do not let business modules reach into DAOs, database instances, parser internals, or app UI constructs.

## Feature Changes

- Keep new behavior local to the feature that owns it unless there is real shared value.
- Add an abstraction only when it removes real complexity, reduces meaningful duplication, or matches an established local pattern.
- Preserve clear module boundaries when wiring new feature actions to data or app presentation.