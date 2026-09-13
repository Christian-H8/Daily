package com.christianh.daily

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.christianh.daily.Domain.DailySelector
import com.christianh.daily.Domain.model.Entry
import com.christianh.daily.data.CorpusLoader
import com.christianh.daily.data.CorpusSource
import com.christianh.daily.data.LoadedCorpus
import com.christianh.daily.ui.DialBand
import com.christianh.daily.ui.EntryView
import com.christianh.daily.ui.TimelineScreen
import com.christianh.daily.ui.theme.DailyTheme
import java.time.LocalDate

private sealed interface Screen {
    data object Today : Screen
    data object Timeline : Screen
    data class Reader(val entry: Entry) : Screen
}

/**
 * The sections that sit on the dial's arc, in order. Reader is not one of them —
 * it is reached from the timeline and keeps the hand where it was.
 * Home and Settings extend this list, which is all it takes to subdivide the arc.
 */
private val SECTIONS = listOf(Screen.Today, Screen.Timeline)

private fun Screen.sectionIndex(): Int = when (this) {
    Screen.Today -> 0
    Screen.Timeline -> 1
    is Screen.Reader -> 1
}

/** Index -> position across the visible half. One section would sit at the left. */
private fun Int.asFraction(): Float =
    if (SECTIONS.size <= 1) 0f else this.toFloat() / (SECTIONS.size - 1).toFloat()

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 37 draws edge-to-edge whether we ask or not. Asking explicitly
        // is what gets the status-bar icons flipped to match the app's background.
        enableEdgeToEdge()
        ensureChannel(this)
        scheduleDailyQuote(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            DailyTheme {
                Surface(Modifier.fillMaxSize()) {
                    var corpus by remember { mutableStateOf(CorpusLoader.load(this)) }
                    var folder by remember { mutableStateOf(CorpusSource.treeLabel(this)) }
                    var screen by remember { mutableStateOf<Screen>(Screen.Today) }

                    val todayScroll = rememberScrollState()
                    val readerScroll = rememberScrollState()

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

                    // The dial gets out of the way the moment you start reading, and
                    // comes back when you return to the top. No timer: scrolling is
                    // the signal, and it is the one that actually means "reading".
                    val reading = when (screen) {
                        Screen.Today -> todayScroll.value > 48
                        is Screen.Reader -> readerScroll.value > 48
                        Screen.Timeline -> false
                    }
                    val dialHeight by animateDpAsState(
                        targetValue = if (reading) 0.dp else 132.dp,
                        animationSpec = tween(durationMillis = 320),
                        label = "dial-height"
                    )

                    val today = LocalDate.now()
                    val cycle = remember(corpus.entries.size, today) {
                        val n = corpus.entries.size
                        if (n <= 1) 0f
                        else DailySelector.indexFor(today, n).toFloat() / (n - 1).toFloat()
                    }

                    // Swipe drives the gears live; releasing settles to a section.
                    // Horizontal, so it never argues with the vertical scroll of an
                    // 800-word entry — the vertical axis is reserved for Home later.
                    var drag by remember { mutableStateOf(0f) }
                    val screenWidthPx = with(LocalDensity.current) {
                        LocalConfiguration.current.screenWidthDp.dp.toPx()
                    }
                    val settled by animateFloatAsState(
                        targetValue = screen.sectionIndex().asFraction(),
                        animationSpec = tween(durationMillis = 420),
                        label = "dial-settle"
                    )
                    val span = (SECTIONS.size - 1).coerceAtLeast(1)
                    val live = (settled - drag / screenWidthPx / span).coerceIn(0f, 1f)

                    val swipeable = screen !is Screen.Reader

                    Box(
                        Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .pointerInput(swipeable, SECTIONS.size) {
                                if (!swipeable) return@pointerInput
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val moved = -drag / screenWidthPx
                                        val from = screen.sectionIndex()
                                        val to = when {
                                            moved > 0.18f -> from + 1
                                            moved < -0.18f -> from - 1
                                            else -> from
                                        }.coerceIn(0, SECTIONS.size - 1)
                                        screen = SECTIONS[to]
                                        drag = 0f
                                    },
                                    onDragCancel = { drag = 0f },
                                    onHorizontalDrag = { _, delta -> drag += delta }
                                )
                            }
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            NavBar(screen) { screen = it }
                            HorizontalDivider()

                            Column(Modifier.weight(1f)) {
                                when (val s = screen) {
                                    Screen.Today -> TodayScreen(
                                        entry = DailySelector.entryFor(today, corpus.entries),
                                        corpus = corpus,
                                        folder = folder,
                                        scroll = todayScroll,
                                        bottomInset = dialHeight,
                                        onPickFolder = { pickFolder.launch(null) },
                                        onReload = { corpus = CorpusLoader.load(this@MainActivity) }
                                    )

                                    Screen.Timeline -> TimelineScreen(
                                        corpus = corpus.entries,
                                        today = today,
                                        onOpen = { screen = Screen.Reader(it) }
                                    )

                                    is Screen.Reader -> ReaderScreen(
                                        s.entry, readerScroll, dialHeight
                                    )
                                }
                            }
                        }

                        // In front of the page, on a transparent ground — the
                        // lightening holes show the text through the metal.
                        DialBand(
                            sectionFraction = live,
                            cycleFraction = cycle,
                            ticks = corpus.entries.size,
                            height = dialHeight,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
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
private fun ReaderScreen(entry: Entry, scroll: ScrollState, bottomInset: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp)
    ) {
        EntryView(entry)
        Spacer(Modifier.height(48.dp + bottomInset))
    }
}

@Composable
fun TodayScreen(
    entry: Entry?,
    corpus: LoadedCorpus,
    folder: String?,
    scroll: ScrollState,
    bottomInset: Dp,
    onPickFolder: () -> Unit,
    onReload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp)
    ) {
        if (entry == null) {
            Text("No entries yet", style = MaterialTheme.typography.bodyLarge)
        } else {
            EntryView(entry)
        }
        Spacer(Modifier.height(48.dp))
        CorpusStatus(corpus, folder, onPickFolder, onReload)
        Spacer(Modifier.height(bottomInset))
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
