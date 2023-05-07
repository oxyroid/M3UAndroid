package com.m3u.androidApp.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.entity.Post
import com.m3u.ui.model.LocalDuration
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

private typealias OnPost = (post: Post) -> Unit

@Composable
fun ColumnScope.OptimizeBanner(
    post: Post?,
    modifier: Modifier = Modifier,
    onPost: OnPost
) {
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    val duration = LocalDuration.current
    val actualBackgroundColor by animateColorAsState(
        when {
            post == null -> theme.tintDisable
            post.temporal -> theme.primary
            else -> theme.tint
        },
        animationSpec = tween(duration.slow)
    )
    val actualContentColor by animateColorAsState(
        when {
            post == null -> theme.onTint
            post.temporal -> theme.onPrimary
            else -> theme.onTintDisable
        },
        animationSpec = tween(duration.slow)
    )
    this.AnimatedVisibility(post != null) {
        Text(
            text = post?.title.orEmpty(),
            color = actualContentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier
                .drawBehind {
                    drawRect(actualBackgroundColor)
                }
                .clickable {
                    post?.let { onPost(it) }
                }
                .padding(
                    horizontal = spacing.medium,
                    vertical = spacing.extraSmall
                )
                .then(modifier)
        )
    }
}