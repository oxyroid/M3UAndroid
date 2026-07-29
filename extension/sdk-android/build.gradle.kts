import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    `maven-publish`
}

android {
    namespace = "com.m3u.extension.sdk.android"
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
    implementation(project(":extension:transport-protocol-android"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":extension:conformance"))
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "extension-sdk-android"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("M3UAndroid Extension SDK for Android")
                description.set("Typed Android service SDK for standalone M3UAndroid extensions.")
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
