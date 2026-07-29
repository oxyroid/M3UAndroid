# 外部 APK 插件威胁模型

[English](threat-model.md) · [维护者指南](README.zh-CN.md)

## 当前结论

外部 APK 插件继续放在需要主动开启的开发者开关后。宿主已经隔离插件代码、凭据、网络访问
和落库结果，但这些措施还不足以让该能力默认开启。

默认开放前，维护者必须对[仍未解决的问题](#仍未解决的问题)逐项做出产品决策。尤其需要注意：
当前 Broker 允许 HTTP，没有防御 DNS 重绑定，也无法阻止插件与用户已经批准的服务端串谋。

## 这份模型覆盖什么

外部插件的整个 Android 包都不可信，包括它的 Service、进程、私有存储和所有返回值。
内置插件则是可信宿主代码：它与外部插件共用类型化 Hook 契约，但属于宿主的可信代码，
不会获得外部 Transport 提供的进程隔离。

本模型假定 Android 的应用进程、私有存储、Binder、包签名和 Keystore 边界正常工作。
已 Root 或已被攻破的设备、被攻破的宿主 APK，以及 Android 平台漏洞不在本模型范围内。

## 宿主要保护什么

| 资产 | 必须避免的后果 |
| --- | --- |
| Provider 密码、Token 和 Secret 设置 | 插件读取或保存明文凭据 |
| 插件身份、证书固定值、授权、批准 Origin 和启用状态 | 一个包静默继承另一个包的信任 |
| 账号、播放列表、频道、EPG、Metadata 和播放 Session | 插件修改不属于本次调用的数据 |
| 搜索词、频道信息、账号元数据和播放引用 | Hook 获得完成任务并不需要的用户数据 |
| 宿主进程、Binder 端点、网络 Broker 和后台配额 | 缓慢、崩溃或恶意插件拖垮应用 |
| 诊断数据 | 日志或导出内容泄露凭据和认证材料 |

## 信任边界

```text
不可信 APK Service
        │  Binder 控制消息 + PFD JSON
        ▼
Android Transport ──► 类型化 Runtime ──► Feature Repository/Importer ──► Room/播放器/界面
        │                    │
        │                    └── 可撤销的调用作用域 ──► HostNetworkBroker
        │                                                   │
        └── Package/Service/UID/证书检查                     └── 已批准 Origin
                                                            │
                                                     CredentialVault
```

插件只提交候选数据。只有宿主可以把结果应用到 Room、播放、界面或计划任务。网络和凭据访问
只在当前调用内有效，调用结束后立即撤销。

## 每个 Hook 会透露什么

批准 Capability 也意味着批准一组数据披露。增加 Request 字段、Capability 或 Broker
作用域时，都要重新检查下表。

| Hook | 插件可见的数据 | 网络作用域 | 宿主接收结果的边界 |
| --- | --- | --- | --- |
| Provider discover | Locale Tag | 无 | 校验 Descriptor 和设置 Schema |
| Provider validate | Provider Kind、普通设置值和不透明凭据句柄 | 用户提交的 Provider Origin 登录作用域 | 消费一次性认证证据并创建账号 |
| Provider refresh | 账号元数据、不透明凭据句柄和刷新原因 | 该账号的 Origin | 校验数据源归属并在事务中导入频道 |
| Playback resolve | 账号元数据、不透明凭据句柄、播放引用和偏好 | 该账号的 Origin | 播放前校验 URL、Header、Media Source 和 Session |
| Playback close | 账号、播放引用、Session 标识和关闭原因 | 该账号的 Origin | 校验账号与 Session 归属，并幂等关闭 |
| Settings schema | Locale Tag 和界面类型 | 声明后可用已批准的 Manifest Origin 与设置 Origin | 校验并渲染声明式 Schema |
| EPG refresh | Source ID、时间范围，以及可选账号/凭据句柄 | 账号 Origin 或已批准 Origin | 导入前校验时间范围和归属 |
| Metadata enrichment | 稳定频道引用、标题、分类，以及可选账号/凭据句柄 | 账号 Origin 或已批准 Origin | 只应用本次请求中频道的 Patch |
| Search | 搜索词、结果上限，以及可选账号/凭据句柄 | 账号 Origin 或已批准 Origin | 只接收有效的账号 ID 和远端 ID |
| Background task | 已声明的 Task ID、输入和重试次数 | 声明后可用已批准 Origin | 只在宿主调度和 Runtime 限制内运行已声明任务 |

不透明句柄和稳定引用不是 Secret，但仍可用于关联用户行为。不能为了方便把它们加入无关 Hook。

## 假定攻击者能做什么

外部插件可能：

- 提交误导性 Manifest，或冒用其他插件的 Extension ID；
- 通过不同签名证书更新；
- 返回畸形、深度过大、超限、迟到、重复或内容虚假的结果；
- 忽略取消、保留 Host Bridge、阻塞、崩溃或反复重连；
- 控制一个已批准 Origin 的服务端，或与该服务端串谋；
- 保存 Hook 为完成任务而合法收到的普通用户数据；
- 消耗自身进程的 CPU、内存、存储或其他设备资源；
- 向用户展示误导性的名称和说明。

## 已实现的控制

| 威胁 | 当前控制 | 证据 |
| --- | --- | --- |
| 冒用 Package 或 Extension ID | 连接时重新校验 Package、Service、UID 和证书；固定用户审阅过的证书；一个 Extension ID 只允许一个可信所有者；签名变化后停用 | `ExtensionTrustStoreTest`、`ExtensionPluginRepositoryLifecycleTest`、`HostileExternalExtensionIpcTest` |
| 拖垮宿主进程 | 外部代码运行在其他进程；限制调用、流大小、嵌套深度、截止时间和并发；处理取消、Binder Death 和迟到回调 | `ExtensionRuntimeTest`、`ExtensionConnectionStateTest`、`HostileExternalExtensionIpcTest` |
| 泄露凭据 | 使用 Keystore + AES-GCM 保存 Secret；只传不透明句柄；只在 Broker 内注入认证；对捕获的认证材料脱敏 | `CredentialVaultTest`、`HostNetworkBrokerSecurityTest`、`ExtensionHostBridgeTest` |
| 未批准的网络访问 | 按 Hook 授予网络；要求 Scheme/Host/Port 完全匹配；限制重定向、Header、响应大小、截止时间、并发和累计请求/响应预算 | `ExtensionNetworkOriginContractTest`、`HostNetworkBrokerSecurityTest`、`InvocationBudgetPropagationTest` |
| 破坏持久化数据 | 解码类型化结果，检查请求归属和限制，只通过宿主 Importer 与事务应用结果 | Provider 与 Contribution Repository/Importer 测试 |
| 停用或调用结束后继续访问 | 撤销 Runtime 注册、连接、Broker 作用域、保留的 Bridge、计划任务和宿主持有的插件数据 | `ExtensionPluginRepositoryLifecycleTest`、`ExtensionHostBridgeTest`、`HostileExternalExtensionIpcTest` |
| 备份意外泄密 | 备份排除插件信任、Secret 设置、Provider 凭据和 Token；恢复后的 Provider 必须重新认证 | Provider 备份/恢复与凭据测试 |

恶意 IPC 的签名用例使用真实发现的 Service 和 PackageManager 返回的真实证书，并与测试注入的
过期持久化证书固定值比较。它验证了生产 Repository 的停用和审阅后重新固定流程；它没有安装
一个不同签名的替换 APK。

## 仍未解决的问题

以下是当前限制，不是已经存在的防护：

1. **传输机密性尚未决定。** 当前同时接受 HTTP 和 HTTPS，因为本地 IPTV 服务常用 HTTP。
   HTTP 流量可能被网络中的其他设备读取或篡改。
2. **缺少解析后地址策略。** Scheme/Host/Port 完全匹配，仍不能阻止 DNS 重绑定，也不能阻止
   域名解析到 Loopback、Link-local 或私有地址。
3. **已批准服务端可以与插件串谋。** 响应脱敏能防止常见的意外泄露，但服务端仍可把敏感信息
   编码进看似普通的响应。脱敏只是纵深防御，不是机密性证明。
4. **Origin 批准范围较宽。** 当前批准覆盖该 Origin 下允许的 HTTP Method 和 Path，
   其中可能包括有副作用的端点；它不是逐端点权限模型。
5. **包准入不等于开发者信誉。** 证书连续性只能证明更新由同一签名者提供，不能证明身份、
   信誉、证书撤销状态或公共信任链。
6. **无法保证不存在任何侧信道。** 拒绝插件包直接使用网络权限，可以保护宿主支持的调用链，
   但不能证明其他 Component、其他 App、设备通道或已批准服务端不能中转数据。
7. **撤销无法抹掉已有副本。** 清除宿主数据不能删除插件此前保存在自身私有存储中的信息。
8. **类型正确的数据仍可能虚假。** 归属和形状检查可以阻止越界写入，不能识别授权范围内的
   虚假或低质量 Metadata。
9. **外部进程隔离不适用于内置插件。** 内置调用超时可以限制宿主等待时间，但内置代码仍运行在
   可信宿主进程内。

## 默认开放前的要求

只有以下各项都有明确且留档的结论，才可以移除开发者开关：

- 选择并测试 HTTPS/LAN 策略，包括如何向用户展示 HTTP 风险；
- 定义 Loopback、Link-local、私有地址和 DNS 变化目标的解析后地址规则；
- 明确接受“已批准服务端串谋”这一产品风险，或把受保护响应的解析移到宿主；
- 把每个 Hook 的数据披露和 Capability 当作隐私决策审阅，并提供匹配的授权文案；
- 定义首次固定证书之外的包准入与证书撤销预期；
- 通过[当前状态与发布门槛](status-and-release.zh-CN.md#开放外部插件之前)中的其余外部插件门禁。

上述决策完成前，内置 Provider 链路可以独立发布，外部 APK 插件继续保持开发者预览。
