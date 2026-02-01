plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}
android {
    namespace = "com.m3u.business.extension"
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "META-INF/**"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core:extension"))
    implementation(project(":data"))
    implementation(libs.m3u.extension.api)
    implementation(libs.m3u.extension.annotation)
    ksp(libs.m3u.extension.processor)

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.google.dagger.hilt)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.google.dagger.hilt.compiler)
}