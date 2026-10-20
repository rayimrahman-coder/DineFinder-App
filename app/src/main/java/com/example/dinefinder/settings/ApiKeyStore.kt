package com.example.dinefinder.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val API_KEY_MAX_LENGTH = 512
private const val KEYSTORE_ALIAS = "dinefinder_google_maps_api_key"
private const val KEY_FILE_NAME = "google_maps_api_key.enc"
private const val GCM_IV_LENGTH = 12
private const val GCM_TAG_BITS = 128
private const val GCM_TAG_LENGTH = GCM_TAG_BITS / 8
private val FILE_HEADER = byteArrayOf(0x44, 0x46, 0x41, 0x4b, 1)
private val STORAGE_LOCK = Any()

/** A device-local API key encrypted with an Android Keystore key, excluded from backup. */
class ApiKeyStore internal constructor(
    private val keyFile: File,
    private val keyProvider: () -> SecretKey
) {
    constructor(context: Context) : this(
        File(context.applicationContext.noBackupFilesDir, KEY_FILE_NAME),
        ::androidKeystoreKey
    )

    /** Missing, damaged, or unreadable keys are treated as unconfigured. */
    fun apiKey(): String = synchronized(STORAGE_LOCK) {
        try {
            val bytes = readEncryptedFile() ?: return@synchronized ""
            val minimumLength = FILE_HEADER.size + GCM_IV_LENGTH + GCM_TAG_LENGTH + 1
            if (bytes.size < minimumLength ||
                !bytes.copyOfRange(0, FILE_HEADER.size).contentEquals(FILE_HEADER)
            ) return@synchronized ""

            val ivEnd = FILE_HEADER.size + GCM_IV_LENGTH
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider(),
                GCMParameterSpec(GCM_TAG_BITS, bytes.copyOfRange(FILE_HEADER.size, ivEnd))
            )
            cipher.updateAAD(FILE_HEADER)
            val decryptedKey = cipher.doFinal(bytes.copyOfRange(ivEnd, bytes.size))
                .toString(Charsets.UTF_8)
            decryptedKey.takeIf { apiKeyValidationError(it) == null && it == it.trim() }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    /** Validates and saves the trimmed key; this does not verify it with Google. */
    fun saveApiKey(input: String): String? {
        apiKeyValidationError(input)?.let { return it }
        return synchronized(STORAGE_LOCK) {
            val atomicFile = AtomicFile(keyFile)
            var output: java.io.FileOutputStream? = null
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
                if (cipher.iv.size != GCM_IV_LENGTH) throw IOException("Unsupported encryption IV")
                cipher.updateAAD(FILE_HEADER)
                val encrypted = cipher.doFinal(input.trim().toByteArray(Charsets.UTF_8))
                output = atomicFile.startWrite()
                output.write(FILE_HEADER)
                output.write(cipher.iv)
                output.write(encrypted)
                atomicFile.finishWrite(output)
                null
            } catch (_: Exception) {
                output?.let { runCatching { atomicFile.failWrite(it) } }
                "Couldn't save the API key on this device. Try again."
            }
        }
    }

    /** Removes the saved ciphertext while retaining the device's reusable encryption key. */
    fun clearApiKey(): String? = synchronized(STORAGE_LOCK) {
        try {
            AtomicFile(keyFile).delete()
            if (keyFile.exists() || File(keyFile.path + ".bak").exists() ||
                File(keyFile.path + ".new").exists()
            ) {
                "Couldn't remove the saved API key. Try again."
            } else {
                null
            }
        } catch (_: Exception) {
            "Couldn't remove the saved API key. Try again."
        }
    }

    private fun readEncryptedFile(): ByteArray? {
        if (!keyFile.exists() && !File(keyFile.path + ".bak").exists()) return null
        val maximumLength = FILE_HEADER.size + GCM_IV_LENGTH + API_KEY_MAX_LENGTH + GCM_TAG_LENGTH
        AtomicFile(keyFile).openRead().use { input ->
            // Bound reads so an invalid file cannot allocate an arbitrarily large buffer.
            val bytes = ByteArray(maximumLength + 1)
            var length = 0
            while (length < bytes.size) {
                val count = input.read(bytes, length, bytes.size - length)
                if (count < 0) break
                length += count
            }
            if (length > maximumLength) throw IOException("Encrypted key file is too large")
            return bytes.copyOf(length)
        }
    }
}

/** Validates a pasted key's format only, without enforcing a particular Google key prefix. */
fun apiKeyValidationError(input: String): String? {
    val key = input.trim()
    return when {
        key.isBlank() -> "Enter your Google Maps API key."
        key.length > API_KEY_MAX_LENGTH -> "The API key is too long. Paste the key only."
        key.any(Char::isWhitespace) -> "Paste the API key without spaces or line breaks."
        key.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '_' && it != '-' } ->
            "Paste the API key only, without a URL or other characters."
        else -> null
    }
}

private fun androidKeystoreKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
        return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        generateKey()
    }
}
