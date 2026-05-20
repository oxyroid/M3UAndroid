# Playbook: Compose Performance

## When to use this playbook

Use this when the task involves recomposition, state reads, lazy lists, compiler stability, Compose side effects, or UI responsiveness.

## Required context

Read these first:

- `AGENTS.md`
- `app/AGENTS.md` or the relevant module `AGENTS.md`
- `.claude/skills/jetpack-compose-audit/SKILL.md` when running an audit
- Nearby composables, state holders, and tests or previews

## Safe change scope

The agent may modify:

- Affected composables, state holders, and small helper APIs
- Lazy list keys, state hoisting seams, and effect keys
- Stable parameter shapes when local and compatible

The agent should avoid modifying:

- Whole-screen architecture without a specific performance cause
- Public component APIs without compatibility notes
- Unrelated visual styling or copy

## Architecture rules

- Keep composables parameter-driven and side-effect free during composition.
- Put long-lived work in ViewModels, business logic, or effect APIs.
- Prefer lifecycle-aware state collection where appropriate.
- Avoid broad state reads in high-frequency recomposition paths.

## Common mistakes

- Optimizing from grep results without reading the call site.
- Adding `remember` around values that should be model state.
- Using unstable keys or no keys in dynamic lazy content.
- Hiding side effects in composable bodies.

## Validation

Run:

```bash
./gradlew :app:smartphone:compileDebugKotlin
```

For audit work, run the Jetpack Compose audit skill and inspect compiler metrics when available.

If the command is not available or cannot be run, explain why.

## PR notes

The PR should mention:

- Recomposition, stability, or side-effect behavior changed
- Any public composable API changes
- Validation results and compiler/audit evidence when available
