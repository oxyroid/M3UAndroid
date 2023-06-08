package com.m3u.data.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.m3u.core.architecture.service.NotificationService

class AndroidNotificationService constructor(
    private val context: Context,
    private val managerCompat: NotificationManagerCompat,
    private val manager: NotificationManager
) : NotificationService {
    private val channelId = "notification channel"
    private fun checkOrCreateNotificationChannel() {
        if (managerCompat.getNotificationChannelCompat(channelId) == null) {
            val channel = NotificationChannelCompat.Builder(
                channelId,
                NotificationManager.IMPORTANCE_DEFAULT
            )
                .setName(channelId)
                .build()
            managerCompat.createNotificationChannel(channel)
        }
    }

    override fun postNotification(
        notificationId: Int,
        @DrawableRes smallIcon: Int,
        contentTitle: CharSequence?,
        contentText: CharSequence?,
        subText: CharSequence?,
        style: NotificationCompat.Style?,
        @NotificationService.PriorityLevel pri: Int
    ): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkOrCreateNotificationChannel()
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(smallIcon)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(subText)
                .setStyle(style)
                .setPriority(pri)
                .build()
            @Suppress("MissingPermission")
            managerCompat.notify(
                notificationId,
                notification
            )
            return notification
        } else {
            @Suppress("DEPRECATION")
            val notification = NotificationCompat.Builder(context)
                .setSmallIcon(smallIcon)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(subText)
                .setStyle(style)
                .setPriority(pri)
                .build()
            manager.notify(
                notificationId,
                notification
            )
            return notification
        }
    }
}