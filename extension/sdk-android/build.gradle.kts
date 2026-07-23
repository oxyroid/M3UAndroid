plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
}

android { namespace = "com.m3u.extension.sdk.android" }

dependencies {
    api(project(":extension:api"))
    api(project(":extension:transport-android"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
