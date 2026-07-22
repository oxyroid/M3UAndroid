# 当前状态与发布门槛

[English](status-and-release.md) · [维护者指南](README.zh-CN.md)

本页定义当前分支可以发布到什么范围。插件实现方法见[插件开发指南](../developers/README.zh-CN.md)。

## 发布边界

- Emby/Jellyfin 内置插件进入正常产品链路。
- 外部插件继续放在开发者功能开关后。
- **开放外部插件之前**的项目全部完成前，不得移除这个开关。

## 已接通链路

| 范围 | 当前行为 | 证据 |
| --- | --- | --- |
| 契约与 Runtime | 类型化、带版本的 Hook 契约；宿主计算 Capability；限制 Payload、超时和并发；传播取消；记录健康状态并隔离连续失败 | `ExtensionContractTest`、`ExtensionRuntimeTest` 与 Transport 一致性测试 |
| 内置 Provider | Emby 和 Jellyfin 是同一个内置插件的两个类型；发现、验证、刷新、播放解析与 Session 关闭共用 Provider 链路 | `EmbyCompatibleProviderIntegrationTest` 与 `SubscriptionProviderRepositoryIntegrationTest` |
| Provider 凭据 | 外部登录只返回一次性宿主回执；Token 和不透明上下文留在宿主，并作为一条加密凭据记录保存 | `HostNetworkBrokerSecurityTest`、`ExtensionHostBridgeTest`、`ProviderBrokerScopeStoreTest` 与 `CredentialVaultTest` |
| Provider 持久化 | 通用 Provider 账号、数据库迁移、无 Token 备份、重新认证状态、WorkManager 刷新和重启后的 Session 清理 | Migration、Provider Repository、Worker、Restore 与 Session Cleanup 测试 |
| 外部插件生命周期 | 发现、身份与证书信任、启停、Capability 重新授权、重连、清除数据、诊断、流式 Payload 和取消 | Transport 测试、`ExtensionPluginRepositoryLifecycleTest` 与 `ExternalExtensionIpcTest` |
| 外部参考 Provider | 发现、宿主管理登录、首次刷新、Room 导入、受保护播放解析和 Session 关闭与内置 Provider 共用 Repository | `ExternalProviderEndToEndTest` |
| Provider 界面 | 手机和 TV 都由 Descriptor 生成 Provider 列表与表单，并显示重新认证状态；Emby 与 Jellyfin 仍是两个独立选项 | `SubscriptionSourceSelectionTest` 以及手机、TV 设备检查 |
| 其他 Hook | 设置、搜索、Metadata、EPG 和受限后台任务已有类型化 SDK Handler 与宿主调用点 | SDK、Contribution Repository/Importer、Worker 与 IPC 测试 |

## 发布内置 Provider 链路之前

- 运行数据库 21、22、23 起点的全部 Migration 测试；
- Provider 或播放链路变化后，回归 M3U、EPG、Xtream、普通播放和 DLNA；
- 使用真实输入与焦点移动检查手机和 TV Provider 表单；
- 数据库 Schema Artifact 与所有手写 Migration 必须处于同一变更中。

## 开放外部插件之前

- 在 TV、WorkManager 和真实播放器中跑通完整外部 Provider 流程，而不只依赖 Repository 级设备测试；
- 为授权、重新授权、设置、错误和 TV 焦点增加可重复的界面自动化；
- 增加进程级恶意 Fixture，覆盖调用阻塞、忽略取消、进程死亡、错误或超限输出、保留 Broker、签名变化与 Extension ID 冲突；
- 实施宿主级调用总预算，并让一次 Hook 与其全部 Broker 请求共用一个 Deadline；
- 让同一套公开一致性测试同时运行于内置和外部 Transport；
- 发布 Wire Golden Fixture、SDK Artifact 与同 Major 兼容策略。

## 决策规则

内置 Provider 的回归全部通过后，可以进入发布。外部插件可以继续作为开发者预览；只有开放清单全部通过，并确认各种失败不会破坏宿主数据和主进程后，才能默认开启。
