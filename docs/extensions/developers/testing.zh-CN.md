# 测试插件

[English](testing.md) · [插件开发指南](README.zh-CN.md)

先在本地验证契约，再让独立安装的 APK 跨越真实 Android 进程边界运行。

## 快速本地检查

使用 JDK 17，在项目根目录运行：

```bash
./gradlew \
  :extension:api:test \
  :extension:runtime:test \
  :testing:extension-reference:assembleDebug
```

这些任务会检查契约校验、类型化 runtime、调用限制、取消和参考 APK 编译，但不能证明 Android 发现与 IPC 正常。

## Android IPC 检查

启动一台状态干净的真机或模拟器，然后运行：

```bash
./gradlew :app:smartphone:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.m3u.testing.ExternalExtensionIpcTest
```

手机端测试任务会在测试前安装参考插件，并在结束后卸载。测试覆盖发现、manifest 读取、类型化调用、大结果、设置和取消。

## 手工设备检查

按 [运行参考插件](quickstart.zh-CN.md) 完成安装，然后检查：

1. 只有开启预览功能后，已安装 APK 才会出现在插件列表；
2. 授权界面显示正确的包名、版本、开发者、证书和 capability；
3. 启用、停用和再次启用无需重装 APK；
4. 设置能够保存，密码不会显示，清除数据后设置被重置；
5. 插件改变 capability 申请后，重新授权界面能够反映变化；
6. 诊断信息包含身份与状态，但不包含设置值或 payload 正文；
7. 结束插件进程或卸载插件后，M3UAndroid 仍可正常使用。

如果插件面向 TV 用户，还要在 TV 上重复插件管理和设置流程。

## 升级检查

保留上一版本的测试 APK，并覆盖以下情况：

| 升级变化 | 预期结果 |
| --- | --- |
| 包名、service、extension ID 和签名均不变 | 可以恢复已有信任 |
| 新增可选 capability | 重新授权前保持未授予 |
| 新增必要 capability | 宿主要求用户重新确认授权 |
| 签名或 extension ID 改变 | 不复用原有信任 |
| 设置分组的 schema version 改变 | 清除该分组的旧值 |

## 故障检查

至少覆盖：

- 不支持的 API 或 Hook schema version；
- 错误格式的请求与结果；
- 超时和主动取消；
- 超过宿主上限的输出；
- 调用期间插件进程退出；
- Hook 连续失败；
- 安全且便于处理的错误信息。

这些检查只提供单个插件的开发证据。整个平台的发布证据统一记录在 [维护者发布状态](../maintainers/status-and-release.zh-CN.md)。

## 发布状态

目前没有稳定的外部 SDK artifact，也没有公开兼容性保证。直接依赖源码模块构建的 APK 只能视为开发版本。未来开放分发后，应保持包名、extension ID 和签名证书稳定，并在每个版本中声明支持的 M3UAndroid API 范围。
