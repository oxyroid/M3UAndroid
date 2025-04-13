plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
}

android {
    namespace = "com.m3u.data.codec.rich"
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":data:codec"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.nextlib.media3ext)
    implementation(libs.nextlib.mediainfo)

    implementation(libs.auto.service.annotations)

    ksp(libs.auto.service.ksp)
}