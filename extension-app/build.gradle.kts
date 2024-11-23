plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
}

android {
    namespace = "com.m3u.extension.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.m3u.extension.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders += mapOf(
            "m3u-target" to 1, // keep this
            "m3u-extensions" to arrayOf(
                ".KodiHlsPropAnalyzer",
                ".OnlyfansWorkflow"
                // add more extensions here..
            )
                .joinToString(
                    separator = ";",
                    prefix = "",
                    postfix = ""
                )
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":extension-api"))
    compileOnly(libs.squareup.okhttp3)
    compileOnly(libs.kotlinx.coroutine.core)
    compileOnly(libs.kotlinx.serialization.json)
}