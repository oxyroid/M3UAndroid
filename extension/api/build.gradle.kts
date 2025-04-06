plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    id("com.squareup.wire") version "5.3.1"
    id("maven-publish")
}

android {
    namespace = "com.m3u.extension.api"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        aidl = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

wire {
    kotlin {
    }
    protoPath {
        srcDir("src/main/proto")
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "com.m3u.extension"
                artifactId = "api"
                version = "1.0"

                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // wire
    implementation("com.squareup.wire:wire-runtime:4.9.2")

    // reflect
    api(libs.org.jetbrains.kotlin.kotlin.reflect)
}
