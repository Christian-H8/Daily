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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * a thought and writing it down, and there is nothing here to submit to.
 */
@Composable
fun NoteEditor(
    date: LocalDate,
    initial: String,
    onSave: (String) -> Unit
) {
    var text by remember(date) { mutableStateOf(initial) }

    LaunchedEffect(text, date) {
        if (text == initial) return@LaunchedEffect
        delay(700)
        onSave(text)
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
