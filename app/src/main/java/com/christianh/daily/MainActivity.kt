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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.christianh.daily.Domain.Day
import com.christianh.daily.Domain.model.AmbientState
import com.christianh.daily.Domain.model.Entry
import com.christianh.daily.Domain.model.partOfDay
import com.christianh.daily.data.CorpusLoader
import com.christianh.daily.data.CorpusSource
import com.christianh.daily.data.ForecastSource
import com.christianh.daily.data.NoteRepository
import com.christianh.daily.data.Prefs
import com.christianh.daily.ui.DialBand
import com.christianh.daily.ui.EntryView
import com.christianh.daily.ui.HomeScreen
import com.christianh.daily.ui.NoteEditor
import com.christianh.daily.ui.SettingsScreen
import com.christianh.daily.ui.TimelineScreen
import com.christianh.daily.ui.theme.DailyTheme
import java.time.LocalDate
import java.time.LocalTime

private sealed interface Screen {
    data object Home : Screen
    data object Today : Screen
    data object Timeline : Screen
    data object Settings : Screen
    data class Reader(val day: Day) : Screen
}

/**
 * The sections on the dial's arc, in order. `Reader` is not one of them — it is
 * reached from the timeline and keeps the hand where it was.
 *
 * Adding a section is an entry here and a branch in the `when`. The arc subdivides
 * itself: four sections sit at 0, ⅓, ⅔ and 1 across the visible half.
 */
private val SECTIONS = listOf(Screen.Home, Screen.Today, Screen.Timeline, Screen.Settings)

private fun Screen.sectionIndex(): Int = when (this) {
    Screen.Home -> 0
    Screen.Today -> 1
    Screen.Timeline -> 2
    Screen.Settings -> 3
    is Screen.Reader -> 2
}

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
                    val ctx = this@MainActivity
                    val today = LocalDate.now()

                    var corpus by remember { mutableStateOf(CorpusLoader.load(ctx)) }
                    var folder by remember { mutableStateOf(CorpusSource.treeLabel(ctx)) }
                    var screen by remember { mutableStateOf<Screen>(Screen.Today) }
                    var notes by remember { mutableStateOf(NoteRepository.all(ctx)) }
                    var hour by remember { mutableStateOf(Prefs.hour(ctx)) }
                    var minute by remember { mutableStateOf(Prefs.minute(ctx)) }
                    var override by remember { mutableStateOf(Prefs.forecastOverride(ctx)) }

                    val todayScroll = rememberScrollState()
                    val readerScroll = rememberScrollState()

                    val pickFolder = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        if (uri != null) {
                            CorpusSource.saveTree(ctx, uri)
                            folder = CorpusSource.treeLabel(ctx)
                            corpus = CorpusLoader.load(ctx)
                        }
                    }

                    BackHandler(enabled = screen != Screen.Today) {
                        screen = if (screen is Screen.Reader) Screen.Timeline else Screen.Today
                    }

                    // The dial retreats the moment reading starts, and returns at the
                    // top. Scroll is the signal, not a timer — a timer would hide it
                    // while you sit still thinking, which is the opposite of the point.
                    val reading = when (screen) {
                        Screen.Today -> todayScroll.value > 48
                        is Screen.Reader -> readerScroll.value > 48
                        else -> false
                    }
                    val dialHeight by animateDpAsState(
                        targetValue = if (reading) 0.dp else 150.dp,
                        animationSpec = tween(durationMillis = 320),
                        label = "dial-height"
                    )

                    val cycle = remember(corpus.entries.size, today) {
                        val n = corpus.entries.size
                        if (n <= 1) 0f
                        else DailySelector.indexFor(today, n).toFloat() / (n - 1).toFloat()
                    }

                    val ambient = remember(override, today, folder) {
                        AmbientState(
                            partOfDay = partOfDay(LocalTime.now()),
                            condition = override
                                ?: ForecastSource.today(ctx, today)?.condition
                        )
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
                                    Screen.Home -> HomeScreen(ambient, today)

                                    Screen.Today -> {
                                        val entry = DailySelector.entryFor(today, corpus.entries)
                                        TodayScreen(
                                            entry = entry,
                                            date = today,
                                            note = notes[today]?.text.orEmpty(),
                                            scroll = todayScroll,
                                            bottomInset = dialHeight,
                                            onNote = { text ->
                                                NoteRepository.put(
                                                    ctx, today, entry?.id.orEmpty(), text
                                                )
                                                notes = NoteRepository.all(ctx)
                                            }
                                        )
                                    }

                                    Screen.Timeline -> TimelineScreen(
                                        corpus = corpus.entries,
                                        today = today,
                                        notes = notes.mapValues { it.value.text },
                                        onOpen = { screen = Screen.Reader(it) }
                                    )

                                    Screen.Settings -> SettingsScreen(
                                        hour = hour,
                                        minute = minute,
                                        onTime = { h, m ->
                                            hour = h; minute = m
                                            Prefs.setTime(ctx, h, m)
                                            scheduleDailyQuote(ctx)
                                        },
                                        forecastOverride = override,
                                        onForecastOverride = { c ->
                                            override = c
                                            Prefs.setForecastOverride(ctx, c)
                                        },
                                        corpusLine = "${corpus.entries.size} entries  ·  ${corpus.version}",
                                        sourceLine = if (corpus.fromSyncedFolder)
                                            "from ${folder ?: "synced folder"}" else "bundled copy",
                                        onPickFolder = { pickFolder.launch(null) },
                                        onReload = { corpus = CorpusLoader.load(ctx) }
                                    )

                                    is Screen.Reader -> ReaderScreen(
                                        day = s.day,
                                        note = notes[s.day.date]?.text.orEmpty(),
                                        scroll = readerScroll,
                                        bottomInset = dialHeight,
                                        onNote = { text ->
                                            NoteRepository.put(
                                                ctx, s.day.date, s.day.entry.id, text
                                            )
                                            notes = NoteRepository.all(ctx)
                                        }
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        NavItem("Home", screen is Screen.Home) { onGo(Screen.Home) }
        NavItem("Today", screen is Screen.Today) { onGo(Screen.Today) }
        NavItem("Timeline", screen is Screen.Timeline) { onGo(Screen.Timeline) }
        NavItem("Settings", screen is Screen.Settings) { onGo(Screen.Settings) }
    }
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

@Composable
private fun ReaderScreen(
    day: Day,
    note: String,
    scroll: ScrollState,
    bottomInset: Dp,
    onNote: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp)
    ) {
        EntryView(day.entry)
        Spacer(Modifier.height(16.dp))
        NoteEditor(date = day.date, initial = note, onSave = onNote)
        Spacer(Modifier.height(48.dp + bottomInset))
    }
}

@Composable
fun TodayScreen(
    entry: Entry?,
    date: LocalDate,
    note: String,
    scroll: ScrollState,
    bottomInset: Dp,
    onNote: (String) -> Unit
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
            Spacer(Modifier.height(16.dp))
            NoteEditor(date = date, initial = note, onSave = onNote)
        }
        Spacer(Modifier.height(48.dp + bottomInset))
    }
}
