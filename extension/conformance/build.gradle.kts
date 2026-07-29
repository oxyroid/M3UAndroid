import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    `maven-publish`
}

group = providers.gradleProperty("extensionSdkGroup").get()
version = providers.gradleProperty("extensionSdkVersion").get()

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

sourceSets {
    main {
        // Golden wire examples ship with the conformance artifact and remain the same files used
        // by the API serializer tests.
        resources.srcDir(
            rootProject.layout.projectDirectory.dir("extension/api/src/jvmTest/resources")
        )
    }
}

dependencies {
    api(project(":extension:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "extension-conformance"
            version = project.version.toString()
            pom {
                name.set("M3UAndroid Extension Conformance")
                description.set("Shared serialized-boundary conformance suite and golden fixtures.")
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
