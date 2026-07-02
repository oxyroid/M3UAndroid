package com.m3u.smartphone

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.m3u.core.Contracts
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.get
import com.m3u.core.util.readFileName
import com.m3u.data.database.model.isSeries
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.App
import com.m3u.smartphone.ui.AppViewModel
import com.m3u.smartphone.ui.business.channel.PlayerActivity
import com.m3u.smartphone.ui.common.helper.Helper
import com.m3u.smartphone.ui.common.internal.Toolkit
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()

    private val helper: Helper = Helper(this)

    @Inject
    lateinit var settings: Settings

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    lateinit var workManager: WorkManager

    override fun onResume() {
        super.onResume()
        helper.applyConfiguration()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Toolkit(helper) {
                App(
                    viewModel = viewModel
                )
            }
        }
        if (savedInstanceState == null) {
            val playlistEnqueued = maybeEnqueueViewedPlaylist(intent)
            if (!playlistEnqueued) {
                maybeResumeLastChannelOnStartup()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeEnqueueViewedPlaylist(intent)
    }

    private fun maybeEnqueueViewedPlaylist(intent: Intent?): Boolean {
        val importIntent = intent?.takeIf { it.action in PLAYLIST_IMPORT_ACTIONS } ?: return false
        val uri = importIntent.data ?: importIntent.streamUri()
        uri ?: return false

        takeReadPermission(uri, importIntent.flags)
        val title = resolveViewedPlaylistTitle(uri)

        SubscriptionWorker.m3u(workManager, title, uri.toString())
        Toast.makeText(
            this,
            getString(string.feat_setting_enqueue_subscribe),
            Toast.LENGTH_SHORT
        ).show()
        return true
    }

    private fun resolveViewedPlaylistTitle(uri: Uri): String {
        return runCatching { uri.readFileName(contentResolver) }
            .getOrNull()
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    private fun takeReadPermission(uri: Uri, flags: Int) {
        if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return
        val modeFlags = flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        runCatching {
            contentResolver.takePersistableUriPermission(uri, modeFlags)
        }
    }

    private fun maybeResumeLastChannelOnStartup() {
        lifecycleScope.launch {
            if (!settings[PreferencesKeys.RESUME_LAST_CHANNEL_ON_STARTUP]) {
                return@launch
            }
            val startupDelay = settings[PreferencesKeys.STARTUP_DELAY]
            if (startupDelay > 0) {
                delay(startupDelay)
            }
            val channel = channelRepository.getPlayedRecently() ?: return@launch
            if (channel.url.requiresNetwork() && !isNetworkConnected()) {
                return@launch
            }
            val playlist = playlistRepository.get(channel.playlistUrl)
            if (playlist?.isSeries == true) {
                return@launch
            }
            startActivity(
                Intent(this@MainActivity, PlayerActivity::class.java)
                    .putExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_RECENTLY, true)
            )
        }
    }

    private fun isNetworkConnected(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun String.requiresNetwork(): Boolean {
        val scheme = substringBefore('|').substringBefore(':', missingDelimiterValue = "")
        return scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true).not() &&
                scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true).not()
    }

    private companion object {
        val PLAYLIST_IMPORT_ACTIONS = setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)
    }
}
