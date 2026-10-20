package com.example.dinefinder.settings

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GoogleMapsAppIdentityTest {
    @Test
    fun fingerprintMatchesSha1AndUsesHeaderCompatibleUppercaseHex() {
        val fingerprint = GoogleMapsAppIdentity.sha1Fingerprint("abc".toByteArray())
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D", fingerprint)
        assertTrue(fingerprint.matches(Regex("[0-9A-F]{40}")))
    }

    @Test
    fun missingPackageCannotInventSigningIdentity() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getPackageName() = "missing.example.app"
        }
        assertNull(GoogleMapsAppIdentity.from(context))
    }
}
