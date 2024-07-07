package com.m3u.androidApp.glance

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import androidx.glance.unit.ColorProvider
import com.m3u.core.Contracts
import dagger.hilt.android.EntryPointAccessors

class PlayRandomlyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val accessor: GlanceAccessor by lazy {
            EntryPointAccessors.fromApplication(context.applicationContext)
        }
        val playedRecently = accessor.channelRepository.observePlayedRecently()
        provideContent {
            val channel by playedRecently.collectAsState(initial = null)
            GlanceTheme {
                val bitmap: Bitmap? by produceState<Bitmap?>(
                    initialValue = null,
                    key1 = channel?.cover
                ) {
                    value = accessor.mediaRepository
                        .loadDrawable(channel?.cover.orEmpty())
                        ?.toBitmap()
                }
                Box(
                    contentAlignment = Alignment.BottomStart,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(16.dp)
                        .clickable(
                            actionStartActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    component = ComponentName.createRelative(
                                        context,
                                        Contracts.PLAYER_ACTIVITY
                                    )
                                    putExtra(
                                        Contracts.PLAYER_SHORTCUT_CHANNEL_RECENTLY,
                                        true
                                    )
                                }
                            )
                        )
                        .background(GlanceTheme.colors.background)
                        .appWidgetBackground()
                        .padding(4.dp)
                ) {
                    val currentBitmap = bitmap
                    if (currentBitmap != null) {
                        Image(
                            provider = ImageProvider(currentBitmap),
                            contentDescription = channel?.title,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .cornerRadius(16.dp)
                        )
                    }
                    Text(
                        text = channel?.title.orEmpty(),
                        style = TextDefaults.defaultTextStyle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color.White)
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(12.dp)
                    )
                }
            }
        }
    }
}