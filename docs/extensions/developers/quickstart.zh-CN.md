# 运行参考插件

[English](quickstart.md) · [插件开发指南](README.zh-CN.md)

完成本文后，你会在 M3UAndroid 的 debug 版本中安装并启用 **M3U Reference Extension**。

## 准备环境

- 本项目的本地源码
- JDK 17
- Android SDK 与 `adb`
- Android 8.0（API 26）或更高版本的真机或模拟器

继续之前，请确认 `adb devices` 中目标设备的状态是 `device`。

## 1. 构建并安装两个 APK

在项目根目录运行：

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew \
  :app:smartphone:installDebug \
  :testing:extension-reference:installDebug
```

宿主和插件是两个独立安装包。只要插件契约没有变化，修改插件后无需重新构建宿主。

## 2. 开启预览功能

在设备上的 M3UAndroid 中：

1. 打开 **设置 → 可选功能**。
2. 开启 **外部扩展**。
3. 打开 **设置 → 订阅管理**，切换到 **扩展插件** 页面。
4. 如列表没有立即更新，点击刷新。

此时应看到名为 **Reference Provider** 的插件卡片，标题下方显示状态、service 名称和证书。

## 3. 启用插件

点击 **启用**，核对插件身份和申请的能力，然后确认。插件状态应变为 **已启用**。

打开插件的 **设置**。参考插件会提供：

- “Enabled”开关；
- “API key”密码字段；
- “Playback”分组中的“Quality”选项。

保存密码后，界面只会显示“已配置”，不会把原值重新填入表单。

## 4. 完成一次可见修改

修改 [`ReferenceExtensionService.kt`](../../../testing/extension-reference/src/main/java/com/m3u/testing/extension/reference/ReferenceExtensionService.kt) 中的 `displayName`，然后只重装插件：

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew :testing:extension-reference:installDebug
```

回到插件页面并刷新。先跑通这条简短的“修改—构建—安装”链路，再创建自己的模块。

如果界面仍保留旧名称，请先停用并重新启用插件，或重启 M3UAndroid，然后再刷新列表。

## 创建自己的模块

在 SDK 正式发布前，建议先在同一个源码仓库内开发：

1. 参考 `:testing:extension-reference` 创建 Android application 模块。
2. 添加 `project(":extension:sdk-android")` 依赖。
3. 使用自己的 `applicationId` 和 service 类。
4. 在 `ExtensionManifest` 中填写稳定的插件 ID、展示名称、开发者名称和语义化版本。
5. 先实现一个 Hook，并且只声明它真正需要的能力。

修改 service 或 manifest 前，请先阅读 [理解插件模型](concepts.zh-CN.md)。

## 找不到插件时

- 运行 `adb shell pm list packages com.m3u.testing.extension.reference`，确认能看到该包名。
- 确认 **外部扩展** 仍处于开启状态。
- 安装新 APK 后重新进入插件页面并刷新。
- 如果改过包名、service、签名或插件 ID，请先点击 **取消信任**，再安装新身份并重新授权。
