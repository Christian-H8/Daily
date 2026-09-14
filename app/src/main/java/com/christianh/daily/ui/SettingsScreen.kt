package com.christianh.daily.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.christianh.daily.Domain.model.Condition

/**
 * Settings. Two real controls and a diagnostic panel.
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
    corpusLine: String,
    sourceLine: String,
    onPickFolder: () -> Unit,
    onReload: () -> Unit,
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
            Spacer(Modifier.height(0.dp))
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
