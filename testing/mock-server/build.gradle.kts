import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.jvm.toolchain.JavaLauncher

@UntrackedTask(because = "Starts an external process whose lifetime is tracked by a PID file")
abstract class StartMockServer : DefaultTask() {
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Input
    abstract val serverMainClass: Property<String>

    @get:Input
    abstract val host: Property<String>

    @get:Input
    abstract val port: Property<String>

    @get:Internal
    abstract val pidFile: RegularFileProperty

    @get:Internal
    abstract val logFile: RegularFileProperty

    @TaskAction
    fun start() {
        val pidFile = pidFile.get().asFile
        val runningPid = pidFile
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) }

        if (runningPid != null) {
            logger.lifecycle("M3U mock server is already running with pid $runningPid")
            return
        }

        val logFile = logFile.get().asFile
        logFile.parentFile.mkdirs()
        val process = ProcessBuilder(
            javaLauncher.get().executablePath.asFile.absolutePath,
            "-cp",
            runtimeClasspath.asPath,
            serverMainClass.get(),
            "--host",
            host.get(),
            "--port",
            port.get(),
        )
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .redirectErrorStream(true)
            .start()

        pidFile.parentFile.mkdirs()
        pidFile.writeText(process.pid().toString())
        Thread.sleep(750)
        if (!process.isAlive) {
            pidFile.delete()
            error("M3U mock server exited during startup. See ${logFile.absolutePath}")
        }
        logger.lifecycle(
            "M3U mock server started on ${host.get()}:${port.get()} with pid ${process.pid()}",
        )
    }
}

@UntrackedTask(because = "Stops the external process identified by a PID file")
abstract class StopMockServer : DefaultTask() {
    @get:Internal
    abstract val pidFile: RegularFileProperty

    @TaskAction
    fun stop() {
        val pidFile = pidFile.get().asFile
        val pid = pidFile
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()

        if (pid == null) {
            pidFile.delete()
            logger.lifecycle("M3U mock server is not running")
            return
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

tasks.register<StartMockServer>("startMockServer") {
    group = "verification"
    description = "Starts the M3U mock server in the background for app tests."
    dependsOn(tasks.named("classes"))
    runtimeClasspath.from(sourceSets.main.get().runtimeClasspath)
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(17)
        },
    )
    serverMainClass.set("com.m3u.testing.mockserver.MainKt")
    host.set(providers.gradleProperty("m3uMockServerHost").orElse("0.0.0.0"))
    port.set(providers.gradleProperty("m3uMockServerPort").orElse("8080"))
    pidFile.set(mockServerPidFile)
    logFile.set(mockServerLogFile)
}

tasks.register<StopMockServer>("stopMockServer") {
    group = "verification"
    description = "Stops the background M3U mock server started by startMockServer."
    pidFile.set(mockServerPidFile)
}

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
}
