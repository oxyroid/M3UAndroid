# 安全修改插件平台

[English](change-guide.md) · [维护者指南](README.zh-CN.md)

先明确用户能看到的行为，再找到对应的类型化 Hook，并一路追踪到宿主消费端。只有契约与真实产品链路一致，改动才算完成。

## 增加或修改 Hook

1. 在 `:extension:api` 中定义一组 request、result 和 `HookSpec`。
2. 为该 Hook 分配独立的正整数 schema version。
3. 在宿主策略中定义最低 capability，不依赖插件 manifest 自己是否声明。
4. 把 Hook 加入协商与 manifest 校验。
5. 实现或调整内置与 Android transport fixture。
6. 增加真实宿主调用点，以及范围明确的 importer 或 renderer。
7. 增加序列化 fixture、策略测试、importer 测试和端到端链路。
8. 更新开发者 Hook 状态表，以及受影响维护者文档的中英文版本。

如果缺少第 6、7 步，就应把 Hook 标为“仅有契约”，而不是“已经接通”。

## 修改契约版本

兼容性新增字段使用带默认值的可选序列化字段。旧实现无法安全解码或回答新格式时，提高该 Hook 的 schema version。只有平台整体不兼容时才提高 extension API major。

Refresh、close 等 reason 使用经过格式校验的开放字符串。未知可选 JSON 字段应忽略；API major 不兼容、不支持必要 Hook schema、未知必要 capability 时应拒绝。

冻结 wire 字段前先找到对应的宿主消费端。Continuation、diagnostics、expiry、retry delay、sync metadata 等字段必须具备经过测试的行为，否则不要进入公开 1.0 契约。

## 增加内置 provider

内置 provider 是由宿主代码注册的插件实现。它与 APK 插件遵循同一条五 Hook 生命周期：

```text
discover -> validate -> refresh -> resolve playback -> close session
```

Provider 结果必须保持为契约数据。HTTP client 和宿主凭据位于 data adapter 后面；持久化保存通用 provider/account 身份；UI 选择由 provider descriptor 驱动，不能增加按 provider kind 分支的 `when`。

使用严格 mock server 验证登录、首次导入、刷新、播放选择、session close、幂等导入和本地频道状态保留。

## 接通外部 provider

外部 provider 还需要：

- 绑定完整插件身份和已批准 origin 的临时 auth session；
- 宿主持有的凭据 capture，以及成功后提升为持久账号的事务；
- 每个结果的贡献者/provider 所有权检查；
- descriptor、频道、字符串、metadata、URL 和 header 边界；
- WorkManager 在 refresh 或 session recovery 前恢复插件注册的顺序保证；
- 参考 APK 实现全部五个 Hook，并经过 repository、broker、importer、Room、播放器和 close 的端到端链路。

只有 provider discovery 不能算完成 provider 接入。

## 增加贡献 importer

Importer 与 transport 无关。它接收贡献者 extension ID、原始请求范围和类型化结果。

对每个导入项检查：

- 数据属于当前贡献插件和请求；
- stable/remote ID 非空、在需要时唯一，并且有长度限制；
- 数量、字符串、map、时间范围、URL 和 header 均有边界；
- retry 或单插件失败时保留此前有效数据；
- 成功返回空结果时，只清除该插件自己拥有的数据；
- cancellation 继续传播，不能被转换为空贡献。

Provider、M3U 和 Xtream 导入语义相同时，应复用同一个通用贡献 importer。

## 修改 Android IPC

把 Binder 调用、PFD、broker bridge 和生命周期回调视为同一个可撤销插件 session。

Session 身份需要包含 package、service、证书集合、UID、extension ID、grant 和 invocation ID。停用、撤销、隔离、Binder death 和超时都必须使 session 失效，并关闭对应 descriptor。耗时插件工作应在一次很短的异步控制 transaction 之后运行，并使用有界 executor。

同时使用正常与恶意 fixture APK 验证：永久阻塞、忽略取消、进程死亡、错误 envelope、超限结果、保留 broker handle、改变签名，以及复用其他插件 extension ID。

## 修改凭据或 broker

统一的凭据 registry 应覆盖 provider 凭据、临时登录输入、登录 capture 结果和插件密码设置。每个 handle 都记录 owner 身份、purpose、account/auth-session/setting scope、key version 和批准的 origin。

认证请求 header 和 capture rule 来自宿主批准的 schema。认证响应只返回白名单数据和 handle，不能依靠猜测敏感 key 来建立安全边界。Broker 调用还要校验活跃 invocation/session grant、caller UID、deadline、并发配额、redirect 策略和响应大小。

## 修改手机或 TV UI

UI 只展示 repository 状态并发送生命周期操作，不能直接绑定 service 或访问 trust 存储。

手机端检查全行点击、可滚动授权、可见错误、设置校验和生命周期刷新。TV 端检查 DPad 焦点顺序、聚焦态对比度、较长 capability 理由、返回行为和完整设置表单。两个端的动态 provider 都必须来自 descriptor。

## 验证顺序

先验证最小受影响层，再逐步扩大：

```bash
./gradlew :extension:api:test :extension:runtime:test
./gradlew :extension:transport-android:connectedDebugAndroidTest
./gradlew :data:testDebugUnitTest :data:connectedDebugAndroidTest
./gradlew :app:smartphone:assembleDebug :app:tv:assembleDebug
./gradlew :app:smartphone:connectedDebugAndroidTest :app:tv:connectedDebugAndroidTest
```

修改 Room 时，实体、migration、导出 schema 和 migration 测试必须一起更新。提交前运行 `git diff --check`，不要把生成的 parser 输出或无关 IDE 文件混入改动。

下一步：[当前状态与发布门槛](status-and-release.zh-CN.md)。
