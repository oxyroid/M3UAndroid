# 当前状态与发布门槛

[English](status-and-release.md) · [维护者指南](README.zh-CN.md)

本页是插件平台当前支持情况的唯一状态表。宿主调用点、UI 链路、安全边界或端到端测试发生变化时，应同步更新本页。

## 当前支持情况

| 范围 | 当前证据 | 状态 |
| --- | --- | --- |
| 内置 Emby/Jellyfin validate、refresh、playback 和 close | 严格 mock server 设备集成测试覆盖两种 kind 与 repository 持久化 | 已接通 |
| 通用 provider Room model、加密凭据、WorkManager 刷新、open-session 恢复 | 已有 data 集成链路 | 已接内置 provider 流程 |
| APK 发现、handshake、PFD payload、类型化调用、设置和取消 | 独立安装参考 APK 的 instrumentation fixture | 开发者预览 |
| 手机插件授权、启停、设置、重新授权、诊断和清除数据 | 产品 UI 已有，手工设备链路可用 | 开发者预览，UI 自动化不完整 |
| TV 插件管理与声明式设置 | 产品 UI 已有 | 开发者预览，没有 DPad 自动化 |
| 声明式插件设置 | 手机、TV、repository、加密密码存储和参考 fixture | 外部 Hook 中最完整 |
| 手机端插件搜索 | 把 stable reference 映射到已有可见频道 | 部分接通 |
| 元数据与 EPG 贡献 | 通用 provider 刷新后运行 | 部分接通，未覆盖 M3U/Xtream 导入 |
| 外部 provider discovery | 手机能展示返回的 descriptor | 部分接通，参考 provider 无法订阅 |
| 外部 provider validate/refresh/playback/close | 没有完整参考 APK，也没有可用 broker/login 链路 | 未接通 |
| TV 动态 provider 订阅 | 没有 provider 列表或表单 state | 未接通 |
| 恢复或密钥丢失后的 provider 重新认证 | 已有状态字段，但没有用户修复流程 | 未接通 |
| 后台任务 | 已有契约、Worker 和直接 transport 测试，但没有代码调度 Worker | 仅有契约 |

手机端 Emby/Jellyfin 选择器在全宽行修复后可以手工操作。对应 UIAutomator 用例目前会因 instrumentation 进程崩溃而结束，因此还不能作为稳定发布证据。

## 发布阻塞项

### 1. 隔离 Android 进程边界

- 把耗时同步 Binder transaction 改为短异步控制调用和宿主持有的结果 pipe。
- 限制 transport executor；超时、取消、Binder death、停用、撤销或隔离时使插件 session 失效。
- Broker 调用绑定活跃插件身份、caller UID、invocation、grant、deadline 和并发配额。
- Handshake、manifest、invoke、cancel、health 和 broker 失败使用同一种稳定错误结果。
- 支持 API 26–27 的证书读取，并以确定顺序固定完整 signer 集合。

### 2. 补齐凭据与登录所有权

- Trust、grant、设置、凭据和 provider 所有权都按 package、service、证书集合、UID 与 extension ID 索引，不能只按 extension ID。
- 增加 provider 账号创建前使用的临时 auth session。
- 用带 scope 的宿主 handle 统一 provider 凭据、登录 capture 结果与插件密码设置。
- 认证 header、capture rule 和允许返回的字段来自宿主批准的 schema；认证响应使用白名单，不能依靠猜测敏感 key。
- Broker session 可撤销，并具备总超时、并发限制和明确的批准 origin 策略。

### 3. 让每个 importer 保护所有权与有效旧数据

- 按调用成功的 extension 替换 EPG；一个插件失败时，所有此前有效 source 都必须保留。
- 校验贡献者/provider 身份、kind、remote ID、结果数量、字段长度、map、URL、scheme、header 和播放 session。
- 隔离 provider discovery 失败，单个插件不能让内置 provider 一起消失。
- Wire 冻结前，对当前未使用的 sync metadata、diagnostics、expiry、continuation 和 retry 字段选择“持久化/消费”或“移除”。
- 在支持的 M3U 与 Xtream 导入完成后，运行共用的 metadata/EPG 贡献逻辑。

### 4. 完成 provider 产品链路

- 让独立安装的参考 APK 通过真实 settings schema 实现 discover、validate、refresh、resolve 和 close。
- 参考 APK 必须经过 repository、broker、Room、WorkManager、播放器和 session close，而不是只做 runtime 直接调用。
- 为 TV 增加动态 provider 订阅。
- 为恢复后或无法解密的 provider 账号增加可见的重新认证流程。
- 移除 app/business 顶层的 Emby/Jellyfin 分支，由 provider descriptor 与 schema 驱动 UI。
- Settings schema 缺失/为空和订阅失败都必须显示明确错误，不能静默返回。

### 5. 冻结更小且可强制执行的公开契约

- 为每个 Hook 增加宿主持有的最低 capability。
- Wire 1.0 冻结前，删除没有经过测试的消费端的公开字段，或补齐真实行为。
- 为每种 request、result 和 error 发布 golden JSON fixture。
- 发布 SDK artifact 和同 major 兼容策略。

### 6. 建立发布证据

- 同一套参数化一致性测试同时运行内置与 Android transport。
- 增加恶意 fixture APK，覆盖永久阻塞、忽略取消、进程死亡、错误/超限输出、保留 broker、签名变化和 extension ID 冲突。
- 增加稳定手机测试，覆盖 Jellyfin 选择、动态 provider 表单、插件授权、重新授权、设置和可见错误。
- 增加 TV DPad 测试，覆盖插件管理、授权、设置和 provider 订阅。
- 回归 M3U、EPG、Xtream、普通播放和 DLNA。

## 验证命令

先选择最小相关集合；开放功能前再运行完整发布表面：

```bash
./gradlew :extension:api:test :extension:runtime:test
./gradlew :extension:transport-android:connectedDebugAndroidTest
./gradlew :data:connectedDebugAndroidTest
./gradlew :app:smartphone:assembleDebug :app:tv:assembleDebug
./gradlew :app:smartphone:connectedDebugAndroidTest :app:tv:connectedDebugAndroidTest
```

外部 IPC 测试必须把独立安装的参考 APK 作为硬前置条件。Fixture 缺失时测试应失败，不能跳过。

## 可以开放的定义

满足以下全部条件后，才能移除开发者开关：

- 新增 provider 无需在 app/business/data 中增加按具体 kind 分支；
- 独立安装 APK 能在手机和 TV 上完成它声明的产品流程；
- 每个公开 Hook 都有真实宿主调用点、有边界的 importer/renderer 和端到端测试；
- 插件永久阻塞、崩溃、错误数据、保留 broker、签名变化和凭据丢失不会破坏宿主数据或主进程；
- 兼容性 fixture 与公开 SDK 版本策略已经发布。
