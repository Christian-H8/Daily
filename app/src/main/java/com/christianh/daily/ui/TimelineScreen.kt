package com.christianh.daily.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.christianh.daily.Domain.Day
import com.christianh.daily.Domain.EPOCH
import com.christianh.daily.Domain.Timeline
import com.christianh.daily.Domain.model.Entry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")
private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/** A flat list so the LazyColumn needs no sticky-header API. */
private sealed interface RowItem {
    data class MonthHeader(val label: String) : RowItem
    data class DayRow(val day: Day) : RowItem
}

private fun rowsFor(days: List<Day>): List<RowItem> {
    val out = ArrayList<RowItem>(days.size + 12)
    var month = ""
    for (d in days) {
        val label = d.date.format(MONTH_FMT)
        if (label != month) {
            out += RowItem.MonthHeader(label)
            month = label
        }
        out += RowItem.DayRow(d)
    }
    return out
}

/**
 * The library: a scrollable timeline, newest first, one row per day, plus tag
 * filtering. Nothing else — D-016 rules out search and related-edge traversal,
 * because the vault is the graph and duplicating Obsidian badly is not the job.
 */
@Composable
fun TimelineScreen(
    corpus: List<Entry>,
    today: LocalDate,
    notes: Map<LocalDate, String>,
    onOpen: (Day) -> Unit
) {
    var tag by remember { mutableStateOf<String?>(null) }

    val allTags = remember(corpus) { Timeline.tagsOf(corpus) }
    val days = remember(corpus, today) { Timeline.days(today, EPOCH, corpus) }
    val rows = remember(days, tag) {
        rowsFor(if (tag == null) days else days.filter { tag in it.entry.tags })
    }

    Column(Modifier.fillMaxSize()) {
        TagFilter(allTags, tag) { picked -> tag = if (tag == picked) null else picked }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows) { row ->
                when (row) {
                    is RowItem.MonthHeader -> MonthHeaderView(row.label)
                    is RowItem.DayRow -> DayRowView(row.day, notes[row.day.date], onOpen)
                }
            }
        }
    }
}

@Composable
private fun TagFilter(tags: List<String>, selected: String?, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        tags.forEach { t ->
            TextButton(onClick = { onPick(t) }) {
                Text(
                    t,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (t == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (t == selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonthHeaderView(label: String) {
    Spacer(Modifier.height(24.dp))
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * One day. The note sits under the entry it was written against — the pairing is
 * the whole point of the timeline (D-016), and a day with a note is also the only
 * day pinned against drift ([R5.8]).
 */
@Composable
private fun DayRowView(day: Day, note: String?, onOpen: (Day) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(day) }
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            day.date.format(DAY_FMT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Column {
            Text(
                day.entry.title,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 22.sp
            )
            if (day.entry.tagline.isNotBlank()) {
                Text(
                    day.entry.tagline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            if (!note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                NotePreview(note)
            }
        }
    }
}
