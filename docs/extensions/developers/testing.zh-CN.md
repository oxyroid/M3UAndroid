# 验证插件行为

[English](testing.md) · [插件开发指南](README.zh-CN.md)

插件功能完成的标志，是 Hook 在 M3UAndroid 的真实产品入口产生预期结果。

## 从产品入口验证

部署修改后，刷新 **设置 → 订阅管理 → 扩展插件**，再从 [Hook 目录](hooks.zh-CN.md) 列出的产品入口触发 Hook。只有插件新增必要 capability 时才使用 **重新授权**。

| Hook 范围 | 验收结果 |
| --- | --- |
| 设置 | 插件设置页绘制返回的分组，并能重新载入已保存值。 |
| 手机搜索 | 返回的稳定引用会在手机搜索中提升本地已有且可见的频道。 |
| 频道信息与 EPG | 通用 provider 刷新只把贡献应用到请求中的频道；EPG 调用失败时保留上一次成功贡献。 |
| Provider 与后台任务 | 这些 APK 产品链路尚未开放，当前调用只验证契约。 |

## M3UAndroid 插件状态

- **不兼容：** 插件 API range 或声明的 Hook schema 不受支持。
- **异常：** 连续 Hook 失败达到 runtime 阈值；修正 handler 后，停用并重新启用插件以再次运行。

SDK 测试验证类型化 handler；功能验收以以上宿主可见结果为准。

发布和升级约束见 [发布与升级](reference/compatibility.zh-CN.md)。修改宿主平台时使用 [维护者验证证据](../maintainers/change-guide.zh-CN.md#验证证据)。
