package com.m3u.tv

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.work.WorkManager
import com.m3u.core.util.readFileName
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.i18n.R.string
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var workManager: WorkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = TvColors.Focus,
                    onPrimary = TvColors.OnFocus,
                    secondary = TvColors.Accent,
                    background = TvColors.Background,
                    onBackground = TvColors.TextPrimary,
                    surface = TvColors.Surface,
                    onSurface = TvColors.TextPrimary,
                    surfaceVariant = TvColors.SurfaceRaised,
                    onSurfaceVariant = TvColors.TextSecondary
                )
            ) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    App(onBackPressed = onBackPressedDispatcher::onBackPressed)
                }
            }
        }
        if (savedInstanceState == null) {
            maybeEnqueueViewedPlaylist(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeEnqueueViewedPlaylist(intent)
    }

    private fun maybeEnqueueViewedPlaylist(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val uri = intent.data ?: intent.streamUri()
        uri ?: return false

        takeReadPermission(uri, intent.flags)
        val title = uri.readFileName(contentResolver)
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)

        SubscriptionWorker.m3u(workManager, title, uri.toString())
        Toast.makeText(
            this,
            getString(string.feat_setting_enqueue_subscribe),
            Toast.LENGTH_SHORT
        ).show()
        return true
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
}
