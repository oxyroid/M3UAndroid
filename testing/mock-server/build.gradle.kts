plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    application
}

application {
    mainClass = "com.m3u.testing.mockserver.MainKt"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val mockServerPidFile = layout.buildDirectory.file("mock-server/mock-server.pid")
val mockServerLogFile = layout.buildDirectory.file("mock-server/mock-server.log")

tasks.register("startMockServer") {
    group = "verification"
    description = "Starts the M3U mock server in the background for app tests."
    notCompatibleWithConfigurationCache("Starts an external mock server process.")
    dependsOn(tasks.named("classes"))

    doLast {
        val pidFile = mockServerPidFile.get().asFile
        val runningPid = pidFile
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) }

        if (runningPid != null) {
            logger.lifecycle("M3U mock server is already running with pid $runningPid")
            return@doLast
        }

        val logFile = mockServerLogFile.get().asFile
        logFile.parentFile.mkdirs()
        val launcher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(17)
        }.get()
        val port = providers.gradleProperty("m3uMockServerPort").orElse("8080").get()
        val host = providers.gradleProperty("m3uMockServerHost").orElse("0.0.0.0").get()
        val classpath = sourceSets.main.get().runtimeClasspath.asPath
        val process = ProcessBuilder(
            launcher.executablePath.asFile.absolutePath,
            "-cp",
            classpath,
            "com.m3u.testing.mockserver.MainKt",
            "--host",
            host,
            "--port",
            port
        )
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .redirectErrorStream(true)
            .start()

        pidFile.writeText(process.pid().toString())
        Thread.sleep(750)
        if (!process.isAlive) {
            pidFile.delete()
            error("M3U mock server exited during startup. See ${logFile.absolutePath}")
        }
        logger.lifecycle("M3U mock server started on $host:$port with pid ${process.pid()}")
    }
}

tasks.register("stopMockServer") {
    group = "verification"
    description = "Stops the background M3U mock server started by startMockServer."
    notCompatibleWithConfigurationCache("Stops an external mock server process.")

    doLast {
        val pidFile = mockServerPidFile.get().asFile
        val pid = pidFile
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()

        if (pid == null) {
            logger.lifecycle("M3U mock server is not running")
            return@doLast
        }

        ProcessHandle.of(pid).ifPresent { handle ->
            if (handle.isAlive) {
                handle.destroy()
                handle.onExit().get()
            }
        }
        pidFile.delete()
        logger.lifecycle("M3U mock server stopped")
    }
}

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.kotlinx.serialization.json)
}
