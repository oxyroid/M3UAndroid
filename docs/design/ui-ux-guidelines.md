# M3UAndroid UI/UX design and acceptance guidelines

[简体中文](ui-ux-guidelines.zh-CN.md)

This document is for maintainers of M3UAndroid's phone, tablet, and TV interfaces. It defines
product hierarchy, layout boundaries, component semantics, and release evidence. It is not an
Android or Compose tutorial.

"Must" identifies a release gate. "Should" identifies the project default; a change that departs
from a "should" needs a testable reason in its change notes. Android guidance defines the
implementation baseline. Apple HIG is only a cross-check for hierarchy, adaptability, and
accessibility; Apple measurements do not replace Android measurements.

## Answer these questions before designing

For every UI change, identify:

1. Which **available-window** class drives the decision, rather than a device name?
2. Is this top-level browsing, a list, a detail view, an input task, or full-screen playback?
3. Which layer owns the system-bar, cutout, gesture-region, and IME insets?
4. Which container scrolls, and how does its last important item enter the focus-safe region?
5. What is the screen's single primary action? Is a trailing icon decoration, a row action, or an
   independent action?
6. What role does each piece of text have, how many lines may it use, and can the task still be
   completed if it is truncated?
7. How are loading, empty, content, refresh failure, unavailable, and operation failure shown?
8. Which windows, directions, font sizes, and input methods must accept this change?

If these are unanswered, do not start by adding cards, fixed dimensions, or padding to repair the
visual result.

## Adaptive hierarchy

M3UAndroid lays out against the app's **current available window**. Phone and tablet are test-device
roles, not `isTablet` branches. Selection, scrolling, and input state must survive breakpoint
changes caused by rotation, split screen, or resizing. Android
[window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
are the breakpoint baseline.

| Available window | M3UAndroid layout | Navigation | Content hierarchy |
| --- | --- | --- | --- |
| Width `< 600dp` | Compact, one pane | Phone bottom bar overlays top-level content | Detail, search, and input tasks replace content; hide the bar during the task |
| Width `600–839dp` | Medium | Rail and content are siblings in a `Row`; the rail consumes width | One primary pane by default; use list-detail only when the task benefits and space is sufficient |
| Width `840–1199dp` | Expanded | Rail and content remain side by side | List-detail or max-width content; never stretch the phone pane indefinitely |
| Width `≥ 1200dp` | Large/extra large | Preserve rail hierarchy | Add whitespace or a supporting region, not unrelated feature density |
| Height `< 480dp` | Compact height | Keep navigation appropriate to the current width | Do not force two panes; preserve primary content, focus, and actions |
| Android TV | TV-specific hierarchy | `TvNavigationRail` and browse pane remain side by side | D-pad-first; never reuse the phone overlay bar |

The product has three top-level destinations: Home, Favorites, and Settings. The phone bottom bar
appears only at the roots of those destinations. Detail, full-screen search, IME input, and
immersive playback must not compete with it. The bar stays stable while content scrolls; it does not
hide and reappear based on scroll direction. In the stable three-destination compact layout, visual
labels may be omitted, but accessible names and selected state must always remain.

At medium widths and above, the rail occupies real horizontal space and is never an overlay.
Compact height may retain the rail while content falls back to one pane. TV always preserves its
own arrangement, intentional initial focus, and return-focus path. Android TV's
[10-foot and D-pad baseline](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv)
and [focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system) are the
minimum TV requirements.

## `layoutPadding`, `contentPadding`, and insets

These concepts are not interchangeable:

- `layoutPadding` reduces a component's measurement and placement viewport. It defines the outer
  layout boundary.
- `contentPadding` lives inside scrolling content. It changes where the first and last items can
  rest without reducing the scrolling viewport.
- Window insets are environmental boundaries. The nearest owner applies and consumes each inset
  once; parent and child must not apply the same inset again.

Compose lazy containers apply
[`contentPadding` to content, not the container](https://developer.android.com/develop/ui/compose/lists#content-padding).
The [Window Insets guide](https://developer.android.com/develop/ui/compose/system/insets-ui)
explains consumption and the trailing-spacer requirement for IME-backed lazy input lists.

| Scenario | `layoutPadding` | Safe trailing space in scrolling content |
| --- | --- | --- |
| Compact top-level screen, bar visible | Logical horizontal safe area and required cutout boundary only; exclude bar height | Under the current consumption owner: safe system bottom + `12dp` + measured bar height + `12dp` |
| Compact detail/search, or bar hidden | Apply system safe boundaries once | Normal page spacing; use IME insets and a trailing spacer during input |
| Medium/expanded | The rail consumes width in the `Row`; it is not simulated with padding | Page spacing plus any unconsumed system bottom inset |
| TV | Preserve the arranged navigation and content panes | Use the TV screen's safe area and focus-scroll spacing |

On a compact top-level screen, the list viewport may extend behind the bottom bar. Intermediate
items may travel behind the translucent surface while scrolling, but these items must never rest in
the obscured region:

- the focused or selected item, an active text field, and its error;
- the final item, submit action, or entry to a destructive confirmation;
- a snackbar, remote-control accessory action, or another overlay action needing an immediate response.

Bring an item into the safe region when it receives focus or enters edit mode. When the bar hides,
snackbars and independent overlay actions return to the system safe area. An input screen must not reuse navigation
`contentPadding` as keyboard avoidance. Use IME insets and a trailing spacer in lazy input lists so
the final field is not hidden.

Do not shrink a whole `HorizontalPager` or similar page container with phone-navigation padding.
Pass scroll clearance to each page's scrolling child and avoid the overlay separately for the page
indicator.

## Navigation, row actions, and accessory actions

Visual proximity does not make two controls one semantic action. Select the interaction model
before selecting a component.

A navigation item changes a top-level destination; it does not refresh, toggle, or run a one-shot
command. Put those actions inside the current screen. Each navigation item has one selectable
semantic node and one stable identity; its icon and visible label are not separately clickable.
See Android [Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)
and the Compose [semantics tree](https://developer.android.com/develop/ui/compose/accessibility/semantics).

| Row meaning | Click and semantics | Trailing element |
| --- | --- | --- |
| Whole row opens detail | One navigation semantics node for the row | Chevron is a directional cue; do not add a duplicate click node |
| Row opens detail and trailing control does something else | Row and trailing button are separate nodes | Use an independent IconButton, explicit name, and at least a `48 × 48dp` target |
| Whole row toggles one Boolean value | Merge row and Switch into one toggle semantic | Avoid two TalkBack nodes that perform the same toggle |
| Informational row | No click semantic | Do not imply navigation with a chevron |
| Drag, swipe, or long press adds a shortcut | Gesture is enhancement | Provide an equivalent tap, keyboard, or D-pad operation |

A 24dp icon is not a 24dp button. Lay out an independent trailing action as an invisible circular
button of at least 48dp:

- Start and end outer row margins are equal.
- Leading-icon and trailing-accessory slots use stable widths.
- The trailing button's **outer bounds** maintain the end margin; the visible glyph does not hug
  the edge.
- All leading glyph centers and all trailing-action centers align in their respective list columns.
- Expanded adjacent hit regions never overlap, and semantic traversal follows visual order.

Export, delete, and refresh icons are Buttons when independently actionable, even without a visible
background. Conversely, do not add a meaningless `contentDescription` to a decorative chevron when
the row owns navigation. Compose's
[48dp target and overlap guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
is the minimum baseline.

The phone remote-control button follows the same rule. It may visually join the glass navigation as
one dock, but it stays outside the `selectableGroup`, exposes Button rather than Tab semantics, and
occupies logical End so RTL mirrors it. Showing the action may reflow the dock, but cannot shrink
the three navigation targets below 48dp.

## Material semantic color, dynamic color, and glass surfaces

Feature screens consume semantic roles from `MaterialTheme.colorScheme`. They must not rebuild the
whole theme locally, copy a fixed palette, or alter unrelated screens to style one component.
Material 3 [ColorScheme and dynamic color](https://developer.android.com/develop/ui/compose/designsystems/material3)
are the implementation basis.

| Purpose | Default role |
| --- | --- |
| Screen and ordinary-list background | `surface` |
| Group or low-emphasis container | `surfaceContainerLow` / `surfaceContainer` |
| Selected item | `secondaryContainer` + `onSecondaryContainer` |
| The screen's single high-emphasis action | `primary` + `onPrimary`, or the component's default prominent role |
| Error container | `errorContainer` + `onErrorContainer` |
| Secondary text and icons | `onSurfaceVariant` |
| Boundary | `outlineVariant`; use `outline` only for stronger separation |
| Modal veil | `scrim` |

Container and content roles are pairs. Prefer Material component state layers for disabled,
pressed, selected, and focused states; do not create a parallel state system from arbitrary alpha
values. Color is never the sole state indicator; add shape, icon, text, or semantics.

Dynamic color changes role values, not information hierarchy. Light, dark, dynamic, and
non-dynamic fallback schemes must all remain readable. Project release thresholds are 4.5:1 for
ordinary text, 3:1 for large or bold text, and 3:1 for meaningful non-text elements against
adjacent surfaces. See [Android accessibility design](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility).

A theme style may change headline typography and how semantic colors are generated, but it cannot
bypass those roles. Every style needs light, dark, and dynamic-color behavior; body text, errors,
and interaction states still consume Material tokens. The warm editorial style uses paper, ink,
and terracotta character without copying another product's brand tokens, fonts, or assets. Its
serif voice is limited to page headlines; it does not spread to body copy, media names, or
technical identifiers.

A theme card represents a complete preset with a stable identity, not an isolated color write.
Selection commits preset identity, seed, light/dark mode, style, and dynamic-color state as one
snapshot so no mixed old/new frame can render. Unknown presets fall back safely to Material.
Follow-system mode shows one representative per seed that matches the system's current appearance.

Call a surface "glass" only when content passing behind it receives local blur, material tint, or
equivalent optical separation. A floating glass surface must also:

- derive color from semantic surface roles without changing the root theme;
- use an outline or shadow to expose its boundary;
- fall back to a sufficiently opaque, contrast-safe theme surface when blur is unavailable;
- keep icons and selection legible in light, dark, dynamic-wallpaper, and high-contrast scenes;
- keep the blurred area small and stable instead of applying an expensive effect to a large
  scrolling region.

Transparency alone is neither glass nor a legibility guarantee.

## Text roles and wrapping

`maxLines = 1` is not the default. Limit text to one line only when the product role is compact, the
source is controlled, and truncation cannot block the task. Compose supports
[`maxLines` and overflow](https://developer.android.com/develop/ui/compose/text/configure-layout),
but the role below decides whether to use them.

| Text role | M3UAndroid default |
| --- | --- |
| Fixed top-level destination title | May use 1 line; keep the resource short and verify every language |
| Playlist, provider, server, or other entity title | Allow 2 lines in lists and detail headers; detail must expose full identity |
| Ordinary list primary text | Default to 2 lines; 1-line ellipsis is limited to dense supporting lists where the full value is recoverable |
| Supporting text, explanation, and empty state | Wrap naturally; do not impose a 1-line limit |
| Error, permission rationale, and destructive consequence | Show in full; never ellipsize |
| Button label | Prefer 1 line; reflow or stack actions if it does not fit, rather than shrinking type or hiding the key verb |
| Chip, badge, or short status | Use only controlled short copy and keep it to 1 line; move long status into body text or its own row |
| Bottom-navigation visual label | May be hidden in compact three-destination mode; if shown, use 1 line; semantics stay complete |
| URL, package name, or fingerprint | May truncate in an overview; detail must be readable and copyable; never use it as the only human label |
| TextField supporting/error text | Wrap naturally; field semantics decide whether the value itself is one line |
| TV content title | Default to at most 2 lines; reduce long descriptions without hiding task-critical distinctions |

Do not shorten correct copy, reduce a typography token, tighten letter spacing, or disable system
font scaling to fit a translation. Fix constraints, available width, action arrangement, or
component hierarchy first. Every critical task must remain completable at 200% font size.

Test long unspaced CJK, long German or Romanian, Arabic RTL, long playlist titles, and technical
identifiers. Visual truncation must not make two items indistinguishable. A full accessible name is
not a replacement for visibly presenting task-critical identity.

## RTL and i18n

RTL is a layout direction, not a string transform.

**Never** reverse characters in words, sentences, URLs, or user input to implement RTL. Natural
language retains its authored order; layout mirrors around it. Do not persist bidi overrides,
isolates, or other invisible controls in titles and account fields. Format mixed-direction content
only at the display boundary; Android recommends
[`BidiFormatter`](https://developer.android.com/training/basics/supporting-devices/languages) for
dynamic values inserted into localized sentences.

| Mirror with RTL | Do not mirror |
| --- | --- |
| Start/end layout, pane order, Back/Forward, chevrons, directional list-entry actions | Brand art, Play/Pause, timeline meaning, clocks, numbers themselves, cover art, technical-string content |

Implementation and review rules:

- Use start/end, `Alignment.*Start`, logical padding, and relative placement; do not express layout
  with left/right.
- Use auto-mirrored directional icons. Every custom icon must declare whether language direction
  affects it.
- Keep text alignment consistent within a list; let long paragraphs follow the natural direction
  of their content language.
- Never reverse URLs, origins, package names, certificate fingerprints, or usernames; never insert
  controls by hand; offer safe copy.
- User-visible copy and accessibility names come from resources. Each locale owns placeholder,
  plural, and word order.
- Use `ar-XB` for stress, but include at least one real-Arabic review. Use `en-XA` to expose length
  failures.

Apple's [Right to left](https://developer.apple.com/design/human-interface-guidelines/right-to-left)
also distinguishes interface mirroring from content direction and is useful as a cross-platform
review reference.

## Boundaries of `ListItem`, `Card`, and `Surface`

| Component | Use it for | Do not use it for |
| --- | --- | --- |
| `ListItem` or a shared row | Peer playlists, settings, actions, and property lists | Do not lose 48dp targets, semantics, or alignment slots merely to look simpler |
| `Card` | One independently understandable unit, such as media, a summary, or an object selected as a whole | Do not wrap every ordinary setting or detail row in a separate card |
| `Surface` | A real tonal, shape, elevation, focus, or click boundary | Do not use it as a universal wrapper with no visual or semantic job |
| `Column` / `Row` | Simple arrangement | Do not assemble imitation Material controls without states, targets, and semantics |

Android's [Settings pattern](https://developer.android.com/design/ui/mobile/guides/patterns/settings)
organizes settings as lists or list-detail and groups them with headings, spacing, or intrinsic
containment instead of one card per item.
[Compose Card](https://developer.android.com/develop/ui/compose/components/card) represents one
coherent, independent content unit.

Project constraints:

- Ordinary lists share one Surface. Establish hierarchy with section headings, 8/16/24dp spacing,
  or a necessary divider.
- A Card inside another Card needs two independently explainable layers; otherwise flatten it.
- A clickable Card owns one primary whole-card action by default. Independent accessory actions
  follow the two-node rules above.
- Leading icon, primary/supporting text, and trailing action use shared slots and vertical centering.
- TV media Cards may use a strong focus layer. TV setting rows still use list semantics.

## State, error, and operation feedback

Every data screen explicitly supports these states instead of using one full-screen spinner for
everything:

| State | Presentation rule |
| --- | --- |
| Initial load | Preserve page hierarchy and show meaningful progress; do not resemble an empty result |
| Empty | Explain what belongs here and why it is empty; offer one primary recovery action when possible |
| Content | Keep actions near the content they affect; at most one high-emphasis action per screen |
| Background refresh | Preserve the last valid content and show local refresh state |
| Refresh failure | Keep old content when safe and show a non-destructive retry |
| Unavailable/disabled | Explain cause, effect, and recovery; do not communicate only by reducing alpha |
| Form validation failure | Place error next to the field with error semantics; retain input and bring the first error into the safe region |
| Operation in progress | Prevent duplicate submission and show progress near the action; long work needs cancellation or recoverable status after leaving |
| Operation success | Update content immediately; use transient feedback only when confirmation helps without interrupting |
| Destructive operation | Name the object and consequence; after concise confirmation, show success or failure |

Snackbars are for transient feedback that may be missed. An error blocking task completion remains
inline. Error, selected, disabled, and progress states cannot rely only on color or motion. Custom
states expose the correct Role, `stateDescription`, error semantics, and a restrained live region;
see [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

State changes must not send focus to hidden content. Phone input remains visible across IME and
bottom-bar changes. TV restores predictable focus after loading, dialog dismissal, and Back.
Rotation or breakpoint changes must not clear unsubmitted input.

## Accessibility and input methods

- Every touch action is at least `48 × 48dp`; expanded adjacent targets never overlap.
- Decorative icons have no accessible name. Independent actions have a localized name, correct
  Role, enabled state, and result feedback.
- Headings and groups expose hierarchy in the semantics tree. Traversal follows visual reading
  order.
- Selection, focus, error, and disabled state each use at least two cues, never color alone.
- Custom drag and motion respect system animation settings, and understanding the task never
  depends on animation.
- Phone flows work with touch and TalkBack. A connected keyboard or remote does not create focus
  traps.
- Every TV action is reachable by D-pad. Initial focus is intentional and visible at couch distance.
- Transparent player controls use a local scrim, capsule, or edge gradient; never assume video is
  dark.

Apple [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)
and [Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons) are useful
cross-checks for control size, alternative interaction, and feedback. Android still uses the
project's 48dp threshold.

## Device and window acceptance matrix

This is a boundary and stress set, not a requirement to run the full Cartesian product. Cover every
value, and always run the Compact + RTL + 200% font combination.

| Dimension | Required values |
| --- | --- |
| Available width | 320, 360, 599, 600, 839, and 840dp; include 1200dp for large-screen changes |
| Available height | 479 and 480dp, plus normal phone portrait, tablet, and TV heights |
| Font | 100% and 200% |
| Locale/direction | English LTR, Simplified Chinese, `en-XA`, and `ar-XB` RTL; add real Arabic for RTL changes |
| Theme | Light and dark; include dynamic color when supported and a non-dynamic fallback |
| System navigation | Gesture and three-button navigation |
| Input state | IME closed, open, field traversal, and submit error |
| Content amount | Empty, one item, a list long enough to scroll; short and extreme titles |
| State | Loading, content, refreshing, empty, unavailable, error, retry, and operation in progress |
| Input device | Phone touch/TalkBack, tablet touch or keyboard, and TV D-pad |

Device responsibilities:

- Prioritize phone experience on the project's Pixel 6 Pro AVD. Cover 320dp and 599dp with a
  resizable window or a dedicated small-screen profile.
- Reserve the 6GB RAM AVD for tablet and large-window validation; it is not phone evidence.
- Tablet evidence crosses both 600dp and 840dp and checks real rail occupancy, content max width,
  and state retention.
- TV uses a TV profile and D-pad. Touch or mouse clicks do not replace focus-path evidence.

Capture two screenshots of the same state for every affected form factor:

1. A normal screenshot for hierarchy, semantic color, dynamic color, contrast, wrapping, and visual
   focus.
2. A screenshot with developer option **Show layout bounds** enabled for real constraints, touch
   slots, alignment, duplicated insets, and overlap.

With layout bounds visible, verify:

- leading and trailing icon center lines are stable, and independent 48dp button bounds neither
  escape nor overlap;
- the compact list viewport truly extends behind the floating bar, while its last item can scroll
  fully above it;
- a medium/expanded rail consumes width and content does not inherit phone bottom-bar height;
- the active field, error, and submit action remain reachable with the IME open;
- TV focus indication is not clipped and focus cannot rest off-screen or behind navigation.

Reject unintended clipping, task-critical truncation, overlap, double insets, drifting alignment
slots, inconsistent baselines, arbitrary centering, one-card-per-list-row treatment, or state
communicated only by color.

## Release evidence

UI change notes must record:

- device or AVD, API level, and available window width/height;
- locale, layout direction, font size, theme, and dynamic-color state;
- system navigation, IME, and input device;
- validation command and result, plus normal and layout-bounds screenshot paths;
- untested form factors, why they were not tested, and the remaining risk.

Automation should assert navigation reachability, semantic-node names/roles/state, 48dp action
bounds, the final action entering the safe region, state retention across breakpoints, and
empty/error/in-progress states. Automation does not replace normal screenshots, layout-bounds
screenshots, TalkBack, real D-pad, or native-language review.

## Official references

Android implementation baseline:

- [Window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [Lazy lists: content padding](https://developer.android.com/develop/ui/compose/lists#content-padding)
- [Window Insets in Compose](https://developer.android.com/develop/ui/compose/system/insets-ui)
- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Support languages and RTL](https://developer.android.com/training/basics/supporting-devices/languages)
- [Mobile settings pattern](https://developer.android.com/design/ui/mobile/guides/patterns/settings)
- [Design for TV](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv)
- [TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system)

Cross-platform review references:

- [Apple HIG: Layout](https://developer.apple.com/design/human-interface-guidelines/layout)
- [Apple HIG: Right to left](https://developer.apple.com/design/human-interface-guidelines/right-to-left)
- [Apple HIG: Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)
- [Apple HIG: Color](https://developer.apple.com/design/human-interface-guidelines/color)
- [Apple HIG: Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons)
