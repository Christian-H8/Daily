package com.christianh.daily.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.christianh.daily.domain.FlickDetector

/**
 * The gyroscope, wired to [FlickDetector]. Emits nothing.
 *
 * All of the judgement lives in the detector, which is pure and tested; this is the
 * unavoidable plumbing — get the sensor, hand it samples, stop when the app is not
 * in front of anyone. It is deliberately the thinnest thing that can do that, for
 * the same reason `Dial.kt`'s Canvas work is: view-layer code that cannot be tested
 * off-device should not be code where anything is decided.
 *
 * **Not routed through `DailyEnv`.** That port is a set of synchronous calls the
 * composition makes — read the corpus, write a note. A sensor is a subscription
 * with a lifetime, which is a different shape, and forcing it through would make
 * every test fake implement a stream it never uses. The seam that matters here is
 * [FlickDetector], and it already exists.
 *
 * @param enabled false unregisters the listener outright. Pass `!reading`: the
 *   reading posture — walking, a bus, lying down, shifting to get comfortable — is
 *   where every false positive is going to come from, and nothing on that surface
 *   wants navigating anyway. It is the same signal that retreats the dial, so the
 *   gesture is live exactly when the mechanism it drives is visible.
 * @param lifecycle the host Activity's. The composition survives backgrounding, so
 *   without this the gyroscope would keep sampling at 50 Hz behind the home screen
 *   for as long as the app stayed in memory.
 * @param onFlick called with +1 or -1 on the main thread.
 */
@Composable
fun RotationFlicks(
    enabled: Boolean,
    lifecycle: Lifecycle,
    onFlick: (Int) -> Unit
) {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onFlick)
    val detector = remember { FlickDetector() }

    DisposableEffect(enabled, lifecycle, context) {
        val manager = context.getSystemService(SensorManager::class.java)
        val gyroscope = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // A device without a gyroscope keeps the swipe and loses nothing else,
        // which is why the manifest marks the feature not required.
        if (!enabled || gyroscope == null) return@DisposableEffect onDispose { }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val direction = detector.onSample(event.values[2], event.timestamp)
                if (direction != 0) latest.value(direction)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var registered = false

        fun start() {
            if (!registered) {
                // GAME, not UI or NORMAL: a flick peaks and is over inside about
                // 200 ms, and NORMAL's ~5 Hz would sample straight past it.
                manager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
                registered = true
            }
        }

        fun stop() {
            if (registered) {
                manager.unregisterListener(listener)
                registered = false
            }
            detector.reset()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()

        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
}
