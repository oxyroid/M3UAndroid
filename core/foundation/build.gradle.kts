plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.com.google.devtools.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "com.m3u.core.foundation"
}

dependencies {
    api(project(":i18n"))

    // compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.serialization.json)

    api(libs.kotlinx.datetime)
    api(libs.androidx.paging.runtime.ktx)
    api(libs.androidx.paging.compose)
    api(libs.timber)
    api("androidx.datastore:datastore-preferences:1.1.7")
}
