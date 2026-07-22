# 开发 M3UAndroid 插件

[English](README.md)

M3UAndroid 插件是一个独立安装的 Android APK，用类型化接口向应用提供数据或行为。推荐先在设备上跑通参考插件，再逐个把 Hook 替换成自己的实现。

## 从这里开始

1. [运行参考插件](quickstart.zh-CN.md)：完成构建、安装、启用和检查。
2. [理解插件模型](concepts.zh-CN.md)：了解 service、manifest、Hook、设置与凭据。
3. [选择 Hook](hooks.zh-CN.md)：确认每个 Hook 的作用和当前宿主支持程度。
4. [测试插件](testing.zh-CN.md)：完成本地、设备、升级和发布前检查。

## 目前能做到什么

外部 APK 插件仍是开发者预览功能。参考插件已经可以在手机和 TV 上被发现、授权、调用、停用、重新授权、配置并导出诊断。

并非所有公开 Hook 都适合现在开发第三方产品。声明式设置的外部链路最完整；搜索、元数据、EPG、provider 和后台任务仍有限制，详见 [Hook 状态](hooks.zh-CN.md)。SDK 目前也没有作为稳定 Maven artifact 发布。

预览插件应与同一源码版本的 M3UAndroid 配套使用。SDK artifact 与版本策略发布后，才开始提供跨版本兼容性。

## 参考实现

可运行样例位于 [`:testing:extension-reference`](../../../testing/extension-reference)，它是仓库内 APK 接入与类型化请求分发的标准示例。公开契约位于 [`:extension:api`](../../../extension/api)，Android service 基类位于 [`:extension:sdk-android`](../../../extension/sdk-android)。
