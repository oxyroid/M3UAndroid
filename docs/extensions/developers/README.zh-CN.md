# Android 插件开发者指南

[English](README.md)

本文说明当前已经实现的本地 Android 插件契约。外部插件目前仍属于开发者功能：用户通过 Android 系统安装器安装 APK，开启“外部扩展”，查看插件身份和申请的能力后，显式确认信任。

## 运行与信任模型

插件以独立 APK、独立进程运行。宿主通过 action `com.m3u.extension.action.BIND_EXTENSION` 发现导出的 bound service，绝不会使用 `DexClassLoader` 把插件代码载入宿主进程。该 service 必须要求权限 `com.m3u.permission.BIND_EXTENSION_HOST`。

首次启用时，宿主会展示包名、插件名称、开发者元数据、语义化版本、申请的能力和签名证书 SHA-256，并固定用户接受的证书。后续升级若证书不一致，插件会自动禁用，必须由用户重新确认。

即使升级包签名相同，也不能静默扩大权限。宿主会取旧 grant 与新 manifest 的交集；新增必要能力会阻止自动恢复并要求用户重新确认，新增可选能力在重新授权前保持未授予状态。

控制面使用少量 AIDL，JSON 数据通过 `ParcelFileDescriptor` 流传输。流中承载 `SerializedExtensionEnvelope` 或 `SerializedExtensionResult`，避免 Binder 事务大小上限。传输层提供 handshake、manifest、invoke、cancel 和 health 操作。

## 模块与最小 service

契约位于 `:extension:api`，Android service 基类位于 `:extension:sdk-android`。`:testing:extension-reference` 是可运行的参考实现和一致性测试样例。

在 manifest 中声明：

```xml
<service
    android:name=".MyExtensionService"
    android:exported="true"
    android:permission="com.m3u.permission.BIND_EXTENSION_HOST">
    <intent-filter>
        <action android:name="com.m3u.extension.action.BIND_EXTENSION" />
    </intent-filter>
</service>
```

继承 `ExtensionService` 并提供 `ExtensionTransport`。transport 负责暴露 manifest 和处理类型化 envelope。仅当 hook 需要宿主网络代理时，才覆盖 SDK service 中带 broker 的 `invoke`。

## Manifest 与兼容性

`ExtensionManifest` 包含：

- 全小写、稳定的 `ExtensionId`；
- 展示名称与插件语义化版本；
- 支持的插件 API 范围；
- 每个 hook 的唯一声明，包括 schema version 和必要能力；
- 必要或可选的能力申请，以及面向用户的申请理由；
- 可选的声明式设置 schema，以及 `developer` 等诊断元数据。

当前插件 API 为 `1.0`，已定义 hook 的 schema 均为版本 `1`。API major 不一致会直接拒绝；同 major 下逐个协商 hook schema。未知的必要能力或不受支持的必要 hook schema 会使插件进入不兼容状态。JSON 解码会忽略未知可选字段，但插件不应假设宿主会原样保留这些字段。

必须使用已发布的 `HookSpec<Request, Result>` 序列化器，不要强转任意 payload，也不要另造 wire 格式。

## Hook

当前类型化目录定义了：

- 订阅 provider 发现、校验、内容刷新、播放解析和播放 session 关闭；
- 设置 schema 贡献；
- EPG 刷新；
- 频道元数据增强；
- 搜索 provider；
- 后台任务。

“已有契约”不等于“所有宿主入口都已接通”。订阅和播放 hook 已为内置 Emby/Jellyfin provider 提供生产调用链。外部插件的生命周期、传输、取消、健康检查和后台任务已接通。搜索贡献已接入手机端：只有当结果中的不透明 `stableReference` 能映射到宿主已有且未隐藏的频道时才会展示。provider 刷新现在也会调用元数据与 EPG 贡献者：元数据 patch 只能更新宿主批准的标题/分类字段，EPG 结果经过校验后进入宿主隔离的数据源。这两个 importer 目前用于通用 provider playlist，尚未覆盖所有旧 M3U/Xtream 导入路径。设置契约、持久化、调用上下文及手机/TV 声明式 renderer 已经接通。两端都支持布尔、单选、文本、数字和 secret 字段；TV renderer 以遥控器为先，保存设置并刷新配置后会保持焦点。

不要返回数据库实体、播放器对象、`DataSource`、密码或 token。插件只返回声明式数据和稳定的不透明引用；校验、持久化、导入和播放对象构造都归宿主所有。

## 能力与凭据

能力由插件在 manifest 中申请，再由宿主策略和用户授权共同决定。声明能力不等于获得能力。hook 只能使用自身声明且宿主批准的能力。

外部插件不能获取明文凭据。凭据以不透明 handle 表示。网络请求必须经过宿主 broker：只允许访问账号 base origin 或额外获批的 origin；移除插件自行提供的认证 header；注入宿主持有的 secret；限制重定向、超时、响应大小和并发；返回脱敏数据。登录 capture rule 可以从 header 或 JSON pointer 捕获值并写入宿主 vault，只把 handle 返回插件。

设置项使用限定的 `section/field` key。`SECRET` 字段不得声明明文默认值。宿主用 Android Keystore 支持的 AES-GCM 存储加密值，并且只在 `ExtensionSettingsSnapshot` 中传递它的 `CredentialHandle`。section 的 schema version 改变时，宿主会丢弃该 section 的值并删除已保存 secret。插件必须把值或 handle 缺失视为正常状态，并且只使用宿主快照中已经解析的默认值。

设置 UI 绝不会把已保存 secret 重新读回输入框，只显示“已配置”标记，并允许用户输入替换值或清除 handle。标签和说明来自协商后的 settings schema，因此插件应按请求的 locale 提供适合手机与 TV 阅读的简洁文案。

必须读取原始密码/token、自行加密凭据或绕过 broker 的插件与本平台不兼容。

## 调用约束

- 将 invocation ID 视为唯一值，并及时传播取消。
- 请求和响应必须符合声明的 schema 及宿主大小限制。
- refresh reason、session close reason 等字段按可扩展字符串处理，容忍兼容宿主新增的值。
- close、cleanup、refresh 和 retry 必须幂等。
- 使用稳定错误 envelope，不要泄漏异常详情或敏感信息。
- 不要假设调用串行；宿主会施加并发上限和超时。

## 开发验收清单

发布测试 APK 前：

1. 使用参考插件/一致性样例验证发现、handshake、manifest 解码、invoke、cancel 和 health。
2. 测试不兼容 API 范围和不支持的 hook schema。
3. 测试进程死亡、Binder death、超时、超大输出和连续失败。
4. 确认日志与错误中没有密码、token、Authorization header 或捕获的 secret。
5. 确认未获宿主批准的 origin 和跨域 redirect 都会失败。
6. 安装同签名升级包验证信任保留，再用不同签名验证插件会被禁用。

在宿主功能仍受开发者开关保护期间，不应把 APK 宣传为默认开放支持的插件。
