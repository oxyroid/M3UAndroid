package com.m3u.androidApp.glance

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

class GlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavouriteWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GlanceAccessor {
    val channelRepository: ChannelRepository
    val programmeRepository: ProgrammeRepository
}