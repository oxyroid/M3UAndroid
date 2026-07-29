plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
}

android {
    namespace = "com.m3u.testing.extension.reference"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.m3u.testing.extension.reference"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val debugApkForHostTests by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(
        debugApkForHostTests.name,
        layout.buildDirectory.file("outputs/apk/debug/extension-reference-debug.apk"),
    ) {
        type = "apk"
        builtBy("assembleDebug")
    }
}

dependencies {
    implementation(project(":extension:conformance"))
    implementation(project(":extension:sdk-android"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
}
