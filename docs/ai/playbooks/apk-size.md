# Playbook: APK Size

## When to use this playbook

Use this when the task affects dependencies, native libraries, resources, R8/minification, baseline profiles, packaging options, generated assets, or release artifacts.

## Required context

Read these first:

- `AGENTS.md`
- Relevant module `AGENTS.md` files for changed build logic
- `docs/native-load-yaml.md` when native pack behavior is affected
- Baseline profile or benchmark docs when startup/performance artifacts are affected

## Safe change scope

The agent may modify:

- Gradle packaging, resource, native library, and R8 configuration directly related to size
- Native-load configuration and docs when required
- Benchmark or size comparison notes

The agent should avoid modifying:

- Dependency versions without version catalog updates and justification
- Generated artifacts unless the established build process requires them
- Runtime loading behavior without compatibility notes

## Architecture rules

- Add dependencies through the version catalog only.
- Keep native library packaging and runtime loading behavior aligned.
- Treat import/export formats, public settings, and generated artifacts as compatibility-sensitive.
- Document binary size and runtime loading risks.

## Common mistakes

- Shrinking size by removing resources or ABIs without product intent.
- Updating dependencies inline in Gradle files.
- Breaking native library loading while changing packaging.
- Comparing artifacts built with different variants or build types.

## Validation

Run:

```bash
./gradlew :app:smartphone:assembleRelease
```

Use the affected app variant if the change is TV- or extension-specific. Compare artifact size when feasible.

If the command is not available or cannot be run, explain why.

## PR notes

The PR should mention:

- Size or packaging behavior changed
- Native/resource/R8 compatibility risks
- Artifact comparison method and validation results
