package com.christianh.daily.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.christianh.daily.domain.model.Entry
import java.time.LocalDate

/**
 * One entry, with the day's note under it.
 *
 * Today and the reader were two composables doing the same thing — the only
 * difference was that one could be handed nothing. They are one composable now;
 * "the entry for today" and "the entry for a day in the timeline" are the same
 * page, and the timeline row that opens it is the only thing that differs.
 *
 * [bottomInset] is the dial's current height, so the last line of the note clears
 * the metal instead of hiding behind it. It animates, which is why it arrives as a
 * value rather than being read from the dial here.
 */
@Composable
fun EntryPage(
    entry: Entry?,
    date: LocalDate,
    note: String,
    scroll: ScrollState,
    bottomInset: Dp,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp)
    ) {
        if (entry == null) {
            // Only reachable with an empty corpus, which means the synced file is
            // missing *and* the bundled one failed to parse. Say so quietly rather
            // than showing a blank page that reads as a crash.
            Text("No entries yet", style = MaterialTheme.typography.bodyLarge)
        } else {
            EntryView(entry)
            Spacer(Modifier.height(16.dp))
            NoteEditor(date = date, initial = note, onSave = onNote)
        }
        Spacer(Modifier.height(48.dp + bottomInset))
    }
}
