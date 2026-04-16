# Material 3 Design System Audit Report

Target: /home/runner/work/M3UAndroid/M3UAndroid
Date: 2026-04-16
Scope: app/smartphone, app/extension, core/foundation, design system implementation
Confidence: Medium (manual audit without dedicated skill)
Overall Score: 72/100

## Executive Summary

The M3UAndroid codebase demonstrates a **solid Material 3 foundation** with proper use of dynamic theming, HCT-based color generation, and adherence to Material 3 component patterns. However, there are **inconsistencies in design token usage**, **hardcoded colors bypassing the theme system**, and **custom spacing that doesn't align with Material 3 spacing guidelines**.

**Strengths:**
- Proper Material 3 ColorScheme implementation using HCT (Hue-Chroma-Tone)
- Dynamic color support for Android 12+
- Comprehensive surface container variants
- Consistent typography usage via MaterialTheme.typography
- Good use of @Immutable on theme-related data classes

**Weaknesses:**
- Hardcoded color literals bypassing theme system (Color(0x...))
- Custom spacing system instead of Material 3 spacing tokens
- SugarColors enum with hardcoded colors for preset themes
- Incomplete token coverage (missing some Material 3 state layer patterns)
- No elevation tokens defined

## Category Scores

| Category | Score | Status | Notes |
|----------|-------|--------|-------|
| Color System | 7/10 | solid | Good M3 implementation but hardcoded colors present |
| Typography | 8/10 | solid | Consistent MaterialTheme.typography usage |
| Spacing & Layout | 6/10 | needs work | Custom spacing system, inconsistent token usage |
| Component Usage | 8/10 | solid | Proper M3 components, good API usage |
| Theme Architecture | 7/10 | solid | HCT-based, dynamic colors, but missing documentation |
| Design Tokens | 6/10 | needs work | Partial token system, inconsistent application |

## Critical Findings

### 1. **Hardcoded Colors Bypassing Theme System**

**Severity:** Medium-High

**Evidence:**
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/playlist/components/ChannelItem.kt:96` — `tint = Color(0xffffcd3c)` hardcoded yellow for star icon
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/business/channel/ChannelMask.kt:306` — `tint = if (favourite) Color(0xffffcd3c) else Color.Unspecified`
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/brush/Scrim.kt:10-11` — Hardcoded scrim colors instead of MaterialTheme.colorScheme.scrim
- `core/foundation/src/main/java/com/m3u/core/foundation/ui/SugarColors.kt:6-13` — 8 hardcoded color presets (Pink, Red, Yellow, etc.) with fixed hex values

**Why it matters:** Hardcoded colors break theme consistency and prevent proper dark mode support. When users switch themes or enable dynamic colors, these hardcoded values remain fixed, creating visual inconsistencies.

**Fix direction:**
- Replace star icon tint with `MaterialTheme.colorScheme.tertiary` or `MaterialTheme.colorScheme.primary`
- Use `MaterialTheme.colorScheme.scrim` for overlay colors
- Refactor SugarColors to generate from seed colors using HCT system (like the main theme does)

**References:**
- https://m3.material.io/styles/color/system/overview
- https://developer.android.com/develop/ui/compose/designsystems/material3

### 2. **Custom Spacing System Instead of Material 3 Tokens**

**Severity:** Medium

**Evidence:**
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/model/Spacing.kt` — Custom spacing scale (extraSmall: 4dp, small: 8dp, medium: 16dp, large: 24dp, extraLarge: 32dp, largest: 40dp)
- Material 3 defines spacing as multiples of 4dp with specific semantic tokens (not size-based names)

**Why it matters:** Material 3 uses semantic spacing tokens (e.g., `padding.horizontal`, `spacing.between`) rather than size-based scales. Custom spacing makes it harder to maintain consistency with Material Design guidelines and creates confusion about which spacing value to use in which context.

**Current implementation:**
```kotlin
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val largest: Dp = 40.dp
)
```

**Material 3 approach:** Spacing should be contextual (e.g., component padding, content spacing) rather than size-based.

**Fix direction:**
- Consider migrating to Material 3's spacing approach or clearly document when to use each spacing value
- Add semantic aliases (e.g., `val contentPadding = medium`, `val componentSpacing = small`)
- Ensure the COMPACT spacing variant is used appropriately for different screen sizes

**References:**
- https://m3.material.io/foundations/layout/understanding-layout/spacing
- https://developer.android.com/develop/ui/compose/designsystems/material3

### 3. **Missing Elevation Token System**

**Severity:** Low-Medium

**Evidence:**
- No centralized elevation system found
- Surface elevations handled ad-hoc in components via `tonalElevation` parameter
- Material 3 defines 5 elevation levels (0dp, 1dp, 3dp, 6dp, 8dp, 12dp)

**Why it matters:** Consistent elevation creates visual hierarchy and helps users understand UI depth. Without a centralized system, different components may use inconsistent elevation values.

**Fix direction:**
- Create an `Elevation` data class similar to `Spacing`:
```kotlin
data class Elevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp
)
```

**References:**
- https://m3.material.io/styles/elevation/overview

## Category Details

### Color System — 7/10

**What is working:**

- ✅ Proper HCT-based color generation via `createScheme()` using Material Color Utilities
- ✅ Complete ColorScheme implementation with all 40+ Material 3 color roles
- ✅ Surface container variants properly implemented (surfaceDim, surfaceBright, surfaceContainer, surfaceContainerLow/High/Highest/Lowest)
- ✅ Dynamic color support for Android 12+ using `dynamicDarkColorScheme()` / `dynamicLightColorScheme()`
- ✅ Proper dark/light theme switching
- ✅ Color scheme persistence via Room database (`ColorScheme` entity)

**What needs improvement:**

- ❌ Hardcoded color literals bypass theme system (star yellow, scrim colors)
- ❌ SugarColors enum with 8 fixed color presets doesn't use HCT generation
- ❌ `surfaceTint` has "todo" comment at line 38 of Colors.kt, should use `scheme.surfaceTint`
- ⚠️ No documentation on when to use which color role

**Evidence:**
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/ktx/Colors.kt:18-65` — Well-structured ColorScheme with proper M3 roles
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/ktx/Colors.kt:38` — TODO comment for surfaceTint
- `core/foundation/src/main/java/com/m3u/core/foundation/ui/SugarColors.kt` — Hardcoded preset colors

### Typography — 8/10

**What is working:**

- ✅ Consistent use of `MaterialTheme.typography.*` throughout codebase
- ✅ Proper Material 3 type scale usage (titleLarge, bodyMedium, headlineSmall, etc.)
- ✅ No hardcoded text sizes or font families
- ✅ Typography passed as parameter to Theme composable

**What needs improvement:**

- ⚠️ No custom Typography definition found — using Material 3 defaults is fine, but consider customizing for brand identity
- ⚠️ No font weight or letter spacing customization
- ℹ️ Extension and TV modules may have separate typography — consistency not verified

**Evidence:**
- Grep results show 20+ usages of `MaterialTheme.typography.*` with proper type scale roles
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/model/Theme.kt:36` — Typography passed to MaterialTheme

### Spacing & Layout — 6/10

**What is working:**

- ✅ Centralized spacing system via `Spacing` data class
- ✅ CompositionLocal for spacing (`LocalSpacing`)
- ✅ Support for compact spacing variant (useful for tablets/large screens)
- ✅ Consistent usage via `LocalSpacing.current`

**What needs improvement:**

- ❌ Size-based naming (extraSmall, small, medium) instead of semantic (componentPadding, contentSpacing)
- ❌ Values don't align with Material 3 spacing recommendations
- ⚠️ No documentation on when to use REGULAR vs COMPACT
- ⚠️ Spacing used inconsistently — some components use hardcoded dp values

**Evidence:**
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/model/Spacing.kt` — Custom spacing system
- Multiple components using `spacing.medium`, `spacing.small` appropriately

### Component Usage — 8/10

**What is working:**

- ✅ Proper M3 components: SearchBar, NavigationSuiteScaffold, BottomSheet, Cards
- ✅ Adaptive navigation using NavigationSuiteScaffold
- ✅ Material 3 Icons (material-icons-extended)
- ✅ Proper composable API patterns (modifier last, proper parameters)
- ✅ Components follow Material 3 guidelines for states (focused, pressed, etc.)

**What needs improvement:**

- ⚠️ Some custom components (PullPanelLayout, Mask) may not follow M3 patterns — needs deeper review
- ⚠️ No consistent error state handling across components
- ℹ️ Loading states handled with custom CircularProgressIndicator wrapper

**Evidence:**
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/App.kt:27` — NavigationSuiteScaffold usage
- Multiple M3 components used correctly throughout

### Theme Architecture — 7/10

**What is working:**

- ✅ Clean theme implementation with seed color generation
- ✅ Proper support for dynamic colors (Android 12+)
- ✅ Theme state managed through preferences
- ✅ @Immutable annotations on theme-related classes
- ✅ HCT color science for accessible color generation

**What needs improvement:**

- ❌ No theme documentation or design system guide
- ⚠️ GradientColors system is separate from main theme (LocalGradientColors)
- ⚠️ Theme switching implementation not reviewed
- ℹ️ No theme preview or design tokens export for designers

**Evidence:**
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/model/Theme.kt` — Clean theme implementation
- `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/model/GradientColors.kt` — Separate gradient system

### Design Tokens — 6/10

**What is working:**

- ✅ Color tokens via MaterialTheme.colorScheme
- ✅ Typography tokens via MaterialTheme.typography
- ✅ Spacing tokens via LocalSpacing
- ✅ @Immutable data classes for tokens

**What needs improvement:**

- ❌ No elevation tokens
- ❌ No shape tokens (corner radius system)
- ❌ No motion tokens (animation durations, curves)
- ❌ No state layer tokens (hover, pressed, focus, drag)
- ⚠️ Tokens not documented or exported for design tools

**Missing token systems:**
1. Elevation (0dp, 1dp, 3dp, 6dp, 8dp, 12dp)
2. Shapes (extra small, small, medium, large, extra large corners)
3. Motion (duration tokens, easing curves)
4. State layers (8%, 12%, 16% opacity overlays)

## Prioritized Fixes

### High Priority

1. **Remove hardcoded colors and use theme tokens**
   - Replace `Color(0xffffcd3c)` with `MaterialTheme.colorScheme.tertiary` or appropriate semantic color
   - Fix scrim colors to use `MaterialTheme.colorScheme.scrim`
   - Impact: Proper theme consistency, better dark mode support
   - Files: `ChannelItem.kt:96`, `ChannelMask.kt:306`, `Scrim.kt:10-11`

2. **Refactor SugarColors to use HCT generation**
   - Convert SugarColors enum to generate ColorSchemes using HCT from seed colors
   - This maintains the preset themes but makes them theme-system compatible
   - Impact: Consistent theming across all color presets
   - File: `core/foundation/src/main/java/com/m3u/core/foundation/ui/SugarColors.kt`

### Medium Priority

3. **Add elevation token system**
   - Create `Elevation` data class with M3 standard levels
   - Replace ad-hoc elevation values with tokens
   - Impact: Consistent visual hierarchy
   - New file: `app/smartphone/src/main/java/com/m3u/smartphone/ui/material/model/Elevation.kt`

4. **Document spacing system**
   - Add KDoc comments explaining when to use each spacing value
   - Consider semantic aliases (e.g., `val cardPadding = medium`)
   - Impact: Better developer experience, consistent spacing
   - File: `Spacing.kt`

5. **Fix surfaceTint TODO**
   - Change `surfaceTint = Color(scheme.surfaceVariant)` to `surfaceTint = Color(scheme.surfaceTint)`
   - Impact: Correct tonal elevation behavior
   - File: `Colors.kt:38`

### Low Priority

6. **Add shape token system**
   - Define corner radius tokens following M3 (extra small: 4dp, small: 8dp, medium: 12dp, large: 16dp, extra large: 28dp)
   - Impact: Consistent component shapes

7. **Create design system documentation**
   - Document color roles and when to use each
   - Document spacing usage guidelines
   - Export tokens for design tools (Figma variables)
   - Impact: Better team alignment, easier onboarding

## Notes

- **Methodology:** Manual audit without dedicated Material 3 skill
- **Coverage:** Focused on smartphone module as primary app, spot-checked extension and TV modules
- **M3 Version:** Using latest Material 3 Compose libraries (2026.01.01 BOM)
- **Confidence:** Medium — comprehensive review of theme system but some components not deeply analyzed
- **Positive highlight:** The HCT-based color generation is sophisticated and properly implements Material Design color science

## Recommendations

1. **Short term (1-2 weeks):**
   - Fix hardcoded colors (High Priority items 1-2)
   - Add elevation tokens (Medium Priority item 3)

2. **Medium term (1 month):**
   - Complete token system (shapes, motion, state layers)
   - Document design system
   - Create theme preview gallery

3. **Long term:**
   - Consider Jetpack Compose Material 3 Adaptive components for better large-screen support
   - Implement proper accessibility color contrast checking
   - Export design tokens for Figma

## Material 3 Compliance Score: 72/100

**Breakdown:**
- Color System: 7/10 (70%)
- Typography: 8/10 (80%)
- Spacing & Layout: 6/10 (60%)
- Component Usage: 8/10 (80%)
- Theme Architecture: 7/10 (70%)
- Design Tokens: 6/10 (60%)

**Overall:** The app has a **solid Material 3 foundation** with proper use of the design system in most areas. The main areas for improvement are eliminating hardcoded colors, completing the design token system, and improving documentation.

---

## Comparison with Compose Audit

The Compose audit scored **59/100** focusing on performance, state, and side effects. This Material 3 audit scores **72/100** focusing on design system consistency. Together, these audits suggest:

- **Strong design foundation** (M3 usage)
- **Needs performance optimization** (lazy list keys, immutable collections)
- **Overall healthy codebase** with clear improvement paths

Recommended next steps: Address Compose performance issues first (high user impact), then polish design system (user experience quality).
