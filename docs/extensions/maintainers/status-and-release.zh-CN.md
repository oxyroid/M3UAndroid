# 当前状态与发布门槛

[English](status-and-release.md) · [维护者指南](README.zh-CN.md)

本页是查表和发布审查用的参考页，不是入门教程。先在“当前支持情况”找到相关能力；只有判断能否开放外部插件时，才需要继续阅读全部阻塞项。宿主调用点、UI 链路、安全边界或端到端测试发生变化时，应同步更新本页。

## 当前支持情况

| 范围 | 当前证据 | 状态 |
| --- | --- | --- |
| 内置 Emby/Jellyfin validate、refresh、playback 和 close | 严格 mock server 设备集成测试覆盖两种 kind 与 repository 持久化 | 已接通 |
| 通用 provider Room model、加密凭据、WorkManager 刷新、open-session 恢复 | 已有 data 集成链路 | 已接内置 provider 流程 |
| APK 发现、handshake、PFD payload、类型化调用、设置和取消 | SDK 单测覆盖类型化 handler；Hello 可显示静态与动态设置；`ExternalExtensionIpcTest` 完成 1/1 | 开发者预览 |
| 单次调用范围的宿主 broker | 宿主检查 caller UID 与 grant、撤销被保留的 bridge、限制并发，并拒绝可直接联网的 APK；参考 APK 已完成 broker probe | Transport 边界已接通，provider 登录未接通 |
| 手机插件授权、启停、设置、重新授权、诊断和清除数据 | 产品 UI 已有，手工设备链路可用 | 开发者预览，UI 自动化不完整 |
| TV 插件管理与声明式设置 | 产品 UI 已有 | 开发者预览，没有 DPad 自动化 |
| 声明式插件设置 | 手机、TV、repository、加密密码存储、Hello 样例和 reference fixture | 外部 Hook 中最完整 |
| 手机端插件搜索 | 把 stable reference 映射到已有可见频道 | 部分接通 |
| 元数据与 EPG 贡献 | 通用 provider 刷新后运行；EPG 只替换调用成功的插件数据 | 部分接通，未覆盖 M3U/Xtream 导入 |
| 外部 provider discovery | 手机能展示返回的 descriptor | 部分接通，参考 provider 无法订阅 |
| 外部 provider validate/refresh/playback/close | 没有完整参考 APK，也没有账号创建前的登录链路 | 未接通 |
| TV 动态 provider 订阅 | 没有 provider 列表或表单 state | 未接通 |
| 恢复或密钥丢失后的 provider 重新认证 | 已有状态字段，但没有用户修复流程 | 未接通 |
| 后台任务 | 已有契约、Worker 和直接 transport 测试，但没有代码调度 Worker | 仅有契约 |

## 发布阻塞项

### 1. 隔离 Android 进程边界

- 把耗时工作移到短异步控制调用和宿主持有的结果 pipe 后面。
- 为所有 transport 设置宿主级 executor 总预算；同步 Binder 调用在远端返回前仍会占用当前 transport 的预算。
- Handshake、manifest、invoke、cancel、health 和 broker 失败使用同一种稳定错误结果。

### 2. 补齐凭据与登录所有权

- Trust、grant、设置、凭据和 provider 所有权按 package、service、证书集合、UID 与 extension ID 索引。
- 增加 provider 账号创建前使用的临时 auth session。
- 用带 scope 的宿主 handle 统一 provider 凭据、登录 capture 结果与插件密码设置。
- 认证 header、capture rule 和允许返回的字段来自宿主批准的 schema；认证响应只暴露白名单字段。
- 为 broker 与 HTTP 增加贯穿整次调用的 deadline，以及用户批准的附加 origin 策略。

### 3. 让每个 importer 保护所有权与有效旧数据

- 校验贡献者/provider 身份、kind、remote ID、结果数量、字段长度、map、URL、scheme、header 和播放 session。
- 每个 provider discovery 失败相互隔离，并始终保留内置 provider。
- Wire 冻结前，对当前未使用的 sync metadata、diagnostics、expiry、continuation 和 retry 字段选择“持久化/消费”或“移除”。
- 在支持的 M3U 与 Xtream 导入完成后，运行共用的 metadata/EPG 贡献逻辑。

### 4. 完成 provider 产品链路

- 让独立安装的参考 APK 通过真实 settings schema 实现 discover、validate、refresh、resolve 和 close。
- 参考插件经过 repository、broker、Room、WorkManager、播放器和 session close 完成验收。
- 为 TV 增加动态 provider 订阅。
- 为恢复后或无法解密的 provider 账号增加可见的重新认证流程。
- Provider descriptor 与 schema 是 UI 唯一的 provider 特定输入；app 与 business 不依赖 Emby/Jellyfin kind。
- Settings schema 缺失、为空或订阅失败时显示明确错误。

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

## 可以开放的定义

满足以下全部条件后，才能移除开发者开关：

- 新增 provider 无需在 app/business/data 中增加按具体 kind 分支；
- 独立安装 APK 能在手机和 TV 上完成它声明的产品流程；
- 每个公开 Hook 都有真实宿主调用点、有边界的 importer/renderer 和端到端测试；
- 插件永久阻塞、崩溃、错误数据、保留 broker、签名变化和凭据丢失不会破坏宿主数据或主进程；
- 兼容性 fixture 与公开 SDK 版本策略已经发布。
