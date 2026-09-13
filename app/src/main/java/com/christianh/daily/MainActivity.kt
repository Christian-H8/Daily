package com.christianh.daily

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.christianh.daily.Domain.DailySelector
import com.christianh.daily.Domain.model.Block
import com.christianh.daily.Domain.model.Entry
import com.christianh.daily.Domain.model.Section
import com.christianh.daily.data.CorpusLoader
import com.christianh.daily.data.CorpusSource
import com.christianh.daily.data.LoadedCorpus
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureChannel(this)
        scheduleDailyQuote(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                Surface {
                    var corpus by remember { mutableStateOf(CorpusLoader.load(this)) }
                    var folder by remember { mutableStateOf(CorpusSource.treeLabel(this)) }

                    val pickFolder = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        if (uri != null) {
                            CorpusSource.saveTree(this, uri)
                            folder = CorpusSource.treeLabel(this)
                            corpus = CorpusLoader.load(this)
                        }
                    }

                    TodayScreen(
                        entry = DailySelector.entryFor(LocalDate.now(), corpus.entries),
                        corpus = corpus,
                        folder = folder,
                        onPickFolder = { pickFolder.launch(null) },
                        onReload = { corpus = CorpusLoader.load(this) }
                    )
                }
            }
        }
    }
}

@Composable
fun TodayScreen(
    entry: Entry?,
    corpus: LoadedCorpus,
    folder: String?,
    onPickFolder: () -> Unit,
    onReload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        if (entry == null) {
            Text("No entries yet", style = MaterialTheme.typography.bodyLarge)
        } else {
            EntryView(entry)
        }
        Spacer(Modifier.height(48.dp))
        CorpusStatus(corpus, folder, onPickFolder, onReload)
    }
}

@Composable
private fun EntryView(entry: Entry) {
    Text(
        "${entry.type.uppercase()}  ·  ${entry.minutes} MIN",
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

/**
 * Not decoration. Without this there is no way to tell a stale corpus from a
 * current one, and "the phone is showing yesterday's file" is invisible otherwise.
 */
@Composable
private fun CorpusStatus(
    corpus: LoadedCorpus,
    folder: String?,
    onPickFolder: () -> Unit,
    onReload: () -> Unit
) {
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(
        "${corpus.entries.size} entries  ·  ${corpus.version}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        if (corpus.fromSyncedFolder) "from ${folder ?: "synced folder"}" else "bundled copy",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row {
        TextButton(onClick = onPickFolder) { Text("Choose folder") }
        TextButton(onClick = onReload) { Text("Reload") }
    }
}
