package com.christianh.daily.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val NOTED = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The note for one day, beneath its entry and nowhere else (SRS [R10.2]).
 *
 * A day is the unit, not an entry: the same entry returning in 27 days is a
 * different day with its own note. That is what makes the rotation worth having —
 * the corpus is fixed, the user is not (D-015).
 *
 * Saves on a debounce rather than a button. A save button is one more thing between
 * a thought and writing it down, and there is nothing here to submit to — but a
 * debounce with no flush is a way to lose writing, which is why [DisposableEffect]
 * below is not optional decoration.
 */
@Composable
fun NoteEditor(
    date: LocalDate,
    initial: String,
    onSave: (String) -> Unit
) {
    var text by remember(date) { mutableStateOf(initial) }

    // What the store has been told, as far as this editor knows.
    //
    // Compared against instead of [initial], because `initial` moves the moment a
    // save lands: an edit made while that write was in flight would compare equal
    // to it and never be flushed. This tracks writes, where `initial` tracks reads.
    val saved = remember(date) { mutableStateOf(initial) }

    // Read through, so the flush below sees the current text without being
    // restarted by every keystroke the way the debounce is.
    val latest = rememberUpdatedState(text)
    val sink = rememberUpdatedState(onSave)

    // Idempotent, so the debounce and the dispose can both call it — a flush with
    // nothing new to write is a no-op rather than a duplicate save.
    val flush = remember(date) {
        {
            val pending = latest.value
            if (pending != saved.value) {
                sink.value(pending)
                saved.value = pending
            }
        }
    }

    // Restarted by every keystroke. That is what makes it a debounce.
    LaunchedEffect(text, date) {
        if (text == saved.value) return@LaunchedEffect
        delay(700)
        flush()
    }

    /*
     * The write that used to disappear.
     *
     * [LaunchedEffect]'s coroutine is scoped to the composition, so leaving this
     * page inside the 700 ms window — a swipe to another section, a nav tap, back
     * out of the reader, a rotation — cancelled the delay and the note was never
     * written at all.
     *
     * Backgrounding was never affected and still isn't: the composition survives
     * it, so the delay fires from behind the home screen. It is navigation that
     * kills the editor, which is exactly what you do after finishing a thought.
     *
     * Notes are the only unrecoverable data in the app ([R10.5]). Losing one to a
     * gesture is not a price worth paying for not having a save button.
     */
    DisposableEffect(date) {
        onDispose { flush() }
    }

    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        Text(
            date.format(NOTED).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    "Write against it.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            minLines = 4
        )
    }
}

/** Read-only, for a timeline row. */
@Composable
fun NotePreview(text: String) {
    Text(
        text.trim(),
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp,
        maxLines = 3
    )
}
