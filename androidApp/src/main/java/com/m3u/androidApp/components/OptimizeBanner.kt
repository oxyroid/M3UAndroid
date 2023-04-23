package com.m3u.androidApp.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.entity.Post
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

private typealias OnPost = (post: Post) -> Unit

@Composable
fun ColumnScope.OptimizeBanner(
    posts: List<Post>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onPost: OnPost
) {
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    val actualBackgroundColor = if (backgroundColor.isUnspecified) theme.tint
    else backgroundColor
    val actualContentColor = if (contentColor.isUnspecified) theme.onTint
    else contentColor
    val post = remember(posts) {
        posts.firstOrNull()
    }
    this.AnimatedVisibility(post != null) {
        if (post != null) {
            Text(
                text = post.title,
                color = actualContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier
                    .drawBehind {
                        drawRect(actualBackgroundColor)
                    }
                    .clickable {
                        onPost(post)
                    }
                    .padding(
                        horizontal = spacing.medium,
                        vertical = spacing.extraSmall
                    )
                    .then(modifier)
            )
        }
    }
}