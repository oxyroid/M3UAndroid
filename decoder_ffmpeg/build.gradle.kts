plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        aarMetadata {
            minCompileSdk = 26
        }
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
}

if (project.file("src/main/jni/ffmpeg").exists()) {
    android.externalNativeBuild.cmake.path = File("src/main/jni/CMakeLists.txt")
    android.externalNativeBuild.cmake.version = "3.21.0+"
}

dependencies {
    implementation(libs.androidx.media3.media3.exoplayer)
    implementation(libs.androidx.media3.media3.decoder)
    implementation (libs.androidx.annotation)
    compileOnly (libs.checker.qual)
    compileOnly (libs.kotlin.annotations.jvm)
}
