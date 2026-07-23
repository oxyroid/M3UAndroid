plugins {
    alias(libs.plugins.com.android.library)
}

android {
    namespace = "com.m3u.core"
}

dependencies {
    api(project(":core:foundation"))
}
