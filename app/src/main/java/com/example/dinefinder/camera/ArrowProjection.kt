package com.example.dinefinder.camera

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class ArrowTriangle(val vertices: IntArray, var depth: Float, var color: Int)

/** Projected XYZ vertices: XY are screen coordinates, Z orders the opaque faces. */
internal data class ProjectedArrow(val positions: FloatArray, val triangles: List<ArrowTriangle>)

/**
 * The supplied model points along local +X. Centre its mesh for a UI preview,
 * make that tip point up, then rotate clockwise for the current walking action.
 * The pitch reveals the bevels and thickness while keeping the tip readable.
 */
internal fun projectArrowMesh(
    mesh: ArrowMesh,
    directionDegrees: Float,
    tiltDegrees: Float = 30f
): ProjectedArrow = ArrowMeshProjector(mesh).project(directionDegrees, tiltDegrees)

/** Reuses vertex, normal and face buffers for the continuously tracked camera pointer. */
internal class ArrowMeshProjector(private val mesh: ArrowMesh) {
    private val count = mesh.positions.size / 3
    private val centered = FloatArray(mesh.positions.size)
    private val projected = FloatArray(mesh.positions.size)
    private val normals = FloatArray(mesh.normals.size)
    private val triangles = ArrayList<ArrowTriangle>(mesh.indices.size / 3)
    private val result = ProjectedArrow(projected, triangles)
    private val depthOrder = compareBy<ArrowTriangle> { it.depth }

    init {
        require(count > 0 && mesh.positions.size % 3 == 0 && mesh.normals.size == mesh.positions.size)
        require(mesh.indices.isNotEmpty() && mesh.indices.size % 3 == 0)
        val minBounds = FloatArray(3) { Float.POSITIVE_INFINITY }
        val maxBounds = FloatArray(3) { Float.NEGATIVE_INFINITY }
        for (vertex in 0 until count) for (axis in 0..2) {
            val value = mesh.positions[vertex * 3 + axis]
            require(value.isFinite() && mesh.normals[vertex * 3 + axis].isFinite())
            minBounds[axis] = minOf(minBounds[axis], value)
            maxBounds[axis] = maxOf(maxBounds[axis], value)
        }
        val center = FloatArray(3) { (minBounds[it] + maxBounds[it]) / 2f }
        var radius = 0f
        for (vertex in 0 until count) {
            var distanceSquared = 0f
            for (axis in 0..2) distanceSquared += (mesh.positions[vertex * 3 + axis] - center[axis]).let { it * it }
            radius = maxOf(radius, sqrt(distanceSquared))
        }
        require(radius.isFinite() && radius > 0f)
        val scale = .88f / radius
        for (vertex in 0 until count) for (axis in 0..2) {
            centered[vertex * 3 + axis] = (mesh.positions[vertex * 3 + axis] - center[axis]) * scale
        }
        for (index in mesh.indices.indices step 3) {
            val vertices = intArrayOf(mesh.indices[index], mesh.indices[index + 1], mesh.indices[index + 2])
            require(vertices.all { it in 0 until count })
            triangles.add(ArrowTriangle(vertices, 0f, 0))
        }
    }

    /** The returned buffers belong to this projector and are updated by the next call. */
    fun project(directionDegrees: Float, tiltDegrees: Float = 30f): ProjectedArrow {
        require(directionDegrees.isFinite() && tiltDegrees.isFinite())
        val angle = Math.toRadians(directionDegrees.toDouble())
        val turnCos = cos(angle).toFloat()
        val turnSin = sin(angle).toFloat()
        // Prevent a phone held almost flat from making the arrow edge-on.
        val tilt = Math.toRadians(tiltDegrees.coerceIn(-65f, 65f).toDouble())
        val tiltCos = cos(tilt).toFloat()
        val tiltSin = sin(tilt).toFloat()
        for (vertex in 0 until count) {
            val offset = vertex * 3
            // Raw mesh XY becomes screen XY; mesh +X is the forward/up direction.
            val x = centered[offset + 1]
            val y = -centered[offset]
            val z = centered[offset + 2]
            val rotatedX = x * turnCos - y * turnSin
            val rotatedY = x * turnSin + y * turnCos
            val tiltedY = rotatedY * tiltCos - z * tiltSin
            val tiltedZ = rotatedY * tiltSin + z * tiltCos
            // Radius-normalisation keeps every point well in front of this camera.
            val perspective = 4f / (4f - tiltedZ)
            projected[offset] = rotatedX * perspective
            projected[offset + 1] = tiltedY * perspective
            projected[offset + 2] = tiltedZ

            val nx = mesh.normals[offset + 1]
            val ny = -mesh.normals[offset]
            val nz = mesh.normals[offset + 2]
            val rotatedNormalX = nx * turnCos - ny * turnSin
            val rotatedNormalY = nx * turnSin + ny * turnCos
            normals[offset] = rotatedNormalX
            normals[offset + 1] = rotatedNormalY * tiltCos - nz * tiltSin
            normals[offset + 2] = rotatedNormalY * tiltSin + nz * tiltCos
        }
        for (triangle in triangles) {
            val indices = triangle.vertices
            var nx = indices.sumOf { normals[it * 3].toDouble() }.toFloat()
            var ny = indices.sumOf { normals[it * 3 + 1].toDouble() }.toFloat()
            var nz = indices.sumOf { normals[it * 3 + 2].toDouble() }.toFloat()
            val length = sqrt(nx * nx + ny * ny + nz * nz)
            require(length.isFinite() && length > 0f)
            nx /= length
            ny /= length
            nz /= length
            // glTF marks this material double-sided. Light the side facing us.
            if (nz < 0f) { nx = -nx; ny = -ny; nz = -nz }
            val diffuse = max(0f, nx * -.36f + ny * -.48f + nz * .8f)
            val highlight = max(0f, nx * -.19f + ny * -.25f + nz * .949f).pow(18) * .12f
            val brightness = .28f + .72f * diffuse
            fun channel(component: Float): Int {
                val linear = (component * brightness + highlight).coerceIn(0f, 1f)
                val srgb = if (linear <= .0031308f) linear * 12.92f else 1.055f * linear.pow(1f / 2.4f) - .055f
                return (srgb * 255f + .5f).toInt().coerceIn(0, 255)
            }
            triangle.color = (0xff shl 24) or (channel(mesh.baseColor[0]) shl 16) or
                (channel(mesh.baseColor[1]) shl 8) or channel(mesh.baseColor[2])
            triangle.depth = indices.sumOf { projected[it * 3 + 2].toDouble() }.toFloat() / 3f
        }
        triangles.sortWith(depthOrder)
        require(projected.all { it.isFinite() && abs(it) <= 1f })
        return result
    }
}
