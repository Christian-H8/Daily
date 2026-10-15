package com.christianh.daily

import com.christianh.daily.domain.FlickDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flick gesture's discrimination, driven by synthetic motion.
 *
 * The thing that decides whether this gesture is usable is not the sensor wiring —
 * it is whether a deliberate twist can be told apart from a phone held by someone
 * walking. That question is answerable at a desk if the decision lives somewhere
 * pure, which is the entire reason [FlickDetector] is not inside the
 * `SensorEventListener`. The alternative is pacing a hallway watching for the app
 * to navigate on its own.
 *
 * Samples arrive at 50 Hz (`SENSOR_DELAY_GAME`, 20 ms apart), which is what the
 * adapter asks for.
 */
class FlickDetectorTest {

    private companion object {
        const val STEP = 20_000_000L // 20 ms, in nanoseconds

        /**
         * A deliberate wrist flick, clockwise as the user sees it. Peaks around
         * 5.6 rad/s ≈ 320°/s and is over in 160 ms. Android's sign for clockwise
         * is negative.
         */
        val CLOCKWISE = floatArrayOf(-0.4f, -1.6f, -3.4f, -5.1f, -5.6f, -4.3f, -2.4f, -0.7f)

        val COUNTER_CLOCKWISE = floatArrayOf(0.4f, 1.6f, 3.4f, 5.1f, 5.6f, 4.3f, 2.4f, 0.7f)

        /**
         * The wrist coming home. Slower than the flick but not by much, and — at
         * 3.8 rad/s — still past the fire threshold. This is what the refractory is
         * for, and why settling alone cannot be the re-arm condition.
         */
        val RETURN_STROKE = floatArrayOf(0.5f, 1.8f, 3.2f, 3.8f, 2.7f, 1.2f, 0.3f)

        /**
         * A phone in the hand of someone walking: continuous, wandering, and an
         * order of magnitude slower than a flick. This is the signal the gesture
         * has to survive, because it is the posture the app is used in.
         */
        val WALKING = floatArrayOf(
            0.12f, 0.31f, 0.55f, 0.74f, 0.61f, 0.28f, -0.09f, -0.38f,
            -0.66f, -0.79f, -0.52f, -0.21f, 0.06f, 0.34f, 0.58f, 0.71f,
            0.44f, 0.15f, -0.17f, -0.49f, -0.72f, -0.63f, -0.30f, -0.04f
        )
    }

    private class Feed(private val detector: FlickDetector) {
        private var at = 1_000_000_000L
        val fired = mutableListOf<Int>()

        fun push(vararg speeds: Float): Feed {
            for (speed in speeds) {
                val direction = detector.onSample(speed, at)
                if (direction != 0) fired += direction
                at += STEP
            }
            return this
        }

        /** The device held still. [samples] × 20 ms of it. */
        fun still(samples: Int): Feed = push(*FloatArray(samples) { 0.04f })
    }

    private fun feed() = Feed(FlickDetector())

    // --- the false positives that would sink it ----------------------------------

    @Test fun `walking does not navigate`() {
        val f = feed()
        repeat(12) { f.push(*WALKING) }   // ~5.8 seconds of it
        assertEquals(emptyList<Int>(), f.fired)
    }

    @Test fun `lifting the phone to read does not navigate`() {
        // A deliberate but unhurried movement: a full quarter turn over 400 ms is
        // about 3.9 rad/s of *displacement*, spread out it never peaks.
        val lift = floatArrayOf(0.2f, 0.5f, 0.9f, 1.3f, 1.6f, 1.7f, 1.5f, 1.1f, 0.6f, 0.2f)
        val f = feed().push(*lift)
        assertEquals(emptyList<Int>(), f.fired)
    }

    // --- firing ------------------------------------------------------------------

    @Test fun `a deliberate flick fires exactly once, not once per fast sample`() {
        val f = feed().push(*CLOCKWISE)
        assertEquals(listOf(1), f.fired)
    }

    @Test fun `clockwise advances and counter-clockwise goes back`() {
        assertEquals(listOf(1), feed().push(*CLOCKWISE).fired)
        assertEquals(listOf(-1), feed().push(*COUNTER_CLOCKWISE).fired)
    }

    @Test fun `it fires on the way out, not on the way back`() {
        // Latency matters more than certainty here: waiting for the return stroke
        // would add a third of a second to every navigation.
        val f = feed().push(-0.4f, -1.6f, -3.4f, -5.1f)
        assertEquals(listOf(1), f.fired)
    }

    // --- the snap-back, which is the whole reason for the refractory --------------

    @Test fun `the return stroke does not immediately undo the flick`() {
        val f = feed().push(*CLOCKWISE).push(*RETURN_STROKE)
        assertEquals(listOf(1), f.fired)
    }

    @Test fun `a sustained fast rotation is one flick, not a stream of them`() {
        // Someone turning the phone over and holding it there. Every sample is well
        // past the threshold; only the first counts.
        val f = feed().push(*CLOCKWISE).push(*FloatArray(40) { -5.0f })
        assertEquals(listOf(1), f.fired)
    }

    @Test fun `settling is not enough on its own — the refractory has to pass too`() {
        // Still for 100 ms, then a second flick. Too soon: this is what a shaky
        // hand's rebound looks like, and it is not a second navigation.
        val f = feed().push(*CLOCKWISE).still(5).push(*CLOCKWISE)
        assertEquals(listOf(1), f.fired)
    }

    @Test fun `the refractory passing is not enough on its own — it has to settle too`() {
        // A full second of continuous fast rotation. Time has passed; the wrist
        // never stopped, so nothing has been aimed at.
        val f = feed().push(*CLOCKWISE).push(*FloatArray(50) { -4.0f })
        assertEquals(listOf(1), f.fired)
    }

    // --- flicking twice ----------------------------------------------------------

    @Test fun `two sections in a row works once the wrist has come to rest`() {
        val f = feed()
            .push(*CLOCKWISE).push(*RETURN_STROKE).still(30)
            .push(*CLOCKWISE)
        assertEquals(listOf(1, 1), f.fired)
    }

    @Test fun `flicking back after flicking forward works`() {
        val f = feed()
            .push(*CLOCKWISE).still(30)
            .push(*COUNTER_CLOCKWISE)
        assertEquals(listOf(1, -1), f.fired)
    }

    // --- state hygiene -----------------------------------------------------------

    @Test fun `reset drops a half-finished gesture`() {
        val detector = FlickDetector()
        // Fire, leaving it disarmed mid-gesture.
        assertEquals(1, detector.onSample(-5.1f, 1_000_000_000L))
        assertEquals(0, detector.onSample(-5.1f, 1_020_000_000L))

        detector.reset()

        // A sample arriving after the sensor was off is unrelated to the last one,
        // and must not be swallowed by a lockout from twenty minutes ago.
        assertEquals(1, detector.onSample(-5.1f, 1_040_000_000L))
    }

    // --- tuning ------------------------------------------------------------------

    @Test fun `the thresholds are the tuning surface, and they do something`() {
        val gentle = floatArrayOf(0.2f, 0.7f, 1.2f, 1.6f, 1.4f, 0.8f, 0.2f)

        val strict = Feed(FlickDetector()).push(*gentle)
        assertEquals(emptyList<Int>(), strict.fired)

        val relaxed = Feed(FlickDetector(fireThreshold = 1.5f)).push(*gentle)
        assertEquals(listOf(-1), relaxed.fired)
    }

    @Test fun `walking survives a threshold lowered to the edge of its own peak`() {
        // WALKING peaks at 0.79. Even at a threshold barely above that — far lower
        // than anyone should set it — ordinary handling stays quiet, which is the
        // margin the default 2.6 is buying.
        val f = Feed(FlickDetector(fireThreshold = 0.85f))
        repeat(12) { f.push(*WALKING) }
        assertTrue("walking fired at a 0.85 threshold: ${f.fired}", f.fired.isEmpty())
    }
}
