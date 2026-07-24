# 准备发布或更新

[English](compatibility.md) · [插件开发指南](../README.zh-CN.md)

更新前，分别判断本次是否改变插件身份、代码版本、Hook 契约、已保存设置或 capability。

## 保持身份稳定

更新时保持以下内容不变：

1. Android `applicationId`
2. Service 类名
3. 签名证书
4. `ExtensionId`

只有确实需要创建另一个插件身份时，才修改其中的值。

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

设置 Key 改名时，提高所在 Section 的 Schema Version。Hook Schema Version 始终使用当前
`HookSpec` 提供的值。

本页涉及的名称见[契约术语](glossary.zh-CN.md)。
