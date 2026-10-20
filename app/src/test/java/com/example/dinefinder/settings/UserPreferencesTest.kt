package com.example.dinefinder.settings

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: UserPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clearPreferences()
        preferences = UserPreferences(context)
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun freshInstallationHasNoSignedInEmailAndUsesLightMode() {
        assertEquals("", preferences.currentEmail())
        assertFalse(preferences.isDarkMode())
        assertEquals("User", preferences.userName(""))
    }

    @Test
    fun preservesLegacyEmailAndDefaultDisplayNameWithoutMigration() {
        context.getSharedPreferences("dinefinder_profile", Context.MODE_PRIVATE)
            .edit().putString("email", "ray.im_rahman@example.com").commit()

        val reopened = UserPreferences(context)
        assertEquals("ray.im_rahman@example.com", reopened.currentEmail())
        assertEquals("Ray Im Rahman", reopened.userName(reopened.currentEmail()))
        assertEquals("User", reopened.userName("guest@dinefinder.local"))
        assertFalse(reopened.isDarkMode())
    }

    @Test
    fun signedInEmailAndTrimmedUserNamePersistAcrossInstances() {
        preferences.signIn("  ray@example.com  ")
        assertNull(preferences.saveUserName(preferences.currentEmail(), "  Rayim Rahman  "))

        val reopened = UserPreferences(context)
        assertEquals("ray@example.com", reopened.currentEmail())
        assertEquals("Rayim Rahman", reopened.userName(reopened.currentEmail()))
    }

    @Test
    fun userNamesBelongToEachNormalizedEmail() {
        assertNull(preferences.saveUserName(" First@Example.com ", "First Person"))
        assertNull(preferences.saveUserName("second@example.com", "Second Person"))

        val reopened = UserPreferences(context)
        assertEquals("First Person", reopened.userName("first@example.com"))
        assertEquals("First Person", reopened.userName(" FIRST@EXAMPLE.COM "))
        assertEquals("Second Person", reopened.userName("second@example.com"))
        assertEquals("Third", reopened.userName("third@example.com"))
    }

    @Test
    fun signOutClearsOnlyTheSessionAndKeepsNamesAndDarkMode() {
        preferences.signIn("ray@example.com")
        preferences.saveUserName("ray@example.com", "Rayim")
        preferences.setDarkMode(true)
        preferences.signOut()

        val reopened = UserPreferences(context)
        assertEquals("", reopened.currentEmail())
        assertTrue(reopened.isDarkMode())
        assertEquals("Rayim", reopened.userName("ray@example.com"))
        reopened.signIn("ray@example.com")
        assertEquals("Rayim", reopened.userName(reopened.currentEmail()))
    }

    @Test
    fun invalidNamesReturnAnErrorAndDoNotOverwriteTheSavedName() {
        preferences.saveUserName("ray@example.com", "Rayim")
        for (name in listOf("", " \t\n ", "x".repeat(41))) {
            assertNotNull(userNameValidationError(name))
            assertNotNull(preferences.saveUserName("ray@example.com", name))
            assertEquals("Rayim", UserPreferences(context).userName("ray@example.com"))
        }
    }

    @Test
    fun fortyCharacterNameIsAcceptedAfterTrimming() {
        val name = "x".repeat(40)
        assertNull(userNameValidationError("  $name  "))
        assertNull(preferences.saveUserName("ray@example.com", "  $name  "))
        assertEquals(name, UserPreferences(context).userName("ray@example.com"))
    }

    @Test
    fun darkModePersistsGloballyAndCanBeTurnedOff() {
        preferences.signIn("first@example.com")
        preferences.setDarkMode(true)

        val reopened = UserPreferences(context)
        assertTrue(reopened.isDarkMode())
        reopened.signIn("second@example.com")
        assertTrue(reopened.isDarkMode())
        reopened.setDarkMode(false)
        assertFalse(UserPreferences(context).isDarkMode())
    }

    private fun clearPreferences() {
        context.getSharedPreferences("dinefinder_profile", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
