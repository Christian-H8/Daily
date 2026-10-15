package com.christianh.daily.domain

import kotlin.math.abs

/**
 * Turns a stream of angular-velocity samples into discrete flicks of the wrist.
 *
 * **Why a gesture nobody else has.** The dial (DESIGN §3, D-019) is a mechanism you
 * are meant to feel turning, and a swipe is a screen gesture borrowed from every
 * other app. Physically twisting the device to advance the mechanism is the one
 * input that matches what the thing on screen claims to be. It also happens to be
 * the gesture available when the app is used as intended — one hand, walking, out
 * and about — where a thumb swipe across a large screen is the awkward option.
 *
 * **Pure on purpose.** Everything Android about this lives in the adapter that
 * feeds it; what is actually hard here is the discrimination — telling a deliberate
 * flick from the constant low-frequency rotation of a phone held by someone walking
 * — and that is decided entirely by the numbers below. Keeping it out of the
 * `SensorEventListener` is what lets a test drive a synthetic walk through it
 * instead of somebody pacing a hallway watching for false positives.
 *
 * **The discrimination.** A deliberate flick is fast: peak angular velocity well
 * past [fireThreshold] rad/s, reached in under a fifth of a second. Walking,
 * shifting in a seat, and lifting the phone to read are an order of magnitude
 * slower — roughly 0.2–0.8 rad/s — so the two separate on speed alone, without
 * needing to recognise a shape.
 *
 * **Raised from 2.6 to 3.5 rad/s after the first real use**, which reported the
 * gesture as over-eager. 2.6 rad/s is about 150 deg/s, which is a turn of the
 * wrist; 3.5 is about 200 deg/s, which has to be meant. The refractory went
 * 450 ms to 600 ms in the same pass, because an unhurried return stroke can
 * outlast 450 ms and then read as a flick back the other way.
 *
 * **Fire on the way out, not on the way back.** A flick is a twist *and a return*,
 * and waiting for the return would be the more certain signal — at the cost of
 * roughly a third of a second of latency on every navigation, which is exactly the
 * kind of lag that makes a gesture feel broken. So the outbound peak fires, and
 * [rearmThreshold] plus [refractoryNanos] absorb the snap-back: the wrist coming
 * home is a large rotation in the opposite direction and would otherwise read as a
 * second flick, immediately undoing the first.
 *
 * All three numbers are guesses until someone walks around with this. They are
 * constructor parameters so tuning is an argument, not an edit.
 *
 * @param fireThreshold rad/s at which a sample counts as deliberate. Raise it if
 *   ordinary handling navigates; lower it if a real flick has to be violent.
 * @param rearmThreshold rad/s the device must settle below before another flick can
 *   fire. Raise it if a single flick sometimes double-fires.
 * @param refractoryNanos the minimum quiet time after a flick, on top of settling.
 *   This is what the return stroke is spent against.
 */
class FlickDetector(
    private val fireThreshold: Float = 3.5f,
    private val rearmThreshold: Float = 0.7f,
    private val refractoryNanos: Long = 600_000_000L
) {

    private var armed = true
    private var firedAt = 0L

    /** Forward, one section. */
    val forward = 1

    /** Back, one section. */
    val back = -1

    /**
     * Drop any half-finished gesture. Called when the sensor stops — after a spell
     * in the user's pocket the next sample is unrelated to the last one, and a
     * detector still holding `armed = false` from twenty minutes ago would swallow
     * the first real flick.
     */
    fun reset() {
        armed = true
        firedAt = 0L
    }

    /**
     * One gyroscope sample.
     *
     * @param rotationZ angular velocity about the axis out of the screen, rad/s —
     *   the twist you make turning a key. Android reports counter-clockwise as
     *   positive when looking at the screen.
     * @param atNanos the sample's timestamp, monotonic. `SensorEvent.timestamp`.
     * @return [forward], [back], or 0 for the overwhelming majority of samples.
     */
    fun onSample(rotationZ: Float, atNanos: Long): Int {
        val speed = abs(rotationZ)

        if (!armed) {
            // Both conditions, not either: settling alone would re-arm mid-return
            // on a slow wrist, and time alone would re-arm mid-twist on a fast one.
            if (speed < rearmThreshold && atNanos - firedAt >= refractoryNanos) armed = true
            return 0
        }

        if (speed < fireThreshold) return 0

        armed = false
        firedAt = atNanos
        // Clockwise as the user sees it — the direction you turn a dial to advance
        // it, and the direction a watch crown winds forward. Android's sign for
        // that rotation is negative. Swap these two if it reads backwards on the
        // device; nothing else depends on the mapping.
        return if (rotationZ < 0f) forward else back
    }
}
