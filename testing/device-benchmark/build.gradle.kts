plugins {
    base
}

val pythonExecutable = providers.gradleProperty("pythonExecutable")
    .orElse("${rootDir}/.venv/bin/python")
val moblyConfig = providers.gradleProperty("moblyConfig")
    .orElse("${projectDir}/mobly_config.yml")
val moblyLogPath = layout.buildDirectory.dir("mobly-results")

tasks.register<Exec>("installMoblyDependencies") {
    group = "verification"
    description = "Installs Python dependencies for Mobly device benchmarks."
    commandLine(
        pythonExecutable.get(),
        "-m",
        "pip",
        "install",
        "-r",
        "${projectDir}/requirements.txt"
    )
}

tasks.register<Exec>("run") {
    group = "verification"
    description = "Runs the Mobly remote-control phone-to-TV subscription benchmark."
    dependsOn(":app:smartphone:assembleDebug")
    dependsOn(":app:tv:assembleDebug")
    dependsOn(":testing:mock-server:startMockServer")
    dependsOn("installMoblyDependencies")
    finalizedBy(":testing:mock-server:stopMockServer")
    workingDir = rootDir

    doFirst {
        moblyLogPath.get().asFile.mkdirs()
        commandLine(
            pythonExecutable.get(),
            "${projectDir}/mobly/remote_control_subscribe_test.py",
            "-c",
            moblyConfig.get(),
            "--log_path",
            moblyLogPath.get().asFile.absolutePath
        )
    }
}
