package com.m3u.features.foryou

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.QuestionMark
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.entity.Playlist
import com.m3u.features.foryou.components.ForyouDialog
import com.m3u.features.foryou.components.OnRename
import com.m3u.features.foryou.components.OnUnsubscribe
import com.m3u.features.foryou.components.PlaylistGallery
import com.m3u.features.foryou.components.recommend.RecommendGallery
import com.m3u.features.foryou.model.PlaylistDetailHolder
import com.m3u.features.foryou.components.recommend.Recommend
import com.m3u.i18n.R
import com.m3u.material.components.Background
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.minus
import com.m3u.material.ktx.only
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalHelper
import com.m3u.ui.MessageEventHandler
import com.m3u.ui.MonoText
import com.m3u.ui.ResumeEvent

@Composable
fun ForyouRoute(
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToRecommendPlaylist: (Playlist, String) -> Unit,
    navigateToSettingSubscription: () -> Unit,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ForyouViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val message by viewModel.message.collectAsStateWithLifecycle()
    val holder by viewModel.playlists.collectAsStateWithLifecycle()
    val recommend by viewModel.recommend.collectAsStateWithLifecycle()

    MessageEventHandler(message)

    EventHandler(resume) {
        helper.actions = emptyList()
    }

    val interceptVolumeEventModifier = remember(pref.godMode) {
        if (pref.godMode) {
            Modifier.interceptVolumeEvent { event ->
                when (event) {
                    KeyEvent.KEYCODE_VOLUME_UP -> pref.rowCount =
                        (pref.rowCount - 1).coerceAtLeast(1)

                    KeyEvent.KEYCODE_VOLUME_DOWN -> pref.rowCount =
                        (pref.rowCount + 1).coerceAtMost(3)
                }
            }
        } else Modifier
    }

    ForyouScreen(
        holder = holder,
        recommend = recommend,
        rowCount = pref.rowCount,
        contentPadding = contentPadding,
        navigateToPlaylist = navigateToPlaylist,
        navigateToStream = navigateToStream,
        navigateToRecommendPlaylist = navigateToRecommendPlaylist,
        unsubscribe = { viewModel.onEvent(ForyouEvent.Unsubscribe(it)) },
        rename = { playlistUrl, target ->
            viewModel.onEvent(
                ForyouEvent.Rename(
                    playlistUrl,
                    target
                )
            )
        },
        modifier = modifier
            .fillMaxSize()
            .then(interceptVolumeEventModifier),
    )
}

@Composable
private fun ForyouScreen(
    rowCount: Int,
    holder: PlaylistDetailHolder,
    recommend: Recommend,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToStream: () -> Unit,
    navigateToRecommendPlaylist: (Playlist, String) -> Unit,
    unsubscribe: OnUnsubscribe,
    rename: OnRename,
    modifier: Modifier = Modifier
) {
    var dialog: ForyouDialog by remember { mutableStateOf(ForyouDialog.Idle) }
    val configuration = LocalConfiguration.current

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    Background(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            val showRecommend = recommend.isNotEmpty()
            val showPlaylist = holder.details.isNotEmpty()
            if (showRecommend) {
                Column {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(contentPadding.calculateTopPadding())
                    )
                    RecommendGallery(
                        recommend = recommend,
                        navigateToStream = navigateToStream,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (showPlaylist) {
                val actualContentPadding = if (!showRecommend) contentPadding
                else contentPadding - contentPadding.only(WindowInsetsSides.Top)
                PlaylistGallery(
                    rowCount = actualRowCount,
                    holder = holder,
                    navigateToPlaylist = navigateToPlaylist,
                    onMenu = { dialog = ForyouDialog.Selections(it) },
                    contentPadding = actualContentPadding,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PlaylistGalleryPlaceholder(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
        ForyouDialog(
            status = dialog,
            update = { dialog = it },
            unsubscribe = unsubscribe,
            rename = rename
        )
    }

    BackHandler(dialog != ForyouDialog.Idle) {
        dialog = ForyouDialog.Idle
    }
}

@Composable
private fun PlaylistGalleryPlaceholder(
    modifier: Modifier = Modifier
) {
    val feedback = LocalHapticFeedback.current
    val spacing = LocalSpacing.current

    var expanded by rememberSaveable { mutableStateOf(false) }

    val cornerSize by animateDpAsState(
        targetValue = if (expanded) spacing.medium else spacing.large,
        label = "playlist-gallery-placeholder-corner-size"
    )

    val elevation by animateDpAsState(
        targetValue = if (expanded) spacing.small else spacing.medium,
        label = "playlist-gallery-placeholder-elevation"
    )

    val shape = RoundedCornerShape(cornerSize)
    val combined = Modifier
        .padding(spacing.medium)
        .clip(shape)
        .toggleable(
            value = expanded,
            role = Role.Checkbox,
            onValueChange = {
                feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                expanded = it
            }
        )
        .semantics(mergeDescendants = true) {}
        .animateContentSize()
        .then(modifier)
    val currentContainerColor by animateColorAsState(
        targetValue = with(MaterialTheme.colorScheme) {
            if (expanded) primary else surface
        },
        label = "playlist-gallery-placeholder-container-color"
    )
    val currentContentColor by animateColorAsState(
        targetValue = with(MaterialTheme.colorScheme) {
            if (expanded) onPrimary else onSurface
        },
        label = "playlist-gallery-placeholder-container-color"
    )
    ElevatedCard(
        modifier = combined,
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = currentContainerColor,
            contentColor = currentContentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = elevation
        )
    ) {
        val innerModifier = Modifier.padding(spacing.medium)
        val icon = @Composable {
            Icon(
                imageVector = Icons.Sharp.QuestionMark,
                contentDescription = null
            )
        }
        val message = @Composable {
            val text =
                stringResource(R.string.feat_playlist_prompt_add_playlist)
                    .capitalize(Locale.current)
            MonoText(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Box(
            modifier = innerModifier,
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = expanded,
                label = "playlist-gallery-placeholder-content"
            ) { expanded ->
                if (expanded) {
                    message()
                } else {
                    icon()
                }
            }
        }
    }
}
