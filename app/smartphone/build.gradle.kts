@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.baselineprofile)
    id("kotlin-parcelize")
    id("dev.oxyroid.native-load")
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi",
            "com.google.accompanist.permissions.ExperimentalPermissionsApi",
        )
    }
}

val m3uMockServerUrl = providers.gradleProperty("m3uMockServerUrl").orElse("http://10.0.2.2:8080")
val useAndroidTestOrchestrator = providers.gradleProperty("m3uUseTestOrchestrator")
    .map(String::toBoolean)
    .orElse(false)
val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { file -> file.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun localDebugFixtureValue(
    propertyName: String,
    environmentName: String,
): String = providers.gradleProperty(propertyName).orNull
    ?: providers.environmentVariable(environmentName).orNull
    ?: localProperties.getProperty(propertyName).orEmpty()

fun String.asBuildConfigString(): String = buildString(length + 2) {
    append('"')
    this@asBuildConfigString.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> append(character)
        }
    }
    append('"')
}

val debugEmbyBaseUrl = localDebugFixtureValue(
    propertyName = "m3u.debug.emby.baseUrl",
    environmentName = "M3U_DEBUG_EMBY_BASE_URL",
).trim()
val debugEmbyUsername = localDebugFixtureValue(
    propertyName = "m3u.debug.emby.username",
    environmentName = "M3U_DEBUG_EMBY_USERNAME",
).trim()
val debugEmbyPassword = localDebugFixtureValue(
    propertyName = "m3u.debug.emby.password",
    environmentName = "M3U_DEBUG_EMBY_PASSWORD",
)
val debugEmbyValues = listOf(
    debugEmbyBaseUrl,
    debugEmbyUsername,
    debugEmbyPassword,
)
require(debugEmbyValues.all(String::isBlank) || debugEmbyValues.none(String::isBlank)) {
    "Configure all of m3u.debug.emby.baseUrl, username, and password, or leave all three unset"
}

android {
    namespace = "com.m3u.smartphone"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.m3u.smartphone"
        minSdk = 26
        targetSdk = 33
        versionCode = 145
        versionName = "1.15.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["m3uMockServerUrl"] = m3uMockServerUrl.get()
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isPseudoLocalesEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField(
                "String",
                "DEBUG_EMBY_BASE_URL",
                debugEmbyBaseUrl.asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "DEBUG_EMBY_USERNAME",
                debugEmbyUsername.asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "DEBUG_EMBY_PASSWORD",
                debugEmbyPassword.asBuildConfigString(),
            )
        }
        all {
            isCrunchPngs = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            val benchmark = project
                .properties
                .keys
                .find { it.contains("testInstrumentationRunnerArguments") } != null

            val snapshotChannel = gradle
                .startParameter
                .taskNames
                .find { it.contains("snapshotChannel", ignoreCase = true) } != null

            val richCodec = gradle
                .startParameter
                .taskNames
                .find { it.contains("richCodec", ignoreCase = true) } != null

            isEnable = !benchmark && !snapshotChannel && richCodec

            reset()
            include("x86", "x86_64", "arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "META-INF/**"
    }
    testOptions {
        execution = if (useAndroidTestOrchestrator.get()) {
            "ANDROID_TEST_ORCHESTRATOR"
        } else {
            "HOST"
        }
        managedDevices.allDevices {
            create<ManagedVirtualDevice>("hostileApi34") {
                device = "Pixel 6 Pro"
                apiLevel = 34
                systemImageSource = "aosp"
            }
        }
    }
}

tasks.matching { task ->
    task.name.startsWith("connected") && task.name.endsWith("AndroidTest")
}.configureEach {
    dependsOn(":testing:mock-server:startMockServer")
    finalizedBy(":testing:mock-server:stopMockServer")
}

tasks.withType<KotlinCompile>().configureEach {
    if (name == "compileDebugAndroidTestKotlin") {
        compilerOptions.freeCompilerArgs.add("-Xno-param-assertions")
    }
}

hilt {
    enableAggregatingTask = true
}

baselineProfile {
    dexLayoutOptimization = true
    saveInSrc = true
    mergeIntoMain = true
}

dependencies {
    implementation(project(":extension:api"))
    implementation(project(":i18n"))
    implementation(project(":core:foundation"))
    implementation(project(":data"))
    // business
    implementation(project(":business:foryou"))
    implementation(project(":business:favorite"))
    implementation(project(":business:setting"))
    implementation(project(":business:playlist"))
    implementation(project(":business:channel"))
    implementation(project(":business:playlist-configuration"))
    // baselineprofile
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile:smartphone"))
    // base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.google.material)
    // lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    // work
    implementation(libs.androidx.work.runtime.ktx)
    // dagger
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    // compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.navigation.compose)
    // compose-material3
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size.clazz)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    // glance
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // accompanist
    implementation(libs.google.accompanist.permissions)
    // performance
    debugImplementation(libs.squareup.leakcanary)
    // other
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.io.coil.kt)
    implementation(libs.io.coil.kt.compose)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.airbnb.lottie.compose)
    implementation(libs.minabox)
    implementation(libs.net.mm2d.mmupnp.mmupnp)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.backdrop)
    implementation(libs.acra.notification)
    implementation(libs.acra.mail)

    testImplementation(kotlin("test-junit"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(project(":extension:conformance"))
    androidTestImplementation(project(":extension:runtime"))
    androidTestImplementation(project(":extension:transport-android"))
    "androidTestUtil"(libs.androidx.test.orchestrator)
    "androidTestUtil"(
        project(
            path = ":testing:extension-reference",
            configuration = "debugApkForHostTests",
        )
    )
}
