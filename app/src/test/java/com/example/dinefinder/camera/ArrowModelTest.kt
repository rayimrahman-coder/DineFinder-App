package com.example.dinefinder.camera

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArrowModelTest {
    @Test
    fun bundledModelRetainsUploadedGeometryAndBlueMaterial() {
        val mesh = loadArrowMesh(bundledBytes())
        assertEquals(354 * 3, mesh.positions.size)
        assertEquals(mesh.positions.size, mesh.normals.size)
        assertEquals(672 * 3, mesh.indices.size)
        assertTrue(mesh.indices.all { it in 0 until 354 })
        assertEquals(-1.2560596f, mesh.positions.filterIndexed { i, _ -> i % 3 == 0 }.min(), 0.00001f)
        assertEquals(1.7524283f, mesh.positions.filterIndexed { i, _ -> i % 3 == 0 }.max(), 0.00001f)
        assertArrayEquals(floatArrayOf(.06913197f, .32952493f, .9309814f, 1f), mesh.baseColor, .000001f)
    }

    @Test
    fun readsInterleavedVertexAttributesWithBothViewAndAccessorOffsets() {
        val fixture = interleavedFixture()
        val mesh = loadArrowMesh(pack(fixture))
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), mesh.positions, 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), mesh.normals, 0f)
        assertArrayEquals(intArrayOf(0, 1, 2), mesh.indices)
    }

    @Test
    fun acceptsUnsignedByteShortAndIntTriangleIndices() {
        for ((type, size) in listOf(5121 to 1, 5123 to 2, 5125 to 4)) {
            val fixture = interleavedFixture()
            val original = fixture.binary
            val indices = ByteBuffer.allocate(size * 3).order(ByteOrder.LITTLE_ENDIAN)
            for (value in 0..2) when (size) {
                1 -> indices.put(value.toByte())
                2 -> indices.putShort(value.toShort())
                else -> indices.putInt(value)
            }
            fixture.binary = original.copyOfRange(0, 104) + indices.array()
            fixture.json.getJSONArray("buffers").getJSONObject(0).put("byteLength", fixture.binary.size)
            fixture.json.getJSONArray("bufferViews").getJSONObject(1).put("byteLength", size * 3)
            fixture.json.getJSONArray("accessors").getJSONObject(2).put("componentType", type)
            assertArrayEquals(intArrayOf(0, 1, 2), loadArrowMesh(pack(fixture)).indices)
        }
    }

    @Test
    fun rejectsTruncatedAndIncorrectGlbHeaders() {
        val bytes = bundledBytes()
        rejects(bytes.copyOf(10))
        rejects(bytes.copyOf(bytes.size - 4))
        rejects(bytes.copyOf().also { it[0] = 0 })
        rejects(bytes.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 1) })
        rejects(bytes.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(12, Int.MAX_VALUE) })
    }

    @Test
    fun rejectsAccessorAndViewRangesOutsideEmbeddedBuffer() {
        val accessor = unpack(bundledBytes())
        accessor.json.getJSONArray("accessors").getJSONObject(0).put("count", 200_000)
        rejects(pack(accessor))
        val view = unpack(bundledBytes())
        view.json.getJSONArray("bufferViews").getJSONObject(2).put("byteLength", 100_000)
        rejects(pack(view))
        val negative = unpack(bundledBytes())
        negative.json.getJSONArray("accessors").getJSONObject(0).put("byteOffset", -4)
        rejects(pack(negative))
    }

    @Test
    fun rejectsNegativeAndOversizedUnsignedIndices() {
        for (invalid in listOf(354, -1)) {
            val fixture = unpack(bundledBytes())
            ByteBuffer.wrap(fixture.binary).order(ByteOrder.LITTLE_ENDIAN).putInt(0, invalid)
            rejects(pack(fixture))
        }
    }

    @Test
    fun rejectsNonFiniteVerticesAndMissingOrZeroNormals() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY)) {
            val fixture = unpack(bundledBytes())
            ByteBuffer.wrap(fixture.binary).order(ByteOrder.LITTLE_ENDIAN).putFloat(10896, invalid)
            rejects(pack(fixture))
        }
        val missing = unpack(bundledBytes())
        missing.json.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0)
            .getJSONObject("attributes").remove("NORMAL")
        rejects(pack(missing))
        val zero = unpack(bundledBytes())
        val buffer = ByteBuffer.wrap(zero.binary).order(ByteOrder.LITTLE_ENDIAN)
        for (offset in 15144..15152 step 4) buffer.putFloat(offset, 0f)
        rejects(pack(zero))
    }

    @Test
    fun rejectsExternalBuffersCompressionAndSkinning() {
        val external = unpack(bundledBytes())
        external.json.getJSONArray("buffers").getJSONObject(0).put("uri", "arrow.bin")
        rejects(pack(external))
        val compressed = unpack(bundledBytes())
        compressed.json.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0)
            .put("extensions", JSONObject().put("KHR_draco_mesh_compression", JSONObject()))
        rejects(pack(compressed))
        val skinned = unpack(bundledBytes())
        skinned.json.put("skins", JSONArray().put(JSONObject()))
        rejects(pack(skinned))
    }

    @Test
    fun rejectsTranslucentOrInvalidMaterialColors() {
        for (invalid in listOf(-.1, 1.1)) {
            val fixture = unpack(bundledBytes())
            fixture.json.getJSONArray("materials").getJSONObject(0).getJSONObject("pbrMetallicRoughness")
                .getJSONArray("baseColorFactor").put(0, invalid)
            rejects(pack(fixture))
        }
        val translucent = unpack(bundledBytes())
        translucent.json.getJSONArray("materials").getJSONObject(0).getJSONObject("pbrMetallicRoughness")
            .getJSONArray("baseColorFactor").put(3, .5)
        rejects(pack(translucent))
    }

    private fun bundledBytes() = RuntimeEnvironment.getApplication().assets.open("models/arrow.glb").use { it.readBytes() }

    private data class Fixture(val json: JSONObject, var binary: ByteArray)

    private fun unpack(bytes: ByteArray): Fixture {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val jsonLength = buffer.getInt(12)
        return Fixture(JSONObject(bytes.decodeToString(20, 20 + jsonLength)), bytes.copyOfRange(28 + jsonLength, bytes.size))
    }

    private fun pack(fixture: Fixture): ByteArray {
        val json = fixture.json.toString().encodeToByteArray()
        val jsonLength = (json.size + 3) / 4 * 4
        val binaryLength = (fixture.binary.size + 3) / 4 * 4
        val buffer = ByteBuffer.allocate(28 + jsonLength + binaryLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46546c67).putInt(2).putInt(buffer.capacity())
        buffer.putInt(jsonLength).putInt(0x4e4f534a).put(json)
        while (buffer.position() < 20 + jsonLength) buffer.put(32.toByte())
        buffer.putInt(binaryLength).putInt(0x004e4942).put(fixture.binary)
        return buffer.array()
    }

    private fun interleavedFixture(): Fixture {
        val binary = ByteBuffer.allocate(107).order(ByteOrder.LITTLE_ENDIAN)
        for ((index, point) in listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)).withIndex()) {
            val offset = 8 + index * 32
            for (component in 0..2) binary.putFloat(offset + 4 + component * 4, point[component])
            binary.putFloat(offset + 16, 0f).putFloat(offset + 20, 0f).putFloat(offset + 24, 1f)
        }
        binary.put(104, 0).put(105, 1).put(106, 2)
        val json = JSONObject("""
            {"asset":{"version":"2.0"},"buffers":[{"byteLength":107}],
            "bufferViews":[{"buffer":0,"byteOffset":8,"byteLength":96,"byteStride":32},
                {"buffer":0,"byteOffset":104,"byteLength":3}],
            "accessors":[{"bufferView":0,"byteOffset":4,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":0,"byteOffset":16,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5121,"count":3,"type":"SCALAR"}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2}]}]}
        """.trimIndent())
        return Fixture(json, binary.array())
    }

    private fun rejects(bytes: ByteArray) {
        try {
            loadArrowMesh(bytes)
            fail("Malformed model must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: malformed data cannot reach the navigation renderer.
        }
    }
}
