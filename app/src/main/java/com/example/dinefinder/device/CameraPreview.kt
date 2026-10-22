package com.example.dinefinder.device

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Rear-camera preview only: no audio, recording, image analysis, or upload. */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onError: (String?) -> Unit,
    onReadyChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val errorCallback by rememberUpdatedState(onError)
    val readyCallback by rememberUpdatedState(onReadyChange)
    val view = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(factory = { view }, modifier = modifier)

    DisposableEffect(context, lifecycleOwner, view) {
        var disposed = false
        readyCallback(false)
        val streamObserver = Observer<PreviewView.StreamState> { stream ->
            if (!disposed) readyCallback(stream == PreviewView.StreamState.STREAMING)
        }
        view.previewStreamState.observe(lifecycleOwner, streamObserver)
        var cameraProvider: ProcessCameraProvider? = null
        var ownedPreview: Preview? = null
        var cameraState: LiveData<CameraState>? = null
        val observer = Observer<CameraState> { state ->
            if (!disposed) {
                when {
                    state.error != null -> errorCallback(
                        "Camera preview is unavailable. Close other camera apps, check permissions, and retry.",
                    )
                    state.type == CameraState.Type.OPEN -> errorCallback(null)
                }
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            errorCallback("Allow camera access in app settings to display the live preview.")
        } else {
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (!disposed) {
                        try {
                            val provider = future.get()
                            cameraProvider = provider
                            if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                errorCallback("No rear camera was found on this device. Use the restaurant list.")
                            } else {
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(view.surfaceProvider)
                                }
                                ownedPreview = preview
                                val camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                )
                                errorCallback(null)
                                val observedState = camera.cameraInfo.cameraState
                                cameraState = observedState
                                observedState.observe(lifecycleOwner, observer)
                            }
                        } catch (_: SecurityException) {
                            errorCallback("Camera permission changed. Allow access in app settings and retry.")
                        } catch (_: Exception) {
                            errorCallback("Unable to open the rear camera. Close other camera apps and retry.")
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (_: Exception) {
                errorCallback("Camera preview is unavailable on this device. Use the restaurant list.")
            }
        }

        onDispose {
            disposed = true
            readyCallback(false)
            view.previewStreamState.removeObserver(streamObserver)
            cameraState?.removeObserver(observer)
            ownedPreview?.let { preview ->
                // Other camera use cases, if any, belong to their own owners.
                runCatching { cameraProvider?.unbind(preview) }
                preview.setSurfaceProvider(null)
            }
        }
    }
}
