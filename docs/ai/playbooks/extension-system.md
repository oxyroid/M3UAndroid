# Playbook: Extension system changes

## When to use this playbook

Use it for extension contracts, runtime policy, built-in extensions such as the Emby/Jellyfin provider, Android plugin transport, host importers, plugin management, or extension conformance tests.

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

The maintainer pages are canonical: architecture, change workflow, and current release gaps live on their respective pages.

## Change workflow

1. Identify the typed hook and its real host call site.
2. Confirm which module owns the change using the architecture module table.
3. Keep built-in and Android transports on the same runtime contract and policy path.
4. Add the host renderer/importer whenever a contract returns declarative data.
5. Test the smallest affected module, then the cross-process or app path when relevant.
6. Update the support matrix and both language versions of every affected extension page.

For contract changes, include golden serialization, schema negotiation, and both transport paths. For phone or TV extension changes, verify the product trigger and visible result on the affected surface.

## Validation evidence

Choose the closest API, runtime, SDK, transport, cross-process, provider, importer, or UI evidence from the maintainer [validation table](../../extensions/maintainers/change-guide.md#validation-evidence). External transport changes require the reference extension path; developer-facing SDK changes keep Hello working through its M3UAndroid settings entry.
