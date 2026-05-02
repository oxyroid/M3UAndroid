plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    application
}

application {
    mainClass = "com.m3u.testing.devicebenchmark.MainKt"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named("run") {
    dependsOn(":app:smartphone:assembleDebug")
    dependsOn(":app:tv:assembleDebug")
    dependsOn(":testing:mock-server:startMockServer")
    finalizedBy(":testing:mock-server:stopMockServer")
}
