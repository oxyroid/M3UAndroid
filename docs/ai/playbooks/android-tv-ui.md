# Playbook: Android TV UI

## When to use this playbook

Use this when the task involves TV screens, DPad navigation, focus behavior, TV playback overlays, or couch-distance layout/readability.

## Required context

Read these first:

- `AGENTS.md`
- `app/AGENTS.md`
- `app/tv/AGENTS.md`
- Nearby TV screen, component, and navigation code

## Safe change scope

The agent may modify:

- TV app Compose screens and TV-only components
- Focus requesters, focus properties, and TV navigation wiring
- TV-specific dimensions, spacing, overlays, and visual states

The agent should avoid modifying:

- Phone UI unless the shared component is intentionally affected
- Data, parser, repository, or playback engine internals for presentation-only work
- Broad design rewrites unrelated to the focus issue

## Architecture rules

- Treat TV as focus-first and DPad-first.
- Define intentional initial focus for screens and modal surfaces.
- Keep focus order predictable in all DPad directions.
- Ensure focused, selected, disabled, and pressed states remain visually distinct.
- Do not assume touch input or pointer hover.

## Common mistakes

- Copying phone layouts into TV without focus and distance checks.
- Adding clickable elements that cannot receive focus.
- Hiding the focused item under video overlays or transient controls.
- Using small text, icons, or low-contrast focused states.

## Validation

Run:

```bash
./gradlew :app:tv:compileDebugKotlin
```

If focus behavior changed, also run or request an `android-tv-focus-audit` and manually check the primary focus path when practical.

If the command is not available or cannot be run, explain why.

## PR notes

The PR should mention:

- Focus path or initial focus behavior changed
- TV readability or overlay behavior changed
- Validation results and any untested focus paths
