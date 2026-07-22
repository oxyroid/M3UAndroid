# 维护插件系统

[English](README.md)

本文写给修改 M3UAndroid 插件平台的项目维护者，只讨论插件系统。

## 一分钟理解架构

无论内置还是外部插件，都必须经过同一个 runtime：

```text
内置 Kotlin adapter ----\
                       > ExtensionRuntime -> 类型化 hook -> 宿主 importer -> Room/UI/播放器
外部 APK -> Android IPC /
```

这条链路保证两件事：

1. transport 只决定请求怎样到达插件，不改变请求的含义；
2. 插件返回的是“建议结果”，最终校验和应用始终由宿主负责。

不要增加 app 直连插件的捷径，也不要让插件调用 DAO。如果新功能无法进入这条链路，应明确修改类型化契约和宿主 importer。

## 代码应该放在哪里

| 模块 | 负责 | 不应负责 |
| --- | --- | --- |
| `:extension:api` | 序列化模型、manifest、hook spec、设置、错误、凭据/broker 契约 | Android API 或宿主持久化 |
| `:extension:runtime` | 注册、协商、策略、调用限制、健康状态、故障隔离 | APK 发现或 UI |
| `:extension:transport-android` | 发现、显式绑定、AIDL、PFD 流、Binder death | hook 语义或数据导入 |
| `:extension:sdk-android` | service 基类和 APK 侧 broker adapter | 宿主凭据或数据库访问 |
| `data` | 内置注册、信任存储、vault、broker、worker、Room、宿主 importer | 权限展示 |
| phone / TV app | 信任与权限详情、管理界面、声明式设置 UI | transport 或 DAO 逻辑 |
| `:testing:extension-reference` | 可执行的外部参考插件 | 只在生产出现的行为 |

`:extension:api` 必须保持 Android-free、KMP-compatible。内置插件与 APK 插件必须使用相同的 `HookSpec`、能力检查、超时、大小/并发限制、取消、健康状态和错误 envelope。

## 顺着一次调用排查

遇到 hook 问题时，按这个顺序追踪：

1. 宿主选择类型化 `HookSpec<Request, Result>`。
2. `ExtensionCatalog` 查找已启用且 schema version 兼容的实现。
3. `CapabilityPolicy` 检查宿主支持、manifest 声明和用户 grant。
4. `InvocationPolicy` 施加超时、payload 和并发限制。
5. 内置 adapter 或 Android transport 执行请求。
6. runtime 解码类型化结果，并记录成功/失败健康状态。
7. 宿主持有的 importer 在写入前校验引用、数量、大小和允许字段。

调用方不能临时传入一组“已授权能力”。插件不能返回 Room 实体、`DataSource`、播放器对象或原始凭据。

## 安全修改契约

每个 hook 有独立的正整数 schema version，插件 API 另有 major version。

兼容改动通常是新增带默认值的可选序列化字段。破坏字段兼容需要新 hook schema；平台整体不兼容才需要新 API major。refresh/close reason 等开放值继续使用字符串，避免新值破坏旧插件。

每次修改契约都要：

1. 更新 API 模型、catalog 和 serializer；
2. 增补 golden JSON 与协商测试；
3. 让内置和 Android transport 通过同一行为测试；
4. 增补或更新宿主 importer/调用点；
5. 明确该 hook 是“仅定义”还是“已经接通”；
6. 同步更新开发者和维护者的中英文文档。

API major 不同、未知必要 capability、不支持的必要 hook schema 都应拒绝。未知可选 JSON 字段应忽略。禁止恢复任意 payload 的运行时强转。

## 外部插件生命周期

正常生命周期如下：

```text
已安装 -> 已发现 -> 已检查 -> 用户信任 -> 已启用
                              \-> 已拒绝 / 不兼容
已启用 -> 已禁用 -> 已启用
已启用 -> 连续失败 -> 异常隔离
签名或稳定 ID 变化 -> 已禁用 -> 重新作出信任决定
```

发现流程只查询 `com.m3u.extension.action.BIND_EXTENSION`，再显式绑定解析出的 component；service 必须要求 `com.m3u.permission.BIND_EXTENSION_HOST`。禁止增加 `QUERY_ALL_PACKAGES`、`DexClassLoader` 或进程内 APK 加载。

外部插件仍受 `PreferencesKeys.EXTERNAL_EXTENSIONS` 保护。关闭开关会注销并关闭外部 transport，但保留信任。生命周期操作在 repository 中串行化，避免 UI、恢复、刷新和 worker 同时注册同一插件。

信任固定到 package/component 身份和签名证书。首次启用会展示身份及每项申请能力。恢复时取旧 grant 与新 manifest 的交集：新增可选能力保持未授予；新增必要能力会阻止恢复。签名或 extension ID 改变时必须重新作出信任决定。

显式重新授权会再次校验签名和 extension ID，再更新当前申请且宿主支持的能力。它不能重复注册 runtime，原本已禁用的插件也必须保持禁用。手机授权内容必须可滚动；TV 使用独立的遥控器界面，不能截断任何能力理由。拒绝原因必须让用户看见。

## 故障隔离与诊断

取消要贯穿 runtime 和 transport。多插件贡献点使用 supervisor 行为：保留取消，但把单个插件的普通异常转成空结果/失败贡献。连续失败只能把该插件变为 `UNHEALTHY`。

有效状态统一为 `ENABLED`、`DISABLED`、`INCOMPATIBLE` 和 `UNHEALTHY`。

禁止记录完整 envelope 或响应 body。诊断导出采用正向白名单：宿主 API、package/service 身份、固定证书、版本/状态、capability/hook ID、失败次数和设置数量汇总。禁止导出自由 metadata、设置 key/value、payload、broker 流量、响应 body 或异常文本。

“清除数据”删除插件设置、加密 secret handle，以及 `m3u-extension-epg://<extension-id>/` 下的插件 EPG；保留订阅、频道、收藏、隐藏状态和播放历史。

## 凭据与网络

`CredentialVault` 使用 Android Keystore 支持的 AES-GCM 加密 secret，只暴露不透明 handle。数据库和备份中永远不能出现明文 token。密钥丢失或恢复密文无法解密时，删除不可用凭据并要求重新认证。

所有外部网络请求都经过 `HostNetworkBroker`，以下检查缺一不可：

- 目标是账号 origin 或用户批准的额外 origin；
- 删除插件提供的认证 header；
- 按引用注入宿主持有的 secret；
- 每个重定向目标都重新检查；
- 限制超时、响应大小和并发；
- 登录 capture 把 header/JSON-pointer 值写入 vault，只返回脱敏数据和 handle。

不得增加读取原始 secret 的逃生口。

## 宿主持有的 Importer

解码之后，importer 是安全边界。它在写入前检查 stable reference、结果数量/大小、允许字段和数据所有权。

- Provider 订阅保存通用 provider/account 身份和 credential handle；UI/repository 禁止硬编码 Emby ID。
- Search 把不透明引用映射到已有且未隐藏的频道；未知引用直接丢弃。
- Metadata 只能通过窄数据方法修改宿主批准的标题/分类等字段。
- EPG 校验引用、时间和大小边界，再写入插件隔离的数据源。
- Settings 按 section 隔离；schema version 改变时清除旧值与 secret。
- Provider refresh 在 WorkManager 中运行，具备联网约束、重试、进度、取消和配额。
- Playback session 打开后立即保存，正常 close 后删除，重启后幂等清理。

备份可以包含账号元数据和稳定播放引用，绝不能包含 token。恢复的 provider playlist 必须重新认证。

## 当前接通情况

| 范围 | 状态 |
| --- | --- |
| 内置 Emby/Jellyfin 订阅、刷新、播放、close | 已接通 |
| 通用 provider 持久化、WorkManager 刷新、session 恢复 | 已接通 |
| APK 发现、信任、IPC、取消、健康、broker、恢复 | 已接通，受开发者开关保护 |
| 手机/TV 插件管理与声明式设置 | 已接通 |
| 手机端搜索贡献 | 已接通 |
| 通用 provider 刷新中的 metadata 与 EPG | 已接通 |
| 所有旧 M3U/Xtream 导入路径中的 metadata/EPG | 尚未补齐 |
| 面向所有用户发布的外部 provider 流程 | 尚未发布 |

必须如实维护这张表。`:extension:api` 中存在序列化类型，不代表已经有生产宿主调用点。

## 发布门槛

移除开发者开关前，至少要有以下验证证据：

- golden 序列化、协商、错误类型、大小/超时/并发限制、取消、能力拒绝、隔离和脱敏；
- Room migration、凭据丢失、备份恢复重认证、幂等导入、后台刷新和重启清理；
- origin/redirect 拒绝、认证 header 移除、签名不符和能力拒绝；
- 独立安装参考 APK 的发现、绑定、handshake、大 PFD 结果、取消、Binder death、崩溃和版本不兼容；
- 手机与 TV 的发现、授权详情、遥控器/全行交互、启禁用、重新授权、恢复和可见错误；
- M3U、EPG、Xtream、普通播放与 DLNA 回归。

只有当独立安装的兼容 APK 能安全发现和启用、所有公开 hook 都有宿主持有的 importer、且插件崩溃、恶意请求或凭据失败不会破坏宿主数据或终止主进程时，平台才达到开放标准。
