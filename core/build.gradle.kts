plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "com.m3u.core"
}

dependencies {
    api(project(":core:foundation"))
}
