plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.org.jetbrains.kotlin.serialization)
    id("com.squareup.wire")
    id("maven-publish")
}

android {
    namespace = "com.m3u.extension.api"
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        aidl = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

wire {
    kotlin {
    }
    protoPath {
        srcDir("src/main/proto")
    }
}
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "com.m3u.extension"
                artifactId = "api"
                version = "1.0"

                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // wire
    implementation("com.squareup.wire:wire-runtime:4.9.2")

    // reflect
    api(libs.org.jetbrains.kotlin.kotlin.reflect)
}