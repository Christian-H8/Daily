package com.christianh.daily

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Surface
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
import com.christianh.daily.Domain.DailySelector
import com.christianh.daily.Domain.model.Entry
import com.christianh.daily.data.CorpusLoader
import com.christianh.daily.data.CorpusSource
import com.christianh.daily.data.LoadedCorpus
import com.christianh.daily.ui.EntryView
import com.christianh.daily.ui.TimelineScreen
import java.time.LocalDate

private sealed interface Screen {
    data object Today : Screen
    data object Timeline : Screen
    data class Reader(val entry: Entry) : Screen
}

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
                    var screen by remember { mutableStateOf<Screen>(Screen.Today) }

                    val pickFolder = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        if (uri != null) {
                            CorpusSource.saveTree(this, uri)
                            folder = CorpusSource.treeLabel(this)
                            corpus = CorpusLoader.load(this)
                        }
                    }

                    BackHandler(enabled = screen != Screen.Today) {
                        screen = if (screen is Screen.Reader) Screen.Timeline else Screen.Today
                    }

                    Column(Modifier.fillMaxSize()) {
                        NavBar(screen) { screen = it }
                        HorizontalDivider()
                        when (val s = screen) {
                            Screen.Today -> TodayScreen(
                                entry = DailySelector.entryFor(LocalDate.now(), corpus.entries),
                                corpus = corpus,
                                folder = folder,
                                onPickFolder = { pickFolder.launch(null) },
                                onReload = { corpus = CorpusLoader.load(this@MainActivity) }
                            )

                            Screen.Timeline -> TimelineScreen(
                                corpus = corpus.entries,
                                today = LocalDate.now(),
                                onOpen = { screen = Screen.Reader(it) }
                            )

                            is Screen.Reader -> ReaderScreen(s.entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBar(screen: Screen, onGo: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TextButton(onClick = { onGo(Screen.Today) }) {
            Text("Today", fontWeight = weightFor(screen is Screen.Today))
        }
        TextButton(onClick = { onGo(Screen.Timeline) }) {
            Text("Timeline", fontWeight = weightFor(screen is Screen.Timeline))
        }
    }
}

private fun weightFor(active: Boolean) = if (active) FontWeight.Bold else FontWeight.Normal

@Composable
private fun ReaderScreen(entry: Entry) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        EntryView(entry)
        Spacer(Modifier.height(48.dp))
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
            .padding(horizontal = 24.dp, vertical = 32.dp)
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

/**
 * Not decoration. Without this there is no way to tell a stale corpus from a
 * current one, and "the phone is showing last week's file" is invisible otherwise.
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
