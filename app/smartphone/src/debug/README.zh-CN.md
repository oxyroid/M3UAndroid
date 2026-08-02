# 本地 Emby 调试账号

手机端 debug 变体可以通过正式的 provider 订阅链路自动导入一个 Emby 账号。在仓库根目录
`local.properties` 中同时填写：

```properties
m3u.debug.emby.baseUrl=https://your-server.example
m3u.debug.emby.username=your-account
m3u.debug.emby.password=your-password
```

也可以使用 `M3U_DEBUG_EMBY_BASE_URL`、`M3U_DEBUG_EMBY_USERNAME` 和
`M3U_DEBUG_EMBY_PASSWORD` 环境变量。优先级依次是 Gradle property、环境变量和
`local.properties`。

这些值只会编译进本地 debug APK；release 变体没有对应代码和字段。首次启动全新安装的
debug 应用时，会在内置播放样例导入完成后添加账号；后续启动会复用已存在的 provider 账号。

## 崩溃报告探针

只在 debug 变体中提供受控崩溃入口。安装后运行：

```shell
adb shell am start -n com.m3u.smartphone/.stability.DebugCrashTestActivity
```

它用于验证崩溃报告是否落盘、发送进程能否独立启动，以及异常消息中的测试 secret 是否已被移除。

需要验证自动 HTTP 发送时，启动仓库内 mock receiver，并让设备端口反向连接宿主机：

```shell
./gradlew :testing:mock-server:startMockServer
./gradlew :app:smartphone:assembleDebug \
  -Pm3u.debug.crash.report.endpoint=http://127.0.0.1:8080/crash-reports
adb reverse tcp:8080 tcp:8080
adb install -r app/smartphone/build/outputs/apk/debug/smartphone-debug.apk
adb shell am start -n com.m3u.smartphone/.stability.DebugCrashTestActivity
curl http://127.0.0.1:8080/crash-reports/latest
```

`m3u.debug.crash.report.endpoint` 仅影响 debug。HTTP 只允许本机、模拟器宿主地址或私网地址；release endpoint 始终要求 HTTPS。
