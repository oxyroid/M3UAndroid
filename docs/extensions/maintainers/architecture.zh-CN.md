# 当前架构与代码地图

[English](architecture.md) · [维护者指南](README.zh-CN.md)

本页只回答一个问题：一次插件调用在当前代码中经过哪里？未完成能力集中在 [状态页](status-and-release.zh-CN.md)。

## 共同主路径

```text
用户操作或 Worker
  -> 业务 repository
  -> ExtensionRuntime
  -> 插件实现
  -> 业务 repository 接收并应用结果
  -> Room、界面或播放器
```

插件实现有两种：

- **内置插件：** Handler 与宿主在同一进程，例如 Emby/Jellyfin。
- **外部插件：** Runtime 通过 `AndroidBoundExtensionTransport` 调用插件进程。

两条路径在 `ExtensionRuntime` 汇合，使用相同的 `HookSpec<Request, Result>`。

## 每一层由谁负责

| 层 | 负责什么 | 第一处代码 |
| --- | --- | --- |
| 契约 | 插件身份、设置、Hook 的 request/result | [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api) |
| Runtime | 注册、版本、能力、大小、并发、超时和健康状态 | [`ExtensionRuntime`](../../../extension/runtime/src/main/kotlin/com/m3u/extension/runtime/ExtensionRuntime.kt) |
| Android 发现 | 查找已安装的插件 Service 和签名身份 | [`AndroidExtensionDiscovery`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidExtensionDiscovery.kt) |
| Android 调用 | 绑定 Service、handshake、发送调用和取消 | [`AndroidBoundExtensionTransport`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidBoundExtensionTransport.kt) |
| 外部插件 SDK | 在插件进程接收调用并运行类型化 Handler | [`ExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/ExtensionService.kt) |
| 插件生命周期 | 信任、启用、停用、重连、重新授权和诊断 | [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt) |
| Provider 产品流程 | 发现、验证、刷新、播放和关闭 session | [`SubscriptionProviderRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryImpl.kt) |
| 结果应用 | 校验并写入宿主数据，或映射到界面/播放器 | [`data/extension`](../../../data/src/main/java/com/m3u/data/extension)、[`data/repository/extension`](../../../data/src/main/java/com/m3u/data/repository/extension) |

## 一次 Hook 调用

以任意类型化 Hook 为例：

1. Repository 选择一个 `HookSpec`，创建 request。
2. Runtime 读取调用方指定的 extension ID，并确认它已启用且声明了该 Hook。
3. Runtime 检查 API/schema、授权能力、payload、并发和超时。
4. 内置 Handler 直接运行；外部 Handler 经过 Android Transport。
5. Runtime 解码 result，并记录本次成功或失败。
6. Repository 按当前 request 校验 result，并将其应用到产品流程。

第 6 步不能放进通用 runtime。搜索结果、EPG、频道快照和播放地址各有不同的所有权与有效性规则。

## 真实例子：Emby/Jellyfin 刷新

```text
ProviderWorker 或用户刷新
  -> SubscriptionProviderRepositoryImpl
  -> SubscriptionHookSpecs.Refresh
  -> ExtensionRuntime
  -> EmbyCompatibleProvider
  -> SubscriptionProviderImporter
  -> Room
```

这条路径已经接通：repository 读取账号与凭据，内置 provider 返回频道快照，importer 在事务中更新当前账号的数据，随后再运行 metadata 与 EPG 贡献。

外部 provider 使用相同的 repository 和 importer；它的 handler 通过 Android transport 运行，而不是进入 `EmbyCompatibleProvider`。

## Provider 认证

```text
Validate Hook
  -> broker.authenticate（登录请求与字段位置）
  -> 宿主发送请求
  -> 宿主保留访问凭据和不透明账号上下文
  -> 插件只收到一次性回执
  -> Repository 消费回执
  -> Vault 保存一条加密的 Provider 凭据记录
```

外部 Provider 不会收到登录响应正文。Repository 使用已批准 Origin 与保存的上下文生成稳定账号身份。刷新和播放只传递短期句柄；`HostNetworkBrokerImpl` 仅在当前插件身份、Hook、账号和 Origin 都匹配时解析句柄。

Emby/Jellyfin 内置插件属于宿主代码，因此使用 `ProviderValidationEvidence.TrustedDirect`。外部插件必须返回 `ProviderValidationEvidence.HostBrokerReceipt`。

## 外部 Service 注册

```text
发现 Service
  -> 宿主确认进程只属于一个 package，网络只经过 broker
  -> 宿主读取 manifest
  -> 用户确认身份与 capability
  -> repository 注册 transport
  -> runtime 可以调用 Hook
```

以下三个词对应不同状态：

- **发现：** 设备上存在符合入口声明的 Service。
- **启用：** 用户允许宿主调用它。
- **注册：** 当前宿主进程已经有可用 transport。

应用重启后，bootstrap worker 会恢复仍然可信且已启用的插件。插件更新或 Binder 断开后，下一次插件列表刷新或恢复任务会让 repository 重新建立注册；runtime 不负责 Android Service 生命周期。

## 最重要的所有权

- Runtime 只负责安全地完成一次调用，不写 Room，也不更新 UI。
- Repository/importer 决定结果是否属于本次请求、是否可以替换旧数据。
- Plugin Repository 持有外部插件的信任、启停和授权。
- Credential Vault 和 Android Keystore 持有 Secret；插件契约只传句柄与一次性回执。

准备修改代码时，继续阅读 [按改动类型操作](change-guide.zh-CN.md)。
