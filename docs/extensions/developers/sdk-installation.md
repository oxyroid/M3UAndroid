# Add the extension SDK

[简体中文](sdk-installation.zh-CN.md) · [Developer guide](README.md)

This checkout builds the `1.0.0-alpha01` preview SDK as
`m3u-extension-sdk-1.0.0-alpha01.zip`. The ZIP contains a local Maven repository.

## Use the bundle

Extract the zip and add its `repository` directory to your plugin's `settings.gradle.kts`:

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

Add one dependency to the plugin module:

```kotlin
dependencies {
    implementation("io.github.oxyroid.m3u:extension-sdk-android:1.0.0-alpha01")
}
```

The API and Android wire protocol are resolved transitively. This is the only M3UAndroid dependency
the plugin needs.

## Build the bundle from this repository

```bash
./gradlew verifyExtensionSdkBundle
```

The ZIP and its SHA-256 file are written to `build/distributions`. The independent Hello sample
uses only that Maven repository; this command verifies both publication and consumption:

```bash
testing/bin/verify-extension-sdk-distribution.sh
```

## Artifact requirements and verified tools

| Setting | Value |
| --- | --- |
| `compileSdk` required by the SDK | 37 or newer |
| `minSdk` required by the SDK | 26 or newer |
| Java bytecode | 17 |
| Verified Gradle / AGP / Kotlin | 9.3.1 / 9.1.1 / 2.4.10 |

The SDK is still an alpha contract. Before updating it, review the
[compatibility rules](reference/compatibility.md).
