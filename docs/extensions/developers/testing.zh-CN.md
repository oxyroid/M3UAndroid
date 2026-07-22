# 测试插件

[English](testing.md) · [插件开发指南](README.zh-CN.md)

每个 Hook 需要证明两件事：handler 返回预期的类型化 result，M3UAndroid 也会在调用该 Hook 的功能中正确应用 result。

## 1. 测试 result 对象

把解析、映射和校验放在普通 Kotlin 函数中。使用 request fixture 对这些函数做单元测试，并断言完整的 result 对象。

至少覆盖：

- 最小合法 request；
- 空值与边界值；
- 预期内的服务端或校验失败；
- 长任务进行中的取消；
- 缺少必要设置或 credential handle。

预期内的失败使用 `handleResult(...)` 或 `handleResultWithBroker(...)` 返回。只有意外故障才抛出异常。

## 2. 构建模块

Hello 模块使用：

```bash
./gradlew :samples:hello-extension:assembleDebug
```

自己的插件使用对应模块任务。

## 3. 从 M3UAndroid 触发 Hook

更新插件后刷新插件列表，再使用对应的 M3UAndroid 功能。

| Hook | 验收结果 |
| --- | --- |
| `settings.schema.contribute` | 设置页绘制返回的分组，并能重新载入已保存值。 |
| `search.provider.query` | 返回的稳定引用会在手机搜索中提升匹配的可见频道。 |
| `metadata.channel.enrich` | 通用 provider 刷新只把 patch 应用到 request 中的频道。 |
| `epg.content.refresh` | 刷新会导入返回的节目；调用失败时保留上一次贡献。 |
| Provider 发现与验证 | 订阅表单显示 descriptor schema，合法凭据可以创建账号。 |
| Provider 刷新 | 导入后的播放列表包含返回的完整频道快照。 |
| Provider 播放与关闭 | 解析后的 source 使用返回的 header 播放，停止播放后远端 session 已关闭。 |

## 4. 检查失败行为

为每个 Hook 触发一次预期内的失败，并确认：

- 插件返回稳定的 `ExtensionError.code`；
- `recoverable` 与“重复同一次调用是否可能成功”一致；
- M3UAndroid 不会应用部分有效的 result；
- 取消会停止长时间运行的工作。

Provider 还应覆盖凭据被拒、刷新失败后保留已有数据、无效播放 result，以及重复关闭 session。

## 5. 验证更新

- 保持相同的插件身份与签名证书；
- 确认已有设置仍能读取；
- 确认删除或改名的字段按预期 reconcile；
- 确认新增必要 capability 时会请求授权；
- 确认诊断信息不包含 secret、credential handle，以及能识别用户的 request 或 response 数据。

下一步：[准备发布或更新](reference/compatibility.zh-CN.md)。
