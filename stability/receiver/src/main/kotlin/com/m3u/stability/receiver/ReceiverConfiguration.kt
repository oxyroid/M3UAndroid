package com.m3u.stability.receiver

import java.nio.file.Path
import kotlin.io.path.Path

internal data class ReceiverConfiguration(
    val host: String,
    val port: Int,
    val stateFile: Path,
    val adminToken: String?,
    val alertConfiguration: AlertConfiguration,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ReceiverConfiguration {
            val alertMode = environment.required("M3U_CRASH_ALERT_MODE")
            val adminToken = environment["M3U_CRASH_ADMIN_TOKEN"]
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.also { token ->
                    require(token.length >= 32) {
                        "M3U_CRASH_ADMIN_TOKEN must contain at least 32 characters"
                    }
                }
            return ReceiverConfiguration(
                host = environment.value("M3U_CRASH_HOST", "0.0.0.0"),
                port = environment.value("M3U_CRASH_PORT", "8080")
                    .toIntOrNull()
                    ?.takeIf { it in 1..65_535 }
                    ?: error("M3U_CRASH_PORT must be a valid TCP port"),
                stateFile = Path(
                    environment.value(
                        "M3U_CRASH_STATE_FILE",
                        "build/crash-receiver/state.json",
                    )
                ),
                adminToken = adminToken,
                alertConfiguration = when (alertMode) {
                    "stdout" -> AlertConfiguration.Stdout
                    "smtp" -> AlertConfiguration.Smtp(
                        host = environment.required("M3U_CRASH_SMTP_HOST"),
                        port = environment.value("M3U_CRASH_SMTP_PORT", "587")
                            .toIntOrNull()
                            ?.takeIf { it in 1..65_535 }
                            ?: error("M3U_CRASH_SMTP_PORT must be a valid TCP port"),
                        username = environment.required("M3U_CRASH_SMTP_USERNAME"),
                        password = environment.required("M3U_CRASH_SMTP_PASSWORD"),
                        from = environment.required("M3U_CRASH_SMTP_FROM"),
                    )
                    else -> error("M3U_CRASH_ALERT_MODE must be stdout or smtp")
                },
            )
        }
    }
}

internal sealed interface AlertConfiguration {
    data object Stdout : AlertConfiguration

    data class Smtp(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val from: String,
    ) : AlertConfiguration
}

private fun Map<String, String>.value(name: String, default: String): String =
    get(name)?.trim()?.takeIf(String::isNotEmpty) ?: default

private fun Map<String, String>.required(name: String): String =
    get(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: error("$name is required")
