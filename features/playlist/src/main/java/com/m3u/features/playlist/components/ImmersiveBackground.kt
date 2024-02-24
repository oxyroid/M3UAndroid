package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.m3u.material.components.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.R
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.SnackHost

@Composable
internal fun ImmersiveBackground(
    title: String,
    stream: Stream?,
    maxBrowserHeight: Dp,
    isUnspecifiedSort: Boolean,
    onRefresh: () -> Unit,
    openSearchDrawer: () -> Unit,
    openSortDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val pref = LocalPref.current

    val noPictureMode = pref.noPictureMode

    Box(modifier) {
        if (stream != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                if (!noPictureMode) {
                    val request = remember(stream.cover) {
                        ImageRequest.Builder(context)
                            .data(stream.cover.orEmpty())
                            .crossfade(1600)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentScale = ContentScale.Crop,
                        contentDescription = stream.title,
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(16 / 9f)
                    )

                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.scrim),
                        tint = MaterialTheme.colorScheme.background,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(16 / 9f)
                    )
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(spacing.medium)
                        .fillMaxWidth()
                ) {
                    SnackHost()
                    Text(
                        text = stream.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = stream.url,
                        style = MaterialTheme.typography.headlineMedium,
                        color = LocalContentColor.current.copy(0.68f),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(
                        modifier = Modifier.heightIn(min = maxBrowserHeight)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "search",
                    onClick = openSearchDrawer
                )
                IconButton(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "sort",
                    onClick = openSortDrawer
                )
                IconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "refresh",
                    onClick = onRefresh
                )
            }
            SnackHost()
        }
    }
}