# Validation Matrix

Use the smallest relevant validation first, then expand if the change affects shared modules or user-facing behavior. Prefer existing Gradle tasks and nearby test suites.

| Change type | Minimum validation | Additional validation |
|---|---|---|
| Documentation or agent guidance | `git diff --check` | Review rendered Markdown when formatting is complex |
| Phone UI change | `./gradlew :app:smartphone:compileDebugKotlin` | Relevant unit/UI tests if available; `./gradlew :app:smartphone:assembleDebug` for broad UI wiring |
| TV UI change | `./gradlew :app:tv:compileDebugKotlin` | Manual/focus checklist; run `android-tv-focus-audit` when focus behavior changes |
| Compose performance change | Run the Compose audit skill | Inspect Compose compiler metrics; compile affected variant |
| Data/repository change | `./gradlew :data:testDebugUnitTest` or closest affected data test | Parser, migration, or integration tests that cover the changed flow |
| Room schema/migration change | Migration tests for the affected schema | Verify schema JSON diff and preservation behavior |
| Parser change | Parser unit tests | Test sample M3U/EPG inputs and empty/malformed input behavior |
| i18n/resource change | `./gradlew :i18n:compileDebugKotlin` or affected app resource compile | Check missing/invalid resources and placeholder consistency |
| Extension API change | Compile host and extension modules | Check binary/API compatibility and sample/plugin behavior |
| Playback change | Compile affected app variant | Run playback-related tests or manual lifecycle checklist |
| Build logic change | Run the affected Gradle task | Run a clean build if feasible |
| APK size or packaging change | Assemble affected release/debug artifact | Compare APK/AAB size and inspect packaged native/resources |

If validation cannot be run, the agent must explicitly state:

- Which command was not run
- Why it was not run
- What risk remains
