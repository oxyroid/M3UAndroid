plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.m3u.ui"
    compileSdk = 34
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
    api(project(":material"))

    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.appcompat.appcompat)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.compose)

    implementation(libs.com.google.android.material.material)
    implementation(libs.androidx.media3.media3.ui)
    implementation(libs.androidx.media3.media3.exoplayer)
}
