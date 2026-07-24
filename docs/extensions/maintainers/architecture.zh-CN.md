# 当前架构与代码地图

[English](architecture.md) · [维护者指南](README.zh-CN.md)

用本页定位插件问题应该由哪一层处理。尚未达到发布标准的部分单独列在
[当前状态与发布门槛](status-and-release.zh-CN.md)。

## 先看主链路

```text
用户操作或 Worker
  -> 功能 repository 创建类型化 request
  -> ExtensionRuntime 调用一个 Hook
  -> 内置 handler 或外部 transport
  -> 功能 repository 校验类型化 result
  -> Room、界面或播放器
```

Runtime 负责完成一次调用。功能 repository 负责解释 result，并决定它能否改变产品状态。
两者分开后，通用 Hook 执行器就不会绕过功能规则，直接写入搜索、EPG、频道或播放数据。

插件实现有两种：

- **内置插件：** handler 在 M3UAndroid 进程中运行；Emby/Jellyfin 使用这条路径。
- **外部插件：** handler 在其他进程中运行，通过 `AndroidBoundExtensionTransport` 调用。

两条路径使用相同的 `HookSpec<Request, Result>` 和 runtime 策略。

## 每一层负责什么

| 负责人 | 职责 | 从这里开始 |
| --- | --- | --- |
| API 契约 | 插件身份、manifest、设置、Hook request/result 与 wire 字段 | [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api) |
| Runtime | 注册、API/schema 协商、按 Hook 分配 capability、payload 限制、并发、超时、取消与健康状态 | [`ExtensionRuntime`](../../../extension/runtime/src/main/kotlin/com/m3u/extension/runtime/ExtensionRuntime.kt) |
| Android transport | Service 发现、身份、绑定、handshake、流式 payload 与 Binder death | [`:extension:transport-android`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android) |
| 外部 SDK | 解码调用并运行已注册的类型化 handler | [`TypedExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/TypedExtensionService.kt) |
| 插件生命周期 | 信任、证书固定、启停、授权、重连、重新授权与诊断 | [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt) |
| 设置生命周期 | 已显示的 Schema、保存值、Secret Handle 与编辑授权 | [`ExtensionSettingsRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/extension/ExtensionSettingsRepositoryImpl.kt) |
| 网络作用域 | 为一次外部 Hook 调用选择已批准的 Origin 与凭据 | [`ExtensionHookBrokerScopeProvider`](../../../data/src/main/java/com/m3u/data/extension/security/ExtensionHookBrokerScopeProvider.kt) |
| 网络执行 | 检查作用域、URL、重定向、值、大小和超时，然后发送 HTTP | [`HostNetworkBrokerImpl`](../../../data/src/main/java/com/m3u/data/extension/security/HostNetworkBrokerImpl.kt) |
| Provider 流程 | Discover、Validate、Refresh、播放解析与 Session 关闭 | [`SubscriptionProviderRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryImpl.kt) |
| Result 应用 | 校验所有权并写入宿主数据，或把结果映射到界面/播放器 | [`data/extension`](../../../data/src/main/java/com/m3u/data/extension)、[`data/repository/extension`](../../../data/src/main/java/com/m3u/data/repository/extension) |
| 后台任务 | 对齐周期任务声明，并由 WorkManager 调用任务 Hook | [`ExtensionBackgroundTaskScheduler`](../../../data/src/main/java/com/m3u/data/worker/ExtensionBackgroundTaskScheduler.kt)、[`ExtensionBackgroundTaskWorker`](../../../data/src/main/java/com/m3u/data/worker/ProviderWorker.kt) |

## 一次 Hook 调用发生什么

1. 功能 repository 选择 `HookSpec`、插件 ID 与 request。
2. Runtime 确认插件已启用，并且声明了该 Hook。
3. Runtime 检查 API 与 Hook schema version。
4. `CapabilityPolicy` 计算用户授权。Runtime 只保留当前 Hook 声明的 capability，其他 Hook
   的 capability 不会进入本次调用。
5. Runtime 应用 payload、并发与超时限制。
6. 如果外部 Hook 声明并获得 `network`，Broker Scope Provider 可以打开一个短期作用域。
7. 内置 handler 直接运行；外部 request 经过 Android transport。
8. Runtime 解码 result、关闭 Broker 作用域并记录健康状态。
9. 功能 repository 校验所有权，再应用 result。

没有 Broker 作用域时，不会提供备用联网路径。Hook 仍可返回离线结果，但 Broker 操作会失败。

## 网络作用域如何选择

外部 Broker 支持 Provider Validate/Refresh/Resolve/Close、设置、搜索、Metadata、EPG 和后台
任务。Provider `Discover` 始终离线。

| Request | 作用域来源 |
| --- | --- |
| Provider `Validate` | 从本次提交的 Provider Origin 创建认证作用域。 |
| Provider `Refresh`、`ResolvePlayback`、`ClosePlayback` | Provider repository 创建账号作用域。 |
| 带 `account + credential` 的搜索、Metadata 或 EPG | 从匹配的已保存 Provider 账号创建账号作用域。 |
| 设置、后台任务，或不带账号的搜索、Metadata、EPG | 从已批准的 manifest 与设置 Origin 创建 Hook 作用域。 |

通用 Hook 作用域会合并：

- 已经为可信插件批准的固定 `manifest.networkOrigins`；
- 用户明确保存、并标记为 `networkOrigin` 的当前文本设置。

Trust Store 只保留已批准的固定 Origin。插件重连不会批准新增 Origin；证书重新固定时，只
保留旧批准列表与新 manifest 的交集。网络 Origin 设置没有默认值：保存当前值才会授权，
清空会撤销授权，设置 schema 变化后需要重新保存。

每个作用域都绑定外部插件身份、Hook、已批准 Origin 与短生命周期。账号作用域还绑定账号。
正常返回、失败、超时或取消后，作用域都会关闭。`HostNetworkBrokerImpl` 会检查首次 URL 与
每次重定向的精确 Scheme、Host 和 Port。

只有当前 Hook 声明了 `credential.read` 且用户批准后，凭据句柄才会进入作用域。Broker 只在
构造请求时解析 `SecretReference` 与 `ContextReference`，不会把解析值直接序列化回插件。

## Provider 认证与刷新

外部 Provider 认证使用独立的一次性流程：

```text
Validate Hook
  -> broker.authenticate 发送登录请求
  -> Broker 保存凭据与选定的账号字段
  -> 插件收到一次性回执
  -> Provider repository 消费回执
  -> Vault 保存加密凭据
```

插件不会收到登录响应正文。Emby/Jellyfin 内置实现属于可信宿主代码，因此返回
`ProviderValidationEvidence.TrustedDirect`。外部 Provider 必须返回
`ProviderValidationEvidence.HostBrokerReceipt`。

刷新随后进入普通产品链路：

```text
ProviderWorker 或用户刷新
  -> SubscriptionProviderRepositoryImpl
  -> SubscriptionHookSpecs.Refresh
  -> ExtensionRuntime
  -> Provider handler
  -> SubscriptionProviderImporter
  -> Room
  -> Metadata 与 EPG 贡献 Hook
```

Importer 只更新当前账号，并保留宿主管理的频道本地状态。外部 Provider 与
Emby/Jellyfin 共用同一个 repository 和 importer；只有 handler 调用会经过 Android IPC。

Broker 可以防止宿主直接泄露凭据，但无法阻止恶意插件与用户已批准的 Origin 串谋。这项
剩余风险是外部插件仍放在开发者开关后的原因之一。

## 后台任务链路

插件在 `manifest.backgroundTasks` 中声明周期任务。

```text
启用、重新授权或恢复插件
  -> ExtensionBackgroundTaskScheduler.reconcile
  -> WorkManager 保存或更新周期任务
  -> ExtensionBackgroundTaskWorker 恢复已启用插件
  -> ExtensionRuntime 调用 HostHookSpecs.BackgroundTask
```

插件停用或失去必要 capability 时，Scheduler 会取消全部任务，也会清理已经删除的任务声明。
`requiresNetwork = true` 的任务带有联网约束。Worker 只重试可恢复错误，并在达到次数上限后
停止。

## 外部插件生命周期

```text
发现 Service
  -> 检查 manifest 与身份，并签发短期审阅 Token
  -> 用户批准身份、capability 与固定 Origin
  -> Repository 消费 Token 并记录信任
  -> Transport 注册到 Runtime
```

启用和重新授权必须使用“展示插件详情时”签发的 Token。Token 只能使用一次，五分钟后过期，
并绑定当时发现的 Service 和证书。Token 缺失、过期或与当前对象不符时，必须让用户重新
审阅。

设置保存也有同样的边界。宿主显示设置表单时，为每个字段签发短期、一次性的编辑 Token。
只有字段仍属于当时显示的 Section Schema 时才能保存；Schema 已变化时拒绝写入并重新载入
表单。

以下状态不能混为一谈：

- **已发现：** 宿主可以看到 Service。
- **已启用：** 用户允许调用，并且必要授权齐全。
- **已注册：** 当前宿主进程已经有可用 transport。

进程重启后，Repository 恢复可信且已启用的插件。插件更新或 Binder 断开后，恢复流程或插件
列表刷新会重新建立注册。Runtime 不管理 Android Service 生命周期。

## 必须保持的规则

- Runtime 不写 Room，也不更新界面。
- Result importer 只能在本次 request 与 owner 范围内修改数据。
- 一个插件失败，不能删除另一个插件的数据，也不能删除自己上一次的有效结果。
- Plugin Repository 负责信任、启停与授权。
- Vault 负责 Secret；契约只携带不透明句柄与一次性回执。

准备修改代码前，继续阅读[按改动类型操作](change-guide.zh-CN.md)。
