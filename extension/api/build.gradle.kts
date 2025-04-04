plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    id("com.squareup.wire")
}

android {
    namespace = "com.m3u.extension.api"
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        aidl = true
    }
}

wire {
    kotlin {
    }
    protoPath {
        srcDir("src/main/proto")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // wire
    implementation("com.squareup.wire:wire-runtime:4.9.2")

    // reflect
    api(libs.org.jetbrains.kotlin.kotlin.reflect)
}