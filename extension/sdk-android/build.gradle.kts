plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
}

android { namespace = "com.m3u.extension.sdk.android" }

dependencies {
    api(project(":extension:api"))
    implementation(project(":extension:transport-protocol-android"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":extension:conformance"))
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}
