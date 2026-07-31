# 当前状态与发布门槛

[English](status-and-release.md) · [维护者指南](README.zh-CN.md)

本页定义当前分支可以发布到什么范围。插件实现方法见[插件开发指南](../developers/README.zh-CN.md)。

## 发布边界

- Emby/Jellyfin 内置插件按 smartphone 应用的正常产品门槛发布，覆盖手机和平板布局。
- 外部 APK 插件仍是需要通过开发者开关主动启用的预览能力。
- TV 不提供也不调用任何插件能力，只支持 M3U 与 Xtream。
- 外部开放清单完成，且已发布的[外部 APK 插件威胁模型](threat-model.zh-CN.md)中每个未决项
  都有留档结论前，保留该开关。

## 已接通链路

| 范围 | 当前行为 | 证据 |
| --- | --- | --- |
| 契约与 Runtime | 类型化、带版本的 Hook 契约；每次调用只获得当前 Hook 已声明且已批准的 Capability；限制单插件与宿主级调用准入；Request 准备、排队、执行、Response 校验与 Broker 请求共用一个截止时间；累计限制 Broker 请求次数、编码后的请求总字节数和响应总字节数；传播取消；记录健康状态并隔离连续失败 | `WireGoldenFixtureTest`、`ExtensionContractTest`、`ExtensionRuntimeTest`、`InvocationBudgetPropagationTest` 与 `ExtensionHostBridgeTest`。同一套序列化一致性测试会分别运行于内置 Runtime、SDK Backend 与独立参考 APK。 |
| SDK 分发 | `1.0.0-alpha01` 会把 API、Android 协议、类型化 SDK、源码、一致性测试库和 Golden Fixture 发布为带版本的 Maven 仓库压缩包，并生成 SHA-256 文件。Hello 是独立的 Gradle 引用工程，只从该仓库解析 SDK group。 | 签名发布任务只打包压缩包并生成校验值，不运行功能验证。`testing/bin/verify-extension-sdk-distribution.sh` 保留为发布前显式执行的独立 Hello 消费测试。快速流水线只打包 debug APK。 |
| 内置 Provider | Emby 和 Jellyfin 是 smartphone 应用中同一个内置插件的两个选择项 | `EmbyCompatibleProviderIntegrationTest`、`EmbyCompatibleProviderLocalizationTest` 与 `SubscriptionProviderRepositoryIntegrationTest` |
| 完整 Provider 契约 | 每个内置和外部 Provider 都实现 `Discover`、`Validate`、`Refresh`、`Browse`、`ResolvePlayback`、`UpdatePlayback` 与 `ClosePlayback`。`Browse` 提供有数量上限、带稳定引用的根页面和子页面；`UpdatePlayback` 上报有边界的 Session 事件。 | `SubscriptionProviderContractsTest`、`ExtensionContractTest`、`ExtensionNetworkOriginContractTest`、`WireGoldenFixtureTest` 与 Provider 产品链路测试 |
| Provider 凭据 | 外部登录只返回一次性宿主回执。验证后的作用域只会把引用解析进发往批准 Origin 的请求；宿主不会把解析值直接序列化回插件。 | `HostNetworkBrokerSecurityTest`、`ExtensionHostBridgeTest`、`ProviderBrokerScopeStoreTest` 与 `CredentialVaultTest` |
| 通用 Hook 联网 | 设置、搜索、Metadata、EPG 和后台任务 Hook 在自身声明并获得 `network` 后可以使用宿主 Broker。搜索、Metadata、EPG request 带账号时使用账号作用域；其他调用使用已批准的 manifest Origin 与用户明确保存的设置 Origin。Discover 保持离线。 | `ExtensionNetworkOriginContractTest`、`ExtensionBrokerScopeRuntimeTest`、`ExtensionHookBrokerScopeStoreTest` 与 `ExtensionHostBridgeTest` |
| Provider 持久化 | 新建和恢复的订阅统一使用 `DataSource.Provider`；通用账号、无 Token 备份、重新认证、WorkManager 刷新和重启 Session 清理共用一条链路 | Migration、Provider Repository、Worker、Restore 与 Session Cleanup 测试 |
| 外部插件生命周期 | 发现、身份与证书信任、与审阅内容绑定的启用/重新授权 Token、启停、Capability 与固定 Origin 授权、重连、清除数据、诊断、文件承载的大 Payload 传输和取消 | Transport 测试、`ExtensionPluginRepositoryLifecycleTest` 与 `ExternalExtensionIpcTest` |
| 插件设置 | Manifest 与动态 Schema、普通值、加密 Secret Handle、网络 Origin 授权，以及与已显示字段绑定的编辑。动态状态绑定一次 Runtime 注册；失败、停用或替换后保持隐藏，直到当前注册重新校验。 | `ExtensionSettingsRepositoryTest`、`ExtensionPluginRepositoryLifecycleTest` 与 `ExtensionHookBrokerScopeProviderTest` |
| 外部参考 Provider | 独立参考 APK 通过 Binder/PFD 完成错误与正确登录、订阅、Room 导入、WorkManager 刷新、媒体浏览、带凭据的播放解析/更新、真实 PlayerManager/Media3 就绪和 Session 关闭，并与内置 Provider 共用 Repository | `ExternalProviderEndToEndTest` |
| Provider 界面 | smartphone 应用的手机和平板布局都由 Descriptor 生成 Provider 列表与表单；Emby 与 Jellyfin 保持独立选项，外部 Provider 选项保留可见的来源身份 | `SubscriptionSourceSelectionTest` 与 `ResourceContractTest`；Connected UI 测试目前需要显式设备运行 |
| 其他 Hook | 设置、搜索、Metadata 与 EPG 已有类型化 SDK Handler 和产品调用点 | SDK、Contribution Repository/Importer 与 IPC 测试 |
| 后台任务 | 插件启用、重新授权或恢复时，manifest 任务声明会对齐为 WorkManager 周期任务。停用或缺少授权时取消；联网任务带联网约束。 | `ExtensionBackgroundTaskSchedulerTest`、Worker 测试与 `ExtensionPluginRepositoryLifecycleTest` |

## 如何理解证据

`.github/workflows/android.yml` 是构建与发布流水线，不是功能测试门禁。PR 与手动的
非发布运行只编译三份无签名 Release APK。`master` 上的发布运行构建签名后的手机、
TV、参考插件与 SDK 产物，上传前只检查发布产物完整性：APK 证书与 16 KB 原生打包。
手动快速流水线只构建三份 debug APK、上传 Artifact 并发送到 Telegram。

单元测试、托管设备测试和手机/平板 UI 测试由维护者在打包流水线外显式执行。下文记录
的是这些专项运行的证据。`ResourceContractTest` 只验证资源结构，不代表母语文案质量。

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
- 尚未覆盖：仍有播放 Session 时宿主冷启动后的恢复。

最近一次手机 Connected 实测（2026-07-30）：

- 设备与配置：`emulator-5558` 上的 Pixel_6_Pro API 36，使用运行脚本的 `phone`
  profile。
- 结果：`compact-ltr` 为 20/20，`compact-narrow-ltr` 为 2/2，
  `compact-height-zh-cn-dark-three-button` 为 2/2，`compact-599-en-xa` 为 3/3，
  `compact-rtl-large` 为 11/11。
- 插件覆盖包括 Descriptor 驱动的 Provider 表单、参考插件的完整管理流程，以及可区分的
  Loading、可重试 Failure、Missing 和 Content 状态。
- 无障碍覆盖包括每行只有一个操作目标、左右镜像的 48dp 首尾区域、无重复的状态语义、
  完整技术身份，以及长设置选项在 200% 字体 RTL 下自然换行且错误只朗读一次。实测还
  覆盖 599dp Compact 边界、480dp 高简体中文输入法场景、`en-XA`、浅色/深色主题及
  手势/三键导航。

最近一次平板 Connected 实测（2026-07-30）：

- 设备与配置：`emulator-5554` 上的 `6GB_RAM_Device` API 36，使用运行脚本的
  `tablet` profile。
- 结果：600dp 的 `medium-600-ltr` 为 1/1，839dp 的 `medium-839-ltr` 为 1/1，
  深色主题下 840dp 的 `expanded-840-ltr-dark` 为 6/6。
- 其中与插件系统直接相关的覆盖包括 Descriptor 驱动的 Provider 表单，以及“设置”
  侧栏保持显示和选中时的完整外部插件管理流程。两个 Medium 边界还验证唯一的上下文
  Heading、唯一返回操作、单面板导航与 48dp 返回触控范围。

在一台已启动、可清空数据且 API 不低于 33 的手机模拟器上，用下面的命令重跑手机矩阵：

```shell
testing/bin/run-smartphone-provider-ui-matrix.sh emulator-5558 phone
```

`phone` profile 会运行完整的 Compact 英语 LTR 组、定向的 320dp 窄屏英语 LTR 组、
360 × 480dp 的简体中文深色/三键导航输入法用例、599dp 的 `en-XA` 断点用例，以及
320dp 宽度、200% 字体下的 Compact `ar-XB` RTL 组。大窗口用例必须另行在专用平板
模拟器上运行：

```shell
testing/bin/run-smartphone-provider-ui-matrix.sh emulator-5554 tablet
```

`tablet` profile 运行精确的 600、839 和 840dp 用例，绝不运行手机宽度用例。每个用例
都会在 Instrumentation 前验证显示、字体、主题、导航和 App Locale。结束后，脚本会恢复
所有改动过的显示、开发者、主题与导航设置，并移除测试包。

## 发布内置 Provider 链路之前

- 从每个受支持的起始 Schema 跑到当前数据库版本（目前为 21→22），覆盖整条 Migration 链；
- Provider 或播放链路变化后，回归 M3U、EPG、Xtream、普通播放和 DLNA；
- 在测试会断言的目标配置下运行手机和平板 Connected UI 检查：LTR/RTL、大字体、
  紧凑/≥600dp 布局，以及 Provider 选择与表单；记录设备或 AVD、Locale、fontScale、
  宽度、命令与结果；
- `ResourceContractTest` 只作为键集、占位符、复数和双向控制字符的结构门禁。将某个语言
  标记为完成前，Provider、登录、授权、错误和删除类文案仍需母语审校；
- 数据库 Schema Artifact 与所有手写 Migration 必须处于同一变更中。

## 开放外部插件之前

- 逐项解决并留档[外部 APK 插件威胁模型](threat-model.zh-CN.md#仍未解决的问题)中的未决项，
  包括 HTTP/LAN 策略、解析后地址、已批准服务端串谋、Hook 数据披露和包准入。
- 保持 `ExternalProviderEndToEndTest` 通过，覆盖错误登录、订阅、WorkManager 刷新、
  媒体浏览、播放更新、真实播放器就绪和 Session 关闭；
- 增加冷启动设备测试：保留一个未关闭的外部播放 Session，重启宿主，并验证服务端幂等关闭
  与本地 Session 删除；
- 为外部插件的授权、重新授权、设置、错误状态和破坏性操作确认增加可在 CI 运行的
  smartphone Connected UI 自动化，并覆盖手机和平板布局；
- 保持进程级恶意 Fixture 门禁通过，覆盖阻塞或迟到调用、忽略取消、进程死亡、错误或超限
  输出、保留 Broker、过期签名信任与 Extension ID 冲突；
- 保持内置 Runtime、SDK Backend 与独立 APK 共用的一致性测试全部通过；
- 保持带版本的 SDK 压缩包与独立 Hello 消费测试通过。默认开放前，将压缩包及校验值附加到
  可长期访问的发布渠道，不能只依赖短期 CI Artifact。

## 决策规则

内置 Provider 的回归门槛通过后可独立发布。外部插件继续作为主动启用的开发者预览；
只有开放清单全部通过并发布威胁模型决策后，才能移除开关。
