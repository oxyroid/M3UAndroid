import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "com.m3u.extension.transport.protocol.android"
    buildFeatures { aidl = true }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

group = providers.gradleProperty("extensionSdkGroup").get()
version = providers.gradleProperty("extensionSdkVersion").get()

dependencies {
    api(project(":extension:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "extension-transport-protocol-android"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("M3UAndroid Extension Android Protocol")
                description.set("AIDL control plane and file-backed wire protocol for extensions.")
                url.set("https://github.com/oxyroid/M3UAndroid")
            }
        }
    }
    repositories {
        maven {
            name = "extensionSdk"
            url = rootProject.layout.buildDirectory
                .dir("extension-sdk/repository")
                .get()
                .asFile
                .toURI()
        }
    }
}
