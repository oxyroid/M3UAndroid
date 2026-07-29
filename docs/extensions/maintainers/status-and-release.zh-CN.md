# 当前状态与发布门槛

[English](status-and-release.md) · [维护者指南](README.zh-CN.md)

本页定义当前分支可以发布到什么范围。插件实现方法见[插件开发指南](../developers/README.zh-CN.md)。

## 发布边界

- Emby/Jellyfin 内置插件按正常产品门槛发布。
- 外部 APK 插件仍是需要通过开发者开关主动启用的预览能力。
- 外部开放清单完成且下方威胁模型正式确定前，保留该开关。

## 已接通链路

| 范围 | 当前行为 | 证据 |
| --- | --- | --- |
| 契约与 Runtime | 类型化、带版本的 Hook 契约；每次调用只获得当前 Hook 已声明且已批准的 Capability；限制单插件与宿主级调用准入；Request 准备、排队、执行、Response 校验与 Broker 请求共用一个截止时间；累计限制 Broker 请求次数、编码后的请求总字节数和响应总字节数；传播取消；记录健康状态并隔离连续失败 | `WireGoldenFixtureTest`、`ExtensionContractTest`、`ExtensionRuntimeTest`、`InvocationBudgetPropagationTest` 与 `ExtensionHostBridgeTest`。CI 运行 API Golden、Runtime、SDK 和 Transport 单测；Broker Bridge 使用 Connected Device Test 作为证据。 |
| 内置 Provider | Emby 和 Jellyfin 是同一个内置插件中可供新订阅选择的两个类型；隐藏的自动识别类型只作为已有账号的兼容值保留，不提供给新订阅选择 | `EmbyCompatibleProviderIntegrationTest`、`EmbyCompatibleProviderLocalizationTest` 与 `SubscriptionProviderRepositoryIntegrationTest` |
| Provider 凭据 | 外部登录只返回一次性宿主回执。验证后的作用域只会把引用解析进发往批准 Origin 的请求；宿主不会把解析值直接序列化回插件。 | `HostNetworkBrokerSecurityTest`、`ExtensionHostBridgeTest`、`ProviderBrokerScopeStoreTest` 与 `CredentialVaultTest` |
| 通用 Hook 联网 | 设置、搜索、Metadata、EPG 和后台任务 Hook 在自身声明并获得 `network` 后可以使用宿主 Broker。搜索、Metadata、EPG request 带账号时使用账号作用域；其他调用使用已批准的 manifest Origin 与用户明确保存的设置 Origin。Discover 保持离线。 | `ExtensionNetworkOriginContractTest`、`ExtensionBrokerScopeRuntimeTest`、`ExtensionHookBrokerScopeStoreTest` 与 `ExtensionHostBridgeTest` |
| Provider 持久化 | 新建和恢复的订阅统一使用 `DataSource.Provider`；通用账号、无 Token 备份、重新认证、WorkManager 刷新和重启 Session 清理共用一条链路 | Migration、Provider Repository、Worker、Restore 与 Session Cleanup 测试 |
| 外部插件生命周期 | 发现、身份与证书信任、与审阅内容绑定的启用/重新授权 Token、启停、Capability 与固定 Origin 授权、重连、清除数据、诊断、文件承载的大 Payload 传输和取消 | Transport 测试、`ExtensionPluginRepositoryLifecycleTest` 与 `ExternalExtensionIpcTest` |
| 插件设置 | Manifest 与动态 Schema、普通值、加密 Secret Handle、网络 Origin 授权，以及与已显示字段绑定的编辑。动态状态绑定一次 Runtime 注册；失败、停用或替换后保持隐藏，直到当前注册重新校验。 | `ExtensionSettingsRepositoryTest`、`ExtensionPluginRepositoryLifecycleTest` 与 `ExtensionHookBrokerScopeProviderTest` |
| 外部参考 Provider | 发现、宿主管理登录、首次与后续刷新、Room 导入、带凭据的播放解析、Header 解析与 Session 关闭都跨 Binder 运行，并与内置 Provider 共用 Repository | `ExternalProviderEndToEndTest` |
| Provider 界面 | 手机和 TV 都由 Descriptor 生成 Provider 列表与表单；Emby 与 Jellyfin 保持独立选项，外部 Provider 选项保留可见的来源身份 | `SubscriptionSourceSelectionTest`、`TvProviderAccessibilityTest` 与 `ResourceContractTest`；Connected UI 测试目前需要显式设备运行 |
| 其他 Hook | 设置、搜索、Metadata 与 EPG 已有类型化 SDK Handler 和产品调用点 | SDK、Contribution Repository/Importer 与 IPC 测试 |
| 后台任务 | 插件启用、重新授权或恢复时，manifest 任务声明会对齐为 WorkManager 周期任务。停用或缺少授权时取消；联网任务带联网约束。 | `ExtensionBackgroundTaskSchedulerTest`、Worker 测试与 `ExtensionPluginRepositoryLifecycleTest` |

## 如何理解证据

CI 门禁指 `.github/workflows/android.yml` 自动执行的检查。Connected UI 检查可重复，但目前
需要显式设备运行；设备检查指有记录的一次性实测。`ResourceContractTest` 验证资源结构，
不代表母语文案质量。CI 会检查手机矩阵脚本的语法，并编译 data、手机与 TV 的
Connected Test，但不会执行这些设备矩阵。

最近一次手机 Connected 实测（2026-07-29）：

- 设备与配置：`emulator-5558` 上的 Pixel_6_Pro API 36，使用运行脚本的 `phone`
  profile。
- 结果：`compact-ltr` 为 17/17，`compact-narrow-ltr` 为 2/2；
  `compact-rtl-large` 在 `ar-XB`、320dp 宽度和 200% 字体下为 7/7。
- 其中与插件系统直接相关的覆盖包括 Provider 选择与表单，以及插件详情 Loading、带重试操作的 Failure、
  Missing 和 Content 状态；同时验证操作目标归属正确、live-region 语义不重复、
  48dp 操作目标互不重叠，以及版本、包名、服务和证书信息完整。

最近一次平板 Connected 实测（2026-07-29）：

- 设备与配置：`emulator-5554` 上的 `6GB_RAM_Device` API 36，使用运行脚本的
  `tablet` profile。
- 结果：英语 LTR、正常字体下，800dp 的 `medium-ltr` 为 1/1，1080dp 的
  `wide-ltr` 为 6/6。
- 其中与插件系统直接相关的覆盖包括 Descriptor 驱动的 Provider 表单，以及“设置”
  侧栏保持显示和选中时的完整外部插件管理流程。中宽用例还验证单面板标题、返回导航
  与 48dp 触控范围。

最近的 TV 证据仍是 2026-07-28 在 API 34、1280×720 下的结果：
`TvProviderAccessibilityTest` 在英语 LTR
与实际 `ar-XB` RTL（侧栏位于右侧）下分别为 1/1，覆盖 DPad 进入、打开和关闭 Provider
表单、可朗读名称，以及焦点返回 Emby。下面的手机命令没有重跑 TV。

在一台已启动、可清空数据且 API 不低于 33 的手机模拟器上，用下面的命令重跑手机矩阵：

```shell
testing/bin/run-smartphone-provider-ui-matrix.sh emulator-5558 phone
```

`phone` profile 会运行完整的紧凑英语 LTR 组、定向的窄版紧凑英语 LTR 组，以及
320dp 宽度、200% 字体下的紧凑 `ar-XB` RTL 组。每次运行都会传入必填的命名用例；
参数、profile、App Locale 或设备实际配置不符都会使测试失败。结束后，脚本会恢复
模拟器显示设置并移除测试包。

## 发布内置 Provider 链路之前

- 从每个受支持的起始 Schema 跑到当前数据库版本（目前为 21→26），覆盖整条 Migration 链；
- Provider 或播放链路变化后，回归 M3U、EPG、Xtream、普通播放和 DLNA；
- 在测试会断言的目标配置下运行手机、平板和 TV Connected UI 检查：LTR/RTL、大字体、
  紧凑/≥600dp 布局、Provider 选择与表单，以及 TV DPad 回焦；记录设备或 AVD、Locale、
  fontScale、宽度、命令与结果；
- `ResourceContractTest` 只作为键集、占位符、复数和双向控制字符的结构门禁。将某个语言
  标记为完成前，Provider、登录、授权、错误和删除类文案仍需母语审校；
- 数据库 Schema Artifact 与所有手写 Migration 必须处于同一变更中。

## 开放外部插件之前

- 发布外部插件威胁模型，并明确接受或拒绝这项剩余风险：Broker 会阻止宿主直接把凭据
  序列化给插件，并把请求限制在已批准 Origin，但无法阻止恶意插件与已批准服务端串谋，
  或编码外传响应中的敏感信息。若不接受该风险，受保护响应的解析与导入必须移到宿主。
- 在 TV、WorkManager 和真实播放器中跑通完整外部 Provider 流程，而不只依赖 Repository 级设备测试；
- 为外部插件的授权、重新授权、设置、错误状态、破坏性操作确认与 TV 回焦增加可在 CI
  运行的 Connected UI 自动化；内置 Provider 的 DPad 测试不算完成此门槛；
- 增加进程级恶意 Fixture，覆盖调用阻塞、忽略取消、进程死亡、错误或超限输出、保留 Broker、签名变化与 Extension ID 冲突；
- 让同一套公开一致性测试同时运行于内置和外部 Transport；
- 将 SDK Artifact 与仓库内 Golden Fixture、兼容策略一起发布。

## 决策规则

内置 Provider 的回归门槛通过后可独立发布。外部插件继续作为主动启用的开发者预览；
只有开放清单全部通过并发布威胁模型决策后，才能移除开关。
