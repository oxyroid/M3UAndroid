package com.m3u.core.architecture.service

import android.app.Notification
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat

interface NotificationService {
    fun postNotification(
        notificationId: Int,
        @DrawableRes smallIcon: Int,
        contentTitle: CharSequence? = null,
        contentText: CharSequence? = null,
        subText: CharSequence? = null,
        style: NotificationCompat.Style? = null,
        @PriorityLevel pri: Int
    ): Notification

    @IntDef(
        NotificationCompat.PRIORITY_MIN,
        NotificationCompat.PRIORITY_LOW,
        NotificationCompat.PRIORITY_DEFAULT,
        NotificationCompat.PRIORITY_HIGH,
        NotificationCompat.PRIORITY_MAX
    )
    annotation class PriorityLevel
}