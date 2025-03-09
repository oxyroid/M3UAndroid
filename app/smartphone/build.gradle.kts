import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.baselineprofile)
    id("kotlin-parcelize")
}

android {
    namespace = "com.m3u.smartphone"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.m3u.smartphone"
        minSdk = 26
        targetSdk = 33
        versionCode = 144
        versionName = "1.14.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += setOf("channel", "codec")
    productFlavors {
        // Official
        create("stableChannel") {
            dimension = "channel"
            isDefault = true
        }
        // Github Workflow
        create("snapshotChannel") {
            dimension = "channel"
            versionNameSuffix =
                "-snapshot[${LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmm"))}]"
            applicationIdSuffix = ".snapshot"
        }
        create("richCodec") {
            dimension = "codec"
            isDefault = true
        }
        create("liteCodec") {
            dimension = "codec"
            versionNameSuffix = "-lite"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
        all {
            isCrunchPngs = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    aaptOptions.cruncherEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "META-INF/**"
    }
    applicationVariants.all {
        outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                val abi = output.getFilter("ABI") ?: "universal"
                val versionName = output.versionNameOverride
                output.outputFileName = "${versionName}_$abi.apk"
            }
    }
}

hilt {
    enableAggregatingTask = true
}

baselineProfile {
    dexLayoutOptimization = true
    saveInSrc = true
}

dependencies {
    implementation(project(":core"))
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
    "baselineProfile"(project(":baselineprofile"))
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
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.layout)
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
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.airbnb.lottie.compose)
    implementation(libs.minabox)
    implementation(libs.net.mm2d.mmupnp.mmupnp)
    implementation(libs.haze)
    // FIXME: for m2 BackdropScaffold only
    implementation("androidx.compose.material:material")
}
