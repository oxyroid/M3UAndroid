# Playbook: Extension system changes

## When to use this playbook

Use it for extension contracts, runtime policy, built-in providers, Android plugin transport, host importers, plugin management, or extension conformance tests.

## Canonical documentation

Read the audience-specific documentation before editing:

- Project maintainers: [`docs/extensions/maintainers/README.md`](../../extensions/maintainers/README.md)
- Change workflow: [`docs/extensions/maintainers/change-guide.md`](../../extensions/maintainers/change-guide.md)
- Current release status: [`docs/extensions/maintainers/status-and-release.md`](../../extensions/maintainers/status-and-release.md)
- 项目维护者：[`docs/extensions/maintainers/README.zh-CN.md`](../../extensions/maintainers/README.zh-CN.md)
- 变更流程：[`docs/extensions/maintainers/change-guide.zh-CN.md`](../../extensions/maintainers/change-guide.zh-CN.md)
- 当前发布状态：[`docs/extensions/maintainers/status-and-release.zh-CN.md`](../../extensions/maintainers/status-and-release.zh-CN.md)
- Extension developers: [`docs/extensions/developers/README.md`](../../extensions/developers/README.md)
- 插件开发者：[`docs/extensions/developers/README.zh-CN.md`](../../extensions/developers/README.zh-CN.md)

The maintainer pages are canonical. Keep architecture, change workflow, and current release gaps on their respective pages instead of duplicating them in this playbook.

Also read the root `AGENTS.md` and the nearest module-specific `AGENTS.md` for every changed file.

## Change workflow

1. Identify the typed hook and its real host call site.
2. Confirm which module owns the change using the architecture module table.
3. Keep built-in and Android transports on the same runtime contract and policy path.
4. Add the host renderer/importer whenever a contract returns declarative data.
5. Test the smallest affected module, then the cross-process or app path when relevant.
6. Update the support matrix and both language versions of every affected extension page.

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
