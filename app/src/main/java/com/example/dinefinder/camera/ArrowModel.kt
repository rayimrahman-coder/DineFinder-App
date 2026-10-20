package com.example.dinefinder.camera

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The bundled arrow's local mesh: its tip points along +X and its thickness is along Z. */
internal data class ArrowMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val indices: IntArray,
    val baseColor: FloatArray
)

/**
 * Reads the single, untextured triangle mesh used by the navigation preview.
 * This deliberately ignores the exporter's scene transforms: the preview centers
 * the local mesh and supplies its own size, orientation and lighting. It is not
 * a general glTF scene loader, and rejects features that would change the mesh.
 */
internal fun loadArrowMesh(bytes: ByteArray): ArrowMesh {
    try {
        return ArrowModelReader(bytes).read()
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid arrow model", error)
    }
}

private class ArrowModelReader(private val bytes: ByteArray) {
    private val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private lateinit var document: JSONObject
    private var binaryStart = -1
    private var binaryLength = 0
    private var declaredBufferLength = 0

    fun read(): ArrowMesh {
        require(bytes.size in 28..4_194_304) { "Invalid arrow model size" }
        require(data.getInt(0) == 0x46546c67 && data.getInt(4) == 2) { "Expected a glTF 2 GLB" }
        require(unsignedInt(8) == bytes.size.toLong()) { "Incorrect GLB length" }
        var cursor = 12
        var json: String? = null
        while (cursor < bytes.size) {
            require(cursor.toLong() + 8 <= bytes.size) { "Truncated GLB chunk" }
            val length = unsignedInt(cursor)
            val type = data.getInt(cursor + 4)
            val start = cursor + 8
            require(length % 4L == 0L && start.toLong() + length <= bytes.size) { "Invalid GLB chunk length" }
            when (type) {
                0x4e4f534a -> {
                    require(cursor == 12 && json == null) { "Expected a single leading JSON chunk" }
                    json = bytes.decodeToString(start, start + length.toInt())
                }
                0x004e4942 -> {
                    require(json != null && binaryStart < 0) { "Expected a single embedded buffer" }
                    binaryStart = start
                    binaryLength = length.toInt()
                }
            }
            cursor = start + length.toInt()
        }
        document = JSONObject(requireNotNull(json) { "Missing GLB JSON" })
        require(binaryStart >= 0 && document.getJSONObject("asset").getString("version") == "2.0")
        require(document.optJSONArray("extensionsRequired").let { it == null || it.length() == 0 })
        require(document.optJSONArray("skins").let { it == null || it.length() == 0 }) { "Skinned meshes are unsupported" }
        document.optJSONArray("nodes")?.let { nodes ->
            for (index in 0 until nodes.length()) require(!nodes.getJSONObject(index).has("skin"))
        }
        val buffers = document.getJSONArray("buffers")
        require(buffers.length() == 1) { "Expected one embedded buffer" }
        val buffer = buffers.getJSONObject(0)
        require(!buffer.has("uri")) { "External buffers are unsupported" }
        declaredBufferLength = buffer.integer("byteLength")
        require(declaredBufferLength > 0 && binaryLength - declaredBufferLength in 0..3) { "Invalid embedded buffer length" }
        val meshes = document.getJSONArray("meshes")
        require(meshes.length() == 1) { "Expected one arrow mesh" }
        val primitives = meshes.getJSONObject(0).getJSONArray("primitives")
        require(primitives.length() == 1) { "Expected one arrow primitive" }
        val primitive = primitives.getJSONObject(0)
        require(primitive.integer("mode", 4) == 4 && !primitive.has("targets") && !primitive.has("extensions")) {
            "Only uncompressed triangle meshes are supported"
        }
        val attributes = primitive.getJSONObject("attributes")
        require(!attributes.has("JOINTS_0") && !attributes.has("WEIGHTS_0"))
        val positions = floatVectors(attributes.integer("POSITION"))
        val normals = floatVectors(attributes.integer("NORMAL"))
        require(positions.size == normals.size) { "Mismatched vertex normals" }
        for (index in normals.indices step 3) {
            val lengthSquared = normals[index].toDouble() * normals[index] +
                normals[index + 1].toDouble() * normals[index + 1] + normals[index + 2].toDouble() * normals[index + 2]
            require(lengthSquared > 1e-12) { "Invalid vertex normal" }
        }
        val indices = triangleIndices(primitive.integer("indices"), positions.size / 3)
        return ArrowMesh(positions, normals, indices, materialColor(primitive))
    }

    private data class Accessor(val offset: Int, val stride: Int, val count: Int, val componentType: Int)

    private fun accessor(index: Int, type: String, componentSizes: Map<Int, Int>, maxCount: Int): Accessor {
        val accessors = document.getJSONArray("accessors")
        require(index in 0 until accessors.length()) { "Invalid accessor index" }
        val accessor = accessors.getJSONObject(index)
        require(accessor.getString("type") == type && !accessor.has("sparse") && !accessor.optBoolean("normalized", false))
        val componentType = accessor.integer("componentType")
        val componentSize = requireNotNull(componentSizes[componentType]) { "Unsupported component type" }
        val components = if (type == "VEC3") 3 else 1
        val elementSize = componentSize * components
        val count = accessor.integer("count")
        require(count in 1..maxCount) { "Invalid accessor count" }
        val views = document.getJSONArray("bufferViews")
        val viewIndex = accessor.integer("bufferView")
        require(viewIndex in 0 until views.length()) { "Invalid buffer view index" }
        val view = views.getJSONObject(viewIndex)
        require(view.integer("buffer") == 0 && !view.has("extensions"))
        val viewOffset = view.integer("byteOffset", 0)
        val viewLength = view.integer("byteLength")
        require(viewOffset >= 0 && viewLength >= 0 && viewOffset.toLong() + viewLength <= declaredBufferLength) {
            "Buffer view exceeds the embedded buffer"
        }
        val offset = accessor.integer("byteOffset", 0)
        val stride = view.integer("byteStride", elementSize)
        require(offset >= 0 && offset % componentSize == 0 && (viewOffset + offset) % componentSize == 0)
        require(stride >= elementSize && stride <= 252 && stride % componentSize == 0)
        require(offset.toLong() + (count - 1L) * stride + elementSize <= viewLength) { "Accessor exceeds its buffer view" }
        return Accessor(binaryStart + viewOffset + offset, stride, count, componentType)
    }

    private fun floatVectors(index: Int): FloatArray {
        val accessor = accessor(index, "VEC3", mapOf(5126 to 4), 200_000)
        return FloatArray(accessor.count * 3) { component ->
            data.getFloat(accessor.offset + (component / 3) * accessor.stride + component % 3 * 4).also {
                require(it.isFinite()) { "Non-finite mesh coordinates" }
            }
        }
    }

    private fun triangleIndices(index: Int, vertexCount: Int): IntArray {
        val accessor = accessor(index, "SCALAR", mapOf(5121 to 1, 5123 to 2, 5125 to 4), 600_000)
        require(accessor.count % 3 == 0) { "Incomplete triangle" }
        return IntArray(accessor.count) { index ->
            val offset = accessor.offset + index * accessor.stride
            val value = when (accessor.componentType) {
                5121 -> data.get(offset).toLong() and 0xffL
                5123 -> data.getShort(offset).toLong() and 0xffffL
                else -> unsignedInt(offset)
            }
            require(value in 0 until vertexCount.toLong()) { "Triangle index exceeds the vertex buffer" }
            value.toInt()
        }
    }

    private fun materialColor(primitive: JSONObject): FloatArray {
        val color = floatArrayOf(1f, 1f, 1f, 1f)
        if (!primitive.has("material")) return color
        val materials = document.getJSONArray("materials")
        val index = primitive.integer("material")
        require(index in 0 until materials.length())
        val material = materials.getJSONObject(index)
        require(material.optString("alphaMode", "OPAQUE") == "OPAQUE" && !material.has("extensions"))
        val pbr = material.optJSONObject("pbrMetallicRoughness") ?: return color
        require(!pbr.has("baseColorTexture")) { "Textured arrows are unsupported" }
        val factor = pbr.optJSONArray("baseColorFactor") ?: return color
        require(factor.length() == 4)
        for (component in color.indices) {
            color[component] = factor.getDouble(component).toFloat()
            require(color[component].isFinite() && color[component] in 0f..1f)
        }
        require(color[3] == 1f) { "Expected an opaque arrow" }
        return color
    }

    private fun unsignedInt(offset: Int): Long = data.getInt(offset).toLong() and 0xffff_ffffL
}

private fun JSONObject.integer(name: String, default: Int? = null): Int {
    if (!has(name) && default != null) return default
    val value = get(name) as? Number ?: throw IllegalArgumentException("Expected integer $name")
    val number = value.toDouble()
    require(number.isFinite() && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE && number == number.toInt().toDouble())
    return number.toInt()
}
