package com.example.dinefinder.camera

/** Keeps image liveness distinct from higher-rate ARCore pose updates. Main thread only. */
internal class NavigationFrameDelivery {
    private val captures = CameraFrameDelivery()
    val generation: Long get() = captures.generation
    val active: Boolean get() = captures.active
    private var lastCaptureTimestamp = 0L
    private var lastObservation = 0L

    fun start(): Long {
        lastCaptureTimestamp = 0L
        lastObservation = 0L
        return captures.start()
    }

    fun stop() {
        captures.stop()
        lastCaptureTimestamp = 0L
        lastObservation = 0L
    }

    fun canDeliver(
        expectedGeneration: Long,
        frame: NavigationCameraFrame,
        nowNanos: Long,
        cameraPermission: Boolean,
        resumed: Boolean
    ): Boolean {
        if (!active || expectedGeneration != generation || !cameraPermission || !resumed) return false
        val image = frame.frame
        val freshCapture = captures.canDeliver(expectedGeneration, image.captureTimestampNanos,
            image.observedAtNanos, nowNanos, cameraPermission, resumed)
        // The predicted pose may change between camera images. An unchanged image keeps
        // its original observation time, including when tracking becomes PAUSED.
        val poseUpdate = frame.source == NavigationCameraSource.ARCORE &&
            image.captureTimestampNanos > 0 && image.captureTimestampNanos == lastCaptureTimestamp &&
            image.observedAtNanos == lastObservation &&
            nowNanos - image.observedAtNanos in 0..1_000_000_000L
        if (!freshCapture && !poseUpdate) return false
        lastCaptureTimestamp = image.captureTimestampNanos
        lastObservation = image.observedAtNanos
        return true
    }
}
