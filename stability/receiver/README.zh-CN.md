# 崩溃接收端

这是 smartphone 崩溃报告的生产接收端。它接收 App 的 gzip JSON，按调用栈和版本聚合，
并把新问题、跨版本复现和 15 分钟突增发送到 `crash@oxyroid.com`。

## 本地运行

```shell
./gradlew :stability:receiver:installDist
M3U_CRASH_ALERT_MODE=stdout \
  stability/receiver/build/install/receiver/bin/receiver
```

本地模式把告警打印到终端，不发送邮件。数据默认写入
`build/crash-receiver/state.json`。

## 生产配置

```shell
M3U_CRASH_HOST=127.0.0.1
M3U_CRASH_PORT=8080
M3U_CRASH_STATE_FILE=/var/lib/m3u-crash-receiver/state.json
M3U_CRASH_ADMIN_TOKEN=<随机长 token>
M3U_CRASH_ALERT_MODE=smtp
M3U_CRASH_SMTP_HOST=<SMTP host>
M3U_CRASH_SMTP_PORT=587
M3U_CRASH_SMTP_USERNAME=<SMTP username>
M3U_CRASH_SMTP_PASSWORD=<SMTP password>
M3U_CRASH_SMTP_FROM=<已授权发件地址>
```

SMTP 强制使用 STARTTLS 并校验服务器证书。密码只存在服务端环境文件中。外层反向代理负责
HTTPS，并将公开的 `/reports` 转发到本服务；不要把管理 token 或 SMTP 密码写进 APK。

构建出的 systemd 示例位于 `deploy/m3u-crash-receiver.service`。安装目录、运行用户和环境
文件路径需要按服务器实际情况修改。

## 接口

- `POST /reports`：App 自动上报地址。
- `GET /health`：公开存活检查，只返回 `ok`。
- `GET /internal/status`：需要 `Authorization: Bearer <admin token>`，返回问题数、待发送
  告警数和最近一次投递状态；未配置或凭据错误时返回 404。
- `POST /internal/test-alert`：使用同一管理鉴权，把一封带唯一 probe ID 的测试邮件加入
  持久队列；队列已满时返回 503。

## 邮件投递验收

部署后在服务器本机调用内部接口，不需要把 `/internal/*` 暴露给公网：

```shell
curl --fail-with-body --request POST \
  --header "Authorization: Bearer <admin token>" \
  http://127.0.0.1:8080/internal/test-alert
```

成功时返回 `202` 和一个 UUID。确认 `crash@oxyroid.com` 收到标题为
`[M3U crash][test] <UUID 前 8 位>` 的邮件，且正文中的 `probe_id` 与响应完全一致；随后检查
`/internal/status` 的 `pendingAlertCount`、`lastAlertDeliveredAtEpochMillis` 和
`lastAlertFailureType`。这条测试不会伪造崩溃，也不会改变问题聚合计数。

接收成功后先原子写盘，再返回 `202`。邮件发送在后台进行，失败会保留队列并退避重试。
相同 `REPORT_ID` 在 30 天内只计一次。报告只保留 30 天；不会保存 IP、账号、服务地址、
token、日志或异常 message。

接收端采用单实例、文件持久化，适合当前低流量规模，不支持多实例同时写入同一个状态文件。
待发送队列最多保留 1000 条；满载时优先保留新问题和跨版本复现，并在管理状态中累计
`droppedAlertCount`，运维监控应把该值大于 0 视为告警。

生产环境还需要独立的外部监控定时访问 `/health`。同一个进程无法在自身宕机时发送告警。
