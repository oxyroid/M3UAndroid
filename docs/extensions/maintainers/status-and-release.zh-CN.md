# 当前状态与发布门槛

[English](status-and-release.md) · [维护者指南](README.zh-CN.md)

本页定义当前分支可以发布到什么范围。插件实现方法见[插件开发指南](../developers/README.zh-CN.md)。

## 发布边界

- Emby/Jellyfin 内置插件按正常产品门槛发布。
- 外部 APK 插件仍是需要通过开发者开关主动启用的预览能力。
- 外部开放清单完成，且已发布的[外部 APK 插件威胁模型](threat-model.zh-CN.md)中每个未决项
  都有留档结论前，保留该开关。

## 已接通链路

| 范围 | 当前行为 | 证据 |
| --- | --- | --- |
| 契约与 Runtime | 类型化、带版本的 Hook 契约；每次调用只获得当前 Hook 已声明且已批准的 Capability；限制单插件与宿主级调用准入；Request 准备、排队、执行、Response 校验与 Broker 请求共用一个截止时间；累计限制 Broker 请求次数、编码后的请求总字节数和响应总字节数；传播取消；记录健康状态并隔离连续失败 | `WireGoldenFixtureTest`、`ExtensionContractTest`、`ExtensionRuntimeTest`、`InvocationBudgetPropagationTest` 与 `ExtensionHostBridgeTest`。同一套序列化一致性测试会分别运行于内置 Runtime、SDK Backend 与独立参考 APK。 |
| SDK 分发 | `1.0.0-alpha01` 会把 API、Android 协议、类型化 SDK、源码、一致性测试库和 Golden Fixture 发布为带版本的 Maven 仓库压缩包，并生成 SHA-256 文件。Hello 是独立的 Gradle 引用工程，只从该仓库解析 SDK group。 | `verifyExtensionSdkBundle`、`verifyExtensionSdkRepository` 与 `testing/bin/verify-extension-sdk-distribution.sh`。两条流水线都已配置上传压缩包与校验值，仍需首次 Actions 成功结果作为远端证据。 |
| 内置 Provider | Emby 和 Jellyfin 是同一个内置插件中可供新订阅选择的两个类型；隐藏的自动识别类型只作为已有账号的兼容值保留，不提供给新订阅选择 | `EmbyCompatibleProviderIntegrationTest`、`EmbyCompatibleProviderLocalizationTest` 与 `SubscriptionProviderRepositoryIntegrationTest` |
| Provider 凭据 | 外部登录只返回一次性宿主回执。验证后的作用域只会把引用解析进发往批准 Origin 的请求；宿主不会把解析值直接序列化回插件。 | `HostNetworkBrokerSecurityTest`、`ExtensionHostBridgeTest`、`ProviderBrokerScopeStoreTest` 与 `CredentialVaultTest` |
| 通用 Hook 联网 | 设置、搜索、Metadata、EPG 和后台任务 Hook 在自身声明并获得 `network` 后可以使用宿主 Broker。搜索、Metadata、EPG request 带账号时使用账号作用域；其他调用使用已批准的 manifest Origin 与用户明确保存的设置 Origin。Discover 保持离线。 | `ExtensionNetworkOriginContractTest`、`ExtensionBrokerScopeRuntimeTest`、`ExtensionHookBrokerScopeStoreTest` 与 `ExtensionHostBridgeTest` |
| Provider 持久化 | 新建和恢复的订阅统一使用 `DataSource.Provider`；通用账号、无 Token 备份、重新认证、WorkManager 刷新和重启 Session 清理共用一条链路 | Migration、Provider Repository、Worker、Restore 与 Session Cleanup 测试 |
| 外部插件生命周期 | 发现、身份与证书信任、与审阅内容绑定的启用/重新授权 Token、启停、Capability 与固定 Origin 授权、重连、清除数据、诊断、文件承载的大 Payload 传输和取消 | Transport 测试、`ExtensionPluginRepositoryLifecycleTest` 与 `ExternalExtensionIpcTest` |
| 插件设置 | Manifest 与动态 Schema、普通值、加密 Secret Handle、网络 Origin 授权，以及与已显示字段绑定的编辑。动态状态绑定一次 Runtime 注册；失败、停用或替换后保持隐藏，直到当前注册重新校验。 | `ExtensionSettingsRepositoryTest`、`ExtensionPluginRepositoryLifecycleTest` 与 `ExtensionHookBrokerScopeProviderTest` |
| 外部参考 Provider | 独立参考 APK 通过 Binder/PFD 完成错误与正确登录、订阅、Room 导入、WorkManager 刷新、带凭据的播放解析、真实 PlayerManager/Media3 就绪和 Session 关闭，并与内置 Provider 共用 Repository | `ExternalProviderEndToEndTest` |
| Provider 界面 | 手机和 TV 都由 Descriptor 生成 Provider 列表与表单；Emby 与 Jellyfin 保持独立选项，外部 Provider 选项保留可见的来源身份 | `SubscriptionSourceSelectionTest`、`TvProviderAccessibilityTest` 与 `ResourceContractTest`；Connected UI 测试目前需要显式设备运行 |
| 其他 Hook | 设置、搜索、Metadata 与 EPG 已有类型化 SDK Handler 和产品调用点 | SDK、Contribution Repository/Importer 与 IPC 测试 |
| 后台任务 | 插件启用、重新授权或恢复时，manifest 任务声明会对齐为 WorkManager 周期任务。停用或缺少授权时取消；联网任务带联网约束。 | `ExtensionBackgroundTaskSchedulerTest`、Worker 测试与 `ExtensionPluginRepositoryLifecycleTest` |

## 如何理解证据

CI 门禁指 `.github/workflows/android.yml` 自动执行的检查。Connected UI 检查可重复，但目前
需要显式设备运行；设备检查指有记录的一次性实测。`ResourceContractTest` 验证资源结构，
不代表母语文案质量。CI 会检查手机矩阵脚本的语法，并编译 data、手机与 TV 的
Connected Test。外部插件门禁会先启动本地参考服务并通过健康检查，再在
`hostileApi34` 构建托管设备上使用独立参考 APK，运行
`HostileExternalExtensionIpcTest`、`ExternalExtensionConformanceIpcTest`、
`ExternalProviderEndToEndTest` 与 `DebugDefaultLibraryBootstrapTest`。参考服务为真实
播放器检查提供确定性的 PCM WAV；该门禁不运行手机、平板或 TV 的界面矩阵。

最近一次恶意 IPC Fixture 实测（2026-07-29）：

- 设备：`emulator-5558` 上的 Pixel 6 Pro API 36。
- 结果：3/3 通过；Fixture 位于测试 APK，使用不同 UID 和独立 `:hostile` 进程。
- 覆盖：重复畸形输出、合法但超限的结果、忽略取消与迟到回调、同一个宿主 Bridge
  在授权期内可用且撤销后拒绝调用、进程死亡、以新 PID 重连、过期的持久化签名固定值、
  审阅后重新固定证书，以及真实进程冒用已由其他可信 Service 持有的 Extension ID 时被拒绝。
- 签名用例把真实发现 Service 的当前 PackageManager 证书与测试注入的过期固定值比较；
  它没有安装一个不同签名的替换 APK。

最近一次外部共享一致性测试实测（2026-07-29）：

- 设备：`emulator-5558` 上的 Pixel 6 Pro API 36。
- 结果：1/1 通过；参考插件以独立 APK 安装，与宿主使用不同 UID。
- 覆盖：类型化成功结果中的 Invocation、Extension、Hook 与 Schema 标识和请求一致；
  Request/设置/授权/预算上下文；缺少必要 Capability 与不支持 Schema 时拒绝；类型化
  Request/Result 经 PFD JSON 传输；取消经 AIDL 发送并由远端 Handler 确认收到。

最近一次外部 Provider 生产链路实测（2026-07-29）：

- 结果：`ExternalProviderEndToEndTest` 通过；参考插件以独立 APK 安装，Provider 调用
  通过 Binder 传递 PFD JSON Payload。
- 失败链路：连续三次错误登录都返回 `provider.authentication_failed`，且插件没有被停用。
- 数据链路：正确登录后订阅成功并导入两个频道；WorkManager 后台刷新完成后仍为两个频道。
- 播放链路：真实 `PlayerManager` 与 Media3 使用确定性 WAV 到达 `STATE_READY`；显式关闭
  与播放器释放对应的服务端 Session 都已关闭。
- 尚未覆盖：TV 上的完整外部 Provider 流程，以及仍有播放 Session 时宿主冷启动后的恢复。

最近一次手机 Connected 实测（2026-07-29）：

- 设备与配置：`emulator-5558` 上的 Pixel_6_Pro API 36，使用运行脚本的 `phone`
  profile。
- 结果：`compact-ltr` 为 20/20，`compact-narrow-ltr` 为 2/2；
  `compact-rtl-large` 在 `ar-XB`、320dp 宽度和 200% 字体下为 11/11。
- 插件覆盖包括 Descriptor 驱动的 Provider 表单、参考插件的完整管理流程，以及可区分的
  Loading、可重试 Failure、Missing 和 Content 状态。
- 无障碍覆盖包括每行只有一个操作目标、左右镜像的 48dp 首尾区域、无重复的状态语义、
  完整技术身份，以及长设置选项在 200% 字体 RTL 下自然换行且错误只朗读一次。

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

- 逐项解决并留档[外部 APK 插件威胁模型](threat-model.zh-CN.md#仍未解决的问题)中的未决项，
  包括 HTTP/LAN 策略、解析后地址、已批准服务端串谋、Hook 数据披露和包准入。
- 保持 `ExternalProviderEndToEndTest` 通过，覆盖错误登录、订阅、WorkManager 刷新、
  真实播放器就绪和 Session 关闭；
- 在 TV 上补齐同一套外部 Provider 完整流程；手机生产链路门禁不能替代 TV 播放器与焦点
  生命周期验证；
- 增加冷启动设备测试：保留一个未关闭的外部播放 Session，重启宿主，并验证服务端幂等关闭
  与本地 Session 删除；
- 为外部插件的授权、重新授权、设置、错误状态、破坏性操作确认与 TV 回焦增加可在 CI
  运行的 Connected UI 自动化；内置 Provider 的 DPad 测试不算完成此门槛；
- 保持进程级恶意 Fixture 门禁通过，覆盖阻塞或迟到调用、忽略取消、进程死亡、错误或超限
  输出、保留 Broker、过期签名信任与 Extension ID 冲突；
- 保持内置 Runtime、SDK Backend 与独立 APK 共用的一致性测试全部通过；
- 保持带版本的 SDK 压缩包与独立 Hello 消费测试通过。默认开放前，将压缩包及校验值附加到
  可长期访问的发布渠道，不能只依赖短期 CI Artifact。

## 决策规则

内置 Provider 的回归门槛通过后可独立发布。外部插件继续作为主动启用的开发者预览；
只有开放清单全部通过并发布威胁模型决策后，才能移除开关。
