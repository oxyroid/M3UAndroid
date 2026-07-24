# 契约术语

[English](glossary.md) · [插件开发指南](../README.zh-CN.md)

| 术语 | 插件代码中的用途 |
| --- | --- |
| `applicationId` | 更新时保持稳定的身份值之一。 |
| Service 类 | 插件实现的 `TypedExtensionService`。 |
| 签名证书 | 更新时保持不变的发布者身份。 |
| `ExtensionId` | `ExtensionManifest` 中填写的稳定 ID。 |
| `ExtensionManifest` | 声明插件版本、Hook、capability、设置与显示信息。 |
| `HookSpec<Request, Result>` | 提供请求类型、结果类型、Hook ID 与当前 Schema Version。 |
| `ExtensionHookDeclaration` | 把已实现的 `HookSpec` 加入 `ExtensionManifest`。 |
| Capability | 表示 Hook 需要的操作。该 Hook 必须声明它，Manifest 必须申请它，用户也必须批准它。 |
| `ExtensionCallContext` | 提供调用 ID、当前 Hook 的有效 capability 与已保存插件设置。 |
| `ExtensionSettingsSnapshot` | 包含普通设置值，以及敏感字段对应的 credential handle。 |
| `CredentialHandle` | 指向本次提交或已保存 secret 的不透明引用。它没有读取接口；获准的 Hook 可以把它作为 `SecretReference` 交给 Broker。 |
| 已批准 Origin | 当前 Hook 可以通过 Broker 访问的精确 HTTP 或 HTTPS Origin。 |
| 网络 Origin 设置 | `networkOrigin = true` 的文本设置。用户保存时明确批准当前 Origin。 |
| Broker 作用域 | 只属于一个插件、一个 Hook、一次调用和一组 Origin 的短期权限；部分作用域还绑定 Provider 账号。 |
| `BrokerValue` | Broker 请求和 Provider 播放 Header 使用的普通值，或受作用域限制的 Secret/Context 引用。 |
| `ExtensionHostNetworkBroker` | 为受支持的 Hook 发送经过检查的 HTTP 请求；不支持 `Discover`。 |
| `ProviderKind` | 一个 Provider 类型的稳定小写 ID。 |
| `PlaybackReference` | 由 Provider 提供、M3UAndroid 保存，并在播放时传回插件的值。 |
| `ExtensionBackgroundTaskDeclaration` | 声明一个周期任务；插件启用并获授权时，M3UAndroid 通过 WorkManager 调度它。 |

插件身份和版本规则见[准备发布或更新](compatibility.zh-CN.md)。
