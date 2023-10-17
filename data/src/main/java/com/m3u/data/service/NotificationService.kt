package com.m3u.data.service

import android.app.Notification
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat

// TODO: combine to ui-service
interface NotificationService {
    fun post(
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