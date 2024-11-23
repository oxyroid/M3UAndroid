plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
}

dependencies {
    compileOnly(libs.squareup.okhttp3)
    compileOnly(libs.kotlinx.coroutine.core)
    compileOnly(libs.kotlinx.serialization.json)
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}