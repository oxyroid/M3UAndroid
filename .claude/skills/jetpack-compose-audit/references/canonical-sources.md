# Canonical Sources

Use these as the source of truth for v1 scoring and guidance. **Every deduction in the audit report must cite at least one of these URLs** (or one of their officially-linked sub-pages) — see `report-template.md` for the citation format.

## Primary Sources

### Performance

- Android Developers: `Follow best practices`  
  `https://developer.android.com/develop/ui/compose/performance/bestpractices`
- Android Developers: `Jetpack Compose Performance`  
  `https://developer.android.com/develop/ui/compose/performance`
- Android Developers: `Compose phases`  
  `https://developer.android.com/develop/ui/compose/performance/phases`
- Android Developers: `Stability`  
  `https://developer.android.com/develop/ui/compose/performance/stability`
- Android Developers: `Diagnose stability problems`  
  `https://developer.android.com/develop/ui/compose/performance/stability/diagnose`
- Android Developers: `Fix stability issues`  
  `https://developer.android.com/develop/ui/compose/performance/stability/fix`
- Android Developers: `Strong Skipping Mode`  
  `https://developer.android.com/develop/ui/compose/performance/stability/strongskipping`
- Android Developers: `Performance tooling` (Compose Compiler reports / metrics)  
  `https://developer.android.com/develop/ui/compose/performance/tooling`
- Android Developers: `Baseline profiles`  
  `https://developer.android.com/develop/ui/compose/performance/baseline-profiles`

These ground:

- `remember` for expensive work
- lazy list keys
- `derivedStateOf`
- deferred state reads
- lambda modifiers
- backwards writes
- stability annotations (`@Stable`, `@Immutable`), `kotlinx.collections.immutable`, `compose_compiler_config.conf`
- Strong Skipping Mode (default since Kotlin 2.0.20), `@NonSkippableComposable`, `@DontMemoize`
- Compose Compiler reports / metrics as the primary diagnostic for skippability and stability
- performance mindset and baseline-profile awareness

### State

- Android Developers: `State and Jetpack Compose`  
  `https://developer.android.com/develop/ui/compose/state`
- Android Developers: `State hoisting`  
  `https://developer.android.com/develop/ui/compose/state-hoisting`
- Android Developers: `Architecting your Compose UI`  
  `https://developer.android.com/develop/ui/compose/architecture`
- Android Developers: `Lists and grids` (lazy keys, `contentType`)  
  `https://developer.android.com/develop/ui/compose/lists`

These ground:

- state hoisting rules
- stateful vs stateless composables
- `remember` vs `rememberSaveable`
- observable vs non-observable mutable state
- lifecycle-aware collection of observable state
- plain state-holder classes
- ViewModel as screen-level source of truth and the rules around `viewModel()` placement
- lazy-list `key` and `contentType` semantics

### Side Effects

- Android Developers: `Side-effects in Compose`  
  `https://developer.android.com/develop/ui/compose/side-effects`

This grounds:

- side-effect-free composition
- `LaunchedEffect`
- `DisposableEffect`
- `SideEffect`
- `rememberUpdatedState`
- `produceState`
- lifecycle-aware effect behavior

### Composable API Quality

- Android Developers: `Style guidelines for Jetpack Compose APIs`  
  `https://developer.android.com/develop/ui/compose/api-guidelines`
- AndroidX component guidelines: `API Guidelines for @Composable components in Jetpack Compose`  
  `https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md`
- AndroidX general guidelines: `API Guidelines for Jetpack Compose`  
  `https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-api-guidelines.md`
- Android Developers: `Custom modifiers` (`Modifier.Node`, `composed { }` discouraged)  
  `https://developer.android.com/develop/ui/compose/custom-modifiers`
- Android Developers: `Locally scoped data with CompositionLocal`  
  `https://developer.android.com/develop/ui/compose/compositionlocal`
- Android Developers: `Navigation with Compose`  
  `https://developer.android.com/develop/ui/compose/navigation`

These ground:

- `modifier` conventions
- parameter order
- explicit vs implicit dependencies
- meaningful defaults
- component layering
- avoiding `MutableState<T>` and `State<T>` in reusable APIs when better shapes exist
- custom modifier authoring with `Modifier.Node` over the discouraged `composed { }` factory
- when `CompositionLocal` is appropriate (tree-scoped data with sensible defaults) vs when explicit parameters are required
- navigation patterns and where navigation calls belong

## Supplemental Sources

These are useful for extra examples and ecosystem framing, but they do **not** override the primary sources. Community blog posts age fast — when the supplemental and primary sources disagree, the primary AndroidX/Android docs win:

- `https://github.com/skydoves/compose-performance`
- `https://medium.com/@idaoskooei/building-better-uis-with-jetpack-compose-best-practices-and-techniques-a1c8953bc5b8`

## Adjacent Skill Pattern

This skill intentionally pairs well with the `material-3` skill, which covers Material 3 design and design-system audit concerns. The reference implementation lives at `https://github.com/hamen/material-3-skill`, but recommend it by skill name (`material-3`) so the reference does not rot if the URL moves.

This Compose audit skill should mention `material-3` as a follow-up when visual or design-system problems are suspected, but should not score design in v1.
