plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    alias(libs.plugins.com.google.devtools.ksp)
}

android {
    namespace = "com.m3u.extension.runtime"
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(project(":extension:api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // reflect
    implementation(libs.org.jetbrains.kotlin.kotlin.reflect)

    // auto
    implementation(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    // wire
    implementation("com.squareup.wire:wire-runtime:4.9.2")
}