package com.m3u.testing.devicebenchmark

import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.time.Instant
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val PHONE_PACKAGE = "com.m3u.smartphone"
private const val TV_PACKAGE = "com.m3u.tv"
private const val TV_LAUNCH_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER"
private const val PHONE_SUBSCRIBE_ACTION = "com.m3u.smartphone.REMOTE_SUBSCRIBE_M3U"
private const val TV_SUBSCRIBE_PORT = 8989
private const val HOST_BRIDGE_PORT = 8998
private const val MOCK_SERVER_PORT = 8080

fun main(args: Array<String>) {
    val options = Options.parse(args.toList())
    val report = runCatching {
        PhoneTvSubscribeBenchmark(options).run()
    }.getOrElse { error ->
        System.err.println(error.message)
        if (options.failOnMissingDevice) exitProcess(1)
        BenchmarkReport.failure(options, error)
    }

    val reportFile = repoFile("testing/device-benchmark/build/reports/phone-tv-subscribe-m3u.json")
    reportFile.parentFile.mkdirs()
    reportFile.writeText(report.toJson())
    println(report.toHumanSummary())
    println("Report: ${reportFile.absolutePath}")

    if (!report.success && options.failOnMissingDevice) exitProcess(1)
}

private class PhoneTvSubscribeBenchmark(
    private val rawOptions: Options
) {
    private val adb = Adb(rawOptions.adbPath)

    fun run(): BenchmarkReport {
        val options = rawOptions.resolve(adb)
        require(adb.isOnline(options.phoneSerial)) {
            "Phone device '${options.phoneSerial}' is not online. Connected devices:\n${adb.devices()}"
        }
        require(adb.isOnline(options.tvSerial)) {
            "TV device '${options.tvSerial}' is not online. Connected devices:\n${adb.devices()}"
        }

        val steps = mutableListOf<BenchmarkStep>()
        steps += timed("install_phone") { adb.install(options.phoneSerial, options.phoneApk) }
        steps += timed("install_tv") { adb.install(options.tvSerial, options.tvApk) }
        steps += timed("wake_phone") { adb.shell(options.phoneSerial, "input", "keyevent", "KEYCODE_WAKEUP") }
        steps += timed("wake_tv") { adb.shell(options.tvSerial, "input", "keyevent", "KEYCODE_WAKEUP") }
        steps += timed("clear_phone") { adb.shell(options.phoneSerial, "pm", "clear", PHONE_PACKAGE) }
        steps += timed("clear_tv") { adb.shell(options.tvSerial, "pm", "clear", TV_PACKAGE) }
        steps += timed("reverse_tv_mock_server") {
            adb.removeReverse(options.tvSerial, "tcp:${options.mockServerPort}")
            adb.reverse(options.tvSerial, "tcp:${options.mockServerPort}", "tcp:${options.mockServerPort}")
        }
        steps += timed("forward_tv_subscribe_port") {
            adb.removeForward(options.tvSerial, "tcp:$TV_SUBSCRIBE_PORT")
            adb.forward(options.tvSerial, "tcp:$TV_SUBSCRIBE_PORT", "tcp:$TV_SUBSCRIBE_PORT")
        }

        HostTcpBridge(
            listenPort = options.hostBridgePort,
            targetPort = TV_SUBSCRIBE_PORT
        ).use { bridge ->
            steps += timed("start_host_subscribe_bridge") {
                bridge.start()
            }
            steps += timed("reverse_phone_subscribe_bridge") {
                adb.removeReverse(options.phoneSerial, "tcp:${options.hostBridgePort}")
                adb.reverse(options.phoneSerial, "tcp:${options.hostBridgePort}", "tcp:${options.hostBridgePort}")
            }

            steps += timed("start_phone_and_tv") {
                val phone = Thread {
                    adb.shell(options.phoneSerial, "monkey", "-p", PHONE_PACKAGE, "1")
                }
                val tv = Thread {
                    adb.shell(
                        options.tvSerial,
                        "monkey",
                        "-p",
                        TV_PACKAGE,
                        "-c",
                        TV_LAUNCH_CATEGORY,
                        "1"
                    )
                }
                phone.start()
                tv.start()
                phone.join()
                tv.join()
            }

            steps += timed("wait_for_tv_subscribe_bridge_ready") {
                waitForHttpOk("http://127.0.0.1:${options.hostBridgePort}/health", 20.seconds)
            }

            val playlistUrl = "${options.mockUrl.trimEnd('/')}/playlist/live.m3u"
            steps += timed("phone_requests_tv_m3u_subscription") {
                adb.shell(
                    options.phoneSerial,
                    "am",
                    "start",
                    "-a",
                    PHONE_SUBSCRIBE_ACTION,
                    "-p",
                    PHONE_PACKAGE,
                    "--es",
                    "tvUrl",
                    options.tvUrl,
                    "--es",
                    "title",
                    options.playlistTitle,
                    "--es",
                    "url",
                    playlistUrl
                )
            }

            steps += timed("wait_for_tv_playlist_visible") {
                adb.waitForText(
                    serial = options.tvSerial,
                    text = options.playlistTitle,
                    timeout = options.timeout
                )
            }
        }

        return BenchmarkReport(
            success = true,
            options = options,
            steps = steps,
            error = null
        )
    }

    private fun timed(name: String, block: () -> Unit): BenchmarkStep {
        println("Starting $name")
        val start = TimeSource.Monotonic.markNow()
        block()
        val durationMs = start.elapsedNow().inWholeMilliseconds
        println("Finished $name in ${durationMs}ms")
        return BenchmarkStep(name, durationMs)
    }
}

private data class Options(
    val phoneSerial: String,
    val tvSerial: String,
    val mockUrl: String,
    val mockServerPort: Int,
    val tvUrl: String,
    val hostBridgePort: Int,
    val playlistTitle: String,
    val timeout: Duration,
    val adbPath: String,
    val phoneApk: File,
    val tvApk: File,
    val failOnMissingDevice: Boolean
) {
    fun resolve(adb: Adb): Options {
        val onlineSerials = adb.onlineSerials()
        val resolvedTvSerial = tvSerial.ifBlank {
            onlineSerials.firstOrNull { adb.isTv(it) }.orEmpty()
        }
        val resolvedPhoneSerial = phoneSerial.ifBlank {
            onlineSerials.firstOrNull { it != resolvedTvSerial && !adb.isTv(it) }.orEmpty()
        }
        return copy(
            phoneSerial = resolvedPhoneSerial,
            tvSerial = resolvedTvSerial
        )
    }

    companion object {
        fun parse(args: List<String>): Options {
            fun value(name: String): String? {
                val index = args.indexOf(name)
                if (index < 0) return null
                return args.getOrNull(index + 1)
            }

            return Options(
                phoneSerial = value("--phone").orEmpty(),
                tvSerial = value("--tv").orEmpty(),
                mockUrl = value("--mock-url") ?: "http://127.0.0.1:$MOCK_SERVER_PORT",
                mockServerPort = value("--mock-server-port")?.toIntOrNull() ?: MOCK_SERVER_PORT,
                tvUrl = value("--tv-url") ?: "http://127.0.0.1:$HOST_BRIDGE_PORT",
                hostBridgePort = value("--host-bridge-port")?.toIntOrNull() ?: HOST_BRIDGE_PORT,
                playlistTitle = value("--playlist-title") ?: "BenchmarkLive",
                timeout = (value("--timeout-seconds")?.toIntOrNull() ?: 60).seconds,
                adbPath = value("--adb") ?: "adb",
                phoneApk = value("--phone-apk")?.let(::File)
                    ?: latestApk(repoFile("app/smartphone/build/outputs/apk/debug")),
                tvApk = value("--tv-apk")?.let(::File)
                    ?: latestApk(repoFile("app/tv/build/outputs/apk/debug")),
                failOnMissingDevice = "--no-fail-on-missing-device" !in args
            )
        }

        private fun latestApk(directory: File): File {
            val apk = directory
                .walkTopDown()
                .filter { it.isFile && it.extension == "apk" }
                .maxByOrNull { it.lastModified() }

            requireNotNull(apk) { "No APK found in ${directory.path}. Run assembleDebug first." }
            return apk
        }
    }
}

private val repositoryRoot: File by lazy {
    generateSequence(File(".").canonicalFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(".").canonicalFile
}

private fun repoFile(path: String): File = File(repositoryRoot, path)

private data class BenchmarkStep(
    val name: String,
    val durationMs: Long
)

private data class BenchmarkReport(
    val success: Boolean,
    val options: Options,
    val steps: List<BenchmarkStep>,
    val error: String?
) {
    fun toHumanSummary(): String {
        val total = steps.sumOf { it.durationMs }
        val body = steps.joinToString(separator = "\n") { "  ${it.name}: ${it.durationMs}ms" }
        return buildString {
            appendLine("Phone TV subscribe benchmark: ${if (success) "PASS" else "FAIL"}")
            appendLine("Total measured time: ${total}ms")
            append(body)
        }
    }

    fun toJson(): String {
        val stepsJson = steps.joinToString(separator = ",\n") {
            """    {"name":"${it.name}","durationMs":${it.durationMs}}"""
        }
        return buildString {
            appendLine("{")
            appendLine("  \"success\": $success,")
            appendLine("  \"createdAt\": \"${Instant.now()}\",")
            appendLine("  \"phoneSerial\": \"${options.phoneSerial}\",")
            appendLine("  \"tvSerial\": \"${options.tvSerial}\",")
            appendLine("  \"mockUrl\": \"${options.mockUrl}\",")
            appendLine("  \"mockServerPort\": ${options.mockServerPort},")
            appendLine("  \"tvUrl\": \"${options.tvUrl}\",")
            appendLine("  \"hostBridgePort\": ${options.hostBridgePort},")
            appendLine("  \"playlistTitle\": \"${options.playlistTitle}\",")
            appendLine("  \"phoneApk\": \"${options.phoneApk.path.jsonEscape()}\",")
            appendLine("  \"tvApk\": \"${options.tvApk.path.jsonEscape()}\",")
            appendLine("  \"error\": ${error?.let { "\"${it.jsonEscape()}\"" } ?: "null"},")
            appendLine("  \"steps\": [")
            appendLine(stepsJson)
            appendLine("  ]")
            append("}")
        }
    }

    companion object {
        fun failure(options: Options, error: Throwable): BenchmarkReport =
            BenchmarkReport(
                success = false,
                options = options,
                steps = emptyList(),
                error = error.message ?: error::class.java.name
            )
    }
}

private class Adb(private val path: String) {
    fun devices(): String = exec(path, "devices", "-l").stdout

    fun onlineSerials(): List<String> = devices()
        .lineSequence()
        .map { it.trim().split(Regex("\\s+")) }
        .filter { columns -> columns.getOrNull(1) == "device" }
        .mapNotNull { columns -> columns.getOrNull(0) }
        .toList()

    fun isOnline(serial: String): Boolean {
        if (serial.isBlank()) return false
        return onlineSerials().contains(serial)
    }

    fun isTv(serial: String): Boolean {
        if (serial.isBlank()) return false
        val characteristics = shell(serial, "getprop", "ro.build.characteristics")
        if (characteristics.split(',').any { it.trim() == "tv" }) return true
        return shellOrNull(serial, "pm", "has-feature", "android.software.leanback")?.trim() == "true"
    }

    fun install(serial: String, apk: File): String {
        require(apk.exists()) { "APK does not exist: ${apk.path}" }
        return exec(path, "-s", serial, "install", "-r", apk.absolutePath).stdout
    }

    fun shell(serial: String, vararg command: String): String =
        exec(path, "-s", serial, "shell", *command).stdout

    private fun shellOrNull(serial: String, vararg command: String): String? =
        execOrNull(path, "-s", serial, "shell", *command)?.stdout

    fun forward(serial: String, local: String, remote: String): String =
        exec(path, "-s", serial, "forward", local, remote).stdout

    fun removeForward(serial: String, local: String) {
        execOrNull(path, "-s", serial, "forward", "--remove", local)
    }

    fun reverse(serial: String, remote: String, local: String): String =
        exec(path, "-s", serial, "reverse", remote, local).stdout

    fun removeReverse(serial: String, remote: String) {
        execOrNull(path, "-s", serial, "reverse", "--remove", remote)
    }

    fun waitForText(serial: String, text: String, timeout: Duration) {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            val xml = dumpWindow(serial)
            if (xml.contains(text)) return
            Thread.sleep(1_000)
        }
        error("Timed out waiting for '$text' on $serial")
    }

    private fun dumpWindow(serial: String): String {
        shell(serial, "uiautomator", "dump", "/data/local/tmp/window.xml")
        Thread.sleep(250)
        return shell(serial, "cat", "/data/local/tmp/window.xml")
    }
}

private class HostTcpBridge(
    private val listenPort: Int,
    private val targetPort: Int
) : AutoCloseable {
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun start() {
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("0.0.0.0", listenPort))
        }
        server = socket
        acceptThread = Thread {
            while (!socket.isClosed) {
                runCatching {
                    val client = socket.accept()
                    Thread { bridge(client) }.start()
                }
            }
        }.apply {
            name = "host-subscribe-bridge"
            isDaemon = true
            start()
        }
    }

    override fun close() {
        runCatching { server?.close() }
        server = null
    }

    private fun bridge(client: Socket) {
        client.use { incoming ->
            Socket("127.0.0.1", targetPort).use { outgoing ->
                val clientToTarget = Thread {
                    incoming.getInputStream().copyTo(outgoing.getOutputStream())
                    runCatching { outgoing.shutdownOutput() }
                }.apply {
                    name = "host-subscribe-bridge-client"
                    isDaemon = true
                }
                val targetToClient = Thread {
                    outgoing.getInputStream().copyTo(incoming.getOutputStream())
                    runCatching { incoming.shutdownOutput() }
                }.apply {
                    name = "host-subscribe-bridge-target"
                    isDaemon = true
                }
                clientToTarget.start()
                targetToClient.start()
                clientToTarget.join()
                targetToClient.join()
            }
        }
    }
}

private data class ExecResult(val stdout: String)

private fun exec(vararg command: String): ExecResult {
    val process = ProcessBuilder(*command)
        .redirectErrorStream(true)
        .start()
    val stdout = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        "Command failed ($exitCode): ${command.joinToString(" ")}\n$stdout"
    }
    return ExecResult(stdout)
}

private fun execOrNull(vararg command: String): ExecResult? = runCatching {
    exec(*command)
}.getOrNull()

private fun waitForHttpOk(url: String, timeout: Duration) {
    val start = TimeSource.Monotonic.markNow()
    var lastError: Throwable? = null
    while (start.elapsedNow() < timeout) {
        runCatching {
            val responseCode = (URL(url).openConnection() as java.net.HttpURLConnection).run {
                connectTimeout = 1_000
                readTimeout = 1_000
                try {
                    responseCode
                } finally {
                    disconnect()
                }
            }
            check(responseCode in 200..299) { "Unexpected response $responseCode" }
        }.onSuccess {
            return
        }.onFailure {
            lastError = it
        }
        Thread.sleep(500)
    }
    error("Timed out waiting for $url: ${lastError?.message.orEmpty()}")
}

private fun String.jsonEscape(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
