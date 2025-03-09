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
    namespace = "com.m3u.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.m3u.tv"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    
    implementation(project(":business:foryou"))
    implementation(project(":business:favorite"))
    implementation(project(":business:setting"))
    implementation(project(":business:playlist"))
    implementation(project(":business:channel"))
    implementation(project(":business:playlist-configuration"))
//    implementation(libs.androidx.profileinstaller)
//    "baselineProfile"(project(":baselineprofile"))

    // tv
    api(libs.androidx.tv.material)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.androidx.core.splashscreen)

    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.work)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    debugImplementation(libs.squareup.leakcanary)
}
