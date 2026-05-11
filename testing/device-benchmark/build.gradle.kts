plugins {
    base
}

val pythonExecutable = providers.gradleProperty("pythonExecutable")
    .orElse("${rootDir}/.venv/bin/python")
val moblyConfig = providers.gradleProperty("moblyConfig")
    .orElse("${projectDir}/mobly_config.yml")
val moblyBenchmarkScript = providers.gradleProperty("moblyBenchmarkScript")
    .orElse("${projectDir}/mobly/full_feature_benchmark_test.py")
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
    description = "Runs the Mobly full-feature device benchmark suite."
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
            moblyBenchmarkScript.get(),
            "-c",
            moblyConfig.get(),
            "--log_path",
            moblyLogPath.get().asFile.absolutePath
        )
    }
}
