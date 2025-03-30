package com.m3u.smartphone.glance

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.m3u.smartphone.R
import com.m3u.core.Contracts
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Programme
import com.m3u.smartphone.TimeUtils.formatEOrSh
import dagger.hilt.android.EntryPointAccessors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FavoriteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val accessor: GlanceAccessor by lazy {
            EntryPointAccessors.fromApplication(context.applicationContext)
        }
        val favouriteFlow = accessor.channelRepository.observeAllFavorite()
        val programmeRepository = accessor.programmeRepository

        provideContent {
            val channels by favouriteFlow.collectAsState(initial = emptyList())
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.primary)
                        .appWidgetBackground()
                        .padding(4.dp)
                ) {
                    val appTitle = LocalContext.current
                        .getString(com.m3u.i18n.R.string.ui_title_favourite)
                    TitleBar(
                        startIcon = ImageProvider(R.drawable.round_calendar_month_24),
                        title = appTitle,
                        iconColor = GlanceTheme.colors.onPrimary,
                        textColor = GlanceTheme.colors.onPrimary
                    )
                    FavoriteGallery(
                        channels = channels,
                        getProgrammeCurrently = { programmeRepository.getProgrammeCurrently(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteGallery(
    channels: List<Channel>,
    getProgrammeCurrently: suspend (channelId: Int) -> Programme?,
    modifier: GlanceModifier = GlanceModifier
) {
    LazyColumn(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(16.dp)
            .then(modifier)
    ) {
        itemsIndexed(channels) { i, channel ->
            FavoriteGalleryItem(
                channel = channel,
                shouldShowDivider = i != channels.lastIndex,
                getProgrammeCurrently = getProgrammeCurrently
            )
        }
    }
}

@Composable
private fun FavoriteGalleryItem(
    channel: Channel,
    shouldShowDivider: Boolean,
    getProgrammeCurrently: suspend (channelId: Int) -> Programme?,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    Column(modifier) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(
                    actionStartActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            component = ComponentName.createRelative(
                                context,
                                Contracts.PLAYER_ACTIVITY
                            )
                            putExtra(
                                Contracts.PLAYER_SHORTCUT_CHANNEL_ID,
                                channel.id
                            )
                        }
                    )
                )
                .padding(16.dp)
                .background(GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(
                    text = channel.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                val programme: Programme? by produceState<Programme?>(
                    initialValue = null,
                    key1 = channel.id
                ) {
                    value = getProgrammeCurrently(channel.id)
                }
                programme?.let {
                    Text(
                        text = it.readText(),
                        style = TextStyle(
                            color = ColorProvider(
                                GlanceTheme.colors.onSurfaceVariant
                                    .getColor(context)
                                    .copy(alpha = 0.65f)
                            ),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
        if (shouldShowDivider) {
            Spacer(GlanceModifier.height(2.dp))
        }
    }
}

private fun Programme.readText(): String = buildString {
    val start = Instant.fromEpochMilliseconds(start)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .formatEOrSh(true)
    append("[$start] $title")
}