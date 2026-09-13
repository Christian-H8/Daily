package com.christianh.daily

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

const val CHANNEL_DAILY = "daily_quote"

fun ensureChannel(context: Context) {
    val channel = NotificationChannel(
        CHANNEL_DAILY,
        "Daily quote",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    context.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(channel)
}