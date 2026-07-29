import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
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
        val logFile = logFile.get().asFile
        val serverPort = port.get().toIntOrNull()
            ?.takeIf { value -> value in 1..65_535 }
            ?: error("Invalid M3U mock server port: ${port.get()}")
        val healthUrl = URI("http://127.0.0.1:$serverPort/health").toURL()
        val runningProcess = pidFile
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
            ?.let { pid -> ProcessHandle.of(pid).orElse(null) }
            ?.takeIf { process -> process.isAlive }

        if (runningProcess != null) {
            awaitHealthy(
                pid = runningProcess.pid(),
                healthUrl = healthUrl,
                isProcessAlive = runningProcess::isAlive,
                logFile = logFile,
            )
            logger.lifecycle(
                "M3U mock server is already running and healthy at $healthUrl " +
                    "with pid ${runningProcess.pid()}",
            )
            return
        }

        pidFile.delete()
        logFile.parentFile.mkdirs()
        val process = ProcessBuilder(
            javaLauncher.get().executablePath.asFile.absolutePath,
            "-cp",
            runtimeClasspath.asPath,
            serverMainClass.get(),
            "--host",
            host.get(),
            "--port",
            serverPort.toString(),
        )
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .redirectErrorStream(true)
            .start()

        pidFile.parentFile.mkdirs()
        pidFile.writeText(process.pid().toString())
        try {
            awaitHealthy(
                pid = process.pid(),
                healthUrl = healthUrl,
                isProcessAlive = process::isAlive,
                logFile = logFile,
            )
        } catch (failure: Exception) {
            if (process.isAlive) process.destroyForcibly()
            pidFile.delete()
            throw failure
        }
        logger.lifecycle(
            "M3U mock server started and is healthy at $healthUrl with pid ${process.pid()}",
        )
    }

    private fun awaitHealthy(
        pid: Long,
        healthUrl: URL,
        isProcessAlive: () -> Boolean,
        logFile: java.io.File,
    ) {
        val deadlineNanos = System.nanoTime() + HEALTH_TIMEOUT_MILLIS * NANOS_PER_MILLISECOND
        var lastProbeFailure = "health probe was not attempted"
        while (true) {
            if (!isProcessAlive()) {
                error(
                    "M3U mock server process $pid exited before $healthUrl became healthy. " +
                        "See ${logFile.absolutePath}",
                )
            }
            val probeFailure = probeHealth(healthUrl)
            if (probeFailure == null) return
            lastProbeFailure = probeFailure

            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) break
            Thread.sleep(
                minOf(
                    HEALTH_POLL_INTERVAL_MILLIS,
                    (remainingNanos / NANOS_PER_MILLISECOND).coerceAtLeast(1L),
                )
            )
        }
        error(
            "M3U mock server process $pid did not become healthy at $healthUrl within " +
                "$HEALTH_TIMEOUT_MILLIS ms. Last probe: $lastProbeFailure. " +
                "See ${logFile.absolutePath}",
        )
    }

    private fun probeHealth(healthUrl: URL): String? {
        val connection = try {
            healthUrl.openConnection() as HttpURLConnection
        } catch (failure: Exception) {
            return failure.describeForHealthProbe()
        }
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = HEALTH_REQUEST_TIMEOUT_MILLIS
            connection.readTimeout = HEALTH_REQUEST_TIMEOUT_MILLIS
            val statusCode = connection.responseCode
            if (statusCode != HttpURLConnection.HTTP_OK) {
                "HTTP $statusCode"
            } else {
                val body = connection.inputStream.bufferedReader().use { reader ->
                    reader.readLine().orEmpty().trim()
                }
                if (body == HEALTH_RESPONSE_BODY) {
                    null
                } else {
                    "unexpected response body '${body.take(MAX_REPORTED_HEALTH_BODY_LENGTH)}'"
                }
            }
        } catch (failure: Exception) {
            failure.describeForHealthProbe()
        } finally {
            connection.disconnect()
        }
    }

    private fun Exception.describeForHealthProbe(): String =
        "${javaClass.simpleName}: ${message.orEmpty().lineSequence().firstOrNull().orEmpty()}"

    private companion object {
        const val HEALTH_TIMEOUT_MILLIS = 10_000L
        const val HEALTH_POLL_INTERVAL_MILLIS = 100L
        const val HEALTH_REQUEST_TIMEOUT_MILLIS = 500
        const val HEALTH_RESPONSE_BODY = "ok"
        const val MAX_REPORTED_HEALTH_BODY_LENGTH = 80
        const val NANOS_PER_MILLISECOND = 1_000_000L
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
