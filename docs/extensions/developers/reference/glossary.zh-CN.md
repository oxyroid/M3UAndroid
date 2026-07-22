# 术语与插件身份

[English](glossary.md) · [插件开发指南](../README.zh-CN.md)

| 名称 | 谁使用它 | Hello 示例 |
| --- | --- | --- |
| `applicationId` | 插件可信身份中的 package 部分 | `com.m3u.samples.hello.extension` |
| Service 类 | M3UAndroid 为该插件绑定的组件 | `HelloExtensionService` |
| `ExtensionId` | M3UAndroid runtime 与 catalog 使用的身份 | `com.m3u.samples.hello` |
| `ExtensionManifest` | 插件向宿主声明名称、版本、设置和 Hook | `HelloExtensionService.kt` 中的 Kotlin 对象 |
| Hook | 宿主对插件发起的一次有固定输入和输出的调用 | `settings.schema.contribute` |
| Capability | 用户授权插件执行的一类操作 | `settings.contribute` |
| Provider | 提供频道订阅、刷新和播放地址的一项服务 | 内置的 Emby、Jellyfin |

## 两个 manifest

两者定义 M3UAndroid 插件的不同部分：

- [`AndroidManifest.xml`](../../../../samples/hello-extension/src/main/AndroidManifest.xml) 注册插件 Service。
- `ExtensionManifest` 声明插件在 M3UAndroid 中的身份、设置、capability 和 Hook。

## 四项稳定身份

插件发布后，应保持以下内容稳定：

1. Android `applicationId`
2. Service 类名
3. APK 签名证书
4. `ExtensionId`

其中任何一项改变，都可能让宿主把它视为新的插件，或要求用户重新确认身份。
