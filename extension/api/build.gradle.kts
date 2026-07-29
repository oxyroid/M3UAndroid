import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    `maven-publish`
}

group = providers.gradleProperty("extensionSdkGroup").get()
version = providers.gradleProperty("extensionSdkVersion").get()

kotlin {
    jvm()
    jvmToolchain(17)
    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = when (name) {
            "kotlinMultiplatform" -> "extension-api"
            "jvm" -> "extension-api-jvm"
            else -> artifactId
        }
        pom {
            name.set("M3UAndroid Extension API")
            description.set("Stable typed contracts shared by M3UAndroid hosts and extensions.")
            url.set("https://github.com/oxyroid/M3UAndroid")
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
