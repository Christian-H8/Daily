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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.christianh.daily.domain.Inline
import com.christianh.daily.domain.model.Block
import com.christianh.daily.domain.model.Entry
import com.christianh.daily.domain.model.Section

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
    TagFooter(entry)
}

/**
 * Where this entry is filed, at the foot rather than the head.
 *
 * The tags are the only handle the timeline offers — [TimelineScreen]'s filter row
 * is the whole of the organising apparatus (D-016) — so an entry that never shows
 * them leaves you filtering by guesswork. They sit at the end because filing is
 * something you do once you have read the thing; at the top they would push the
 * title down a surface whose entire job is reading.
 *
 * **Lowercase on purpose.** Everything else in this file uppercases its labels, and
 * doing that here would be worse: these strings have to be matched by eye against
 * the filter row, which renders them exactly as authored. Matching is the point.
 */
@Composable
private fun TagFooter(entry: Entry) {
    if (entry.tags.isEmpty()) return

    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
    Spacer(Modifier.height(14.dp))
    Text(
        "FILED UNDER",
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    Text(
        entry.tags.joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        lineHeight = 20.sp
    )
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

/**
 * Markdown emphasis, styled. The corpus is authored by hand in markdown, so
 * `*Nicomachean Ethics*` and a bolded lead were reaching the device as literal
 * asterisks — see [Inline], which does the parsing.
 */
private fun inline(source: String): AnnotatedString {
    val marked = Inline.parse(source)
    return buildAnnotatedString {
        append(marked.text)
        marked.spans.forEach { span ->
            addStyle(
                style = when (span.emphasis) {
                    Inline.Emphasis.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Inline.Emphasis.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Inline.Emphasis.BOLD_ITALIC ->
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                },
                start = span.start,
                end = span.end
            )
        }
    }
}

@Composable
private fun BlockView(block: Block) {
    when (block) {
        is Block.Paragraph -> Text(
            inline(block.text),
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
                    inline(block.text),
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
