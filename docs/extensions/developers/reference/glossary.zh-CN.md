# 契约术语

[English](glossary.md) · [插件开发指南](../README.zh-CN.md)

| 术语 | 插件代码中的用途 |
| --- | --- |
| `applicationId` | 更新时保持稳定的身份值之一。 |
| Service 类 | 插件实现的 `TypedExtensionService`。 |
| 签名证书 | 更新时保持不变的发布者身份。 |
| `ExtensionId` | `ExtensionManifest` 中填写的稳定 ID。 |
| `ExtensionManifest` | 声明插件版本、Hook、capability、设置与显示信息。 |
| `HookSpec<Request, Result>` | 提供 request 类型、result 类型、Hook ID 与当前 schema version。 |
| `ExtensionHookDeclaration` | 把已实现的 `HookSpec` 加入 `ExtensionManifest`。 |
| Capability | 声明 Hook 需要用户授权的操作。 |
| `ExtensionCallContext` | 提供当前调用的 invocation ID、已授权 capability 与已保存插件设置。 |
| `ExtensionSettingsSnapshot` | 包含普通设置值，以及敏感字段对应的 credential handle。 |
| `CredentialHandle` | Broker value 使用的凭据引用，不是凭据明文。 |
| `BrokerValue` | Broker request 中的普通值、凭据引用、组合值或编码值。 |
| `ExtensionHostNetworkBroker` | 为当前 handler 调用发送 provider HTTP request。 |
| `ProviderKind` | 一个 provider variant 的稳定小写 ID。 |
| `PlaybackReference` | 由 provider 提供、M3UAndroid 保存，并在播放时传回插件的值。 |

## Service 声明与插件契约

[`AndroidManifest.xml`](../../../../samples/hello-extension/src/main/AndroidManifest.xml) 包含 Service 声明。该 Service 返回的 Kotlin `ExtensionManifest` 包含 M3UAndroid 契约。

Hello 实现包含这两部分：

- [`AndroidManifest.xml`](../../../../samples/hello-extension/src/main/AndroidManifest.xml)
- [`HelloExtensionService.kt`](../../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt)

更新时保持 application ID、Service 类、签名证书与 `ExtensionId` 稳定。参见[准备发布或更新](compatibility.zh-CN.md)。
