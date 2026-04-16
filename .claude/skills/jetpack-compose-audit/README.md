# Jetpack Compose Audit Skill

A strict, evidence-based audit skill for Android Jetpack Compose repositories. Scores four categories on a 0-10 scale, produces a cited Markdown report, and tells you what to fix and what each fix will buy you — down to the predicted `skippable%` delta.

Built for Claude Code, Cursor, and any agent that loads the Anthropic skill format.

---

## What it does

Given a Compose repo path, the skill:

1. Confirms Compose is actually present (fast-fails if not).
2. Maps modules, screens, shared UI, state holders, ViewModels.
3. **Generates Compose Compiler reports automatically** via a bundled Gradle init script — no edits to the target's `build.gradle`.
4. Scores four categories against the rubric:
   - **Performance** (35%) — expensive work in composition, lazy keys, lambda modifiers, stability, Strong Skipping, backwards writes
   - **State management** (25%) — hoisting, single source of truth, `rememberSaveable`, lifecycle-aware collection, observable collections
   - **Side effects** (20%) — correct effect API, keys, stale captures, cleanup, composition-time work
   - **Composable API quality** (20%) — modifier conventions, parameter order, slot APIs, `CompositionLocal` usage, `Modifier.Node`
5. Writes `COMPOSE-AUDIT-REPORT.md` at the target root.
6. Returns a chat summary with the top three actionable fixes, each with file:line, doc URL, and expected impact.

Bands: `0-3` fail · `4-6` needs work · `7-8` solid · `9-10` excellent.

---

## What makes it different

**Measured, not inferred.** The skill ships `scripts/compose-reports.init.gradle` and injects it into your Gradle build with `--init-script`. Every run parses real `*-classes.txt` / `*-composables.txt` / `*-module.json` output. Stability claims stop being folklore.

**Mandatory ceilings.** A Performance score cannot exceed the cap set by measured `skippable%` and unstable-param count. 69% skippability caps Performance at 4 — no room for generous interpretation. The ceiling math appears in the report so the score is auditable.

**Every deduction cites an official source.** Each finding carries a `References:` line pointing at `developer.android.com` or the AndroidX component API guidelines. Audits that can't be defended with a URL don't ship.

**Actionable chat summary.** The chat output mirrors the report's `Prioritized Fixes` — same file paths, same doc links, same predicted impact ("moves `skippable%` from 69% → ~85%, Performance ceiling 4 → 6").

---

## Install

Symlink the repo into your skills directory so `git pull` updates everywhere at once:

```bash
# Claude Code
mkdir -p ~/.claude/skills
ln -s "$(pwd)" ~/.claude/skills/jetpack-compose-audit

# Cursor
mkdir -p ~/.cursor/skills
ln -s "$(pwd)" ~/.cursor/skills/jetpack-compose-audit
```

---

## Use

From the AI prompt:

```
/jetpack-compose-audit [repo path or module path]
```

Or in natural language:

```
Audit this Compose repo.
Score the :app module for Compose quality.
Run a Compose performance review on core/ui.
```

The compiler-report build runs automatically and typically takes 1-5 minutes depending on the target. If the build fails, the skill falls back to source-inferred findings and reduces confidence one level — explicitly flagged in the report.

---

## Example output

```
Overall: 59/100

Performance:  4/10  capped by skippable% 69.14% (qualitative 7)
State:        6/10  collectAsState without lifecycle, duplicate VM reads
Side effects: 7/10  LaunchedEffect key too broad at HomeScreen.kt:240
API quality:  8/10  BoxCard / SearchBar follow conventions

Compiler:
  Strong Skipping: on
  skippable% = 186/269 = 69.14%
  deferredUnstableClasses: 59

Top 3 fixes
1. collectAsState -> collectAsStateWithLifecycle across 6 call sites
   feature/home/HomeScreen.kt:37, MainActivity.kt:213, ...
   Doc: developer.android.com/.../side-effects
   Impact: fewer redundant collections, lifecycle-correct

2. Stabilize HomeFeedScreen / HomeFeedItem / BoxCard params
   Evidence: app/build/compose_audit/app_release-classes.txt
   Doc: developer.android.com/.../stability
   Impact: skippable% 69% -> ~85%, Performance ceiling 4 -> 6

3. Narrow LaunchedEffect(homeScreenState) at HomeScreen.kt:240-254
   Doc: developer.android.com/.../side-effects
   Impact: fewer redundant ensureAuthenticated() calls
```

---

## Scope

**In scope (v1).** Jetpack Compose on Android, Kotlin 2.0.20+ / Compose Compiler 1.5.4+ (Strong Skipping default).

**Out of scope (v1)** — the skill will call these out as a note rather than silently produce thin coverage:

- Material 3 compliance, theming, color/typography — defer to the `material-3` skill
- Accessibility scoring (semantics, touch targets) — flagged as notes, not scored
- UI test coverage and Compose test-rule patterns
- Compose Multiplatform (`expect`/`actual`, target-specific code paths)
- Wear OS / TV / Auto / Glance surfaces
- Build performance (incremental compilation, KSP/KAPT)

---

## Layout

```
SKILL.md                         main skill manifest (process, principles, output)
scripts/
  compose-reports.init.gradle    Gradle init script injected via --init-script
references/
  scoring.md                     rubric with measured ceilings and inline citations
  search-playbook.md             grep patterns, regex, read-the-file heuristics
  canonical-sources.md           every URL the rubric cites
  report-template.md             required structure for COMPOSE-AUDIT-REPORT.md
  diagnostics.md                 manual-mode fallback snippets
```

---

## Philosophy

- **Strict but evidence-based.** Every deduction has a file:line and an official-doc URL.
- **Measured beats inferred.** Compiler reports are generated automatically; source-inferred stability is a fallback, not the default.
- **Written for action.** The report's `Prioritized Fixes` section and the chat summary mirror each other, so the developer can act on the chat alone.
- **Narrow scope on purpose.** The skill does not score design, accessibility, or build performance in v1. It says so rather than pretending otherwise.

---

## License

MIT.
