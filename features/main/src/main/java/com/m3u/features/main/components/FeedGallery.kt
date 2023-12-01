package com.m3u.features.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.m3u.data.database.entity.Feed
import com.m3u.features.main.NavigateToFeed
import com.m3u.features.main.model.FeedDetailHolder
import com.m3u.i18n.R.string
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing

internal typealias OnMenu = (Feed) -> Unit

@Composable
internal fun FeedGallery(
    rowCount: Int,
    feedDetailHolder: FeedDetailHolder,
    navigateToFeed: NavigateToFeed,
    onMenu: OnMenu,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val details = feedDetailHolder.details

    LazyVerticalGrid(
        columns = GridCells.Fixed(rowCount),
        contentPadding = PaddingValues(LocalSpacing.current.medium) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = details,
            key = { it.feed.url },
            contentType = {}
        ) { detail ->
            FeedItem(
                label = detail.feed.calculateUiTitle(),
                number = detail.count,
                local = detail.feed.local,
                modifier = Modifier.fillMaxWidth(),
                onClick = { navigateToFeed(detail.feed) },
                onLongClick = { onMenu(detail.feed) }
            )
        }
    }
}

@Composable
private fun FeedGalleryPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(string.feat_feed_prompt_add_playlist)
        )
    }
}

@Composable
private fun Feed.calculateUiTitle(): AnnotatedString {
    val actual = title.ifEmpty {
        if (local) stringResource(string.feat_main_imported_feed_title)
        else ""
    }
    return AnnotatedString(
        text = actual.uppercase()
    )
}