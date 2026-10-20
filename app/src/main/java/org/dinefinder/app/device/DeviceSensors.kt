package org.dinefinder.app.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import org.dinefinder.app.core.GeoPoint
import kotlin.math.atan2
import kotlin.math.sqrt

/** A location's age is measured with elapsed realtime, never the wall clock. */
data class SensorSnapshot(
    val position: GeoPoint? = null,
    val heading: Double? = null,
    val accuracyMeters: Float? = null,
    val lastFixElapsedMs: Long? = null,
    val locationError: String? = null,
    val headingError: String? = null,
)

/**
 * Foreground-only Android sensors. The Activity owns permission prompts and
 * calls stop when a scan closes or the app leaves the foreground.
 *
 * Only this instance's listeners are released. No background service, fused
 * location API, internet lookup, or fabricated reading is used here.
 */
class DeviceSensors(
    context: Context,
    private val onUpdate: (SensorSnapshot) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val contextualDisplay = if (Build.VERSION.SDK_INT >= 30) {
        runCatching { context.display }.getOrNull()
    } else null

    var snapshot: SensorSnapshot = SensorSnapshot()
        private set

    private var active = false
    private var closed = false
    private var generation = 0L
    private var locationListener: LocationListener? = null
    private var rotationListener: SensorEventListener? = null
    private var fixTimeout: Runnable? = null
    private var freshnessWatchdog: Runnable? = null
    private val currentRequests = mutableListOf<CancellationSignal>()
    private val singleListeners = mutableListOf<LocationListener>()
    private var declinationDegrees = 0.0
    private var smoothedHeading: Double? = null
    private var lastHeadingPublishMs = 0L
    private var lastHeadingEventMs = 0L

    fun start() = onMain {
        if (closed || active) return@onMain
        active = true
        val run = ++generation
        dropStalePosition()
        snapshot = snapshot.copy(locationError = null, heading = null, headingError = null)
        smoothedHeading = null
        lastHeadingPublishMs = 0L
        lastHeadingEventMs = SystemClock.elapsedRealtime()
        startCompass(run)
        startLocation(run)
        startFreshnessWatchdog(run)
        emit()
    }

    /** Keeps the last fix with its original age, but never keeps a stale heading. */
    fun stop() = onMain {
        stopOnMain()
        if (!closed) emit()
    }

    override fun close() = onMain {
        if (closed) return@onMain
        stopOnMain()
        closed = true
    }

    /** Requests a fresh reading without reopening the camera or prompting. */
    fun refreshLocation() = onMain {
        if (closed || !active) return@onMain
        if (!hasLocationPermission()) {
            snapshot = snapshot.copy(
                position = null,
                accuracyMeters = null,
                lastFixElapsedMs = null,
                locationError = "Allow location access in app settings, then retry the scan.",
            )
            emit()
            return@onMain
        }
        dropStalePosition()
        snapshot = snapshot.copy(locationError = null)
        val providers = enabledProviders()
        if (providers.isEmpty()) {
            setLocationUnavailable("Location services are off. Enable location in device settings.")
            return@onMain
        }
        requestFreshFix(providers, generation)
        emit()
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else mainHandler.post(action)
    }

    private fun isCurrent(run: Long) = active && !closed && generation == run

    private fun emit() {
        if (!closed) onUpdate(snapshot)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun enabledProviders(): List<String> {
        val manager = locationManager ?: return emptyList()
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { provider ->
            // Android can return approximate provider fixes when the user
            // chooses coarse access. Older providers that require fine access
            // are handled individually when subscription is attempted.
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission") // Every entry point checks both runtime permissions.
    private fun startLocation(run: Long) {
        val manager = locationManager
        if (manager == null) {
            setLocationUnavailable("Location is unavailable on this device.")
            return
        }
        if (!hasLocationPermission()) {
            setLocationUnavailable("Allow foreground location access to find nearby restaurants.")
            return
        }
        val providers = enabledProviders()
        if (providers.isEmpty()) {
            setLocationUnavailable("Location services are off. Enable location in device settings.")
            return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                acceptLocation(location, run)
            }

            override fun onProviderDisabled(provider: String) {
                if (isCurrent(run) && enabledProviders().isEmpty()) {
                    setLocationUnavailable("Location services were turned off. Enable them and retry.")
                }
            }

            override fun onProviderEnabled(provider: String) = Unit

            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        locationListener = listener
        var subscribed = false
        for (provider in providers) {
            try {
                // A stationary user still needs new timestamps so an older fix
                // cannot silently remain the source of navigation indefinitely.
                manager.requestLocationUpdates(provider, 2_000L, 0f, listener, Looper.getMainLooper())
                subscribed = true
            } catch (_: SecurityException) {
                // Permissions can be revoked between the check and native call.
            } catch (_: IllegalArgumentException) {
                // A provider may disappear while its settings change.
            }
        }
        if (!subscribed) {
            setLocationUnavailable("Unable to start location updates. Check permissions and retry.")
            return
        }
        // Cached fixes are accepted only if their native elapsed timestamp is fresh.
        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.sortedBy { it.elapsedRealtimeNanos }.forEach { acceptLocation(it, run) }
        requestFreshFix(providers, run)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun requestFreshFix(providers: List<String>, run: Long) {
        val manager = locationManager ?: return
        cancelCurrentRequests()
        for (provider in providers) {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    currentRequests += signal
                    manager.getCurrentLocation(
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(appContext),
                    ) { location ->
                        if (location != null) acceptLocation(location, run)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            acceptLocation(location, run)
                            runCatching { manager.removeUpdates(this) }
                            singleListeners.remove(this)
                        }

                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                        @Deprecated("Deprecated by Android")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    }
                    singleListeners += listener
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (_: SecurityException) {
                if (isCurrent(run)) {
                    snapshot = snapshot.copy(locationError = "Location access changed. Check app permissions.")
                }
            } catch (_: IllegalArgumentException) {
                // Continuous updates from the other available provider can still succeed.
            }
        }
        val timeout = Runnable {
            if (!isCurrent(run)) return@Runnable
            cancelCurrentRequests()
            val timestamp = snapshot.lastFixElapsedMs
            if (timestamp == null || SystemClock.elapsedRealtime() - timestamp > MAX_FIX_AGE_MS) {
                snapshot = snapshot.copy(
                    locationError = "Still waiting for a fresh GPS fix. Move to an open area and retry. " +
                        "Foreground location updates will continue.",
                )
                emit()
            }
        }
        fixTimeout = timeout
        mainHandler.postDelayed(timeout, 20_000L)
    }

    private fun acceptLocation(fix: Location, run: Long) {
        if (!isCurrent(run)) return
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite() ||
            fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0
        ) return
        val now = SystemClock.elapsedRealtime()
        val elapsedMs = fix.elapsedRealtimeNanos / 1_000_000L
        if (elapsedMs <= 0L || elapsedMs > now + 1_000L || now - elapsedMs > MAX_FIX_AGE_MS) return
        val previousTime = snapshot.lastFixElapsedMs
        if (previousTime != null && elapsedMs < previousTime) return
        if (previousTime == elapsedMs && snapshot.position != null &&
            snapshot.accuracyMeters != null && fix.hasAccuracy() &&
            fix.accuracy >= snapshot.accuracyMeters!!
        ) return
        snapshot = snapshot.copy(
            position = GeoPoint(fix.latitude, fix.longitude),
            accuracyMeters = fix.accuracy.takeIf { fix.hasAccuracy() && it.isFinite() && it >= 0f },
            lastFixElapsedMs = elapsedMs.coerceAtMost(now),
            locationError = null,
        )
        declinationDegrees = GeomagneticField(
            fix.latitude.toFloat(),
            fix.longitude.toFloat(),
            if (fix.hasAltitude() && fix.altitude.isFinite()) fix.altitude.toFloat() else 0f,
            System.currentTimeMillis(),
        ).declination.toDouble()
        emit()
    }

    private fun dropStalePosition() {
        val fixTime = snapshot.lastFixElapsedMs
        if (fixTime == null || SystemClock.elapsedRealtime() - fixTime > MAX_FIX_AGE_MS) {
            snapshot = snapshot.copy(position = null, accuracyMeters = null, lastFixElapsedMs = null)
        }
    }

    private fun setLocationUnavailable(message: String) {
        snapshot = snapshot.copy(
            position = null,
            accuracyMeters = null,
            lastFixElapsedMs = null,
            locationError = message,
        )
        emit()
    }

    private fun startCompass(run: Long) {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (manager == null || sensor == null) {
            snapshot = snapshot.copy(headingError = "Compass unavailable. Use the written walking directions.")
            return
        }
        val matrix = FloatArray(9)
        val remapped = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!isCurrent(run)) return
                val now = SystemClock.elapsedRealtime()
                val eventElapsedMs = event.timestamp / 1_000_000L
                if (eventElapsedMs <= 0L || eventElapsedMs > now + 1_000L ||
                    now - eventElapsedMs > MAX_HEADING_AGE_MS
                ) return
                lastHeadingEventMs = eventElapsedMs.coerceAtMost(now)
                if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    updateHeadingError("Compass needs calibration. Move the phone in a figure-eight, away from metal.")
                    return
                }
                if (now - lastHeadingPublishMs < 80L) return
                lastHeadingPublishMs = now
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                @Suppress("DEPRECATION")
                val rotation = contextualDisplay?.rotation ?: windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                val (xAxis, yAxis) = when (rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                if (!SensorManager.remapCoordinateSystem(matrix, xAxis, yAxis, remapped)) return
                // The rear camera looks along device -Z. Its horizontal optical
                // direction is stable when upright, unlike flat-compass azimuth.
                val east = -remapped[2].toDouble()
                val north = -remapped[5].toDouble()
                if (sqrt(east * east + north * north) < .25) {
                    updateHeadingError("Hold the phone upright to align the camera directions.")
                    return
                }
                val measured = normalize(Math.toDegrees(atan2(east, north)) + declinationDegrees)
                val previous = smoothedHeading
                val heading = if (previous == null) measured else {
                    val delta = (measured - previous + 540.0) % 360.0 - 180.0
                    normalize(previous + delta * .22)
                }
                smoothedHeading = heading
                snapshot = snapshot.copy(
                    heading = heading,
                    headingError = if (event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                        "Compass accuracy is low. Calibrate away from metal objects."
                    } else null,
                )
                emit()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (isCurrent(run) && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    updateHeadingError("Compass needs calibration. Move the phone in a figure-eight, away from metal.")
                }
            }
        }
        rotationListener = listener
        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, mainHandler)) {
            rotationListener = null
            snapshot = snapshot.copy(headingError = "Unable to start the compass. Use written walking directions.")
        }
    }

    private fun updateHeadingError(message: String) {
        smoothedHeading = null
        if (snapshot.heading != null || snapshot.headingError != message) {
            snapshot = snapshot.copy(heading = null, headingError = message)
            emit()
        }
    }

    /** Native streams can fall silent without an error callback. */
    private fun startFreshnessWatchdog(run: Long) {
        val watchdog = object : Runnable {
            override fun run() {
                if (!isCurrent(run)) return
                val now = SystemClock.elapsedRealtime()
                var changed = false
                val fixTime = snapshot.lastFixElapsedMs
                if (snapshot.position != null &&
                    (fixTime == null || now - fixTime > MAX_FIX_AGE_MS)
                ) {
                    snapshot = snapshot.copy(
                        position = null,
                        accuracyMeters = null,
                        lastFixElapsedMs = null,
                        locationError = "Your GPS fix expired. Move to an open area or retry location.",
                    )
                    changed = true
                }
                if (now - lastHeadingEventMs > MAX_HEADING_AGE_MS &&
                    (snapshot.heading != null || snapshot.headingError == null)
                ) {
                    smoothedHeading = null
                    snapshot = snapshot.copy(
                        heading = null,
                        headingError = "Compass readings stopped. Retry the sensors or use written directions.",
                    )
                    changed = true
                }
                if (changed) emit()
                if (isCurrent(run)) mainHandler.postDelayed(this, 2_000L)
            }
        }
        freshnessWatchdog = watchdog
        mainHandler.postDelayed(watchdog, 2_000L)
    }

    private fun cancelCurrentRequests() {
        fixTimeout?.let(mainHandler::removeCallbacks)
        fixTimeout = null
        currentRequests.forEach { it.cancel() }
        currentRequests.clear()
        singleListeners.forEach { listener -> runCatching { locationManager?.removeUpdates(listener) } }
        singleListeners.clear()
    }

    private fun stopOnMain() {
        active = false
        generation++
        freshnessWatchdog?.let(mainHandler::removeCallbacks)
        freshnessWatchdog = null
        cancelCurrentRequests()
        locationListener?.let { listener -> runCatching { locationManager?.removeUpdates(listener) } }
        locationListener = null
        rotationListener?.let { sensorManager?.unregisterListener(it) }
        rotationListener = null
        smoothedHeading = null
        snapshot = snapshot.copy(heading = null)
    }

    private fun normalize(value: Double) = ((value % 360.0) + 360.0) % 360.0

    companion object {
        const val MAX_FIX_AGE_MS = 45_000L
        private const val MAX_HEADING_AGE_MS = 3_000L
    }
}
