plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.com.google.dagger.hilt.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.m3u.ui"
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "META-INF/**"
    }
}

dependencies {
    implementation(project(":core"))
    api(project(":material"))
    api(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.google.material)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer)

    implementation(libs.google.dagger.hilt)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.google.dagger.hilt.compiler)
}
