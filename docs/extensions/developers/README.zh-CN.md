# 开发 Android 插件

[English](README.md)

本文写给准备为 M3UAndroid 开发插件 APK 的开发者。外部插件目前仍是开发者功能，在默认开放前契约仍可能调整。

## 先记住这张图

插件是一个独立 Android 应用，其中包含一个 bound service。M3UAndroid 找到该 service，读取 manifest，再调用 manifest 声明的 hook。

```text
你的 APK                         M3UAndroid
--------                         ----------
声明 hook   -- 类型化 JSON --->  校验结果
返回数据    <--- 请求 ---------  保存数据 / 构造播放
使用 handle -- broker 请求 --->  保管凭据 / 执行网络访问
```

插件代码不会进入 M3UAndroid 进程，也拿不到 Room 实体、播放器对象、密码或 token。插件只返回契约数据，是否导入以及怎样保存由宿主决定。

## 从参考插件开始

最快的可运行样例是 [`:testing:extension-reference`](../../../testing/extension-reference)。它已经包含合法 service、manifest、类型化 hook 分发、取消处理、设置、搜索、元数据、EPG 和后台任务。

SDK 目前还没有作为稳定 Maven artifact 发布。现阶段请在本仓库内构建参考插件，或从同一源码版本引入 extension 模块。公开 artifact 和兼容策略完成后，第三方分发才算稳定支持。

创建最小插件：

1. 在本仓库开发时依赖 `:extension:sdk-android`；它会暴露 service 所需的 API 与 transport 类型。
2. 在 `AndroidManifest.xml` 声明导出的 service：

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

3. 继承 `ExtensionService` 并提供一个 `ExtensionTransport`：

   ```kotlin
   class MyExtensionService : ExtensionService() {
       override val transport: ExtensionTransport = MyExtensionTransport
   }
   ```

4. 为 transport 提供 `ExtensionManifest`，只实现 manifest 声明的 hook。
5. 用 Android 系统安装器安装 APK，在 M3UAndroid 中开启“外部扩展”，核对证书和申请的权限，再启用插件。

宿主只按 service action 发现插件。不要申请 `QUERY_ALL_PACKAGES`，也不要期待宿主通过 `DexClassLoader` 加载你的类。

## Manifest 要写什么

可以把 `ExtensionManifest` 理解为插件的身份证和权限申请表：

| 字段 | 应填写的内容 |
| --- | --- |
| `id` | 全小写、永不随版本改变的插件 ID |
| `displayName` | 展示给用户的名称 |
| `extensionVersion` | 插件的语义化版本 |
| `apiRange` | 插件支持的宿主扩展 API 范围 |
| `hooks` | 实现的每个 hook 及其 schema version |
| `capabilities` | 必要/可选权限，每项都写清原因 |
| `settingsSchema` | 可选的声明式设置 |
| `metadata["developer"]` | 授权时展示的开发者名称 |

当前 API 是 `1.0`，hook schema 均为版本 `1`。API major 不同会被拒绝；存在宿主不支持的必要 hook 或未知必要 capability 时，插件会被判定为不兼容。

请使用已发布的 `HookSpec<Request, Result>` serializer。它就是 wire 契约；不要另造 JSON 结构，也不要强转任意 payload。

## 选择 Hook

| 目标 | Hook | 当前宿主接入情况 |
| --- | --- | --- |
| 增加订阅 provider | discover、validate、refresh | 内置 Emby/Jellyfin 已走完整链路；外部 provider 仍在补齐 |
| 解析播放并关闭服务端 session | playback resolve/close | 内置 provider 已有生产链路 |
| 增加设置 | settings schema | 手机和 TV 均已渲染 |
| 增加搜索结果 | search provider | 已接手机端；结果必须指向宿主已有频道 |
| 改善频道标题/分类 | metadata enrichment | 已接通用 provider 刷新 |
| 增加节目单 | EPG refresh | 已接通用 provider 刷新 |
| 执行定时工作 | background task | 已接宿主配额与取消机制 |

“契约已经定义”不代表“所有旧 M3U/Xtream 路径都已调用”。使用某个 hook 前请先看最后一列。

搜索、元数据和 EPG 结果使用 `stableReference`。它只是返回宿主已有数据的不透明桥梁。宿主会忽略无法识别的引用，绝不会直接把它变成数据库记录或可播放对象。

## 权限、升级与重新授权

在 manifest 中声明 capability 只是提出申请。只有宿主策略允许且用户授权后，hook 才能使用它。

首次启用时，M3UAndroid 会显示包名、开发者、版本、签名证书 SHA-256，以及每项 capability 和申请理由，并固定用户接受的证书。

后续升级规则：

- 签名相同且没有新增能力：可以恢复原信任；
- 新增可选能力：保持未授予，直到用户重新授权；
- 新增必要能力：停止自动恢复，直到用户重新授权；
- 签名不同或 extension ID 改变：禁用插件，要求重新作出信任决定。

权限理由要写给用户看。“从你配置的服务器读取节目单”是清楚的；“需要 `epg.read`”不是。

## 凭据与网络请求

所有 secret 都由宿主持有。插件只会收到不透明的 `CredentialHandle`，不会看到其中的密码或 token。

网络请求经过 `HostNetworkBroker`。它会：

- 只允许账号 origin 和另行获批的 origin；
- 删除插件自行添加的认证 header；
- 按引用注入宿主持有的 secret；
- 对每次重定向重新检查 origin；
- 限制超时、响应大小和并发；
- 从 header 或 JSON pointer 捕获登录值，只返回保存后的 handle。

如果插件必须读取原始密码/token、自行加密凭据或绕过 broker，它就不兼容本平台。

## 设置

使用 `ExtensionSettingSchema` 描述设置，不要为宿主配置另做 Activity。手机和 TV 支持布尔、单选、文本、数字和 secret 字段。

设置 key 按 `section/field` 隔离。`SECRET` 字段不能有明文默认值。宿主使用 Android Keystore 支持的加密存储，hook 调用中只出现 handle。UI 只提供“已配置”、替换和清除，不会把已保存 secret 回填到输入框。

改变 section 的 schema version 会清除该 section 的旧值和 secret。插件必须把值缺失当作正常情况。

## 让 Hook 经得住真实故障

- invocation ID 是唯一值；收到取消后及时停止工作。
- refresh、retry、close 和 cleanup 必须幂等。
- 调用可能并发；不要依赖全局可变的请求状态。
- 请求和结果不得超出约定 schema 与大小限制。
- refresh/close reason 是开放字符串，不是穷尽枚举。
- 返回标准错误 envelope；错误和日志里不能出现凭据或响应 body。

## 分享 APK 前

先用参考插件和一致性测试验证发现、handshake、manifest 解码、调用、取消和健康检查，还要测试：

- 不兼容 API 与 hook 版本；
- 进程/Binder death、超时、超大输出和连续失败；
- 未批准 origin 与跨域重定向；
- 日志和诊断中没有密码、token、认证 header 或捕获的 secret；
- 同签名升级和不同签名升级；
- 手机与 TV 的授权、设置、禁用、重新授权、清除数据和诊断。

外部插件仍受开发者开关保护期间，不要把 APK 描述为面向所有用户的正式支持插件。
