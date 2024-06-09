plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.com.google.devtools.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "com.m3u.core"
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":i18n"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    api(libs.androidx.compose.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.squareup.retrofit2)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)

    api(libs.kotlinx.datetime)

    api(libs.androidx.paging.runtime.ktx)
    api(libs.androidx.paging.compose)
}