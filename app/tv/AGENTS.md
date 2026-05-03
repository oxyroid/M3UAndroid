# AGENTS.md

This file applies to `app/tv/`. Use it together with `app/AGENTS.md` and the root guidance.

## Android TV Experience

- Treat TV UI as a focus-first, DPad-first experience. Do not directly copy phone layouts into TV code.
- Every screen needs a clear initial focus, predictable up/down/left/right movement, and a focus state readable from couch distance.
- When rebuilding or heavily changing TV UI, first study mature Android TV references such as official Android TV and JetStream design guidance, then implement an original layout that fits this app.
- On TV, verify the first viewport on a real device or emulator when practical. Important rows, focused cards, labels, and call-to-action buttons should be visible enough to communicate the screen structure.

## Layout And Focus

- Align related content to a shared visual edge. If a hero, section header, row, and cards belong to the same reading flow, their text and primary content should share a consistent leading margin.
- Use alignment to communicate hierarchy and scanability. Important content should sit near the top and leading side of the viewport.
- Prefer proportional layout, aspect ratios, adaptive grids, and constraint-based sizing over scattered fixed dimensions.
- Avoid small secondary controls inside cards, such as per-item favorite buttons, unless they are reachable and understandable through DPad as a first-class action.
- Use long-press behavior deliberately for secondary or advanced actions when it keeps the primary DPad path simple.

## Video And Controls

- Video playback UI should not let secondary controls obscure the main video.
- Prefer compact edge controls, transient overlays, and minimal metadata over large button rows or panels on top of content.
- Transparent controls over video need a controlled contrast surface, such as a local scrim, capsule, or edge gradient, so labels and icons remain readable over bright or busy frames.

## Visual Polish

- UI controls must look deliberately aligned. Text and icons inside buttons, chips, cards, and focusable surfaces should be visually centered and balanced.
- Treat container and content colors as a state pair. Check default, pressed/selected, disabled, and focused states together so text and icons keep strong contrast.
- When fixed spacing or sizing is needed, use a consistent small scale such as 2dp, 4dp, 8dp, 16dp, 24dp, 32dp, and related multiples.