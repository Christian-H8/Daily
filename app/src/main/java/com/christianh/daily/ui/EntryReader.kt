package com.christianh.daily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.christianh.daily.Domain.model.Block
import com.christianh.daily.Domain.model.Entry
import com.christianh.daily.Domain.model.Section

/**
 * Renders any entry. Lives here rather than in MainActivity because both the
 * daily screen and the timeline open the same thing.
 *
 * Styling is driven by block kind, never by heading text — headings are authored
 * freely ("Idea Two — The Cold Monster"), so matching on them would break the
 * moment an entry is written slightly differently.
 */
@Composable
fun EntryView(entry: Entry) {
    Text(
        entry.type.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    Text(
        entry.title,
        style = MaterialTheme.typography.headlineMedium,
        lineHeight = 38.sp
    )
    if (entry.tagline.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            entry.tagline,
            style = MaterialTheme.typography.titleSmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
    Spacer(Modifier.height(40.dp))
    entry.sections.forEach { SectionView(it) }
}

@Composable
private fun SectionView(section: Section) {
    if (section.heading.isNotBlank()) {
        Text(
            section.heading.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
    }
    if (section.thinkers.isNotEmpty()) {
        Text(
            section.thinkers.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
    }
    section.blocks.forEachIndexed { i, block ->
        if (i > 0) Spacer(Modifier.height(14.dp))
        BlockView(block)
    }
    Spacer(Modifier.height(34.dp))
}

@Composable
private fun BlockView(block: Block) {
    when (block) {
        is Block.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 27.sp
        )

        is Block.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 29.sp
                )
                block.attribution?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "— $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
