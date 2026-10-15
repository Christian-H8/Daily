package com.christianh.daily.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.christianh.daily.domain.model.Condition

/**
 * Settings: the notification time, the sky override, flick-to-turn, the corpus folder
 * and the notes backup, each with the diagnostic line that says which of its failure
 * modes you are looking at.
 *
 * No time-picker dialog: Material3's `TimePicker` is behind `@ExperimentalMaterial3Api`,
 * and an experimental annotation is a poor trade for setting an hour once.
 */
@Composable
fun SettingsScreen(
    hour: Int,
    minute: Int,
    onTime: (Int, Int) -> Unit,
    forecastOverride: Condition?,
    onForecastOverride: (Condition?) -> Unit,
    flickNavigation: Boolean,
    onFlickNavigation: (Boolean) -> Unit,
    forecastLine: String,
    corpusLine: String,
    sourceLine: String,
    onPickFolder: () -> Unit,
    onReload: () -> Unit,
    backupLine: String,
    restoreLine: String?,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp)
    ) {
        SectionLabel("DAILY NOTIFICATION")
        Row {
            Stepper("Hour", hour) { onTime(wrap(hour + it, 24), minute) }
            // Width, not height: this is a Row, so a height spacer between two
            // steppers separated them by nothing at all.
            Spacer(Modifier.width(16.dp))
            Stepper("Minute", minute) { onTime(hour, wrap(minute + it * 5, 60)) }
        }
        Text(
            "Delivery, not punctuality — Android may hold this up to about nine minutes " +
                "in Doze, and that is the platform's floor rather than a setting.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(32.dp))
        SectionLabel("SKY")
        Text(
            "The forecast is read from the synced folder. Override it when it is wrong.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        // Same argument as the corpus status line: without this, "no weather" is
        // three different problems wearing the same face — no folder picked, the
        // file not synced yet, or synced but with no row for this hour.
        Text(
            forecastLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Choice("Auto", forecastOverride == null) { onForecastOverride(null) }
        }
        Row(Modifier.fillMaxWidth()) {
            Condition.entries.take(3).forEach { c ->
                Choice(c.label(), forecastOverride == c) { onForecastOverride(c) }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Condition.entries.drop(3).forEach { c ->
                Choice(c.label(), forecastOverride == c) { onForecastOverride(c) }
            }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel("FLICK TO TURN")
        Text(
            // This used to say the gesture stays off while reading. That gate was
            // removed (D-026) and the sentence outlived it.
            "A sharp twist of the wrist moves between sections, the way a swipe does, " +
                "and works wherever the swipe works.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Choice("Off", !flickNavigation) { onFlickNavigation(false) }
            Choice("On", flickNavigation) { onFlickNavigation(true) }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel("CORPUS")
        Text(
            corpusLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            sourceLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            TextButton(onClick = onPickFolder) { Text("Choose folder") }
            TextButton(onClick = onReload) { Text("Reload") }
        }

        Spacer(Modifier.height(32.dp))
        SectionLabel("NOTES BACKUP")
        Text(
            "Whenever you write, a copy of your notes is saved to the folder above as " +
                "daily-notes.json. It survives an uninstall, and reaches your computer " +
                "if your sync app copies that folder in both directions. Restore merges " +
                "a copy back in and never deletes a note.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            backupLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (restoreLine != null) {
            Text(
                restoreLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRestore) { Text("Restore from folder") }
    }
}

private fun Condition.label() = name.lowercase().replaceFirstChar { it.uppercase() }

private fun wrap(v: Int, mod: Int) = ((v % mod) + mod) % mod

@Composable
private fun SectionLabel(text: String) {
    HorizontalDivider()
    Spacer(Modifier.height(14.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Stepper(label: String, value: Int, onStep: (Int) -> Unit) {
    Row {
        TextButton(onClick = { onStep(-1) }) { Text("−") }
        Column {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        TextButton(onClick = { onStep(1) }) { Text("+") }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
