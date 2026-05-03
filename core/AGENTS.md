# AGENTS.md

This file applies to `core/` and nested core modules. Use it together with the root guidance.

## Core Layer Scope

- Core should stay lightweight, reusable, and as platform-neutral as practical.
- Prefer KMP-friendly standard Kotlin for helpers, contracts, validation, reducers, and shared models.
- Do not introduce Android-only APIs into reusable core logic unless the module already owns a narrow platform surface.
- Keep foundation UI primitives parameter-driven and free of feature-specific data access.

## Shared APIs

- Keep shared APIs small and explicit. Avoid adding broad utility surfaces for one caller.
- Do not create parallel conventions when a local helper, wrapper, or extension already exists.
- Package reusable behavior here only when multiple modules truly need it or when it clearly belongs to shared contracts.

## Extension Integration

- Extension features rely on several registration mechanisms. Read nearby extension modules before changing that flow.
- Keep extension contracts stable and avoid app-module assumptions in core extension APIs.