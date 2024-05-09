plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "com.m3u.data"
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
    ksp {
        arg("room.schemaLocation", "${projectDir}/schemas")
        arg("ksp.incremental", "true")
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.media3.exoplayer.workmanager)

    val richCodec = gradle
        .startParameter
        .taskNames
        .find { it.contains("richCodec", ignoreCase = true) } != null
    if (richCodec) {
        implementation(project(":codec:rich"))
    } else {
        implementation(project(":codec:lite"))
    }

    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.appcompat.appcompat)

    implementation(libs.androidx.room.room.runtime)
    implementation(libs.androidx.room.room.paging)
    implementation(libs.androidx.room.room.ktx)
    ksp(libs.androidx.room.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.coverter.retrofit)

    implementation(libs.io.coil.kt.coil)

    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.compiler)

    implementation(libs.com.squareup.retrofit2.retrofit)

    implementation(libs.androidx.media3.media3.exoplayer)
    implementation(libs.androidx.media3.media3.exoplayer.dash)
    implementation(libs.androidx.media3.media3.exoplayer.hls)
    implementation(libs.androidx.media3.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.media3.session)
    implementation(libs.androidx.media3.media3.container)
    implementation(libs.androidx.media3.media3.datasource.rtmp)
    implementation(libs.androidx.media3.media3.datasource.okhttp)
    implementation(libs.androidx.media3.media3.extractor)

    implementation(libs.androidx.work.work.runtime.ktx)
    implementation(libs.androidx.hilt.hilt.work)
    ksp(libs.androidx.hilt.hilt.compiler)

    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    debugImplementation(libs.chucker)
    releaseImplementation(libs.chucker.no.op)
    debugImplementation(libs.slf4j.api)
    debugImplementation(libs.logback.android)
}