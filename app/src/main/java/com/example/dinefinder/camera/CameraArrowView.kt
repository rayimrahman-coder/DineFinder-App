package com.example.dinefinder.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.util.Log
import java.io.IOException
import androidx.core.graphics.withTranslation
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The supplied 3D arrow mesh shows the current walking action over the camera. */
class CameraArrowView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaft = Path()
    private val head = Path()
    private var direction: Float? = null
    private val silhouette = Path()
    private var projectedAngle: Float? = null
    private var projectedFaces = emptyList<ShadedFace>()
    private data class ShadedFace(val path: Path, val color: Int)
    private val mesh: ArrowMesh? by lazy {
        try {
            context.assets.open("models/arrow.glb").use { loadArrowMesh(it.readBytes()) }
        } catch (error: IOException) {
            Log.w("CameraArrowView", "Arrow model unavailable; using the maneuver symbol", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w("CameraArrowView", "Arrow model invalid; using the maneuver symbol", error)
            null
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** The current instruction includes timing, for example “Continue straight for 63 m”. */
    fun setManeuver(directionDegrees: Float, instruction: String) {
        if (!directionDegrees.isFinite() || instruction.isBlank()) {
            clearDirection()
            return
        }
        // Preserve the U-turn side for the fallback; the model uses the same tip direction.
        val normalized = directionDegrees % 360f
        val nextDirection = when {
            normalized > 180f -> normalized - 360f
            normalized < -180f -> normalized + 360f
            else -> normalized
        }
        contentDescription = instruction
        if (direction != nextDirection) {
            direction = nextDirection
            invalidate()
        }
    }

    fun clearDirection() {
        contentDescription = null
        if (direction != null) {
            direction = null
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val angle = direction ?: return
        if (width <= 0 || height <= 0) return
        val diameter = minOf(148f * resources.displayMetrics.density, minOf(width, height) * .88f)
        val unit = diameter / 2f
        canvas.withTranslation(width * .5f, height * .5f) {
            canvas.scale(unit, unit)
            if (!drawModel(canvas, angle)) drawFallback(canvas, angle)
        }
    }

    private fun drawModel(canvas: Canvas, angle: Float): Boolean {
        val arrowMesh = mesh ?: return false
        if (projectedAngle != angle) {
            val projection = projectArrowMesh(arrowMesh, angle)
            silhouette.reset()
            projectedFaces = projection.triangles.map { triangle ->
                val coordinates = projection.positions
                val a = triangle.vertices[0] * 3
                var b = triangle.vertices[1] * 3
                var c = triangle.vertices[2] * 3
                // Give all silhouette subpaths the same winding so front and
                // back surfaces cannot cancel and leave a transparent hole.
                val area = (coordinates[b] - coordinates[a]) * (coordinates[c + 1] - coordinates[a + 1]) -
                    (coordinates[b + 1] - coordinates[a + 1]) * (coordinates[c] - coordinates[a])
                if (area < 0f) { val swap = b; b = c; c = swap }
                val face = Path().apply {
                    moveTo(coordinates[a], coordinates[a + 1])
                    lineTo(coordinates[b], coordinates[b + 1])
                    lineTo(coordinates[c], coordinates[c + 1])
                    close()
                }
                silhouette.addPath(face)
                ShadedFace(face, triangle.color)
            }
            projectedAngle = angle
        }
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = .035f
        paint.color = Color.WHITE
        paint.setShadowLayer(.10f, 0f, .07f, Color.argb(160, 0, 0, 0))
        canvas.drawPath(silhouette, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.FILL
        // Adjacent triangles share their edges. Anti-alias only the silhouette
        // to avoid pale seams between opaque faces of the same mesh.
        paint.isAntiAlias = false
        projectedFaces.forEach { face ->
            paint.color = face.color
            canvas.drawPath(face.path, paint)
        }
        return true
    }

    /** Keep usable turn guidance if a model cannot be read from the APK. */
    private fun drawFallback(canvas: Canvas, angle: Float) {
        buildManeuver(angle)
        paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .31f
        paint.color = Color.WHITE
        paint.setShadowLayer(.12f, 0f, .06f, Color.argb(180, 0, 0, 0))
        canvas.drawPath(shaft, paint)
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = .075f
        canvas.drawPath(head, paint)
        paint.clearShadowLayer()
        paint.color = Color.rgb(19, 150, 84)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .22f
        canvas.drawPath(shaft, paint)
        paint.style = Paint.Style.FILL
        canvas.drawPath(head, paint)
    }

    private fun buildManeuver(angle: Float) {
        val right = if (angle < 0f) -1f else 1f
        val magnitude = abs(angle)
        shaft.reset()
        val tipX: Float
        val tipY: Float
        val heading: Double
        when {
            magnitude < 15f -> {
                shaft.moveTo(0f, .82f)
                shaft.lineTo(0f, -.57f)
                tipX = 0f
                tipY = -.92f
                heading = -Math.PI / 2
            }
            magnitude > 150f -> {
                shaft.moveTo(-right * .5f, .82f)
                shaft.lineTo(-right * .5f, -.35f)
                shaft.cubicTo(-right * .5f, -.98f, right * .5f, -.98f, right * .5f, -.35f)
                shaft.lineTo(right * .5f, .35f)
                tipX = right * .5f
                tipY = .76f
                heading = Math.PI / 2
            }
            else -> {
                // Start ahead and bend toward the maneuver. A 90° turn is a
                // right-angle sign, not a straight arrow rotated sideways.
                val radians = Math.toRadians((magnitude - 90f).toDouble())
                val dx = cos(radians).toFloat() * right
                val dy = sin(radians).toFloat()
                shaft.moveTo(-right * .35f, .82f)
                shaft.lineTo(-right * .35f, .05f)
                shaft.quadTo(-right * .35f, -.35f, -right * .10f, -.35f)
                tipX = -right * .10f + dx * .98f
                tipY = -.35f + dy * .98f
                shaft.lineTo(tipX - dx * .35f, tipY - dy * .35f)
                heading = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
            }
        }
        val dx = cos(heading).toFloat()
        val dy = sin(heading).toFloat()
        val baseX = tipX - dx * .48f
        val baseY = tipY - dy * .48f
        head.reset()
        head.moveTo(tipX, tipY)
        head.lineTo(baseX - dy * .34f, baseY + dx * .34f)
        head.lineTo(baseX + dy * .34f, baseY - dx * .34f)
        head.close()
    }
}
