# 准备发布或更新

[English](compatibility.md) · [插件开发指南](../README.zh-CN.md)

更新前，分别判断本次是否改变插件身份、插件代码版本、Hook 契约、已保存设置或 capability。

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
| Hook `schemaVersion` | 直接使用所选 `HookSpec` 的值；声明与 handler 必须完全一致。 |
| `settingsSchema.version` | 已保存值不再兼容时提升，例如删除、改名字段，或改变字段类型、含义。 |

兼容性新增字段时，保留原 schema version 与已有 key，M3UAndroid 会应用新字段的默认值。如果提高 schema version，M3UAndroid 会先清除该 schema 已保存的值与 credential handle，再应用默认值。

## 检查 capability 变化

新增必要 capability 会改变用户授权内容。把 capability 同时加入 Hook 声明与 `manifest.capabilities`，并给出具体用途说明；更新测试必须验证授权提示。

移除 capability 时，也要移除每项使用它的 handler 操作。

## 发布检查

- 插件模块构建成功；
- 从对应宿主功能触发每个已声明 Hook；
- 同时测试首次启用与覆盖旧版本更新；
- 确认已有设置按预期 reconcile；
- 确认插件身份保持不变；
- 确认 result 与诊断信息不包含 secret 或可识别用户的 request 数据。

最常见的不兼容更新，是设置 key 已改名却没有提高 `settingsSchema.version`；其次是复制旧 Hook schema version，而没有使用当前 `HookSpec.schemaVersion`。

本页涉及的名称见[契约术语](glossary.zh-CN.md)。
