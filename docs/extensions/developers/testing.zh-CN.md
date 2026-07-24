# 测试插件

[English](testing.md) · [插件开发指南](README.zh-CN.md)

每个 Hook 需要证明两件事：处理函数返回预期的类型化结果，M3UAndroid 也会在对应功能中
正确应用该结果。

## 1. 测试结果对象

把解析、映射和校验放在普通 Kotlin 函数中。使用请求样例做单元测试，并断言完整的结果
对象。

至少覆盖：

- 最小合法请求；
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

让 M3UAndroid 使用当前插件构建，再操作该 Hook 所属的功能。

| Hook | 验收结果 |
| --- | --- |
| `settings.schema.contribute` | 设置页绘制返回的分组，并能重新载入已保存值。 |
| `search.provider.query` | 返回的稳定引用会在手机搜索中提升匹配的可见频道。 |
| `metadata.channel.enrich` | 通用 Provider 刷新只修改本次请求中的频道。 |
| `epg.content.refresh` | 刷新会导入返回的节目；调用失败时保留上一次贡献。 |
| `background.task.run` | 启用插件会调度每项声明，停用会取消任务；联网任务等待网络可用。 |
| Provider 发现与验证 | Discover 返回一个 Descriptor。表单使用它的 Schema。合法输入完成宿主管理的认证。 |
| Provider 刷新 | 只有首次刷新成功后才保存账号。导入的播放列表包含完整快照。 |
| Provider 播放与关闭 | 同源 Source 使用宿主解析的 Header 播放。停止播放会关闭远端 Session。 |

参考 Provider 测试通过外部插件链路覆盖：发现、登录失败、登录成功、首次刷新、后续刷新、
播放解析、Header 解析和 Session 关闭。

## 4. 检查失败行为

为每个 Hook 触发一次预期内的失败，并确认：

- 插件返回稳定的 `ExtensionError.code`；
- `recoverable` 与“重复同一次调用是否可能成功”一致；
- M3UAndroid 不会应用部分有效的结果；
- 取消会停止长时间运行的工作。

使用 Broker 的 Hook 还要确认：已批准 Origin 可以访问，未批准 Origin 和跨 Origin
重定向会失败。缺少 `network` 时必须拒绝请求；请求使用 Credential Handle 时，缺少
`credential.read` 也必须拒绝。

Provider 还应覆盖凭据被拒、刷新失败后保留已有数据、无效播放 Result，以及重复关闭 Session。

## 5. 验证更新

- 保持相同的插件身份与签名证书；
- 确认已有设置仍能读取；
- 确认删除或改名的字段按预期 reconcile；
- 确认新增必要 capability 时会请求授权；
- 确认诊断信息不包含 Secret、Credential Handle，以及能识别用户的请求或响应数据。

下一步：[准备发布或更新](reference/compatibility.zh-CN.md)。
