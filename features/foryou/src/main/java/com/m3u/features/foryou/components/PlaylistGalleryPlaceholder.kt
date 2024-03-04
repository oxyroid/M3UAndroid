package com.m3u.features.foryou.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.QuestionMark
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.m3u.i18n.R
import com.m3u.material.components.Icon
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.MonoText
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlaylistGalleryPlaceholder(
    navigateToSettingPlaylistManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val feedback = LocalHapticFeedback.current
    val spacing = LocalSpacing.current

    var expanded by rememberSaveable { mutableStateOf(false) }
    val cornerSize by animateDpAsState(
        targetValue = if (expanded) spacing.medium else spacing.large,
        label = "playlist-gallery-placeholder-corner-size"
    )
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(3.seconds)
            expanded = false
        }
    }
    LifecycleResumeEffect {
        onPauseOrDispose {
            expanded = false
        }
    }
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
                if (it) {
                    feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = true
                } else {
                    navigateToSettingPlaylistManagement()
                }
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
    OutlinedCard(
        modifier = combined,
        shape = shape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = currentContainerColor,
            contentColor = currentContentColor
        ),
        elevation = CardDefaults.outlinedCardElevation(
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
            MonoText(
                text = stringResource(R.string.feat_foryou_prompt_add_playlist)
                    .capitalize(Locale.current),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
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
