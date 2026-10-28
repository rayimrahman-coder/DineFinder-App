package com.example.dinefinder.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Private on-device photos, isolated by account. Call writes on an IO dispatcher. */
internal class ProfilePhotoStore(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.noBackupFilesDir, "profile-photos")
    private val preferences = app.getSharedPreferences("dinefinder_profile_photos", Context.MODE_PRIVATE)
    private fun accountKey(email: String): String = MessageDigest.getInstance("SHA-256")
        .digest(email.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun photoPath(email: String): String? = preferences.getString(accountKey(email), null)
        ?.takeIf { it.matches(Regex("[a-f0-9-]+\\.jpg")) }
        ?.let { File(directory, it) }?.takeIf { it.isFile }?.absolutePath

    fun save(email: String, uri: Uri): String {
        require(email.isNotBlank())
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                require(output.size() + read <= 16 * 1024 * 1024) { "Image is too large" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("Image is unavailable")
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width > 0 && height > 0)
            val scale = minOf(1.0, 768.0 / maxOf(width, height))
            decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        check(directory.isDirectory || directory.mkdirs())
        val photo = File(directory, "${accountKey(email)}-${UUID.randomUUID()}.jpg")
        try {
            photo.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            val old = photoPath(email)
            check(preferences.edit().putString(accountKey(email), photo.name).commit())
            old?.let { File(it).delete() }
            return photo.absolutePath
        } catch (error: Exception) {
            photo.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    fun remove(email: String) {
        val old = photoPath(email)
        check(preferences.edit().remove(accountKey(email)).commit())
        old?.let { File(it).delete() }
    }
}
