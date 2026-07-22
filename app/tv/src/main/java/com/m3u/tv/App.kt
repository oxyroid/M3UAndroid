package com.m3u.tv

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.m3u.data.tv.model.keyCode
import com.m3u.i18n.R.string

@Composable
fun App(
    onBackPressed: () -> Unit,
    viewModel: TvHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val remoteControlCode by viewModel.remoteControlCode.collectAsStateWithLifecycle()
    val view = LocalView.current
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val context = LocalContext.current
    val diagnosticsShareTitle = stringResource(string.feat_setting_extension_diagnostics_share_title)
    var destination by remember { mutableStateOf(TvDestination.Home) }
    var surface by remember { mutableStateOf(TvSurface.Browse) }
    val closePlayer = {
        viewModel.releasePlayer()
        surface = TvSurface.Browse
    }

    BackHandler {
        if (surface == TvSurface.Player) {
            closePlayer()
        } else {
            onBackPressed()
        }
    }

    LaunchedEffect(view) {
        viewModel.remoteDirections.collect { direction ->
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, direction.keyCode))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, direction.keyCode))
        }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.extensionDiagnostics.collect { payload ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, payload)
                    },
                    diagnosticsShareTitle,
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TvBackdrop(channel = currentChannel ?: state.heroChannel)
        Row(Modifier.fillMaxSize()) {
            TvNavigationRail(
                selected = destination,
                onSelect = { destination = it }
            )
            TvBrowsePane(
                destination = destination,
                state = state,
                onOpenLibrary = { destination = TvDestination.Library },
                onPlaylist = {
                    viewModel.selectPlaylist(it)
                    destination = TvDestination.Library
                },
                onRefresh = viewModel::refreshSelectedPlaylist,
                onPlay = {
                    viewModel.play(it)
                    surface = TvSurface.Player
                },
                onPlayRecent = {
                    viewModel.playRecent()
                    surface = TvSurface.Player
                },
                onExternalExtensionsEnabled = viewModel::setExternalExtensionsEnabled,
                onEnableExtension = viewModel::enableExtensionPlugin,
                onReauthorizeExtension = viewModel::reauthorizeExtensionPlugin,
                onDisableExtension = viewModel::disableExtensionPlugin,
                onRevokeExtension = viewModel::revokeExtensionPlugin,
                onClearExtensionData = viewModel::clearExtensionData,
                onExportExtensionDiagnostics = viewModel::exportExtensionDiagnostics,
                onOpenExtensionSettings = { extensionId ->
                    viewModel.openExtensionSettings(extensionId, localeTag)
                },
                onCloseExtensionSettings = viewModel::closeExtensionSettings,
                onUpdateExtensionSetting = { sectionId, fieldKey, value ->
                    viewModel.updateExtensionSetting(sectionId, fieldKey, value, localeTag)
                },
            )
        }

        AnimatedVisibility(
            visible = surface == TvSurface.Player,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TvPlayerScreen(
                player = player,
                channel = currentChannel,
                isPlaying = isPlaying,
                playbackState = playbackState,
                onPlayPause = { viewModel.pauseOrContinue(!isPlaying) },
                onBack = closePlayer,
                onClose = closePlayer
            )
        }

        remoteControlCode?.let { code ->
            Text(
                text = code.toString().padStart(6, '0'),
                color = TvColors.TextPrimary,
                fontFamily = TvFonts.Body,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(TvColors.Surface.copy(alpha = 0.86f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}
