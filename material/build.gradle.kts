plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.m3u.material"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeCompiler {
        enableStrongSkippingMode = true
        includeSourceInformation = true
    }
}

dependencies {
    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.activity.activity.compose)

    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.foundation.layout)
    api(libs.androidx.compose.material.iconsExtended)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui.util)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.tooling.preview)
    debugApi(libs.androidx.compose.ui.tooling)

    api(libs.androidx.compose.material3.material3.window.size.clazz)
    api(libs.androidx.constraintlayout.constraintlayout.compose)

    api(libs.androidx.navigation.navigation.compose)

    api(libs.io.coil.kt.coil)
    api(libs.io.coil.kt.coil.compose)

    implementation(libs.com.airbnb.android.lottie.compose)

    api(libs.androidx.tv.tv.foundation)
    api(libs.androidx.tv.tv.material)

    api(libs.androidx.graphics.shapes.android)
    api(libs.com.google.android.material.material)
    api(libs.haze)

    api(libs.com.google.accompanist.accompanist.permissions)
}
