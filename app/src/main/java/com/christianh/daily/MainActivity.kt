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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.christianh.daily.data.AndroidEnv
import com.christianh.daily.ui.DailyState
import com.christianh.daily.ui.DialBand
import com.christianh.daily.ui.EntryPage
import com.christianh.daily.ui.HomeScreen
import com.christianh.daily.ui.NavBar
import com.christianh.daily.ui.RotationFlicks
import com.christianh.daily.ui.SECTIONS
import com.christianh.daily.ui.Screen
import com.christianh.daily.ui.SettingsScreen
import com.christianh.daily.ui.TimelineScreen
import com.christianh.daily.ui.asFraction
import com.christianh.daily.ui.sectionIndex
import com.christianh.daily.ui.theme.DailyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The composition root, and nothing else.
 *
 * It used to be 424 lines doing four jobs at once — lifecycle, navigation, data
 * loading, and the derived values the screens print. The last three moved to
 * [com.christianh.daily.ui.DailyState] and [com.christianh.daily.domain.Derived],
 * which is what finally gave them a test seam; SDS §14.2 records why.
 *
 * What is left is what genuinely belongs to an Activity: the permission request,
 * the notification channel, the alarm, and the wiring that turns state into screens.
 */
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
                    // Remembered, not rebuilt: a fresh env on every recomposition
                    // would re-read the corpus out of the synced folder every frame.
                    val state = remember {
                        DailyState(AndroidEnv(ctx) { scheduleDailyQuote(ctx) })
                    }
                    DailyApp(state, lifecycle)
                }
            }
        }
    }
}

/**
 * The wall clock, as state rather than as a value read once.
 *
 * `LocalDate.now()` called during composition is a reading, not a subscription.
 * Nothing invalidated it, so the day and the hour stayed at whatever they were when
 * the screen was first composed and only moved when some unrelated tap happened to
 * cause a recomposition.
 *
 * Three things followed, in rising order of seriousness. The sky did not advance
 * with the hour, though the comment claimed it did. The Settings forecast
 * diagnostic reported a stale hour, so [com.christianh.daily.domain.Derived.forecastLine]
 * could call a perfectly good file stale. And past midnight the app showed
 * **yesterday's entry**.
 *
 * The last one sat on the morning path. [MainActivity] declares no `launchMode` and
 * the notification's `PendingIntent` carries no flags, so tapping it resumes the
 * existing task instead of recreating the Activity — `onCreate` does not run again,
 * and the composition that comes back is the stale one. The single path this app
 * exists for was the one that failed, and it failed silently, by showing a real
 * entry that was simply the wrong day's.
 *
 * Ticks on the minute. The hour is what the sky needs, but midnight is a minute
 * boundary too, and a coroutine that wakes once a minute while the screen is in
 * front of you costs nothing worth measuring. It is scoped to the composition, so
 * it stops when the app does.
 */
@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val at = LocalDateTime.now()
            now = at
            // Sleep to the next boundary rather than a flat 60 seconds, so the tick
            // lands just after :00 instead of drifting further from it every pass.
            val next = at.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(Duration.between(at, next).toMillis().coerceAtLeast(50L))
        }
    }
    return now
}

/**
 * The whole app below the Activity.
 *
 * Everything it needs arrives through [DailyState], with one deliberate exception:
 * [lifecycle], which [RotationFlicks] needs in order to stop sampling the gyroscope
 * when the app is not in front of anyone. The composition survives backgrounding,
 * so without it a 50 Hz sensor would keep running behind the home screen for as
 * long as the app stayed in memory.
 */
@Composable
private fun DailyApp(state: DailyState, lifecycle: Lifecycle) {
    val now = rememberNow()
    val today = now.toLocalDate()
    // Truncated to the hour, because that is the resolution the forecast file has
    // rows for — and keying the reload off it means one reload an hour, not one a
    // minute.
    val nowHour = now.truncatedTo(ChronoUnit.HOURS)

    LaunchedEffect(nowHour) { state.refreshForecast() }

    val todayScroll = rememberScrollState()
    val readerScroll = rememberScrollState()

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) state.chooseFolder(uri) }

    BackHandler(enabled = state.screen != Screen.Today) { state.back() }

    // Which page's scroll counts right now. Null on the screens you do not read.
    val readingScroll = when (state.screen) {
        Screen.Today -> todayScroll
        is Screen.Reader -> readerScroll
        else -> null
    }

    // Swipe drives the gears live; releasing settles to a section. Horizontal, so it
    // never argues with the vertical scroll of an 800-word entry.
    var drag by remember { mutableStateOf(0f) }
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val settled by animateFloatAsState(
        targetValue = state.screen.sectionIndex().asFraction(),
        animationSpec = tween(durationMillis = 420),
        label = "dial-settle"
    )
    val span = (SECTIONS.size - 1).coerceAtLeast(1)
    val live = (settled - drag / screenWidthPx / span).coerceIn(0f, 1f)
    val swipeable = state.screen !is Screen.Reader

    // Bumped by anything deliberate. Screen changes and flicks nudge it here;
    // touches nudge it from the observer on the Box below.
    var nudge by remember { mutableStateOf(0) }

    // The same gesture as the swipe, made with the wrist instead of the thumb — so
    // it is available in exactly the same places, and `swipeable` is the whole gate.
    //
    // It was also gated on `!reading` for one build, on the theory that the reading
    // posture is where the false positives live. Wrong, and wrong in a way that was
    // obvious the moment it was on a phone: the swipe kept working mid-entry while
    // the flick stopped, so two names for one action behaved differently for no
    // reason anyone could infer from the outside. If the sensitivity is right the
    // gate is unnecessary, and at 3.5 rad/s it is right.
    RotationFlicks(
        enabled = state.flickNavigation && swipeable,
        lifecycle = lifecycle,
        onFlick = { direction ->
            nudge++
            state.settleTo(state.screen.sectionIndex() + direction)
        }
    )

    LaunchedEffect(state.screen) { nudge++ }

    /*
     * One rule: **the dial is up for five seconds after you navigate, and gone
     * otherwise.**
     *
     * It used to collapse on scroll and expand at the top of a page, which meant it
     * was absent during the one event it exists to report. You would flick mid-entry,
     * the section would change, and the mechanism that is supposed to show you the
     * change had already retreated. A dial you cannot see turn is a decoration.
     *
     * So navigation shows it — screen change, flick, or a settled swipe — and
     * nothing else does. Scrolling does not summon it, because scrolling is reading,
     * and it actively dismisses it: start reading and the dial is out of the way at
     * once rather than sitting through the rest of its five seconds on top of the
     * text.
     *
     * The old scroll-derived `reading` flag is gone. With a timeout it had nothing
     * left to decide — anything it would have collapsed, idling collapses anyway.
     */
    var dialIdle by remember { mutableStateOf(false) }

    LaunchedEffect(nudge) {
        dialIdle = false
        delay(5_000)
        dialIdle = true
    }

    // `drop(1)` because subscribing emits the current value: only a *change* is
    // someone scrolling, and the first emission is just the page arriving.
    LaunchedEffect(readingScroll) {
        val scroll = readingScroll ?: return@LaunchedEffect
        snapshotFlow { scroll.value }.drop(1).collect { dialIdle = true }
    }

    // Height, not alpha. Fading would leave 150dp of reserved emptiness under the
    // last line of an entry, and the space is the point of getting out of the way.
    // Slow away, quick back: a mechanism that takes its time withdrawing reads as
    // deliberate, one that takes its time returning reads as broken.
    val dialHeight by animateDpAsState(
        targetValue = if (dialIdle) 0.dp else 150.dp,
        animationSpec = tween(durationMillis = if (dialIdle) 620 else 240),
        label = "dial-height"
    )

    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .pointerInput(swipeable, SECTIONS.size) {
                if (!swipeable) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val moved = -drag / screenWidthPx
                        val from = state.screen.sectionIndex()
                        val to = when {
                            moved > 0.18f -> from + 1
                            moved < -0.18f -> from - 1
                            else -> from
                        }
                        if (to != from) nudge++
                        state.settleTo(to)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                    onHorizontalDrag = { _, delta -> drag += delta }
                )
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            NavBar(current = state.screen, onGo = { state.go(it) })
            HorizontalDivider()

            // The gears already follow the finger; without this the page underneath
            // them simply cut, and a mechanism that glides over content that
            // teleports reads as broken. The content moves at 0.35x the drag — the
            // parallax against the dial's full travel is what gives it depth.
            AnimatedContent(
                targetState = state.screen,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { translationX = drag * 0.35f },
                transitionSpec = {
                    val dir =
                        if (targetState.sectionIndex() >= initialState.sectionIndex()) 1
                        else -1
                    (slideInHorizontally(tween(340)) { w -> dir * w } +
                        fadeIn(tween(220)))
                        .togetherWith(
                            slideOutHorizontally(tween(340)) { w -> -dir * w } +
                                fadeOut(tween(180))
                        )
                },
                label = "screen"
            ) { current ->
                when (val s = current) {
                    Screen.Home -> HomeScreen(
                        ambient = state.ambientAt(nowHour),
                        // The minute, not the hour: the sky's condition comes from
                        // the forecast's hourly rows, but the sun's position is just
                        // the clock, and it should drift rather than step.
                        at = now,
                        date = today,
                        entry = state.entryFor(today),
                        passDay = state.passDay(today),
                        passLength = state.passLength,
                        lastNote = state.lastNote,
                        onOpenToday = { state.go(Screen.Today) }
                    )

                    Screen.Today -> {
                        val entry = state.entryFor(today)
                        EntryPage(
                            entry = entry,
                            date = today,
                            note = state.noteText(today),
                            scroll = todayScroll,
                            bottomInset = dialHeight,
                            onNote = { text ->
                                state.writeNote(today, entry?.id.orEmpty(), text)
                            }
                        )
                    }

                    Screen.Timeline -> TimelineScreen(
                        corpus = state.entries,
                        today = today,
                        notes = state.noteTexts,
                        onOpen = { state.go(Screen.Reader(it)) }
                    )

                    Screen.Settings -> SettingsScreen(
                        hour = state.hour,
                        minute = state.minute,
                        onTime = { h, m -> state.setTime(h, m) },
                        forecastOverride = state.forecastOverride,
                        onForecastOverride = { c -> state.overrideForecast(c) },
                        flickNavigation = state.flickNavigation,
                        onFlickNavigation = { on -> state.useFlickNavigation(on) },
                        forecastLine = state.forecastLineAt(nowHour),
                        corpusLine = state.corpusLine,
                        sourceLine = state.sourceLine,
                        onPickFolder = { pickFolder.launch(null) },
                        onReload = { state.reloadCorpus() },
                        backupLine = state.backupLine,
                        restoreLine = state.restoreLine,
                        onRestore = { state.restoreNotes() }
                    )

                    is Screen.Reader -> EntryPage(
                        entry = s.day.entry,
                        date = s.day.date,
                        note = state.noteText(s.day.date),
                        scroll = readerScroll,
                        bottomInset = dialHeight,
                        onNote = { text ->
                            state.writeNote(s.day.date, s.day.entry.id, text)
                        }
                    )
                }
            }
        }

        // In front of the page, on a transparent ground — the lightening holes show
        // the text through the metal.
        DialBand(
            sectionFraction = live,
            cycleFraction = state.cycleFraction(today),
            ticks = state.entries.size,
            height = dialHeight,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
