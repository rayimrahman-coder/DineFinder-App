package com.example.dinefinder.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import kotlin.math.max

data class CameraPreviewFrame(
    /** Empty when camera calibration is unavailable; capture liveness remains usable. */
    val projection: DoubleArray,
    /** Camera capture timestamp, used for uniqueness rather than wall-clock conversion. */
    val captureTimestampNanos: Long,
    /** Monotonic time when the live analysis image arrived. */
    val observedAtNanos: Long
) {
    val hasCalibratedProjection: Boolean
        get() = projection.size == 16 && projection.all { it.isFinite() } &&
            projection[0] > 0 && projection[5] > 0 && projection[11] == -1.0 &&
            projection[10] <= -1.0 && projection[14] < 0
}

/** Ordinary back-camera preview. Callers own camera permission requests. */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
class CameraPreviewController(
    private val activity: ComponentActivity,
    private val container: FrameLayout,
    private val onFrame: (CameraPreviewFrame) -> Unit,
    private val onError: (String) -> Unit
) : DefaultLifecycleObserver {
    private val mainExecutor = ContextCompat.getMainExecutor(activity)
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val delivery = CameraFrameDelivery()
    private val previewView = PreviewView(activity).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
        setBackgroundColor(Color.BLACK)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var closed = false
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var characteristics: Camera2CameraInfo? = null
    private var binding = false
    @Volatile private var currentOptics: Pair<Long, CurrentOptics>? = null
    private val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            val display = previewView.display ?: return
            if (display.displayId != displayId || !delivery.active) return
            preview?.targetRotation = display.rotation
            analysis?.targetRotation = display.rotation
        }
    }

    private data class CurrentOptics(
        val focalLength: Float?,
        val intrinsicCalibration: FloatArray?,
        val cropRegion: Rect?,
        val zoomRatio: Float?
    )

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (delivery.active && camera == null) bindIfReady(delivery.generation)
    }

    init {
        container.addView(previewView, 0, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        previewView.addOnLayoutChangeListener(layoutListener)
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        activity.lifecycle.addObserver(this)
    }

    @MainThread
    fun start() {
        if (closed || delivery.active) return
        if (!cameraPermission()) {
            onError("Allow camera access to scan restaurants.")
            return
        }
        val generation = delivery.start()
        try {
            val future = ProcessCameraProvider.getInstance(activity)
            future.addListener({
                if (!isCurrent(generation)) return@addListener
                try {
                    provider = future.get()
                    bindIfReady(generation)
                } catch (_: Exception) {
                    fail(generation, "Camera unavailable. Try opening the camera again.")
                }
            }, mainExecutor)
        } catch (_: Exception) {
            fail(generation, "Camera unavailable. Try opening the camera again.")
        }
    }

    @MainThread
    fun stop() {
        delivery.stop()
        analysis?.clearAnalyzer()
        try {
            provider?.unbindAll()
        } catch (_: Exception) {}
        preview = null
        analysis = null
        camera = null
        characteristics = null
        currentOptics = null
        binding = false
    }

    @MainThread
    fun close() {
        if (closed) return
        stop()
        closed = true
        activity.lifecycle.removeObserver(this)
        displayManager.unregisterDisplayListener(displayListener)
        previewView.removeOnLayoutChangeListener(layoutListener)
        container.removeView(previewView)
        analysisExecutor.shutdown()
    }

    override fun onResume(owner: LifecycleOwner) {
        if (delivery.active) bindIfReady(delivery.generation)
    }

    override fun onPause(owner: LifecycleOwner) = stop()
    override fun onDestroy(owner: LifecycleOwner) = close()

    private fun cameraPermission() = ContextCompat.checkSelfPermission(
        activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun isCurrent(generation: Long) = !closed && delivery.active &&
        generation == delivery.generation

    private fun resumed() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun bindIfReady(generation: Long) {
        if (!isCurrent(generation) || binding || camera != null || !resumed() ||
            previewView.width <= 0 || previewView.height <= 0) return
        if (!cameraPermission()) {
            fail(generation, "Allow camera access to scan restaurants.")
            return
        }
        val cameraProvider = provider ?: return
        val viewport = previewView.viewPort
        binding = true
        try {
            if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                fail(generation, "A back camera is required to scan restaurants.")
                return
            }
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val previewBuilder = Preview.Builder().setTargetRotation(rotation)
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        currentOptics = generation to CurrentOptics(
                            result.get(CaptureResult.LENS_FOCAL_LENGTH),
                            result.get(CaptureResult.LENS_INTRINSIC_CALIBRATION)?.copyOf(),
                            result.get(CaptureResult.SCALER_CROP_REGION)?.let(::Rect),
                            if (Build.VERSION.SDK_INT >= 30) result.get(CaptureResult.CONTROL_ZOOM_RATIO)
                            else null
                        )
                    }
                })
            val previewUseCase = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysisUseCase = ImageAnalysis.Builder()
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            var lastPostedAtNanos = 0L
            analysisUseCase.setAnalyzer(analysisExecutor) { image ->
                try {
                    val observed = SystemClock.elapsedRealtimeNanos()
                    val timestamp = image.imageInfo.timestamp
                    // Markers need at most ten updates a second. Images are always released.
                    if (observed - lastPostedAtNanos >= 100_000_000L) {
                        lastPostedAtNanos = observed
                        mainExecutor.execute { deliver(generation, timestamp, observed) }
                    }
                } finally {
                    image.close()
                }
            }
            preview = previewUseCase
            analysis = analysisUseCase
            val useCaseGroupBuilder = UseCaseGroup.Builder()
                .addUseCase(previewUseCase)
                .addUseCase(analysisUseCase)
            viewport?.let { useCaseGroupBuilder.setViewPort(it) }

            try {
                cameraProvider.unbindAll()
            } catch (_: Exception) {}

            camera = cameraProvider.bindToLifecycle(
                activity,
                CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroupBuilder.build()
            )
            characteristics = Camera2CameraInfo.from(requireNotNull(camera).cameraInfo)
        } catch (error: Exception) {
            Log.e("CameraPreviewController", "Failed to bind camera use cases", error)
            fail(generation, "Camera unavailable. Try opening the camera again.")
        } finally {
            binding = false
        }
    }

    private fun deliver(generation: Long, timestamp: Long, observed: Long) {
        if (!isCurrent(generation)) return
        if (!cameraPermission()) {
            fail(generation, "Allow camera access to scan restaurants.")
            return
        }
        if (!delivery.canDeliver(generation, timestamp, observed, SystemClock.elapsedRealtimeNanos(),
                cameraPermission = true, resumed = resumed())) return
        val projection = projection() ?: DoubleArray(0)
        onFrame(CameraPreviewFrame(projection, timestamp, observed))
    }

    private fun projection(): DoubleArray? {
        val info = characteristics ?: return null
        val active = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?: return null
        val transform = previewView.sensorToViewTransform ?: return null
        val optics = currentOptics?.takeIf { it.first == delivery.generation }?.second
        val calibration = optics?.intrinsicCalibration ?:
            info.getCameraCharacteristic(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
        val intrinsics = if (calibration != null && calibration.size >= 5 &&
            calibration[0] > 0 && calibration[1] > 0 && kotlin.math.abs(calibration[4]) < 0.001f) {
            CameraSensorIntrinsics(calibration[0].toDouble(), calibration[1].toDouble(),
                calibration[2].toDouble(), calibration[3].toDouble())
        } else {
            val physical = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                ?: return null
            val pixels = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                ?: return null
            val focal = optics?.focalLength ?:
                info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull() ?: return null
            if (physical.width <= 0 || physical.height <= 0 || focal <= 0) return null
            CameraSensorIntrinsics(focal * pixels.width / physical.width.toDouble(),
                focal * pixels.height / physical.height.toDouble(),
                active.exactCenterX().toDouble(), active.exactCenterY().toDouble())
        }
        val region = optics?.cropRegion?.takeIf { it.width() > 0 && it.height() > 0 } ?: active
        // CONTROL_ZOOM_RATIO cameras can keep SCALER_CROP_REGION equal to the active array.
        val cropZoomX = active.width().toDouble() / region.width()
        val cropZoomY = active.height().toDouble() / region.height()
        val zoom = (optics?.zoomRatio ?: camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f)
            .toDouble().takeIf { it.isFinite() && it > 0 } ?: 1.0
        val crop = CameraSensorCrop(max(cropZoomX, zoom), max(cropZoomY, zoom),
            region.exactCenterX().toDouble(), region.exactCenterY().toDouble(),
            active.exactCenterX().toDouble(), active.exactCenterY().toDouble())
        val values = FloatArray(9)
        transform.getValues(values)
        return CameraProjection.fromSensorToView(intrinsics, DoubleArray(9) { values[it].toDouble() },
            previewView.width, previewView.height, crop)
    }

    private fun fail(generation: Long, message: String) {
        if (!isCurrent(generation)) return
        onError(message)
        stop()
    }
}
