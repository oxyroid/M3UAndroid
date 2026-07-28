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
| 契约与 Runtime | 类型化、带版本的 Hook 契约；每次调用只获得当前 Hook 已声明且已批准的 Capability；限制单插件与宿主级调用准入；Request 准备、排队、执行、Response 校验与 Broker 请求共用一个截止时间；累计限制 Broker 请求次数、编码后的请求总字节数和响应总字节数；传播取消；记录健康状态并隔离连续失败 | `WireGoldenFixtureTest`、`ExtensionContractTest`、`ExtensionRuntimeTest`、`InvocationBudgetPropagationTest` 与 `ExtensionHostBridgeTest`。CI 运行 API Golden、Runtime、SDK 和 Transport 单测；Broker Bridge 使用 Connected Device Test 作为证据。 |
| 内置 Provider | Emby 和 Jellyfin 是同一个内置插件中可供新订阅选择的两个类型；隐藏的自动识别类型只作为已有账号的兼容值保留，不提供给新订阅选择 | `EmbyCompatibleProviderIntegrationTest`、`EmbyCompatibleProviderLocalizationTest` 与 `SubscriptionProviderRepositoryIntegrationTest` |
| Provider 凭据 | 外部登录只返回一次性宿主回执。验证后的作用域只会把引用解析进发往批准 Origin 的请求；宿主不会把解析值直接序列化回插件。 | `HostNetworkBrokerSecurityTest`、`ExtensionHostBridgeTest`、`ProviderBrokerScopeStoreTest` 与 `CredentialVaultTest` |
| 通用 Hook 联网 | 设置、搜索、Metadata、EPG 和后台任务 Hook 在自身声明并获得 `network` 后可以使用宿主 Broker。搜索、Metadata、EPG request 带账号时使用账号作用域；其他调用使用已批准的 manifest Origin 与用户明确保存的设置 Origin。Discover 保持离线。 | `ExtensionNetworkOriginContractTest`、`ExtensionBrokerScopeRuntimeTest`、`ExtensionHookBrokerScopeStoreTest` 与 `ExtensionHostBridgeTest` |
| Provider 持久化 | 新建和恢复的订阅统一使用 `DataSource.Provider`；通用账号、无 Token 备份、重新认证、WorkManager 刷新和重启 Session 清理共用一条链路 | Migration、Provider Repository、Worker、Restore 与 Session Cleanup 测试 |
| 外部插件生命周期 | 发现、身份与证书信任、与审阅内容绑定的启用/重新授权 Token、启停、Capability 与固定 Origin 授权、重连、清除数据、诊断、流式 Payload 和取消 | Transport 测试、`ExtensionPluginRepositoryLifecycleTest` 与 `ExternalExtensionIpcTest` |
| 插件设置 | Manifest 与动态 Schema、普通值、加密 Secret Handle、网络 Origin 授权，以及与已显示字段绑定的编辑 | `ExtensionSettingsRepositoryTest` 与 `ExtensionPluginRepositoryLifecycleTest` |
| 外部参考 Provider | 发现、宿主管理登录、首次与后续刷新、Room 导入、带凭据的播放解析、Header 解析与 Session 关闭都跨 Binder 运行，并与内置 Provider 共用 Repository | `ExternalProviderEndToEndTest` |
| Provider 界面 | 手机和 TV 都由 Descriptor 生成 Provider 列表与表单；Emby 与 Jellyfin 保持独立选项，外部 Provider 选项保留可见的来源身份 | `SubscriptionSourceSelectionTest` 以及手机、TV 设备检查 |
| 其他 Hook | 设置、搜索、Metadata 与 EPG 已有类型化 SDK Handler 和产品调用点 | SDK、Contribution Repository/Importer 与 IPC 测试 |
| 后台任务 | 插件启用、重新授权或恢复时，manifest 任务声明会对齐为 WorkManager 周期任务。停用或缺少授权时取消；联网任务带联网约束。 | `ExtensionBackgroundTaskSchedulerTest`、Worker 测试与 `ExtensionPluginRepositoryLifecycleTest` |

## 发布内置 Provider 链路之前

- 从每个受支持的起始 Schema 跑到当前数据库版本（目前为 21→26），覆盖整条 Migration 链；
- Provider 或播放链路变化后，回归 M3U、EPG、Xtream、普通播放和 DLNA；
- 使用真实输入，在 LTR、RTL、大字体、无障碍 Label 与 TV 焦点移动下检查手机和 TV
  Provider 表单；
- 数据库 Schema Artifact 与所有手写 Migration 必须处于同一变更中。

## 开放外部插件之前

- 明确发布时的威胁模型。当前 Broker 不能防止恶意插件与其批准服务端串谋恢复 Token；若要求更强保证，必须由宿主解析并导入受保护响应。
- 在 TV、WorkManager 和真实播放器中跑通完整外部 Provider 流程，而不只依赖 Repository 级设备测试；
- 为授权、重新授权、设置、错误和 TV 焦点增加可重复的界面自动化；
- 增加进程级恶意 Fixture，覆盖调用阻塞、忽略取消、进程死亡、错误或超限输出、保留 Broker、签名变化与 Extension ID 冲突；
- 让同一套公开一致性测试同时运行于内置和外部 Transport；
- 将 SDK Artifact 与仓库内 Golden Fixture、兼容策略一起发布。

## 决策规则

内置 Provider 的回归全部通过后，可以进入发布。外部插件可以继续作为开发者预览；只有开放清单全部通过，并确认各种失败不会破坏宿主数据和主进程后，才能默认开启。
