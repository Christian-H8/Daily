package com.christianh.daily.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The four sections as words, above the page.
 *
 * It reads [SECTIONS] rather than listing the screens itself, so adding a section
 * is still the one-line change the dial assumes it is. The reader is not in
 * [SECTIONS] and so is not offered here — it is somewhere you arrive from the
 * timeline, not somewhere you navigate to.
 */
@Composable
fun NavBar(current: Screen, onGo: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        SECTIONS.forEach { section ->
            NavItem(
                label = section.label(),
                active = current == section,
                onClick = { onGo(section) }
            )
        }
    }
}

private fun Screen.label(): String = when (this) {
    Screen.Home -> "Home"
    Screen.Today -> "Today"
    Screen.Timeline -> "Timeline"
    Screen.Settings -> "Settings"
    is Screen.Reader -> "Reading"
}

@Composable
private fun NavItem(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
