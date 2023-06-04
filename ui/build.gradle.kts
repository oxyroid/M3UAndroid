@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "com.m3u.ui"
    compileSdk = 33
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.core.ktx)

    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx)

    api(libs.bundles.androidx.compose)
    api(libs.androidx.compose.material.material.icons.extended)
    api(libs.androidx.compose.material3.material3)
    debugApi(libs.androidx.compose.ui.ui.tooling)
    api(libs.androidx.compose.ui.ui.tooling.preview)

    implementation(libs.androidx.tv.tv.foundation)
    implementation(libs.androidx.tv.tv.material)

    api(libs.androidx.navigation.navigation.compose)

    api(libs.com.google.accompanist.accompanist.navigation.animation)
    api(libs.com.google.accompanist.accompanist.systemuicontroller)

    implementation(libs.androidx.media3.media3.ui)
    implementation(libs.androidx.media3.media3.exoplayer)

    implementation(libs.io.coil.kt.coil)
    implementation(libs.io.coil.kt.coil.compose)

    implementation(libs.com.airbnb.android.lottie.compose)
}
