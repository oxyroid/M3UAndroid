# 手机端崩溃报告

## 当前能力

- 捕获 smartphone 进程内未处理的 JVM/Kotlin 异常。
- Android 11 及以上会在下次启动时补报 ANR 和 native process exit；不读取系统 trace。
- 有 endpoint 时自动发送；没有 endpoint 时保留通知 + 邮件的人工兜底。
- 报告发送在独立 `:acra` 进程执行，不会重复启动 WorkManager、provider 或 extension 初始化。
- 同一调用栈 7 天最多发送 3 次，全部报告 7 天最多 25 次，最多保留 5 个失败报告。

Android 10 及以下仍无法补报 ANR 和 native crash；用户崩溃后未再次启动时也无法补报。因此不能把本方案解释为完整的 crash-free 指标。

## 构建配置

二选一配置接收地址：

```properties
m3u.crash.report.endpoint=https://crash.example.com/reports
```

```shell
export M3U_CRASH_REPORT_ENDPOINT=https://crash.example.com/reports
```

只接受无账号、query 和 fragment 的绝对 HTTPS URL。不要把接收端 token 或 Basic Auth 密码编译进 APK。

未配置 endpoint 的 APK 不会自动上传；它只能在用户点击崩溃通知后打开邮件客户端，
由用户确认后将报告发送到 `crash@oxyroid.com`。这条路径是人工兜底，不能替代生产监控。

生产环境使用仓库内的 `:stability:receiver`：App 仍通过 HTTPS 自动上传，接收端完成
聚合和二次脱敏后，再通过 SMTP 向 `crash@oxyroid.com` 发送新问题、跨版本复现和突增提醒。
SMTP 凭据只能保存在服务端，不能编译进 APK。部署与环境变量见
`stability/receiver/README.zh-CN.md`。

## 接收协议

请求为 `POST`，body 是 gzip 压缩的 ACRA JSON，包含：

```http
Content-Type: application/json
Content-Encoding: gzip
X-M3U-Report-Schema: 1
```

- `2xx`：接收成功。
- `408` 或 `5xx`：客户端稍后重试。
- 其他 `4xx`：客户端丢弃该报告。

接收端不能把请求视为已认证。必须限制 body 大小、请求速率，并按 `PACKAGE_NAME`、版本和 schema 过滤。

## 数据边界

会发送版本、设备型号、Android 版本、内存/显示配置、崩溃时间、线程、异常类型、调用栈和有限页面上下文。

明确不收集：

- BuildConfig、日志、SharedPreferences 和文件路径；
- 安装 ID、设备 ID、IP、邮箱和用户评论；
- playlist/provider 名称、服务地址、账号、token、credential handle 或 extension 包名；
- 异常 message。报告仅保留异常类型和 stack frame。

接收端建议 30 天自动删除原始报告，只让维护者访问，并记录导出和删除操作。

## 上线检查

1. 为 release 构建注入 endpoint。
2. 运行 debug probe，确认报告可接收且不含 `must-not-be-stored`。
3. 归档同一 `versionCode` 的 `mapping.txt`，否则 R8 后的调用栈无法还原。
4. 接收端按 `STACK_TRACE_HASH + APP_VERSION_CODE` 聚合。
5. 至少配置三类提醒：新崩溃立即提醒、单一问题 15 分钟内突增、接收端健康检查失败。
6. 先灰度，再确认发送量、重试量和限流均正常。

debug 探针命令见 `app/smartphone/src/debug/README.zh-CN.md`。

仓库内 mock receiver 会校验 gzip、schema、包名、payload 上限和禁止字段，可用于验证即时发送与失败后重试；它只用于测试，不是生产接收端。

生产接收端提供 `/health` 存活检查和 `/ready` 邮件投递就绪检查，但提醒必须由使用独立
通知渠道的外部监控发出；不能依赖接收端或同一条 SMTP 链路监控自身。

参考：[ACRA sender](https://www.acra.ch/docs/Senders)、[ACRA advanced usage](https://www.acra.ch/docs/AdvancedUsage)。
