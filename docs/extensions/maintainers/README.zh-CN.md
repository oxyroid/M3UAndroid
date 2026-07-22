# 维护插件平台

[English](README.md)

这些文档写给修改 M3UAndroid 插件契约、内置插件、外部 APK transport、宿主 importer、插件 UI 或一致性测试的项目维护者。

## 按任务选择文档

| 任务 | 阅读 |
| --- | --- |
| 理解内置插件与 APK 插件如何组成完整系统 | [架构](architecture.zh-CN.md) |
| 增加或修改 Hook、importer、provider、transport 或 UI | [变更指南](change-guide.zh-CN.md) |
| 判断某项能力是否真正接通、能否发布 | [当前状态与发布门槛](status-and-release.zh-CN.md) |

## 维护规则

- 只有序列化契约不代表功能完成；必须记录真实宿主调用点、importer、UI 和测试证据。
- 内置插件与外部 transport 共用同一套类型化 Hook 契约和 runtime 行为。
- 插件结果只是候选贡献，宿主校验所有权和边界后才能应用。
- 面向插件开发者的文档只描述独立安装 APK 能够实际验证的行为。
- 每篇插件文档都要同时维护英文和简体中文版本。

外部 APK 插件仍受开发者功能开关保护。剩余阻塞项以 [当前状态页](status-and-release.zh-CN.md) 为准。
