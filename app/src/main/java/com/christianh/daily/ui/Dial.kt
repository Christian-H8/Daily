package com.christianh.daily.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.christianh.daily.ui.theme.BrassColors
import com.christianh.daily.ui.theme.LocalBrass
import kotlin.math.cos
import kotlin.math.sin

/**
 * The dial (DESIGN §2). Half a circle, centred exactly on the bottom edge so the
 * upper half shows. Drawn on a transparent ground — it floats in front of the
 * page, and the lightening holes in each gear are real holes: the text shows
 * through them.
 *
 * It is not a clock. Two hands, both showing position:
 *
 *  - the **fast hand** is which section you are in, and follows the swipe live
 *  - the **slow hand** is where today sits in the corpus rotation, a step per day
 *
 * Tick marks are the corpus — one per entry — so the ring gets denser as the
 * corpus grows and the slow hand's travel means something visible.
 *
 * The clock is inaccurate on purpose; the *mechanism* is not. Tooth counts mesh,
 * centres sit exactly r1 + r2 apart, and every satellite's angle is derived from
 * one drive value by its real tooth ratio.
 *
 * ## A rejected rebuild, and why it is worth recording
 *
 * This was once rebuilt as five stacked planes — a translucent movement plate, a
 * blurred gear behind it, receded satellites, the front gear, and a glass layer
 * with a specular sweep — chasing more depth. It was worse, and the reason is in
 * the third sentence of this comment: **the holes are real holes.** Putting a plate
 * behind the gears filled them in. What had been metal laid over a page became a
 * picture of a mechanism pasted on top of one, and every other cue added — shadows,
 * blur, parallax — was paying for a loss it had itself created.
 *
 * The transparency is not a limitation this design works around. It *is* the
 * design. Anything that closes it needs to be worth more than it, and a plate
 * was not.
 */
@Composable
fun DialBand(
    sectionFraction: Float,
    cycleFraction: Float,
    ticks: Int,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp
) {
    val brass = LocalBrass.current
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (size.height <= 0f) return@Canvas

        val r = size.height
        val c = Offset(size.width / 2f, size.height)
        val f = sectionFraction.coerceIn(0f, 1f)

        // One number drives the whole train. A full sweep of the hand turns the
        // main gear 120°; everything else follows by tooth ratio.
        val drive = f * 120f
        val main = r * 0.96f

        // Satellites first, so the main gear's rim overlaps them at the mesh
        // point — which is what stops it reading as three separate circles.
        for (sat in SATELLITES) {
            val sr = main * sat.ratio
            val dist = main + sr                       // meshing: centres at r1 + r2
            val a = Math.toRadians(sat.bearing.toDouble())
            drawGear(
                center = Offset(
                    c.x + (cos(a) * dist).toFloat(),
                    c.y + (sin(a) * dist).toFloat()
                ),
                radius = sr,
                teeth = sat.teeth,
                rotationDeg = -drive * (MAIN_TEETH.toFloat() / sat.teeth),
                brass = brass,
                holes = 3,
                alpha = 0.85f
            )
        }

        drawGear(
            center = c,
            radius = main,
            teeth = MAIN_TEETH,
            rotationDeg = drive,
            brass = brass,
            holes = 6,
            alpha = 1f
        )

        drawTickRing(c, main * 0.62f, ticks, quiet)

        // Slow hand under the fast one.
        drawHand(c, main * 0.50f, cycleFraction, quiet.copy(alpha = 0.75f), main * 0.020f)
        drawHand(c, main * 0.74f, f, accent, main * 0.034f)

        // Cap over both pivots.
        drawCircle(brass.hi, main * 0.070f, c)
        drawCircle(brass.lo, main * 0.070f, c, style = Stroke(width = main * 0.012f))
        drawCircle(accent, main * 0.026f, c)
    }
}

private const val MAIN_TEETH = 40

private data class Satellite(val teeth: Int, val ratio: Float, val bearing: Float)

/** Bearings in canvas degrees: -90 is straight up. Ratio = teeth / MAIN_TEETH. */
private val SATELLITES = listOf(
    Satellite(teeth = 10, ratio = 10f / MAIN_TEETH, bearing = -152f),
    Satellite(teeth = 16, ratio = 16f / MAIN_TEETH, bearing = -30f)
)

/**
 * One gear: toothed rim, web, bore and lightening holes — all a single path with
 * an even-odd fill, so the holes are genuinely transparent rather than painted in
 * a background colour. That is the whole reason this reads as metal over paper
 * instead of a sticker.
 */
private fun DrawScope.drawGear(
    center: Offset,
    radius: Float,
    teeth: Int,
    rotationDeg: Float,
    brass: BrassColors,
    holes: Int,
    alpha: Float
) {
    val rTip = radius
    val rRoot = radius * 0.88f
    val rBore = radius * 0.17f
    val step = 360f / teeth

    val path = Path().apply { fillType = PathFillType.EvenOdd }

    // Rim: alternate root arc and tooth. Trapezoidal teeth — narrower at the tip,
    // which is what separates a gear from a sunburst.
    for (i in 0 until teeth) {
        val a = rotationDeg + i * step
        val rootHalf = step * 0.26f
        val tipHalf = step * 0.15f

        val r1 = polar(center, rRoot, a - rootHalf)
        val t1 = polar(center, rTip, a - tipHalf)
        val t2 = polar(center, rTip, a + tipHalf)
        val r2 = polar(center, rRoot, a + rootHalf)

        if (i == 0) path.moveTo(r1.x, r1.y) else path.lineTo(r1.x, r1.y)
        path.lineTo(t1.x, t1.y)
        path.lineTo(t2.x, t2.y)
        path.lineTo(r2.x, r2.y)
    }
    path.close()

    // Bore and lightening holes — subpaths, so even-odd punches them through.
    path.addOval(
        androidx.compose.ui.geometry.Rect(
            center.x - rBore, center.y - rBore, center.x + rBore, center.y + rBore
        )
    )
    if (holes > 0) {
        val hr = radius * 0.13f
        val hd = radius * 0.52f
        for (i in 0 until holes) {
            val a = rotationDeg + i * (360f / holes) + 12f
            val p = polar(center, hd, a)
            path.addOval(
                androidx.compose.ui.geometry.Rect(
                    p.x - hr, p.y - hr, p.x + hr, p.y + hr
                )
            )
        }
    }

    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                brass.hi.copy(alpha = alpha),
                brass.mid.copy(alpha = alpha),
                brass.lo.copy(alpha = alpha)
            ),
            start = Offset(center.x - radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius)
        )
    )
    drawPath(
        path = path,
        color = brass.lo.copy(alpha = alpha * 0.85f),
        style = Stroke(width = radius * 0.018f)
    )

    // A concentric line where the web meets the rim. Cheap, and it stops the
    // face reading as one undifferentiated disc.
    drawCircle(
        color = brass.lo.copy(alpha = alpha * 0.55f),
        radius = rRoot * 0.80f,
        center = center,
        style = Stroke(width = radius * 0.014f)
    )
}

/**
 * One tick per corpus entry across the visible half, every fifth one longer.
 * Sits well inside the teeth so the two rings do not collide.
 */
private fun DrawScope.drawTickRing(center: Offset, radius: Float, count: Int, color: Color) {
    drawArc(
        color = color.copy(alpha = 0.35f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = radius * 0.010f)
    )
    if (count <= 1) return
    for (i in 0 until count) {
        val t = i / (count - 1f)
        val a = 180f + t * 180f
        val long = i % 5 == 0
        val inner = radius * (if (long) 0.90f else 0.94f)
        val p1 = polar(center, inner, a)
        val p2 = polar(center, radius, a)
        drawLine(
            color = color.copy(alpha = if (long) 0.70f else 0.40f),
            start = p1,
            end = p2,
            strokeWidth = radius * (if (long) 0.018f else 0.011f),
            cap = StrokeCap.Round
        )
    }
}

/**
 * A tapered blade with a counterweight tail — the shape is what makes it read as
 * a hand rather than a line.
 *
 * [fraction] 0..1 maps across the visible half: 0 points left, 1 points right.
 * Coerced rather than wrapped — a hand that stops tells you there is nothing past
 * the end, which is worth more than an endless ring.
 */
private fun DrawScope.drawHand(
    center: Offset,
    length: Float,
    fraction: Float,
    color: Color,
    width: Float
) {
    val a = 180f + fraction.coerceIn(0f, 1f) * 180f
    val tip = polar(center, length, a)
    val baseL = polar(center, width * 1.6f, a - 90f)
    val baseR = polar(center, width * 1.6f, a + 90f)
    val midL = polar(polar(center, length * 0.55f, a), width * 0.55f, a - 90f)
    val midR = polar(polar(center, length * 0.55f, a), width * 0.55f, a + 90f)

    val blade = Path().apply {
        moveTo(baseL.x, baseL.y)
        lineTo(midL.x, midL.y)
        lineTo(tip.x, tip.y)
        lineTo(midR.x, midR.y)
        lineTo(baseR.x, baseR.y)
        close()
    }
    drawPath(blade, color)

    val tail = polar(center, length * 0.16f, a + 180f)
    drawCircle(color, width * 1.5f, tail)
}

private fun polar(from: Offset, radius: Float, degrees: Float): Offset {
    val a = Math.toRadians(degrees.toDouble())
    return Offset(
        from.x + (cos(a) * radius).toFloat(),
        from.y + (sin(a) * radius).toFloat()
    )
}
