plugins {
    alias(libs.plugins.com.android.library)
}

android {
    namespace = "com.m3u.i18n"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
