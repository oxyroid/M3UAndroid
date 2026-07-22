# Extension system documentation

The extension documentation is split by audience and language. It covers only the extension system and its host integration.

## Extension developers

- [English guide](developers/README.md)
- [简体中文指南](developers/README.zh-CN.md)

Use these guides when building an independently installed Android extension APK.

## Project maintainers

- [English guide](maintainers/README.md)
- [简体中文指南](maintainers/README.zh-CN.md)

Use these guides when changing contracts, runtime policy, Android transport, security boundaries, host call sites, or conformance tests.

The external APK platform is currently guarded by the **External Extensions** developer feature. A contract existing in `extension/api` does not by itself mean that every host UI or importer is connected; each guide records the currently connected hooks explicitly.
