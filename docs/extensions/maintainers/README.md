# Maintain the extension platform

[简体中文](README.zh-CN.md)

These pages are for M3UAndroid maintainers changing extension contracts, built-in extensions, external APK transport, host importers, plugin UI, or conformance tests.

## Choose the page for your task

| Task | Read |
| --- | --- |
| Understand how built-in and APK extensions fit together | [Architecture](architecture.md) |
| Add or change a hook, importer, provider, transport, or UI | [Change guide](change-guide.md) |
| Decide whether a feature is actually connected or ready to ship | [Status and release gates](status-and-release.md) |

## Working rules

- A serialized contract is not a finished feature. Record the real host caller, importer, UI, and test evidence.
- Built-in and external transports share the same typed hook contract and runtime behavior.
- Extension results are proposals. Host code validates ownership and bounds before applying them.
- Public developer docs describe only behavior that an independently installed APK can exercise.
- Keep every extension page paired in English and Simplified Chinese.

The external APK platform remains behind a developer feature switch. The [status page](status-and-release.md) is the source of truth for remaining release blockers.
