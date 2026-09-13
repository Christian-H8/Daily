package com.christianh.daily

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

const val REQUEST_DAILY = 0

// Your morning time. Change these two to test.
const val DAILY_HOUR = 8
const val DAILY_MINUTE = 0

fun scheduleDailyQuote(context: Context) {
    val pending = PendingIntent.getBroadcast(
        context,
        REQUEST_DAILY,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        nextOccurrence(DAILY_HOUR, DAILY_MINUTE),
        pending
    )
}

private fun nextOccurrence(hour: Int, minute: Int): Long {
    val now = ZonedDateTime.now()
    var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.toInstant().toEpochMilli()
}