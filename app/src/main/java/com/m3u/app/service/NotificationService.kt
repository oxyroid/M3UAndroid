@file:Suppress("unused")

package com.m3u.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import com.m3u.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun notifyTiramisu(
        title: String,
        message: String,
        channelId: String,
    ) {
        val notificationId = System.currentTimeMillis().toInt()
        val icon = AppCompatResources.getDrawable(
            context,
            R.drawable.round_play_arrow_24
        )?.toBitmap()

        val channel = NotificationChannelCompat.Builder(
            channelId,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName("Importance Channel")
            .build()
        val notification = NotificationCompat.Builder(context, channel.id)
            .setLargeIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .build()

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(channel)
        manager.notify(notificationId, notification)
    }

    @SuppressLint("MissingPermission")
    fun notify(
        title: String,
        message: String,
        channelId: String,
    ) {
        val notificationId = System.currentTimeMillis().toInt()
        val icon = AppCompatResources.getDrawable(
            context,
            R.drawable.round_play_arrow_24
        )?.toBitmap()

        val channel = NotificationChannelCompat.Builder(
            channelId,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName("Importance Channel")
            .build()
        val notification = NotificationCompat.Builder(context, channel.id)
            .setLargeIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .build()

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(channel)
        manager.notify(notificationId, notification)
    }
}