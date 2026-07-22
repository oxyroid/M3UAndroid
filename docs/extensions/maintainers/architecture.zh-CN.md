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

- **内置插件：** handler 与宿主在同一进程，例如 Emby/Jellyfin。
- **APK 插件：** runtime 通过 `AndroidBoundExtensionTransport` 调用另一个 APK 的 Service。

两条路径在 `ExtensionRuntime` 汇合，使用相同的 `HookSpec<Request, Result>`。

## 每一层由谁负责

| 层 | 负责什么 | 第一处代码 |
| --- | --- | --- |
| 契约 | 插件身份、设置、Hook 的 request/result | [`:extension:api`](../../../extension/api/src/main/kotlin/com/m3u/extension/api) |
| Runtime | 注册、版本、能力、大小、并发、超时和健康状态 | [`ExtensionRuntime`](../../../extension/runtime/src/main/kotlin/com/m3u/extension/runtime/ExtensionRuntime.kt) |
| Android 发现 | 查找已安装的插件 Service 和签名身份 | [`AndroidExtensionDiscovery`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidExtensionDiscovery.kt) |
| Android 调用 | 绑定 Service、handshake、发送调用和取消 | [`AndroidBoundExtensionTransport`](../../../extension/transport-android/src/main/java/com/m3u/extension/transport/android/AndroidBoundExtensionTransport.kt) |
| APK SDK | 在插件进程接收调用并运行类型化 handler | [`ExtensionService`](../../../extension/sdk-android/src/main/java/com/m3u/extension/sdk/android/ExtensionService.kt) |
| 插件生命周期 | 信任、启用、停用、重连、重新授权和诊断 | [`ExtensionPluginRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/plugin/ExtensionPluginRepositoryImpl.kt) |
| Provider 产品流程 | 发现、验证、刷新、播放和关闭 session | [`SubscriptionProviderRepositoryImpl`](../../../data/src/main/java/com/m3u/data/repository/provider/SubscriptionProviderRepositoryImpl.kt) |
| 结果应用 | 校验并写入宿主数据，或映射到界面/播放器 | [`data/extension`](../../../data/src/main/java/com/m3u/data/extension)、[`data/repository/extension`](../../../data/src/main/java/com/m3u/data/repository/extension) |

## 一次 Hook 调用

以任意类型化 Hook 为例：

1. Repository 选择一个 `HookSpec`，创建 request。
2. Runtime 读取调用方指定的 extension ID，并确认它已启用且声明了该 Hook。
3. Runtime 检查 API/schema、授权能力、payload、并发和超时。
4. 内置 handler 直接运行；APK handler 经过 Android transport。
5. Runtime 解码 result，并记录本次成功或失败。
6. Repository 按当前产品流程处理 result；各流程的校验完整度并不相同。

第 6 步不能放进通用 runtime。搜索结果、EPG、频道快照和播放地址各有不同的所有权与有效性规则；尚未补齐的检查列在 [发布阻塞项](status-and-release.zh-CN.md#发布阻塞项)。

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

如果将来换成 APK provider，`ExtensionRuntime` 前后的 repository 和 importer 不应改变；只有插件调用这一步经过 Android transport。

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
- Plugin repository 持有外部 APK 的信任、启停和授权。
- Credential vault 和 Android Keystore 持有 secret；插件契约只传句柄。

准备修改代码时，继续阅读 [按改动类型操作](change-guide.zh-CN.md)。
