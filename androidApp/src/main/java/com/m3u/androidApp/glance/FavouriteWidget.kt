package com.m3u.androidApp.glance

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.m3u.core.Contracts
import dagger.hilt.android.EntryPointAccessors

class FavouriteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val accessor: GlanceAccessor by lazy {
            EntryPointAccessors.fromApplication(context.applicationContext)
        }
        val favouriteFlow = accessor.channelRepository.observeAllFavourite()
        provideContent {
            val channels by favouriteFlow.collectAsState(initial = emptyList())
            GlanceTheme {
                Box(
                    contentAlignment = Alignment.BottomStart,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.background)
                        .appWidgetBackground()
                        .padding(4.dp)
                ) {
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxWidth()
                    ) {
                        itemsIndexed(channels) { i, channel ->
                            Column {
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
                                                    putExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_ID, channel.id)
                                                }
                                            )
                                        )
                                        .padding(16.dp)
                                        .cornerRadius(16.dp)
                                        .background(GlanceTheme.colors.surfaceVariant),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = channel.title,
                                        style = TextStyle(GlanceTheme.colors.onSurfaceVariant)
                                    )
                                }
                                if (i != channels.lastIndex) {
                                    Spacer(GlanceModifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}