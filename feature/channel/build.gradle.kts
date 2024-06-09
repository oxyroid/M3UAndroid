plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

android {
    namespace = "com.m3u.feature.channel"
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
    packaging {
        resources.excludes += "META-INF/**"
    }
    composeCompiler {
        enableStrongSkippingMode = true
        includeSourceInformation = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.media3.exoplayer)

    implementation(libs.google.dagger.hilt)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.google.dagger.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.work)

    implementation(libs.minabox)
    implementation(libs.net.mm2d.mmupnp.mmupnp)
}
