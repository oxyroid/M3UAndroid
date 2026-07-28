# Extension UI quality gates

[简体中文](ui-quality-gates.zh-CN.md) · [Maintainer guide](README.md)

Use this checklist for extension discovery, plugin lists, details, authorization, settings, and
provider entry points. A UI change is not release-ready until every applicable gate passes.

## Official Android baseline

These are platform requirements or recommendations, not project-specific styling choices.

| Area | Pass condition | Official source |
| --- | --- | --- |
| Touch | Every interactive target is at least 48 × 48 dp. Adjacent expanded targets do not overlap. | [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) |
| Compact layout | Body content and actions stay inside 16 dp side margins. Margins adapt when more width is available. | [Content composition and structure](https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure) |
| Adaptive layout | Decisions use the available window, not a device-type check: compact is below 600 dp, medium is 600–839 dp, and expanded starts at 840 dp. Compact height is below 480 dp. | [Window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes) |
| Large text | At the system maximum of 200% font size, text, controls, and the task flow remain usable without clipping or obstruction. | [Android 14 nonlinear font scaling](https://developer.android.com/about/versions/14/features#non-linear-font-scaling-200) |
| Contrast | Text has at least 4.5:1 contrast. Large or bold text may use 3:1. Surfaces and meaningful non-text elements have at least 3:1. | [Android accessibility](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility), [Android accessibility codelab](https://developer.android.com/codelabs/starting-android-accessibility) |
| Insets | Content may draw edge to edge, but controls never overlap system bars, display cutouts, gesture regions, or the IME. Insets are applied and consumed once at the owning level. | [Android system bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars), [Compose window insets](https://developer.android.com/develop/ui/compose/system/insets-ui) |

Use a list or list-detail structure for settings. Group rows with spacing, headings, or dividers;
do not place every row in its own card. On a small window, detail replaces the list. When enough
width is available, list and detail may appear side by side. Keep a single-pane form from stretching
across the full large window. See [Settings](https://developer.android.com/design/ui/mobile/guides/patterns/settings)
and [List-detail](https://developer.android.com/develop/adaptive-apps/guides/list-detail).

## M3UAndroid product policy

The rules in this section are project gates. They are not claims about the Material specification.

### Flow and hierarchy

- Settings contains exactly one extension-management entry. One click opens the plugin list.
- The list is for scanning and choosing a plugin. A row opens its detail page; authorization and
  settings are separate full screens.
- A screen exposes at most one high-emphasis action. Secondary and destructive actions use lower
  emphasis; a dialog is reserved for a short destructive confirmation.
- Each screen has one page title. Nested extension screens provide Back and do not show unrelated
  global search, remote-control actions, or top-level navigation over the task.
- Loading, empty, content, unavailable, and error states are distinct. A failed refresh keeps the
  last valid content when it is safe to do so.
- User-facing copy and accessibility labels come from localized resources. Layout uses logical
  start/end alignment and mirrored directional icons; long translated text may wrap.
- Status is communicated with text or semantics as well as color. Secrets and tokens are never
  rendered; package names, service names, certificate fingerprints, and origins remain readable
  and use safe bidirectional formatting.
- A drag, swipe, or long-press interaction has an equivalent tap, keyboard, or D-pad action.

### Layout behavior

- Compact windows use one pane. Plugin lists scroll independently; detail, authorization, and
  settings take the task viewport.
- Medium and expanded windows preserve the same information order and may use list-detail panes.
  Resizing across a breakpoint must not lose the selected plugin or form state.
- A floating phone navigation surface may overlay the scrolling viewport. Scroll content receives
  enough bottom space for its last item and final action to move fully above that surface and the
  system safe area.
- A text-input screen handles the IME with window insets. Do not reuse navigation padding as
  keyboard avoidance, and do not double-apply consumed insets.
- TV keeps D-pad navigation: initial focus is intentional, focus is visible without relying only
  on color, every action is reachable, and Back returns focus to the item that opened the page.

## Required test matrix

This matrix is M3UAndroid policy. It is a boundary and stress matrix, not an instruction to run the
full Cartesian product. Cover every value and always run the compact RTL + 200% font combination.

| Dimension | Required values |
| --- | --- |
| Available width | 360, 599, 600, 839, and 840 dp |
| Constrained height | 480 dp, plus the normal portrait or TV height |
| Font | 100% and 200% |
| Direction and language | English LTR, Simplified Chinese, `en-XA`, and `ar-XB` RTL |
| Theme | Light and dark; include dynamic color when available |
| Navigation and input | Gesture navigation, three-button navigation, IME closed, and IME open |
| Plugin count | 0, 1, and 30 |
| Capability count | 0, 1, and 12 |
| Settings field count | 0, 1, and 20 |
| Text | Short copy, longest localized copy, and long identity/origin values |

Run phone cases on a phone profile and large-window cases on a tablet or resizable large-screen
profile. TV evidence must come from a TV profile with D-pad input.

## What the checks must prove

Automated UI tests must assert:

- the single settings entry, list-to-detail navigation, and full authorization/settings flow;
- 48 dp minimum bounds for every actionable semantics node;
- localized accessible names, roles, state, enabled state, and logical traversal order;
- complete visibility of the final action after scrolling, with system bars, floating navigation,
  and the IME represented in the test;
- state preservation across window-class changes and no duplicate high-emphasis action;
- empty, loading, unavailable, and error behavior, not only the successful one-plugin case.

Visual review is still required. Capture a normal screenshot and a screenshot with **Show layout
bounds** enabled for each changed form factor. Reject the change for clipping, unintended
truncation, overlap, double insets, inconsistent row alignment, broken text baselines, arbitrary
centering, or an individual-card treatment applied to ordinary list rows. Use the normal screenshot
to review hierarchy, contrast, and dynamic color; use layout bounds to review constraints and
touch geometry. Android recommends combining automated checks with manual accessibility testing;
see [Compose accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing).

## Release evidence

Record the device or AVD, API level, available window size, locale, layout direction, font size,
theme, navigation mode, command, result, and screenshot paths. Structural resource tests do not
replace native-language review of authorization, errors, and destructive actions.
