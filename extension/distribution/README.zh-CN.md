# M3UAndroid 插件 SDK 1.0.0-alpha01

这个 ZIP 内是一份本地 Maven 仓库，用于按照 `1.0.0-alpha01` 开发者预览契约构建
Android 插件。

## 添加仓库

解压 ZIP，然后在插件工程的 `settings.gradle.kts` 中加入其中的 `repository` 目录：

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

在插件模块中添加 SDK：

```kotlin
dependencies {
    implementation("io.github.oxyroid.m3u:extension-sdk-android:1.0.0-alpha01")
}
```

压缩包包含 API、Android 协议、类型化 Service SDK、源码包、一致性测试库，以及
`golden-wire` 目录中的标准 JSON 示例。

## 已验证工具链

示例插件使用 compile SDK 37、最低 SDK 26、Java 17、Gradle 9.3.1、
Android Gradle Plugin 9.1.1 和 Kotlin 2.4.10 完成验证。

## 兼容规则

当前契约仍为 alpha。升级时应保持 Android application ID、插件 ID 和签名证书不变。
每个 Hook 的 schema 版本都从 SDK 复制。API major 不同或 Hook schema 不受支持时，
宿主会拒绝插件；未知的可选 JSON 字段会被忽略；新增必要 capability 后需要用户重新授权。

解压前，请使用与 ZIP 一同发布的 `.sha256` 文件校验下载内容。
