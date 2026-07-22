plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":extension:api"))
    testImplementation(kotlin("test"))
}
