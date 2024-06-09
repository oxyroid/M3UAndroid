plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.m3u.material"
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.foundation.layout)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui.util)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.tooling.preview)
    debugApi(libs.androidx.compose.ui.tooling)

    api(libs.androidx.compose.material3.window.size.clazz)
    api(libs.androidx.constraintlayout.compose)

    api(libs.androidx.navigation.compose)

    api(libs.io.coil.kt)
    api(libs.io.coil.kt.compose)

    implementation(libs.airbnb.lottie.compose)

    api(libs.androidx.tv.material)

    api(libs.androidx.graphics.shapes.android)
    api(libs.google.material)
    api(libs.haze)

    api(libs.google.accompanist.permissions)
}
