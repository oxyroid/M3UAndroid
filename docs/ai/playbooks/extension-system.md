# Playbook: Extension system changes

## When to use this playbook

Use it for extension contracts, runtime policy, built-in providers, Android plugin transport, host importers, plugin management, or extension conformance tests.

## Canonical documentation

Read the audience-specific documentation before editing:

- Project maintainers: [`docs/extensions/maintainers/README.md`](../../extensions/maintainers/README.md)
- 项目维护者：[`docs/extensions/maintainers/README.zh-CN.md`](../../extensions/maintainers/README.zh-CN.md)
- Extension developers: [`docs/extensions/developers/README.md`](../../extensions/developers/README.md)
- 插件开发者：[`docs/extensions/developers/README.zh-CN.md`](../../extensions/developers/README.zh-CN.md)

The maintainer guide is the architecture source of truth. Keep module ownership, lifecycle, current integration status, and release gates there instead of duplicating them in this playbook.

Also read the root `AGENTS.md` and the nearest module-specific `AGENTS.md` for every changed file.

## Change workflow

1. Identify the typed hook and its real host call site.
2. Confirm which module owns the change using the maintainer guide's module table.
3. Keep built-in and Android transports on the same runtime contract and policy path.
4. Add the host renderer/importer whenever a contract returns declarative data.
5. Test the smallest affected module, then the cross-process or app path when relevant.
6. Update the current-integration table and both language versions of the affected developer/maintainer guide.

For contract changes, include golden serialization, schema negotiation, and both transport paths. For Room changes, update migrations, schema artifacts, and migration tests together. For phone or TV changes, verify interaction on the appropriate device surface when practical.

## Useful validation commands

Use JDK 17 and select the commands that match the change:

```bash
./gradlew :extension:api:test :extension:runtime:test
./gradlew :extension:transport-android:connectedDebugAndroidTest
./gradlew :data:connectedDebugAndroidTest
./gradlew :app:smartphone:assembleDebug :app:tv:assembleDebug
./gradlew :app:smartphone:connectedDebugAndroidTest :app:tv:connectedDebugAndroidTest
```

Before committing, run `git diff --check` and confirm generated parser outputs and unrelated IDE files are not staged.
