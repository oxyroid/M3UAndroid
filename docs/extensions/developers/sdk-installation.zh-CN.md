# 接入插件 SDK

[English](sdk-installation.md) · [插件开发指南](README.zh-CN.md)

当前代码会把 `1.0.0-alpha01` 预览版 SDK 构建为
`m3u-extension-sdk-1.0.0-alpha01.zip`。这个 ZIP 内是一份本地 Maven 仓库。

## 使用压缩包

解压后，在插件工程的 `settings.gradle.kts` 中加入其中的 `repository` 目录：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        maven {
            url = uri("/path/to/m3u-extension-sdk-1.0.0-alpha01/repository")
        }
        mavenCentral()
    }
}
```

插件模块只需添加一个依赖：

```kotlin
dependencies {
    implementation("io.github.oxyroid.m3u:extension-sdk-android:1.0.0-alpha01")
}
```

API 与 Android Wire 协议会作为传递依赖解析；插件只需这一项 M3UAndroid 依赖。

## 从本仓库构建压缩包

```bash
./gradlew verifyExtensionSdkBundle
```

ZIP 与 SHA-256 文件位于 `build/distributions`。独立的 Hello 示例只使用这份 Maven
仓库；以下命令会同时验证 SDK 内容，并确认独立插件工程能够引用它并完成构建：

```bash
testing/bin/verify-extension-sdk-distribution.sh
```

## 产物要求与已验证工具

| 配置 | 值 |
| --- | --- |
| SDK 要求的 `compileSdk` | 37 或更高 |
| SDK 要求的 `minSdk` | 26 或更高 |
| Java 字节码 | 17 |
| 已验证的 Gradle / AGP / Kotlin | 9.3.1 / 9.1.1 / 2.4.10 |

SDK 契约目前仍是 alpha。升级前请先阅读[兼容规则](reference/compatibility.zh-CN.md)。
