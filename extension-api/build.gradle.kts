plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
}

dependencies {
    api(libs.squareup.okhttp3)
    api(libs.kotlinx.coroutine.core)
}