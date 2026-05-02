package com.m3u.smartphone.remote

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class RemoteTvSubscribeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tvUrl = intent.getStringExtra(EXTRA_TV_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Remote M3U" }
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        Log.i(TAG, "Received remote M3U request title='$title' tvUrl='$tvUrl' url='$url'")
        if (tvUrl.isBlank() || url.isBlank()) {
            Log.e(TAG, "Missing required extras: tvUrl='$tvUrl' url='$url'")
            Toast.makeText(this, "Missing TV or playlist URL.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Thread {
            val result = runCatching {
                RemoteTvSubscribeClient.subscribe(
                    tvUrl = tvUrl,
                    title = title,
                    playlistUrl = url
                )
            }
            runOnUiThread {
                result
                    .onSuccess { Log.i(TAG, "Sent M3U subscription '$title' to $tvUrl") }
                    .onFailure { Log.e(TAG, "Failed to send M3U subscription '$title' to $tvUrl", it) }
                Toast.makeText(
                    this@RemoteTvSubscribeActivity,
                    if (result.isSuccess) "Sent subscription to TV." else "Remote TV subscription failed.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }.apply {
            name = "remote-tv-subscribe"
            start()
        }
    }

    companion object {
        private const val TAG = "RemoteTvSubscribe"
        const val ACTION_SUBSCRIBE_M3U = "com.m3u.smartphone.REMOTE_SUBSCRIBE_M3U"
        const val EXTRA_TV_URL = "tvUrl"
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
    }
}

object RemoteTvSubscribeClient {
    fun subscribe(tvUrl: String, title: String, playlistUrl: String) {
        val endpoint = "${tvUrl.trimEnd('/')}/subscribe/m3u"
        val body = listOf(
            "title" to title,
            "url" to playlistUrl
        ).joinToString("&") { (key, value) ->
            "${key.encodeForm()}=${value.encodeForm()}"
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        }
        try {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body)
            }
            val responseCode = connection.responseCode
            check(responseCode in 200..299) { "Unexpected response $responseCode" }
        } finally {
            connection.disconnect()
        }
    }

    private fun String.encodeForm(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}
