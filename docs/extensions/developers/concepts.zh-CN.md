# 理解插件模型

[English](concepts.md) · [插件开发指南](README.zh-CN.md)

理解 M3UAndroid 插件只需要掌握四个部分：

1. Android service 让宿主能够找到并连接插件；
2. manifest 描述插件的身份和能力；
3. Hook 使用类型化契约接收请求并返回结果；
4. M3UAndroid 校验结果后再应用到宿主。

## Service 是插件入口

在插件 APK 的 `AndroidManifest.xml` 中声明一个 service：

```xml
<service
    android:name=".ExampleExtensionService"
    android:exported="true"
    android:permission="com.m3u.permission.BIND_EXTENSION_HOST">
    <intent-filter>
        <action android:name="com.m3u.extension.action.BIND_EXTENSION" />
    </intent-filter>
</service>
```

该 service 提供一个 `ExtensionTransport`：

```kotlin
class ExampleExtensionService : ExtensionService() {
    override val transport: ExtensionTransport = ExampleTransport
}
```

完整可运行写法请参考 [`ReferenceExtensionService.kt`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt)。

## `ExtensionManifest` 描述一个稳定的插件

宿主会在调用任何 Hook 之前读取 `ExtensionManifest`。

| 字段 | 含义 |
| --- | --- |
| `id` | 稳定的全小写标识，例如 `com.example.guide` |
| `displayName` | M3UAndroid 中显示的插件名称 |
| `extensionVersion` | 插件的语义化版本 |
| `apiRange` | 当前 APK 接受的宿主插件 API 版本范围 |
| `hooks` | APK 已实现的 Hook ID 与 schema version |
| `capabilities` | 向用户申请的能力，以及容易理解的申请理由 |
| `settingsSchema` | 由宿主展示的可选设置 |
| `metadata["developer"]` | 授权时展示的开发者名称 |

当前宿主 API 为 `1.0`，Hook schema 均为版本 `1`。升级 APK 时应保持插件 ID 不变，并更新 `extensionVersion`。

Hook 声明的必要 capability 必须同时出现在 manifest 的 capability 申请中。只有能够返回合法结果的 Hook 才应该写入 manifest。

## Hook 是类型化函数

每个公开的 `HookSpec<Request, Result>` 都包含 Hook ID、schema version，以及请求和结果的 serializer。插件应直接使用这些 serializer，不要另外定义一套 JSON 格式。

分发代码的基本写法如下：

```kotlin
val spec = HostHookSpecs.SearchProvider
val input = json.decodeFromJsonElement(spec.requestSerializer, envelope.payload)
val output = SearchProviderResult(/* ... */)

return SerializedExtensionResult(
    invocationId = envelope.invocationId,
    extensionId = manifest.id,
    hook = spec.hook,
    schemaVersion = spec.schemaVersion,
    payload = json.encodeToJsonElement(spec.responseSerializer, output),
)
```

结果中的 invocation ID、extension ID、Hook 和 schema version 必须与请求一致。结果只能包含 payload 或 `ExtensionError` 其中一个。

## 结果由宿主应用

Hook 返回的是“贡献结果”，不是直接操作数据库或播放器的命令。例如：

- 搜索返回 stable reference，由宿主映射到已有频道；
- 元数据返回范围受限的 patch，由宿主校验；
- EPG 返回节目信息，由宿主导入；
- 播放 Hook 返回播放源描述；当前只有内置 provider 接通了这条产品链路。

因此插件无需依赖 M3UAndroid 的 Room 实体或 UI model。开发前请查看 [选择 Hook](hooks.zh-CN.md)；当前预览版本仍有多条贡献链路没有完整接通。

## 设置

需要在 M3UAndroid 中展示的设置使用 `ExtensionSettingSchema` 描述。当前支持文本、密码、布尔、数字和单选字段。

Manifest 中的 schema 显示在 `manifest` 分组；settings-schema Hook 可以增加其他命名分组。Hook 请求通过 `ExtensionSettingsSnapshot` 接收当前设置，key 的形式例如 `manifest/enabled` 或 `playback/quality`。

密码字段不能有明文默认值，它在 snapshot 中对应 `CredentialHandle`；普通字段位于 `values`。用户可以清除设置，schema 升级也可能重置旧值，因此插件必须允许字段缺失。

## Capability

Manifest 中的每项 capability 申请包括：

- capability ID；
- 写给启用插件的用户看的申请理由；
- 该能力是否为必要能力。

宿主可以授予能力、让可选能力保持未授予，或拒绝不兼容插件。预览版 runtime 会检查插件在 `requiredCapabilities` 中列出的能力；每个 Hook 的宿主最低能力尚未冻结。后续 APK 新增能力时，用户可能需要重新授权。

## 网络与凭据

Android SDK 提供 `ExtensionHostNetworkBroker`，用于由宿主代为执行网络请求。凭据只以 handle 表示，Hook 不会收到 secret 原值。

外部插件的完整登录和 provider 账号链路目前还不能供第三方稳定使用，尤其是“账号创建前登录”和“把插件设置中的密码用于 broker 请求”尚未接通。在 [provider Hook](hooks.zh-CN.md#subscription-provider) 标记为可用前，应把依赖 broker 的 provider 开发视为实验功能。

## 取消与错误

- 使用 `InvocationId` 追踪任务，并在收到 `cancel` 时停止对应工作。
- refresh、close、retry 和 cleanup 应具备幂等性。
- 插件必须能处理并发调用。
- reason 字段是开放字符串；新版宿主可能发送插件从未见过的值。
- `ExtensionError` 只写安全且可执行的信息；错误和日志中不能包含凭据或完整响应正文。

下一步：[选择 Hook](hooks.zh-CN.md)。
