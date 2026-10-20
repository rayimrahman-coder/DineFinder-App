package com.example.dinefinder.camera

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.sqrt

internal data class NavigationCameraPose(
    val cameraToTrueEnu: DoubleArray,
    val projection: DoubleArray,
    val motion: NavigationVector,
    val source: NavigationCameraSource
)

/** Compass supplies north; ARCore supplies smooth relative rotation and short local movement. */
internal class NavigationOrientationTracker(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val handler = Handler(Looper.getMainLooper())
    private val sensorMatrix = FloatArray(9)
    private var deviceToMagneticEnu: DoubleArray? = null
    private var sensorTimestampNanos = 0L
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var active = false
    private var startedAtNanos = 0L
    private var lastReliableSensorNanos = 0L
    private val alignment = ArPoseAlignment()
    private var latestFrame: NavigationCameraFrame? = null
    private val history = ArrayDeque<PositionSample>()
    private var anchor: PositionSample? = null
    private var anchorFixNanos = 0L
    private var declinationFixNanos = 0L
    private var declinationDegrees = 0.0
    val hasOrientationSensor: Boolean get() = rotationSensor != null
    val hasCalibratedCamera: Boolean get() = latestFrame?.frame?.hasCalibratedProjection == true
    private data class PositionSample(val timestampNanos: Long, val position: DoubleArray)

    fun start() {
        if (active) return
        startedAtNanos = SystemClock.elapsedRealtimeNanos()
        active = rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler)
        } == true
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        active = false
        deviceToMagneticEnu = null
        sensorTimestampNanos = 0
        sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        lastReliableSensorNanos = 0
        latestFrame = null
        resetArReference()
    }

    var onOrientationChanged: (() -> Unit)? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR || event.timestamp < startedAtNanos) return
        SensorManager.getRotationMatrixFromVector(sensorMatrix, event.values)
        deviceToMagneticEnu = DoubleArray(9) { sensorMatrix[it].toDouble() }.takeIf { it.all(Double::isFinite) }
        sensorTimestampNanos = event.timestamp
        sensorAccuracy = event.accuracy
        if (sensorAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM && deviceToMagneticEnu != null) {
            lastReliableSensorNanos = event.timestamp
            onOrientationChanged?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) sensorAccuracy = accuracy
    }

    fun acceptFrame(frame: NavigationCameraFrame) {
        if (!active) return
        val previous = latestFrame
        latestFrame = frame
        if (previous?.source == NavigationCameraSource.ARCORE &&
            frame.frame.observedAtNanos - previous.frame.observedAtNanos > 1_500_000_000L) resetArReference()
        if (frame.source != NavigationCameraSource.ARCORE || !frame.tracking ||
            frame.cameraToWorld?.let { it.size == 9 && it.all(Double::isFinite) } != true ||
            frame.position?.validPosition() != true) {
            resetArReference()
            return
        }
        if (previous?.source != frame.source || !previous.tracking) resetArReference()
        previous?.cameraToWorld?.takeIf { it.size == 9 && it.all(Double::isFinite) && previous.tracking &&
            frame.frame.observedAtNanos - previous.frame.observedAtNanos in 0..100_000_000L
        }?.let { before ->
            val after = requireNotNull(frame.cameraToWorld)
            val forwardDot = listOf(2, 5, 8).sumOf { before[it] * after[it] }.coerceIn(-1.0, 1.0)
            if (forwardDot < kotlin.math.cos(Math.toRadians(45.0))) resetArReference()
        }
        val position = requireNotNull(frame.position)
        val last = history.lastOrNull()
        // A relocalization jump invalidates both north calibration and the GPS anchor.
        if (last != null && frame.frame.observedAtNanos - last.timestampNanos in 0..500_000_000L &&
            positionDistance(position, last.position) > 2.0) resetArReference()
        if (history.lastOrNull()?.timestampNanos != frame.frame.observedAtNanos) {
            history.addLast(PositionSample(frame.frame.observedAtNanos, position.copyOf()))
        }
        while (history.size > 180 || history.firstOrNull()?.let {
                frame.frame.observedAtNanos - it.timestampNanos > 3_000_000_000L
            } == true) history.removeFirst()
    }

    fun cameraToTrueEnu(fix: Location, displayRotationDegrees: Int, nowNanos: Long): DoubleArray? {
        if (!active) return null
        val sensorMatrix = deviceToMagneticEnu?.takeIf {
            sensorAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
                nowNanos - sensorTimestampNanos in 0..2_000_000_000L
        } ?: return null
        if (declinationFixNanos != fix.elapsedRealtimeNanos) {
            declinationFixNanos = fix.elapsedRealtimeNanos
            declinationDegrees = GeomagneticField(
                fix.latitude.toFloat(), fix.longitude.toFloat(),
                if (fix.hasAltitude() && fix.altitude.isFinite()) fix.altitude.toFloat() else 0f,
                fix.time.takeIf { it > 0 } ?: System.currentTimeMillis()
            ).declination.toDouble()
        }
        return NavigationPointerGeometry.sensorCameraToTrueEnu(sensorMatrix, displayRotationDegrees, declinationDegrees)
    }

    fun pose(fix: Location, displayRotationDegrees: Int, nowNanos: Long): NavigationCameraPose? {
        val frame = latestFrame ?: return null
        if (!active) return null
        if (nowNanos - frame.frame.observedAtNanos !in 0..1_500_000_000L || !frame.frame.hasCalibratedProjection) {
            if (frame.source == NavigationCameraSource.ARCORE) resetArReference()
            return null
        }
        if (declinationFixNanos != fix.elapsedRealtimeNanos) {
            declinationFixNanos = fix.elapsedRealtimeNanos
            declinationDegrees = GeomagneticField(fix.latitude.toFloat(), fix.longitude.toFloat(),
                if (fix.hasAltitude() && fix.altitude.isFinite()) fix.altitude.toFloat() else 0f,
                fix.time.takeIf { it > 0 } ?: System.currentTimeMillis()).declination.toDouble()
        }
        val reliableSensor = deviceToMagneticEnu?.takeIf {
            sensorAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
                nowNanos - sensorTimestampNanos in 0..2_000_000_000L
        }?.let { NavigationPointerGeometry.sensorCameraToTrueEnu(it, displayRotationDegrees, declinationDegrees) }
        if (frame.source == NavigationCameraSource.SENSORS) {
            return reliableSensor?.let {
                NavigationCameraPose(it, frame.frame.projection, NavigationVector(0.0, 0.0, 0.0), frame.source)
            }
        }
        if (!frame.tracking || frame.cameraToWorld == null ||
            nowNanos - lastReliableSensorNanos !in 0..60_000_000_000L) return null
        val synchronizedSensor = reliableSensor?.takeIf {
            kotlin.math.abs(frame.frame.observedAtNanos - sensorTimestampNanos) <= 300_000_000L
        }
        val rotation = alignment.align(frame.cameraToWorld, synchronizedSensor) ?: return null
        if (anchorFixNanos != fix.elapsedRealtimeNanos) {
            anchor = history.minByOrNull { kotlin.math.abs(it.timestampNanos - fix.elapsedRealtimeNanos) }
                ?.takeIf { kotlin.math.abs(it.timestampNanos - fix.elapsedRealtimeNanos) <= 300_000_000L }
            if (anchor != null) anchorFixNanos = fix.elapsedRealtimeNanos
        }
        var motion = NavigationVector(0.0, 0.0, 0.0)
        val currentPosition = frame.position
        anchor?.takeIf { anchorFixNanos == fix.elapsedRealtimeNanos }?.let { origin ->
            if (nowNanos - origin.timestampNanos > 5_000_000_000L || currentPosition == null) return null
            val delta = DoubleArray(3) { currentPosition[it] - origin.position[it] }
            if (sqrt(delta.sumOf { it * it }) > 8.0) return null
            motion = alignment.arWorldDeltaToTrueEnu(delta) ?: return null
        }
        return NavigationCameraPose(rotation, frame.frame.projection, motion, frame.source)
    }

    private fun resetArReference() {
        alignment.reset()
        history.clear()
        anchor = null
        anchorFixNanos = 0
    }

    private fun DoubleArray.validPosition() = size == 3 && all(Double::isFinite)
    private fun positionDistance(a: DoubleArray, b: DoubleArray) = sqrt((0..2).sumOf { (a[it] - b[it]).let { d -> d * d } })
}
