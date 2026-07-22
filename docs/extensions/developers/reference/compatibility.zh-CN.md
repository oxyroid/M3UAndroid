# 发布与升级插件

[English](compatibility.md) · [插件开发指南](../README.zh-CN.md)

先让插件正常运行，再阅读本页。当前 SDK 仍是开发者预览，请使用同一份源码构建宿主和插件。

## 创建自己的仓库内插件

当前最短做法是复制 `samples/hello-extension` 模块，为副本设置独立的 package、Service 身份、`ExtensionId`、显示名称和开发者名称，并把模块注册到当前源码中。保留它对 `:extension:sdk-android` 的依赖；SDK artifact 发布前，该样例只作为仓库内模板。

## 三种版本不是一回事

| 版本 | 何时修改 |
| --- | --- |
| 插件版本 `extensionVersion` | 每次发布插件功能或修复时 |
| Hook schema version | 某一个 Hook 的输入或输出不再兼容时 |
| Extension API major | 整个平台契约不再兼容时 |

新增可选字段时应提供默认值。旧宿主或旧插件无法安全理解新结构时，才提升相应的 schema 或 API major。

## 升级前检查

- 四项插件身份保持不变；
- `extensionVersion` 已更新；
- 已保存的普通设置仍能读取；
- 删除或改名字段后，已保存设置仍能完成 reconcile；
- 新增必要 capability 时，用户会看到重新授权提示；
- 同一签名更新后，刷新插件列表能读到新 manifest。
