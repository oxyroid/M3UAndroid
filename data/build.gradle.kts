import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    id("dev.oxyroid.native-load")
}

fun ByteArray.sha256Hex(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}

fun String.asBuildConfigString(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

val nativePackIdForIntegrity = "nextlib-${libs.versions.nextLib.get()}"
val nativePackDirectoryForIntegrity = rootProject.layout.projectDirectory.dir(
    "native-packs/$nativePackIdForIntegrity"
)
val nativePackManifestForIntegrity = nativePackDirectoryForIntegrity.file(
    "m3u-codec-$nativePackIdForIntegrity.json"
)
val nativePackManifestBytesForIntegrity = providers
    .fileContents(nativePackManifestForIntegrity)
    .asBytes
    .get()
val nativePackAssetNamesForIntegrity = Regex(
    """"fileName"\s*:\s*"([^"]+\.zip)""""
).findAll(nativePackManifestBytesForIntegrity.decodeToString())
    .map { match -> match.groupValues[1] }
    .distinct()
    .sorted()
    .toList()
require(nativePackAssetNamesForIntegrity.isNotEmpty()) {
    "No codec pack assets are declared by ${nativePackManifestForIntegrity.asFile}."
}

val nativePackAssetBytesForIntegrity = nativePackAssetNamesForIntegrity.associateWith { fileName ->
    providers.fileContents(nativePackDirectoryForIntegrity.file(fileName)).asBytes.get()
}
val nativePackAssetSha256ForIntegrity = nativePackAssetBytesForIntegrity
    .mapValues { (_, bytes) -> bytes.sha256Hex() }
    .entries
    .joinToString(";") { (fileName, digest) -> "$fileName=$digest" }
val nativePackLibrarySha256ForIntegrity = nativePackAssetBytesForIntegrity
    .flatMap { (fileName, bytes) ->
        buildList {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        add("$fileName/${entry.name}" to zip.readBytes().sha256Hex())
                    }
                    zip.closeEntry()
                }
            }
        }
    }
    .sortedBy { (key, _) -> key }
    .joinToString(";") { (key, digest) -> "$key=$digest" }

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
        buildConfigField(
            "String",
            "NATIVE_PACK_EXPECTED_MANIFEST_SHA256",
            nativePackManifestBytesForIntegrity.sha256Hex().asBuildConfigString()
        )
        buildConfigField(
            "String",
            "NATIVE_PACK_EXPECTED_ASSET_SHA256",
            nativePackAssetSha256ForIntegrity.asBuildConfigString()
        )
        buildConfigField(
            "String",
            "NATIVE_PACK_EXPECTED_LIBRARY_SHA256",
            nativePackLibrarySha256ForIntegrity.asBuildConfigString()
        )
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
    implementation(libs.androidx.media3.datasource.rtmp) {
        exclude(group = "io.antmedia", module = "rtmp-client")
    }
    implementation(libs.rtmp.client.page16k)
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
    androidTestImplementation(libs.squareup.okhttp3.mockwebserver)
}

tasks.matching { task ->
    task.name.startsWith("connected") && task.name.endsWith("AndroidTest")
}.configureEach {
    dependsOn(":testing:mock-server:startMockServer")
    finalizedBy(":testing:mock-server:stopMockServer")
}

tasks.matching { task -> task.name == "connectedDebugAndroidTest" }.configureEach {
    dependsOn(":testing:extension-reference:installDebug")
    finalizedBy(":testing:extension-reference:uninstallDebug")
}
