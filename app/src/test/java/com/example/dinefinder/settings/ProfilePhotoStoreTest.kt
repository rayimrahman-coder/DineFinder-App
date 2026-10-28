package com.example.dinefinder.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfilePhotoStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clean() {
        context.getSharedPreferences("dinefinder_profile_photos",Context.MODE_PRIVATE).edit().clear().commit()
        File(context.noBackupFilesDir,"profile-photos").deleteRecursively()
    }
    private fun photo(): Uri {
        val bytes = ByteArrayOutputStream()
        Bitmap.createBitmap(1200,600,Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
            compress(Bitmap.CompressFormat.PNG,100,bytes)
            recycle()
        }
        val uri=Uri.parse("content://test/photos/selected")
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream(bytes.toByteArray()))
        return uri
    }
    @Test fun copiedPhotoSurvivesNewStoreAndIsResizedAndIsolatedPerUser() {
        val store=ProfilePhotoStore(context)
        val path=store.save(" Person@example.com ",photo())
        assertEquals(path,ProfilePhotoStore(context).photoPath("person@EXAMPLE.com"))
        assertNull(store.photoPath("someone@example.com"))
        val bitmap=android.graphics.BitmapFactory.decodeFile(path)
        assertEquals(768,bitmap.width);assertEquals(384,bitmap.height)
        store.remove("someone@example.com")
        assertTrue(File(path).isFile)
        store.remove("PERSON@example.com")
        assertNull(store.photoPath("person@example.com"));assertFalse(File(path).exists())
    }
    @Test fun invalidReplacementKeepsPreviousPhotoAndSuccessfulReplacementRemovesOldFile() {
        val store=ProfilePhotoStore(context)
        val old=store.save("guest@dinefinder.local",photo())
        val bad=Uri.parse("content://test/photos/bad")
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(bad,ByteArrayInputStream("not an image".toByteArray()))
        try { store.save("guest@dinefinder.local",bad);fail("Invalid image accepted") } catch (_: Exception) { }
        assertEquals(old,store.photoPath("guest@dinefinder.local"));assertTrue(File(old).isFile)
        val replacement=store.save("guest@dinefinder.local",photo())
        assertNotEquals(old,replacement);assertFalse(File(old).exists());assertTrue(File(replacement).isFile)
    }
}
