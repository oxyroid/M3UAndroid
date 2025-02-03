import java.util.Properties

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":extension-api")) // TODO: Publish to MavenCentral
    implementation(libs.squareup.okhttp3)
    implementation(libs.kotlinx.coroutine.core)
    implementation(libs.kotlinx.serialization.json)
}

val jarProvider = tasks.register<Jar>("compileFullJar") {
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Exec>("convertToDex") {
    dependsOn("compileFullJar")
    val sourceName = jarProvider.flatMap { it.archiveFileName }.orNull
    val properties = Properties().apply {
        load(project.rootProject.file("local.properties").inputStream())
    }
    val dexOutput = file("build/dex")
    dexOutput.mkdirs()
    commandLine = listOf(
        "${properties["sdk.dir"]}/build-tools/34.0.0/d8",
        "--output", dexOutput.absolutePath,
        "build/libs/$sourceName"
    )
}

tasks.register("assembleExtension") {
    dependsOn("convertToDex")
}

fun readExtensionFileName(): String {
    val properties = Properties().apply {
        load(project.file("extension.properties").inputStream())
    }
    val namespace = checkNotNull(properties["extension.namespace"] as? String) {
        "You haven't defined the extension.namespace in extension-dex module extension.properties file."
    }
    val version = checkNotNull((properties["extension.version"] as? String)?.toIntOrNull()) {
        "You haven't defined the extension.version in extension-dex module extension.properties file."
    }
    val author = checkNotNull(properties["extension.author"] as? String) {
        "You haven't defined the extension.author in extension-dex module extension.properties file."
    }
    return "$namespace:$author:$version"
}