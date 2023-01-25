package com.m3u.subscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.entity.Live
import com.m3u.ui.components.M3UImage
import com.m3u.ui.components.basic.M3UColumn
import com.m3u.ui.components.basic.M3URow
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.local.LocalTheme
import java.net.URI

@Composable
internal fun SubscriptionRoute(
    id: Int,
    navigateToLive: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val state by viewModel.readable.collectAsStateWithLifecycle()

    val title = state.title
    val lives = state.lives

    LaunchedEffect(id) {
        viewModel.onEvent(SubscriptionEvent.GetDetails(id))
    }

    SubscriptionScreen(
        title = title,
        lives = lives,
        navigateToLive = navigateToLive,
        modifier = modifier
    )
}

@Composable
private fun SubscriptionScreen(
    title: String,
    lives: List<Live>,
    navigateToLive: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column {
        M3URow(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6
            )
        }
        LazyColumn(
            modifier = modifier
        ) {
            items(lives) { live ->
                LiveItem(
                    live = live,
                    onClick = { navigateToLive(live.id) },
                    modifier = Modifier.fillParentMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LiveItem(
    live: Live,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = remember(live) {
        URI(live.url).scheme
            .orEmpty()
            .ifEmpty { context.getString(R.string.scheme_unknown) }
            .uppercase()
    }
    Card {
        M3UColumn(
            modifier = modifier.clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple()
            )
        ) {
            M3UImage(
                model = live.cover,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4 / 3f)
            )
            Row {
                Text(
                    text = live.label,
                    style = MaterialTheme.typography.body1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.extraSmall)
            ) {
                SchemeIcon(scheme = scheme)
                Text(
                    text = live.url,
                    maxLines = 1,
                    style = MaterialTheme.typography.overline,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SchemeIcon(
    scheme: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LocalTheme.current.primary,
        contentColor = LocalTheme.current.onPrimary,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = modifier
                .padding(
                    horizontal = LocalSpacing.current.extraSmall
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = scheme,
                style = MaterialTheme.typography.subtitle2
            )
        }
    }
}