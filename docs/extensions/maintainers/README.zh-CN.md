# 插件系统维护者指南

[English](README.md)

本文记录 M3UAndroid 插件系统专属的架构与发布约束，不扩展到无关的应用架构。

## 架构边界

插件栈按以下层次划分：

- `:extension:api`：KMP-compatible、基于 `kotlinx.serialization` 的契约、hook spec、manifest、设置模型、错误、凭据 handle 和 broker 契约；不得依赖 Android。
- `:extension:runtime`：目录注册、兼容性协商、能力与调用策略、类型化序列化、资源限制、健康状态和故障隔离。
- `:extension:transport-android`：service 发现、显式绑定、AIDL 控制面、`ParcelFileDescriptor` JSON 流、Binder death 和 Android 传输错误。
- `:extension:sdk-android`：面向外部 APK 的 service 与网络 broker 适配器。
- `data`：宿主适配，包括内置 provider 注册、插件信任持久化、凭据 vault、网络 broker 实现、Room migration、worker、session 恢复和 hook repository。
- phone/TV app：权限与信任展示、插件管理 UI。
- `:testing:extension-reference`：外部进程参考插件与一致性样例。

内置 extension 和外部 APK transport 必须进入同一个 runtime，并遵守相同的类型化 hook、能力、超时、payload、并发、取消、健康和错误规则。不要增加 app 直连插件的捷径。

## 契约演进

每个 hook 都有 `HookSpec<Request, Result>` 和独立的正整数 schema version。兼容演进应新增带默认值的可选序列化字段。refresh reason、close reason 等开放 wire 值使用可扩展字符串。禁止重新引入任意 payload 的运行时强转。

API major 不一致即不兼容。同 major 内按 manifest 声明协商 hook schema。未知必要能力和不受支持的必要 hook version 必须拒绝；未知可选 JSON 字段应忽略。

冻结或修改契约前：

1. 更新 API catalog 与类型化 serializer；
2. 增补 golden JSON fixture 和版本协商测试；
3. 让内置与 Android transport 通过相同的行为测试；
4. 同步更新本目录树中开发者、维护者各自的中英文文档；
5. 明确该契约是“仅已定义”还是“已有生产宿主调用点”。

## Runtime 策略与隔离

调用方只指定插件和类型化 hook，不得手工传入一组临时的已授权能力。`CapabilityPolicy` 根据宿主支持、manifest 声明、hook 要求、信任和用户授权计算 grant；`InvocationPolicy` 提供超时、并发和 payload 限制。

取消必须传播到 runtime 和 Android transport。普通插件故障必须被限制在该插件内。多插件贡献点使用 supervisor 语义，保留取消，并把其他单插件异常转为失败/空贡献。连续失败更新健康状态，并可将插件隔离为 `UNHEALTHY`；统一生命周期状态为 `ENABLED`、`DISABLED`、`INCOMPATIBLE`、`UNHEALTHY`。

禁止直接记录完整序列化 payload。runtime 和 transport 诊断必须脱敏凭据、认证 header、secret 设置、捕获值和其他已知敏感字段。

## 外部 APK 生命周期

发现只查询 action `com.m3u.extension.action.BIND_EXTENSION`，禁止增加 `QUERY_ALL_PACKAGES`。必须显式绑定解析出的 component，并要求权限 `com.m3u.permission.BIND_EXTENSION_HOST`。绝不把 APK 代码加载进宿主进程。

外部插件受 `PreferencesKeys.EXTERNAL_EXTENSIONS` 开关保护。关闭功能会注销并关闭外部 transport，但保留信任记录；重新开启后恢复满足条件的已启用插件。repository 必须串行化生命周期变更，避免设置 observer、worker、刷新和 UI 操作竞态注册。

首次启用是安全决策。手机和 TV 都必须在接受前展示包名、开发者、证书 SHA-256、版本和申请能力。信任绑定包/component 身份并固定签名者。签名变化会禁用插件并要求显式重新授权。“忘记信任”删除持久化决定，不等同于普通禁用。

恢复流程不得调用用户授权路径。必须用当前 manifest 重新核对已保存 grant：删除不再申请的能力、新增可选能力保持未授予，缺少任何新增必要能力时停止恢复并禁用。component 改变 extension ID 时同样必须显式重新授权。

## 凭据与网络

secret 归宿主所有。`CredentialVault` 使用 Android Keystore 保护的 AES-GCM 密文，只暴露不透明 handle。数据库和备份中不得出现明文 token。密钥丢失或恢复后的密文无法解密时，应删除不可用凭据，并将 provider 标记为需要重新认证。

外部网络请求必须经过 `HostNetworkBroker`，并保持以下约束：

- 目标 origin 只能是账号 base origin 或用户显式批准的附加 origin；
- 移除插件提供的认证 header；
- 按引用注入宿主持有的 secret；
- 每次 redirect 都重新校验 origin；
- 限制超时、响应大小和并发；
- 登录 capture 从 header/JSON pointer 提取 secret 写入 vault，只返回脱敏内容与 handle。

不得为了兼容性增加读取原始 secret 的逃生口。

## 宿主调用点规则

插件只返回声明式结果，宿主 importer 负责校验和持久化。UI、business 与插件均不得操作 DAO，也不得构造按 provider 分支的 `DataSource`。

provider 订阅应持久化通用 provider/account 身份与 credential handle。`providerId`、`providerKind` 从 provider account 解析，禁止硬编码 Emby ID。备份包含账号元数据和稳定播放引用，绝不包含 token；恢复后的 provider playlist 必须重新认证。

搜索时，宿主并发调用已启用的贡献者，再把不透明 stable reference 映射回已有且未隐藏的宿主频道。无法映射的插件结果不能构造成可播放对象。未来设置、EPG、元数据和后台贡献也遵守同一所有权规则：对应的宿主 renderer/importer 始终拥有最终决定权。

provider refresh 必须进入 WorkManager，具备联网约束、取消、重试、进度和受控配额。播放 session 打开后立即持久化，正常 close 后删除，进程重启后执行幂等清理。

## 当前接入状态

目前已有生产宿主链路：内置 Emby/Jellyfin 发现、校验、刷新、播放解析、close、provider 持久化、后台刷新与播放 session 恢复。外部 APK 的发现、信任、启禁用、签名固定、handshake、invoke、cancel、health、broker 代理、进程恢复和后台任务已在开发者开关下实现。手机端搜索贡献使用宿主持有的 stable-reference 映射。通用 provider 刷新以受限分批方式调用 metadata 与 EPG 贡献者；metadata 只能通过窄 DAO 更新标题/分类，EPG 经引用、时间和大小校验后替换到插件专属的宿主数据源。

metadata 与 EPG importer 尚未覆盖所有旧 M3U/Xtream 导入路径。设置 hook 已经类型化，但完整的外部设置 renderer、值存储与调用上下文仍在建设中。在这些链路和测试落地前，不得称其为生产接入。

## 发布门槛

至少验证：

- API/runtime golden 序列化、协商、类型错误、大小限制、超时、取消、并发、能力拒绝、异常隔离和脱敏；
- Room migration、凭据丢失、备份恢复后的重认证、幂等导入、后台刷新和重启 session 清理；
- 跨域/跨域 redirect 拒绝、认证 header 移除、签名变化和未授权能力；
- 参考插件发现、绑定、handshake、大 PFD 结果、取消、Binder death、进程崩溃和版本不兼容；
- 手机与 TV 的发现、全行/DPad 交互、信任详情、启禁用、重新授权、功能开关恢复和错误状态；
- M3U、EPG、Xtream、普通播放与 DLNA 回归。

在独立安装的兼容 APK 可以安全发现和启用、所有对外 hook 都有宿主持有的 importer、且插件崩溃、恶意请求或凭据失败不会破坏宿主数据或终止宿主进程之前，不得移除开发者开关。
