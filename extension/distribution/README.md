# M3UAndroid extension SDK 1.0.0-alpha01

This ZIP contains a local Maven repository for building an Android extension against the
`1.0.0-alpha01` developer-preview contract.

## Add the repository

Extract the ZIP, then add its `repository` directory to the plugin project's
`settings.gradle.kts`:

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

Add the SDK to the plugin module:

```kotlin
dependencies {
    implementation("io.github.oxyroid.m3u:extension-sdk-android:1.0.0-alpha01")
}
```

The bundle includes the API, Android protocol and typed service SDK, source archives, the
conformance library, and canonical JSON examples under `golden-wire`.

## Verified toolchain

The sample plugin is verified with compile SDK 37, minimum SDK 26, Java 17, Gradle 9.3.1,
Android Gradle Plugin 9.1.1, and Kotlin 2.4.10.

## Compatibility

The contract is alpha. Keep the Android application ID, extension ID, and signing certificate
stable across updates. Copy each Hook schema version from the SDK. A different API major or an
unsupported Hook schema is rejected. Unknown optional JSON fields are ignored. Adding a required
capability requires new user authorization.

Use the `.sha256` file published beside this ZIP to verify the download before extracting it.
