# 10 分钟运行 Hello

[English](quickstart.md) · [插件开发指南](README.zh-CN.md)

完成标志：M3UAndroid 的插件列表出现 **Hello Extension**，设置页显示 **Greeting** 和 **Phone name**。

本教程直接使用仓库中的可运行样例。

## 1. 构建并部署

在项目根目录运行：

```bash
./gradlew \
  :app:smartphone:installDebug \
  :samples:hello-extension:installDebug
```

## 2. 在 M3UAndroid 中启用 Hello

1. 打开 **设置 → 可选功能**，开启 **外部扩展**。
2. 打开 **设置 → 订阅管理**。
3. 在订阅页面向左滑到 **扩展插件**。
4. 找到 **Hello Extension**，点击 **启用**并确认。
5. 点击 Hello 的 **设置**。

手机上应看到：

- **Greeting**，默认值为 `Hello from my extension`；
- **Phone name**，默认值为 `My phone`。

看到这两个字段，就说明插件发现、用户授权和真实 Hook 调用都已走通。

## 3. 做一次修改

打开 [`HelloExtensionService.kt`](../../../samples/hello-extension/src/main/java/com/m3u/samples/hello/extension/HelloExtensionService.kt)，把静态字段的 `label = "Greeting"` 改成 `label = "Message"`。重新部署样例，回到 **扩展插件** 页，点击 **刷新**，再打开 Hello 设置。标题应变为 **Message**。

这就是日常开发循环：修改代码、部署样例、刷新、从真实界面检查结果。

下一步：[读懂并修改第一个 Hook](first-hook.zh-CN.md)。
