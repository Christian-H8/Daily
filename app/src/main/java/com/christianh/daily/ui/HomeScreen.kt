package com.christianh.daily.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.christianh.daily.Domain.model.AmbientState
import com.christianh.daily.Domain.model.Condition
import com.christianh.daily.Domain.model.PartOfDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sin

private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM")

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
 * mass and static rain, and stops there.
 */
@Composable
fun HomeScreen(ambient: AmbientState, date: LocalDate, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawSky(ambient)
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
                .padding(horizontal = 24.dp, vertical = 40.dp)
        ) {
            Text(
                date.format(LONG_DATE),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                caption(ambient),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun caption(a: AmbientState): String {
    val part = when (a.partOfDay) {
        PartOfDay.DAWN -> "DAWN"
        PartOfDay.DAY -> "DAY"
        PartOfDay.DUSK -> "DUSK"
        PartOfDay.NIGHT -> "NIGHT"
    }
    val sky = a.condition?.name ?: "—"
    return "$part  ·  $sky"
}

// --- sky ---------------------------------------------------------------------

private fun DrawScope.drawSky(a: AmbientState) {
    val (top, bottom) = skyColors(a)
    drawRect(
        brush = Brush.verticalGradient(listOf(top, bottom)),
        size = size
    )
}

/**
 * Warm through the whole range — this shares a screen with brass and paper, so a
 * cold blue sky would fight everything else in the app.
 */
private fun skyColors(a: AmbientState): Pair<Color, Color> {
    val dim = when (a.condition) {
        Condition.STORM -> 0.55f
        Condition.RAIN -> 0.70f
        Condition.CLOUD, Condition.FOG -> 0.85f
        else -> 1f
    }
    val (t, b) = when (a.partOfDay) {
        PartOfDay.DAWN -> Color(0xFF6E5A6B) to Color(0xFFE0A878)
        PartOfDay.DAY -> Color(0xFF8FA6B8) to Color(0xFFEDE3D4)
        PartOfDay.DUSK -> Color(0xFF5A4A5E) to Color(0xFFC98A5E)
        PartOfDay.NIGHT -> Color(0xFF14100D) to Color(0xFF2C2438)
    }
    return t.dim(dim) to b.dim(dim)
}

private fun Color.dim(f: Float) = Color(red * f, green * f, blue * f, alpha)

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
