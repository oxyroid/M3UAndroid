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
