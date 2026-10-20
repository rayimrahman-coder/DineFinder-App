package com.example.dinefinder.ar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.example.dinefinder.core.localArrowYaw
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.hypot

/** Optional, on-device SLAM and plane tracking. No Google API key or cloud anchors. */
@Composable
fun ArNavigationPreview(
    directionDelta: Double?,
    modifier: Modifier = Modifier,
    onStatus: (String) -> Unit,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val status by rememberUpdatedState(onStatus)
    val unavailable by rememberUpdatedState(onUnavailable)
    val view = remember(context) { LocalArView(context, { status(it) }, { unavailable() }) }
    SideEffect { view.directionDelta = directionDelta }
    AndroidView(factory = { view }, modifier = modifier)
    DisposableEffect(view, owner) {
        owner.lifecycle.addObserver(view)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Release any previously composed CameraX preview before opening ARCore.
            view.post { view.startSession() }
        }
        onDispose { owner.lifecycle.removeObserver(view); view.release() }
    }
}

private class LocalArView(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) : GLSurfaceView(context), GLSurfaceView.Renderer, DefaultLifecycleObserver {
    @Volatile var directionDelta: Double? = null
    @Volatile private var session: Session? = null
    @Volatile private var closed = false
    @Volatile private var running = false
    private var installationRequested = false
    private var unavailableReported = false
    private var lastStatus = ""
    private var cameraTexture = 0
    private var backgroundProgram = 0
    private var arrowProgram = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var anchor: Anchor? = null
    private val vertices = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val cameraUvs = floats(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private val arrowVertices = arrowMesh()

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onResume(owner: LifecycleOwner) { post { startSession() } }
    override fun onPause(owner: LifecycleOwner) { stopSession() }
    override fun onDestroy(owner: LifecycleOwner) { release() }

    fun startSession() {
        if (closed || running || unavailableReported) return
        val activity = context.activity() ?: return fail()
        try {
            val apk = ArCoreApk.getInstance()
            if (apk.checkAvailability(activity).isUnsupported) return fail()
            if (apk.requestInstall(activity, !installationRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installationRequested = true
                status("Install Google Play Services for AR to use local 3D guidance. No API key is required.")
                stopSession()
                return
            }
            val active = session ?: Session(activity).also { next ->
                val config = Config(next).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    // Cloud anchors and Geospatial localization are never enabled.
                }
                next.configure(config)
                session = next
            }
            active.resume()
            running = true
            onResume()
            status("Move your phone slowly and point at the ground to find a surface.")
        } catch (_: Exception) { fail() }
    }

    private fun stopSession() {
        val wasRunning = running
        running = false
        // Stop frame updates before pausing the AR session and releasing its camera.
        onPause()
        if (wasRunning) runCatching { session?.pause() }
    }

    fun release() {
        if (closed) return
        closed = true
        stopSession()
        runCatching { anchor?.detach() }
        anchor = null
        runCatching { session?.close() }
        session = null
    }

    private fun fail() {
        if (closed || unavailableReported) return
        unavailableReported = true
        stopSession()
        post { if (!closed) onUnavailable() }
    }

    private fun status(message: String) {
        if (message == lastStatus || closed) return
        lastStatus = message
        post { if (!closed) onStatus(message) }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.05f, .1f, .08f, 1f)
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        cameraTexture = texture[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        try {
        backgroundProgram = program(
            "attribute vec2 aPosition; attribute vec2 aUv; varying vec2 vUv; void main(){gl_Position=vec4(aPosition,0.,1.);vUv=aUv;}",
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES uCamera; varying vec2 vUv; void main(){gl_FragColor=texture2D(uCamera,vUv);}",
        )
        arrowProgram = program(
            "uniform mat4 uMvp; attribute vec3 aPosition; void main(){gl_Position=uMvp*vec4(aPosition,1.);}",
            "precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}",
        )
        } catch (_: Exception) { post { fail() } }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val active = session ?: return
        if (!running || closed) return
        try {
            @Suppress("DEPRECATION")
            val rotation = context.activity()?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            active.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
            active.setCameraTextureName(cameraTexture)
            val frame = active.update()
            val camera = frame.camera
            if (frame.timestamp != 0L) {
                frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, vertices, Coordinates2d.TEXTURE_NORMALIZED, cameraUvs)
                drawBackground()
            }
            if (camera.trackingState != TrackingState.TRACKING) {
                status("AR tracking paused. Move slowly in a well-lit area; use written directions if tracking is unavailable.")
                return
            }
            val delta = directionDelta
            if (delta == null || !delta.isFinite()) {
                status("Waiting for a fresh GPS fix and compass heading before placing the arrow.")
                return
            }
            val current = anchor
            val cameraPose = camera.pose
            if (current != null && (current.trackingState == TrackingState.STOPPED ||
                    hypot((current.pose.tx() - cameraPose.tx()).toDouble(), (current.pose.tz() - cameraPose.tz()).toDouble()) > 3.5 ||
                    (current.pose.tx() - cameraPose.tx()) * -cameraPose.zAxis[0] +
                    (current.pose.tz() - cameraPose.tz()) * -cameraPose.zAxis[2] < .25f)) {
                current.detach()
                anchor = null
            }
            if (anchor == null) {
                val hit = frame.hitTest(viewportWidth * .5f, viewportHeight * .72f).firstOrNull {
                    val plane = it.trackable as? Plane
                    plane?.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.isPoseInPolygon(it.hitPose)
                }
                anchor = hit?.createAnchor()
            }
            val floor = anchor
            if (floor == null || floor.trackingState != TrackingState.TRACKING) {
                status("Point toward the ground and move slowly until ARCore detects a surface.")
                return
            }
            val zAxis = cameraPose.zAxis
            val forwardX = -zAxis[0].toDouble()
            val forwardZ = -zAxis[2].toDouble()
            if (hypot(forwardX, forwardZ) < .2) {
                status("Tilt your phone forward to see the ground arrow.")
                return
            }
            val model = FloatArray(16)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, floor.pose.tx(), floor.pose.ty() + .015f, floor.pose.tz())
            Matrix.rotateM(model, 0, localArrowYaw(forwardX, forwardZ, delta), 0f, 1f, 0f)
            Matrix.scaleM(model, 0, .65f, .65f, .65f)
            val view = FloatArray(16)
            val projection = FloatArray(16)
            val viewModel = FloatArray(16)
            val mvp = FloatArray(16)
            camera.getViewMatrix(view, 0)
            camera.getProjectionMatrix(projection, 0, .1f, 100f)
            Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
            drawArrow(mvp)
            status("Local 3D tracking · direction estimated from GPS and compass")
        } catch (_: Exception) {
            post { fail() }
        }
    }

    private fun drawBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(backgroundProgram)
        val position = GLES20.glGetAttribLocation(backgroundProgram, "aPosition")
        val uv = GLES20.glGetAttribLocation(backgroundProgram, "aUv")
        vertices.position(0); cameraUvs.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, cameraUvs)
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(backgroundProgram, "uCamera"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(uv)
    }

    private fun drawArrow(mvp: FloatArray) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glUseProgram(arrowProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(arrowProgram, "uMvp"), 1, false, mvp, 0)
        val position = GLES20.glGetAttribLocation(arrowProgram, "aPosition")
        arrowVertices.position(0)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, arrowVertices)
        GLES20.glEnableVertexAttribArray(position)
        // Dark lower prism and bright top make the arrow visibly three-dimensional.
        GLES20.glUniform4f(GLES20.glGetUniformLocation(arrowProgram, "uColor"), .12f, .35f, .29f, 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 9, arrowVertices.limit() / 3 - 9)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(arrowProgram, "uColor"), .24f, .91f, .60f, 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 9)
        GLES20.glDisableVertexAttribArray(position)
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
private fun floats(vararg values: Float): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
    .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
private fun program(vertex: String, fragment: String): Int {
    fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source); GLES20.glCompileShader(id)
        val result = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, result, 0)
        check(result[0] == GLES20.GL_TRUE) { "AR renderer shader compilation failed" }
        return id
    }
    val v = shader(GLES20.GL_VERTEX_SHADER, vertex)
    val f = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
    val id = GLES20.glCreateProgram()
    GLES20.glAttachShader(id, v); GLES20.glAttachShader(id, f); GLES20.glLinkProgram(id)
    val result = IntArray(1); GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, result, 0)
    GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
    check(result[0] == GLES20.GL_TRUE) { "AR renderer linking failed" }
    return id
}

/** Extruded arrow: top is three triangles, with a raised perimeter. */
private fun arrowMesh(): FloatBuffer {
    val outline = listOf(-.18f to .55f, .18f to .55f, .18f to -.05f,
        .44f to -.05f, 0f to -.65f, -.44f to -.05f, -.18f to -.05f)
    val points = ArrayList<Float>()
    fun vertex(x: Float, y: Float, z: Float) { points.add(x); points.add(y); points.add(z) }
    fun triangle(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>, y: Float) {
        vertex(a.first, y, a.second); vertex(b.first, y, b.second); vertex(c.first, y, c.second)
    }
    triangle(outline[0], outline[1], outline[2], .10f)
    triangle(outline[0], outline[2], outline[6], .10f)
    triangle(outline[3], outline[4], outline[5], .10f)
    for (i in outline.indices) {
        val a = outline[i]; val b = outline[(i + 1) % outline.size]
        vertex(a.first, 0f, a.second); vertex(b.first, 0f, b.second); vertex(b.first, .10f, b.second)
        vertex(a.first, 0f, a.second); vertex(b.first, .10f, b.second); vertex(a.first, .10f, a.second)
    }
    return floats(*points.toFloatArray())
}
