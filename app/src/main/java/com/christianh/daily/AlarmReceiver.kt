package com.christianh.daily

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.christianh.daily.Domain.DailySelector
import com.christianh.daily.Domain.model.Block
import com.christianh.daily.Domain.model.Entry
import com.christianh.daily.data.CorpusLoader
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ensureChannel(context)

        val corpus = CorpusLoader.load(context)
        val entry = DailySelector.entryFor(LocalDate.now(), corpus.entries)

        if (entry != null) {
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(entry.title)
                .setContentText(entry.tagline)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextFor(entry)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()

            val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

            if (allowed) NotificationManagerCompat.from(context).notify(1, notification)
        }

        // Self-rescheduling: set tomorrow's before we finish.
        scheduleDailyQuote(context)
    }
}

/**
 * The notification is the invitation, not the entry. Lead with the hook — the
 * quote if the entry has one, otherwise its opening — then the reading time, so
 * "do I have time for this?" is answered on the lock screen.
 */
private fun bigTextFor(entry: Entry): String {
    val blocks = entry.sections.flatMap { it.blocks }
    val hook = blocks.filterIsInstance<Block.Quote>().firstOrNull()?.let { q ->
        q.text + (q.attribution?.let { "\n— $it" } ?: "")
    } ?: blocks.filterIsInstance<Block.Paragraph>().firstOrNull()?.text.orEmpty()

    return buildString {
        if (entry.tagline.isNotBlank()) append(entry.tagline).append("\n\n")
        if (hook.isNotBlank()) append(hook).append("\n\n")
        append("${entry.minutes} min read")
    }
}
