package com.example.dinefinder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApiKeyStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var keyFile: File
    private val encryptionKey = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val sampleApiKey = "Sample_key-for-storage_tests_123456789"
    private lateinit var store: ApiKeyStore

    @Before
    fun setUp() {
        keyFile = File(temporaryFolder.root, "api-key.enc")
        store = reopenedStore()
    }

    @Test
    fun missingFileIsUnconfiguredWithoutAccessingTheKeystore() {
        val missingStore = ApiKeyStore(keyFile) { error("No key available") }

        assertEquals("", missingStore.apiKey())
        assertNull(missingStore.clearApiKey())
        assertFalse(keyFile.exists())
    }

    @Test
    fun savedKeyIsEncryptedAndReopensWithTheSameEncryptionKey() {
        assertNull(store.saveApiKey("  $sampleApiKey\n"))

        assertTrue(keyFile.isFile)
        assertFalse(keyFile.readText(Charsets.ISO_8859_1).contains(sampleApiKey))
        assertEquals(sampleApiKey, reopenedStore().apiKey())
    }

    @Test
    fun repeatedSavesUseFreshCiphertext() {
        assertNull(store.saveApiKey(sampleApiKey))
        val firstCiphertext = keyFile.readBytes()

        assertNull(store.saveApiKey(sampleApiKey))

        assertFalse(firstCiphertext.contentEquals(keyFile.readBytes()))
        assertEquals(sampleApiKey, reopenedStore().apiKey())
    }

    @Test
    fun clearingKeyRemovesTheCiphertextAndPersistsAcrossInstances() {
        assertNull(store.saveApiKey(sampleApiKey))
        assertNull(store.clearApiKey())

        assertFalse(keyFile.exists())
        assertEquals("", reopenedStore().apiKey())
        assertNull(store.clearApiKey())
    }

    @Test
    fun invalidInputDoesNotOverwriteAnExistingKey() {
        assertNull(store.saveApiKey(sampleApiKey))
        val ciphertext = keyFile.readBytes()
        val invalidInputs = listOf(
            "", " \t\n ", "key with spaces", "key\nwith-line-breaks",
            "https://example.com/key", "key?query=value", "key\u0000", "ékey", "x".repeat(513)
        )

        for (input in invalidInputs) {
            assertNotNull(apiKeyValidationError(input))
            assertNotNull(store.saveApiKey(input))
            assertTrue(ciphertext.contentEquals(keyFile.readBytes()))
            assertEquals(sampleApiKey, reopenedStore().apiKey())
        }
    }

    @Test
    fun validationAcceptsTrimmedTokenWithoutRequiringAPrefix() {
        assertNull(apiKeyValidationError("  any_Valid-Key123  "))
        assertNull(apiKeyValidationError(" x "))
        assertNull(apiKeyValidationError("x".repeat(512)))
    }

    @Test
    fun damagedCiphertextFailsClosedAndCanBeReplaced() {
        assertNull(store.saveApiKey(sampleApiKey))
        val bytes = keyFile.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        keyFile.writeBytes(bytes)

        assertEquals("", reopenedStore().apiKey())
        assertNull(store.saveApiKey("replacement_key"))
        assertEquals("replacement_key", reopenedStore().apiKey())
    }

    @Test
    fun invalidFileFormatsFailClosed() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3), ByteArray(600))) {
            keyFile.writeBytes(bytes)
            assertEquals("", reopenedStore().apiKey())
        }
    }

    @Test
    fun inaccessibleOrWrongEncryptionKeyFailsClosed() {
        assertNull(store.saveApiKey(sampleApiKey))
        val wrongKey = SecretKeySpec(ByteArray(32) { 99 }, "AES")

        assertEquals("", ApiKeyStore(keyFile) { wrongKey }.apiKey())
        assertEquals("", ApiKeyStore(keyFile) { error("Keystore unavailable") }.apiKey())
    }

    @Test
    fun encryptionFailureReportsAnErrorAndPreservesExistingKey() {
        assertNull(store.saveApiKey(sampleApiKey))
        val ciphertext = keyFile.readBytes()
        val failingStore = ApiKeyStore(keyFile) { error("Keystore unavailable") }

        assertNotNull(failingStore.saveApiKey("replacement_key"))
        assertTrue(ciphertext.contentEquals(keyFile.readBytes()))
        assertEquals(sampleApiKey, reopenedStore().apiKey())
    }

    @Test
    fun unwritableFileReportsAnErrorWithoutClaimingToSave() {
        val parentFile = temporaryFolder.newFile("not-a-directory")
        val unusableStore = ApiKeyStore(File(parentFile, "api-key.enc")) { encryptionKey }

        assertNotNull(unusableStore.saveApiKey(sampleApiKey))
        assertEquals("", unusableStore.apiKey())
    }

    private fun reopenedStore(): ApiKeyStore = ApiKeyStore(keyFile) { encryptionKey }
}
