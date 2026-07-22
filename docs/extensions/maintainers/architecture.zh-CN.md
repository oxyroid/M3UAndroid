# 插件平台架构

[English](architecture.md) · [维护者指南](README.zh-CN.md)

本页只解释当前系统如何组成，不代表所有链路都已经适合外部插件。发布判断请查看 [当前状态与发布门槛](status-and-release.zh-CN.md)。

## 整体模型

所有插件先进入同一个类型化 runtime，再由宿主持有的消费端决定如何处理结果。

```text
内置插件 ----------------------------\
                                      > ExtensionRuntime
外部 APK -> Android transport -------/         |
                                                v
                                    HookSpec<Request, Result>
                                                |
                                                v
                                      宿主 importer / renderer
                                                |
                                                v
                                      Room / UI / 播放器
```

Emby 与 Jellyfin 是一个 **内置插件**。它的 adapter 由宿主直接注册，并在宿主进程内运行。外部插件是独立安装的 APK，通过 Android IPC 调用。Transport 只改变调用跨越边界的方式，不改变 Hook 的语义。

## 核心名词

| 名词 | 含义 |
| --- | --- |
| Extension manifest | 稳定身份、版本范围、Hook、capability 申请、设置和诊断 metadata |
| Hook spec | 一组类型化请求与结果，拥有独立 schema version |
| Catalog | 某个 Hook 当前可用的已启用实现 |
| Runtime | 协商、序列化、调用限制、取消、健康状态和失败记录 |
| Transport | 内置调用 adapter 或 Android 跨进程 adapter |
| Importer/renderer | 校验并应用插件贡献的宿主代码 |

## 模块职责

| 模块 | 负责 |
| --- | --- |
| `:extension:api` | 不依赖 Android 的序列化契约、manifest、Hook spec、设置、错误、凭据和 broker 类型 |
| `:extension:runtime` | catalog、注册、API/schema 协商、调用策略、并发、取消和健康状态 |
| `:extension:transport-android` | APK 发现、显式 service 绑定、AIDL、PFD payload、package 身份和信任存储 |
| `:extension:sdk-android` | APK 侧 `ExtensionService` 与宿主 bridge adapter |
| `data` | 内置注册、插件生命周期 repository、vault、broker、worker、Room 和宿主 importer |
| 手机与 TV app | 预览开关、授权、管理、设置和 provider 展示 |
| `:testing:extension-reference` | 验证外部边界的已安装 APK fixture |

`:extension:api` 保持 Kotlin Multiplatform-compatible。Android 发现、Binder、Keystore、WorkManager 和 Room 留在 Android 侧模块。

## 一次调用如何完成

1. 宿主选择 `HookSpec<Request, Result>` 并创建类型化请求。
2. `ExtensionCatalog` 选择已经启用且 Hook schema 兼容的实现。
3. `CapabilityPolicy` 提供当前已授予的 manifest capability。
4. `InvocationPolicy` 限制请求/结果大小、逻辑超时和单插件并发。
5. 内置 adapter 或 Android transport 调用插件。
6. Runtime 解码结果，并为对应插件记录成功或失败。
7. 宿主 importer 或 renderer 校验贡献后再应用。

当前 runtime 只强制检查插件在 Hook 声明中列出的 capability，还没有为每个公开 Hook 固定一套宿主持有的最低 capability。补齐这张宿主映射表是发布要求。

## 内置插件

`EmbyCompatibleProvider` 通过同一个内置插件发布 Emby 和 Jellyfin 两个 provider descriptor。它实现全部五个 provider Hook，并使用与外部插件相同的契约 model。内置代码可以通过 adapter 使用宿主服务，但仍返回公开契约结果，而不是 Room 实体或播放器对象。

这条链路是 provider 语义的参考实现，但不能证明外部 provider 生命周期已经完整。

## 外部 APK 生命周期

宿主发现声明插件 service action 的组件，解析为显式 component，读取 package 和签名证书，完成 handshake，并读取 manifest。随后由用户核对身份与申请的 capability，再决定是否启用。

```text
已安装 -> 已发现 -> 已检查 -> 已信任 -> 已启用
                              \-> 已拒绝 / 不兼容
已启用 -> 已停用 -> 已启用
已启用 -> 连续失败 -> 异常隔离
身份变化 -> 重新作出信任决定
```

Runtime 状态统一为 `ENABLED`、`DISABLED`、`INCOMPATIBLE` 和 `UNHEALTHY`。

当前信任实现会为 package/service 固定检查到的签名证书，但 grant、设置和 provider 所有权尚未全部绑定到完整的 package/service/certificate 身份。该问题仍是发布阻塞项。

## Android transport

控制面使用 AIDL，JSON envelope 通过 `ParcelFileDescriptor` 传输，因此结果可以超过 Binder 常规的内联 transaction 大小。Service 暴露 handshake、manifest、invoke、cancel 和 health。

当前 `invoke` transaction 仍是同步调用，虽然 payload 使用 PFD，但 coroutine 超时不能终止一个永不返回的 Binder transaction。生产级隔离需要异步且有界的 session 协议，以及恶意插件 fixture 测试。

## 宿主持有的数据应用

插件拿不到 DAO，也不能写 Room 实体。每条贡献链路都需要范围明确的消费端：

- provider discovery 校验贡献者身份、descriptor 数量、kind 和字段边界；
- provider refresh 校验所有权、唯一 remote ID、数量限制和 playback reference；
- search 把 stable reference 解析为已有且可见的频道；
- metadata 只对请求中出现的频道应用允许字段；
- EPG 只替换调用成功的插件自身数据，其他插件失败时不能删除旧数据；
- playback 在交给播放器前校验 URL、scheme、header、过期时间和 session 所有权。

其中多项检查目前仍未实现完整。这里保留架构边界，具体缺口统一记录在 [状态页](status-and-release.zh-CN.md)。

## 凭据与 broker

Provider 凭据和插件密码设置分别使用 Android Keystore 支持的 AES-GCM 存储。契约请求只携带 `CredentialHandle`，不携带明文 secret。

当前 `HostNetworkBroker` 只能解析已经落库的 provider account 凭据。它会检查账号所有权、限制请求到账号 origin、重新检查 redirect，并限制响应正文大小。但账号创建前登录、插件密码设置、用户批准的额外 origin、按 invocation 可撤销的 broker session，以及安全的认证响应白名单都尚未实现。

生产设计需要统一的宿主凭据 registry，明确记录 owner、purpose、scope 和批准的 origin。临时登录 session 只有在校验成功后，才能提升为持久账号。

## 持久化与恢复

Provider 账号、通用 provider playlist、playback reference 和已打开的播放 session 都由宿主持有。备份不包含 token；恢复后的 provider 账号必须重新认证，才能刷新或播放。

播放开始前先保存 open session，正常 close 后删除，进程重启时可以幂等清理。长时间运行的 provider refresh 与恢复任务由 WorkManager 承担。

下一步：[安全修改插件平台](change-guide.zh-CN.md)。
