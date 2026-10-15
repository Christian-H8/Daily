package com.christianh.daily.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.christianh.daily.domain.model.AmbientState
import com.christianh.daily.domain.model.Condition
import com.christianh.daily.domain.model.Note
import com.christianh.daily.domain.model.PartOfDay
import com.christianh.daily.domain.model.Entry
import com.christianh.daily.ui.theme.Ink
import com.christianh.daily.ui.theme.Lamp
import com.christianh.daily.ui.theme.Night
import com.christianh.daily.ui.theme.Paper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val NOTE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * The atmosphere screen (DESIGN §4).
 *
 * **Never on the path from notification to entry.** Tapping the notification goes
 * straight to the reading screen; Home is somewhere you go, not something you wade
 * through at 8am.
 *
 * The forecast is rendered rather than reported — no icons, no temperature tiles,
 * nothing that reads as a weather widget.
 *
 * **Deliberately unanimated.** Falling rain and lightning were flagged as the most
 * expensive piece and the most likely to look cheap; this draws the sky, the cloud
 * mass and static rain, and stops there ([R9.4] stays deferred).
 */
@Composable
fun HomeScreen(
    ambient: AmbientState,
    at: LocalDateTime,
    date: LocalDate,
    entry: Entry?,
    passDay: Int,
    passLength: Int,
    lastNote: Note?,
    onOpenToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (skyTop, skyBottom) = skyColors(ambient)

    /*
     * The words sit straight on the weather, and carry their own contrast.
     *
     * The first attempt was `if (skyTop.luminance() > 0.45f) Ink else Lamp`, which
     * was wrong twice over: no `skyTop` in the palette is above 0.45, so the branch
     * never ran and the text was always [Lamp] — cream at **1.07:1** against the
     * bottom of a clear day, which is invisible.
     *
     * The second attempt put the text on a translucent panel. That worked and it
     * cost the screen its character, which is the wrong trade on the one screen
     * whose entire job is atmosphere.
     *
     * **A colour alone cannot do it.** Measured across all sixteen sky states —
     * four times of day by four conditions, with the condition dimming applied —
     * picking whichever of the two inks does better bottoms out at **2.12:1**
     * (dawn under cloud, dusk on a clear evening). The gradient spans too much
     * range, and the dimming slides it around underneath. There is no winner.
     *
     * So: the better of the two inks *and* a soft halo under the glyphs. The halo
     * is what text over photographs has always used — it makes contrast at the
     * letter edge, where reading actually happens, instead of demanding it from the
     * whole background. Nothing is drawn behind the text; the sky is untouched.
     */
    val topLum = skyTop.luminance()
    val botLum = skyBottom.luminance()
    val ink = if (contrast(Ink, topLum, botLum) >= contrast(Lamp, topLum, botLum)) Ink else Lamp
    val faint = ink.copy(alpha = 0.72f)

    // Opposite the ink, offset barely at all, and wide enough to be a halo rather
    // than a drop shadow — a visible offset would read as a print misregistration.
    val halo = Shadow(
        color = (if (ink == Ink) Paper else Night).copy(alpha = 0.72f),
        offset = Offset(0f, 0.5f),
        blurRadius = 9f
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(listOf(skyTop, skyBottom)), size = size)

            // Light in the sky rather than objects on it. Drawn between the
            // gradient and the weather, and coloured from the gradient itself.
            val clear = clearness(ambient.condition)
            if (ambient.partOfDay == PartOfDay.NIGHT) {
                drawStars(clear)
                drawMoon(at, clear)
            } else {
                drawSun(at, lerp(skyBottom, Color(0xFFFFF1D6), 0.45f), clear)
            }

            when (ambient.condition) {
                Condition.CLOUD -> drawClouds(0.55f, 0.5f)
                Condition.RAIN -> { drawClouds(0.75f, 0.62f); drawRain(0.40f) }
                Condition.STORM -> { drawClouds(0.90f, 0.72f); drawRain(0.62f) }
                Condition.FOG -> drawFog()
                Condition.SNOW -> { drawClouds(0.60f, 0.55f); drawSnow() }
                Condition.CLEAR, null -> Unit
            }
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp)
        ) {
            Text(
                date.format(LONG_DATE),
                style = MaterialTheme.typography.headlineMedium.copy(shadow = halo),
                color = ink
            )
            Spacer(Modifier.height(6.dp))
            Text(caption(ambient), style = MaterialTheme.typography.labelMedium.copy(shadow = halo),
                letterSpacing = 1.5.sp, color = faint)

            if (entry != null) {
                Spacer(Modifier.height(40.dp))
                Text("TODAY", style = MaterialTheme.typography.labelSmall.copy(shadow = halo),
                    letterSpacing = 1.5.sp, color = faint)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().clickable { onOpenToday() }) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.headlineMedium.copy(shadow = halo),
                        color = ink,
                        lineHeight = 34.sp
                    )
                    if (entry.tagline.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            entry.tagline,
                            style = MaterialTheme.typography.titleSmall.copy(shadow = halo),
                            fontStyle = FontStyle.Italic,
                            color = faint,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // The slow hand, in words. The dial shows this too, but a position in a
            // cycle is easier to read as a sentence than as an angle.
            if (passLength > 0) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "DAY $passDay OF $passLength IN THIS PASS",
                    style = MaterialTheme.typography.labelSmall.copy(shadow = halo),
                    letterSpacing = 1.5.sp,
                    color = faint
                )
            }

            if (lastNote != null && lastNote.text.isNotBlank()) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "LAST WRITTEN  ·  ${lastNote.date.format(NOTE_DATE).uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(shadow = halo),
                    letterSpacing = 1.5.sp,
                    color = faint
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    lastNote.text.trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = halo),
                    fontStyle = FontStyle.Italic,
                    color = ink.copy(alpha = 0.92f),
                    lineHeight = 22.sp,
                    maxLines = 4
                )
            }
        }
    }
}

/**
 * The worse of this ink's two contrast ratios, against the top and bottom of the
 * gradient. Worst-case, because the text spans the whole thing — judging by the top
 * alone is precisely the mistake that made the screen unreadable.
 */
private fun contrast(ink: Color, topLum: Float, botLum: Float): Float =
    minOf(ratio(ink.luminance(), topLum), ratio(ink.luminance(), botLum))

private fun ratio(a: Float, b: Float): Float {
    val hi = maxOf(a, b)
    val lo = minOf(a, b)
    return (hi + 0.05f) / (lo + 0.05f)
}

private fun caption(a: AmbientState): String {
    val part = when (a.partOfDay) {
        PartOfDay.DAWN -> "DAWN"
        PartOfDay.DAY -> "DAY"
        PartOfDay.DUSK -> "DUSK"
        PartOfDay.NIGHT -> "NIGHT"
    }
    return "$part  ·  ${a.condition?.name ?: "—"}"
}

// --- sky ---------------------------------------------------------------------

/**
 * Warm through the whole range — this shares an app with brass and paper, so a cold
 * blue sky would fight everything else.
 */
private fun skyColors(a: AmbientState): Pair<Color, Color> {
    val dim = when (a.condition) {
        Condition.STORM -> 0.55f
        Condition.RAIN -> 0.70f
        Condition.CLOUD, Condition.FOG -> 0.85f
        else -> 1f
    }
    val pair = when (a.partOfDay) {
        PartOfDay.DAWN -> Color(0xFF6E5A6B) to Color(0xFFE0A878)
        PartOfDay.DAY -> Color(0xFF8FA6B8) to Color(0xFFEDE3D4)
        PartOfDay.DUSK -> Color(0xFF5A4A5E) to Color(0xFFC98A5E)
        PartOfDay.NIGHT -> Color(0xFF14100D) to Color(0xFF2C2438)
    }
    return pair.first.dim(dim) to pair.second.dim(dim)
}

private fun Color.dim(f: Float) = Color(red * f, green * f, blue * f, alpha)

// --- sun, moon, stars ---------------------------------------------------------

/**
 * How much light gets through the weather. Not the same as the gradient's `dim`:
 * cloud darkens the sky a little and hides the sun almost entirely, so the two
 * curves are different and a storm takes it to nothing.
 */
private fun clearness(c: Condition?): Float = when (c) {
    null, Condition.CLEAR -> 1f
    Condition.CLOUD -> 0.42f
    Condition.SNOW -> 0.34f
    Condition.FOG -> 0.28f
    Condition.RAIN -> 0.14f
    Condition.STORM -> 0f
}

/**
 * Cloud does not only dim a sun, it *spreads* it — the disc goes and a broad
 * brightness stays. So the murkier the sky, the wider and flatter the bloom, which
 * is the one line that makes this part of the weather rather than a layer over it.
 */
private fun spread(clear: Float): Float = 1f + (1f - clear) * 0.85f

/**
 * Where the light sits, from [rise] o'clock across [span] hours: low at both ends,
 * highest at the midpoint.
 *
 * This is the one place the screen says something it was not told. The sky is
 * painted from a four-value part-of-day, so 9am and 4pm are identical; the light is
 * placed from the actual clock, so they are not. Rendered rather than reported,
 * which is the rule the rest of this screen already follows.
 *
 * **Biased right and low.** The words live at the top left, and although nothing
 * here has a hard enough edge to collide with anything, the brightest part of the
 * bloom has no business sitting behind the date.
 *
 * Hours before [rise] are pushed into the next day so the night arc crosses
 * midnight without jumping back to the horizon at 00:00.
 */
private fun DrawScope.arcPosition(at: LocalDateTime, rise: Float, span: Float): Offset {
    val hour = at.hour + at.minute / 60f
    val wrapped = if (hour < rise) hour + 24f else hour
    val t = ((wrapped - rise) / span).coerceIn(0f, 1f)
    return Offset(
        x = size.width * (0.40f + 0.55f * t),
        y = size.height * (0.66f - 0.40f * sin(PI.toFloat() * t))
    )
}

/**
 * The sun, as a bloom with no edge.
 *
 * An earlier version drew a disc at full alpha with a halo around it. It read as a
 * sticker: a hard-edged object sitting on the sky rather than light coming through
 * it, and bright enough to compete with the text it sat behind.
 *
 * [warm] arrives already mixed from the gradient's own lower stop, so the glow is
 * whatever colour the sky is at the horizon — orange at dawn, pale at midday —
 * without this function knowing anything about the palette. That is what keeps it
 * from ever clashing: it cannot introduce a colour the sky does not already have.
 *
 * The inner bloom is only twice as dense as the outer one. Enough to suggest where
 * the light is coming from, not enough to have a rim.
 */
private fun DrawScope.drawSun(at: LocalDateTime, warm: Color, clear: Float) {
    if (clear <= 0f) return
    val c = arcPosition(at, rise = 5f, span = 15f)
    val wide = size.minDimension * 0.62f * spread(clear)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(warm.copy(alpha = 0.26f * clear), Color.Transparent),
            center = c,
            radius = wide
        ),
        radius = wide,
        center = c
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(warm.copy(alpha = 0.22f * clear), Color.Transparent),
            center = c,
            radius = wide * 0.26f
        ),
        radius = wide * 0.26f,
        center = c
    )
}

/**
 * The moon gets a faint disc where the sun gets none, because a bloom alone on a
 * dark sky is just a lighter patch of night — there has to be something there.
 *
 * **Not a crescent.** A crescent states a phase, and the app has no idea what
 * tonight's phase is; drawing one would be the screen claiming to know something it
 * does not, which is the same fault as a stale forecast.
 */
private fun DrawScope.drawMoon(at: LocalDateTime, clear: Float) {
    if (clear <= 0f) return
    val c = arcPosition(at, rise = 20f, span = 10f)
    val pale = Color(0xFFD9CFBA)
    val wide = size.minDimension * 0.34f * spread(clear)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(pale.copy(alpha = 0.16f * clear), Color.Transparent),
            center = c,
            radius = wide
        ),
        radius = wide,
        center = c
    )
    drawCircle(pale.copy(alpha = 0.34f * clear), size.minDimension * 0.034f, c)
}

/**
 * A fixed scatter, thinning toward the bottom where the ground would be. Seeded the
 * same way as the rain and the snow, so the field is identical every night — a sky
 * that reshuffled itself on every recomposition would be noticeable and wrong.
 *
 * Kept faint enough to be something you notice on the second look.
 */
private fun DrawScope.drawStars(clear: Float) {
    if (clear <= 0f) return
    var seed = 11f
    repeat(64) { i ->
        seed = (seed * 2.7f + i * 1.3f) % 101f
        val x = (seed / 101f) * size.width
        val y = ((seed * 4.3f) % 100f) / 100f * size.height * 0.78f
        val brightness = 0.18f + ((seed * 7f) % 10f) / 22f
        val radius = 0.8f + ((seed * 3f) % 10f) / 12f
        drawCircle(
            color = Color(0xFFEDE3D4).copy(alpha = clear * brightness * 0.34f),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

// --- weather -----------------------------------------------------------------

/** Soft overlapping ellipses. Cheap, and at low alpha it reads as mass rather than shapes. */
private fun DrawScope.drawClouds(alpha: Float, coverage: Float) {
    val base = Color(0xFF3B3229)
    val band = size.height * coverage * 0.42f
    var seed = 7f
    repeat(14) { i ->
        seed = (seed * 1.7f + i) % 31f
        val cx = (seed / 31f) * size.width
        val cy = band * (0.15f + (i % 4) * 0.18f)
        val r = size.width * (0.18f + (seed % 5f) / 22f)
        drawOval(
            color = base.copy(alpha = alpha * 0.16f),
            topLeft = Offset(cx - r, cy - r * 0.42f),
            size = Size(r * 2f, r * 0.84f)
        )
    }
}

private fun DrawScope.drawRain(density: Float) {
    val n = (size.width / 14f * density).toInt().coerceAtLeast(12)
    val colour = Color(0xFFCBBFA8)
    var seed = 13f
    repeat(n) { i ->
        seed = (seed * 2.3f + i) % 97f
        val x = (seed / 97f) * size.width
        val y = ((seed * 3f) % 100f) / 100f * size.height
        val len = size.height * 0.045f
        drawLine(
            color = colour.copy(alpha = 0.28f),
            start = Offset(x, y),
            end = Offset(x - len * 0.22f, y + len),
            strokeWidth = 1.4f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnow() {
    var seed = 5f
    repeat(50) { i ->
        seed = (seed * 2.9f + i) % 89f
        val x = (seed / 89f) * size.width
        val y = ((seed * 5f) % 100f) / 100f * size.height
        drawCircle(Color(0xFFEDE3D4).copy(alpha = 0.35f), radius = 2.2f, center = Offset(x, y))
    }
}

private fun DrawScope.drawFog() {
    repeat(6) { i ->
        val y = size.height * (0.25f + i * 0.11f)
        val h = size.height * 0.06f
        drawRect(
            color = Color(0xFFEDE3D4).copy(alpha = 0.05f + 0.015f * sin(i.toFloat())),
            topLeft = Offset(0f, y),
            size = Size(size.width, h)
        )
    }
}
