plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    `maven-publish`
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
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

dependencies {
    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.activity.activity.compose)

    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx)

    api(libs.bundles.androidx.compose)
    api(libs.androidx.compose.material.material.icons.extended)
    api(libs.androidx.compose.material3.material3)
    api(libs.androidx.compose.material3.material3.window.size.clazz)
    debugApi(libs.androidx.compose.ui.ui.tooling)
    debugApi(libs.androidx.compose.ui.ui.tooling.preview)

    api(libs.androidx.navigation.navigation.compose)

    api(libs.io.coil.kt.coil)
    api(libs.io.coil.kt.coil.compose)

    implementation(libs.com.airbnb.android.lottie.compose)

    api(libs.kotlinx.collections.immutable)

    api(libs.androidx.tv.tv.foundation)
    api(libs.androidx.tv.tv.material)

    api(libs.com.google.android.material.material)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.m3u"
                artifactId = "material"
                version = "1.0.0"

                from(components["release"])
            }
        }
    }
}
