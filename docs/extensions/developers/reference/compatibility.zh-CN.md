# 准备发布或更新

[English](compatibility.md) · [插件开发指南](../README.zh-CN.md)

更新前，分别判断本次是否改变插件身份、代码版本、Hook 契约、已保存设置或 capability。

## 保持身份稳定

更新时保持 Android `applicationId` 与 `ExtensionId` 不变。Service 类名或签名证书变化会
触发重新审阅，并使 Provider 凭据失效；应把它当作显式迁移，而不是普通升级。

## 分别设置每种版本

| 值 | 更新规则 |
| --- | --- |
| `extensionVersion` | 每次发布功能或修复时提升。 |
| `apiRange` | 设为当前构建支持的宿主 API 范围。 |
| Hook `schemaVersion` | 直接使用所选 `HookSpec` 的值；声明与处理函数必须完全一致。 |
| `ExtensionSettingSchema.version` | Manifest 与动态设置的每个 Section 独立版本化。删除、改名字段，或改变字段类型、含义时，提升受影响 Section 的版本。 |

固定设置使用 `manifest.settingsSchema.version`；动态设置使用各自返回的
`section.schema.version`。兼容性新增字段时，保留该 Section 的版本与已有 Key，
M3UAndroid 会应用新字段的默认值。提高 Section 版本后，M3UAndroid 会先清除该 Section
已保存的值与 Credential Handle，再应用默认值。

## 遵守 Wire 兼容规则

API 版本与 Hook Schema 回答的是两个不同问题：

| 契约变化 | 宿主行为 | 插件规则 |
| --- | --- | --- |
| API Major 不同 | 拒绝注册。`apiRange` 的最小值和最大值都必须使用宿主的 Major。 | 为新 Major 发布单独构建。 |
| API Minor 不同，但 Major 相同 | 仍可进入兼容检查。Minor 不负责选择 Hook 解码器。 | 逐个检查声明的 Hook Schema，不要把 Minor 当成功能开关。 |
| 未知 Hook 或不支持的 Hook Schema | 拒绝注册。Hook Schema 必须精确匹配。 | 使用 SDK `HookSpec` 中的 Schema。删除、改名必要字段，或改变其含义时，新建 Hook Schema。 |
| 未知 JSON 字段 | 宿主与 SDK 解码器会忽略。 | 只有字段可选，且旧端省略它时功能仍可工作，才能不改变 Hook Schema 直接新增。插件解码器也必须忽略未知字段。 |
| 未知 Capability，且 `required = true` | 拒绝注册。 | 只要求宿主已经发布的 Capability。 |
| 未知 Capability，且 `required = false` | 不阻止注册，旧宿主也不会授权。 | API 1 尚无 Hook 级可选 Capability；Hook 不得依赖它，写入 `requiredCapabilities` 的能力都属于本次调用的必要能力。 |

只有接收端契约为字段定义了默认值时，省略该字段才兼容。新增一个所有接收端都必须理解的
字段时，即使 API Major 不变，也要新建 Hook Schema。

仓库内的 [API 1 Golden Wire Fixtures](../../../../extension/api/src/jvmTest/resources/golden-wire/v1/README.zh-CN.md)
给出了 Envelope、Hook Payload 与 Broker JSON 的规范形状。新增 Hook Schema 时，新建
`schema-<version>` 目录，不要覆盖旧 Schema 的 Fixture。

## 检查 capability 变化

新增必要 capability 会改变用户授权内容。把 capability 同时加入 Hook 声明与
`manifest.capabilities`，并给出具体用途说明；更新测试必须验证授权提示。

移除 capability 时，也要移除处理函数中使用它的操作。

## 检查网络 Origin 变化

在 `manifest.networkOrigins` 中新增 Origin，不会自动加入已有授权。依赖新 Origin 前，先
验证重新授权流程。

标记为 `networkOrigin` 的字段没有默认值。提高它所在设置 Section 的 version 会清除已保存
的值与授权，因此用户必须重新保存该 Origin。

## 发布检查

- 插件模块构建成功；
- 从对应宿主功能触发每个已声明 Hook；
- 同时测试首次启用与覆盖旧版本更新；
- 确认已有设置按预期 reconcile；
- 确认每个使用 Broker 的 Hook 只能访问预期 Origin；
- 确认插件身份保持不变；
- 确认结果与诊断信息不包含 Secret 或可识别用户的请求数据。
- 对照 Golden Fixture 审查 Wire 变化，并运行 `./gradlew :extension:api:jvmTest`。

设置 Key 改名时，提高所在 Section 的 Schema Version。Hook Schema Version 始终使用当前
`HookSpec` 提供的值。

本页涉及的名称见[契约术语](glossary.zh-CN.md)。
