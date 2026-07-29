plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
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
