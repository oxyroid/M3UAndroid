plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.com.google.dagger.hilt.android)
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
    implementation(libs.m3u.extension.api)
    implementation(libs.m3u.extension.annotation)
    ksp(libs.m3u.extension.processor)
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // hilt
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // auto
    implementation(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    // wire
    implementation("com.squareup.wire:wire-runtime:4.9.2")
}