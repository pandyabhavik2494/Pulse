package com.pandyabhavik.pulse.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.time.LocalDate

/**
 * The live step ticker's arithmetic, kept apart from Android so it is easy to follow (and to test).
 *
 * Health Connect only changes when the Fitbit or Google's step service syncs, which can be many minutes
 * apart. The phone's own step counter ticks with every step. So the number shown is Health Connect's
 * count for today plus the phone steps taken since that count last changed. Whenever Health Connect's
 * count goes up (it now includes those steps) or the day changes, the extra starts again from zero. The
 * extra is never negative, so the shown number never drops below Health Connect's.
 */
class StepTicker {
    private var date: LocalDate? = null
    private var hc = 0
    /** The phone counter's reading when the extra last started from zero. */
    private var base: Long? = null
    private var latest: Long? = null

    /** True while the phone sensor is delivering readings. */
    @Volatile var active = false
        private set

    /** Health Connect's count for [day]. Called every time the state is rebuilt. */
    @Synchronized
    fun onHealth(day: LocalDate, hcSteps: Int) {
        if (day != date) {
            date = day
            hc = hcSteps
            base = latest
            return
        }
        if (hcSteps > hc) base = latest
        hc = hcSteps
    }

    /** A reading of the phone's step counter (steps since the phone booted). */
    @Synchronized
    fun onSensor(day: LocalDate, totalSinceBoot: Long) {
        if (day != date) {
            // Past midnight before Health Connect has anything for the new day.
            date = day
            hc = 0
            base = totalSinceBoot
        }
        val b = base
        // The first reading starts the extra at zero; a reading below the base means the phone rebooted.
        if (b == null || totalSinceBoot < b) base = totalSinceBoot
        latest = totalSinceBoot
        active = true
    }

    /**
     * The sensor stopped (app in the background). The base stays, so steps walked meanwhile still count
     * if Health Connect hasn't changed by the time the app is back. The last reading is dropped so a
     * later reset doesn't start from a stale one.
     */
    @Synchronized
    fun onStopped() {
        active = false
        latest = null
    }

    /** The number to show for [day], given Health Connect's [hcSteps] for that day. */
    @Synchronized
    fun display(day: LocalDate, hcSteps: Int): Int {
        if (day != date) return hcSteps
        val b = base ?: return hcSteps
        val l = latest ?: return hcSteps
        return hcSteps + (l - b).coerceAtLeast(0L).toInt()
    }

    @Synchronized
    fun reset() {
        date = null
        hc = 0
        base = null
        latest = null
        active = false
    }
}

/**
 * Listens to the phone's step counter while Pulse is on screen (any activity started), and feeds every
 * reading to [PulseRepository.onSensorSteps]. Needs ACTIVITY_RECOGNITION; without it (or on a phone
 * with no step counter) the ticker simply stays off and Health Connect's number is shown.
 */
class LiveSteps(private val context: Context, private val repo: PulseRepository) : DefaultLifecycleObserver, SensorEventListener {
    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private var registered = false
    private var attached = false

    /** Call once, on the main thread (PulseApp.onCreate). */
    fun attach() {
        if (attached) return
        attached = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = start()

    override fun onStop(owner: LifecycleOwner) = stop()

    fun allowed(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Starts listening if allowed. Safe to call again, e.g. right after the permission is granted. */
    fun start() {
        if (registered || !allowed()) return
        val manager = sensors ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        registered = try {
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (e: SecurityException) {
            Log.w(TAG, "Step counter refused", e)
            false
        }
    }

    fun stop() {
        if (registered) sensors?.unregisterListener(this)
        registered = false
        repo.onSensorStopped()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER || event.values.isEmpty()) return
        repo.onSensorSteps(event.values[0].toLong())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val TAG = "LiveSteps"
    }
}
