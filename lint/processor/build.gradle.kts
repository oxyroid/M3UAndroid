plugins {
    kotlin("jvm")
    alias(libs.plugins.com.google.devtools.ksp)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("autoserviceKsp.verify", "true")
    arg("autoserviceKsp.verbose", "true")
}

dependencies {
    implementation(project(":lint:annotation"))

    implementation(libs.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.auto.service.annotations)

    ksp(libs.auto.service.ksp)
}
