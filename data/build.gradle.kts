plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    id("dev.oxyroid.native-load")
}

val m3uMockServerUrl = providers.gradleProperty("m3uMockServerUrl").orElse("http://10.0.2.2:8080")

android {
    namespace = "com.m3u.data"
    ksp {
        arg("room.schemaLocation", "${projectDir}/schemas")
        arg("ksp.incremental", "true")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "NEXTLIB_CODEC_VERSION", "\"${libs.versions.nextLib.get()}\"")
        testInstrumentationRunnerArguments["m3uMockServerUrl"] = m3uMockServerUrl.get()
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    packaging {
        resources.excludes += "META-INF/**"
    }
}

dependencies {
    implementation(project(":core:foundation"))
    implementation(project(":extension:runtime"))
    implementation(project(":extension:transport-android"))
    implementation("dev.oxyroid.parser:m3u")
    api("dev.oxyroid.parser:xtream")
    implementation(project(":lint:annotation"))
    ksp(project(":lint:processor"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.coverter.retrofit)

    implementation(libs.io.coil.kt)

    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.squareup.retrofit2)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.exoplayer.workmanager)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.container)
    implementation(libs.androidx.media3.datasource.rtmp)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.muxer)

    implementation(libs.nextlib.media3ext)
    implementation(libs.nextlib.mediainfo)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    debugImplementation(libs.chucker)
    releaseImplementation(libs.chucker.no.op)
    debugImplementation(libs.slf4j.api)
    debugImplementation(libs.logback.android)

    implementation(libs.jakewharton.disklrucache)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

tasks.matching { task ->
    task.name.startsWith("connected") && task.name.endsWith("AndroidTest")
}.configureEach {
    dependsOn(":testing:mock-server:startMockServer")
    finalizedBy(":testing:mock-server:stopMockServer")
}
