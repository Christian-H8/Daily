package com.christianh.daily

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.christianh.daily.data.Prefs
import java.time.ZonedDateTime

const val REQUEST_DAILY = 0

/**
 * The daily alarm.
 *
 * `setAndAllowWhileIdle` is inexact and subject to a documented ~9-minute per-app
 * Doze throttle — and so is `setExactAndAllowWhileIdle`, which is the correction
 * recorded in D-011. Only `setAlarmClock` escapes it, and that shows a system alarm
 * icon, which is a stronger claim on the device than a reading prompt deserves.
 *
 * Per D-013 the bar is **delivery, not punctuality**: an entry arriving at 08:09 is
 * the product working.
 */
fun scheduleDailyQuote(context: Context) {
    val pending = PendingIntent.getBroadcast(
        context,
        REQUEST_DAILY,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        nextOccurrence(Prefs.hour(context), Prefs.minute(context)),
        pending
    )
}

/**
 * Resolved at schedule time in the system zone, so the alarm is a wall-clock time
 * rather than a fixed instant — 08:00 stays 08:00 across a timezone change.
 */
private fun nextOccurrence(hour: Int, minute: Int): Long {
    val now = ZonedDateTime.now()
    var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.toInstant().toEpochMilli()
}
