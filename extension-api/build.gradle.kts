plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    id("maven-publish")
}

dependencies {
    compileOnly(libs.squareup.okhttp3)
    compileOnly(libs.kotlinx.coroutine.core)
    compileOnly(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.m3u"
            artifactId = "extension-api"
            version = "1.0.0"
        }
    }
}