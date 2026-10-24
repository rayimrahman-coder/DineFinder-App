package com.example.dinefinder.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import org.robolectric.shadows.ShadowDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraLocationPermissionDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun referenceDialogUsesExactCopyAndEqualWidthAccessibleActions() {
        var dismissals = 0
        var continues = 0
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CameraLocationPermissionDialog({ dismissals++ }, { continues++ })
            }
        }
        compose.onNodeWithText("Allow camera & location").assertIsDisplayed()
        val window = ShadowDialog.getLatestDialog().window!!.attributes
        assertEquals(.55f, window.dimAmount, .001f)
        assertTrue(window.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        compose.onNodeWithText("To find nearby restaurants and guide you with AR navigation, we need access to your camera and location.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
        val left = compose.onNodeWithText("Not now").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithText("Continue").fetchSemanticsNode().boundsInRoot
        assertEquals(left.width, right.width, 1f)
        assertEquals(left.height, right.height, 1f)
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle { assertEquals(1, continues); assertEquals(0, dismissals) }
        compose.onNodeWithText("Not now").performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    @Config(sdk = [28], qualifiers = "w320dp-h480dp", fontScale = 2f)
    fun smallScreenWithLargeSystemTextKeepsActionsReachableWithoutHorizontalOverflow() {
        compose.setContent { CameraLocationPermissionDialog({}, {}) }
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        val card = compose.onNodeWithTag("camera_location_permission_dialog").fetchSemanticsNode().boundsInRoot
        val largeView = ShadowDialog.getLatestDialog().window!!.decorView
        val largeBitmap = Bitmap.createBitmap(largeView.width, largeView.height, Bitmap.Config.ARGB_8888)
        largeView.draw(Canvas(largeBitmap))
        val largeFile = File("build/reports/previews/camera-location-permission-large-text.png")
        largeFile.parentFile?.mkdirs()
        largeFile.outputStream().use { largeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        for (label in listOf("Not now", "Continue")) {
            val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= card.left)
            assertTrue(bounds.right <= card.right)
            assertTrue(bounds.bottom <= card.bottom)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val layout = layouts.single()
            assertFalse("$label clipped: size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, lines=${layout.lineCount}", layout.hasVisualOverflow)
        }
    }

    @Test
    @Config(sdk = [28], qualifiers = "w800dp-h360dp")
    fun landscapeKeepsButtonsReachable() {
        compose.setContent { CameraLocationPermissionDialog({}, {}) }
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [28], qualifiers = "w800dp-h1280dp")
    fun tabletKeepsDialogCentredAndReadable() {
        compose.setContent { CameraLocationPermissionDialog({}, {}) }
        compose.onNodeWithText("Allow camera & location").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
    }

    @Test fun renderPermissionDialogForVisualReview() {
        compose.setContent { CameraLocationPermissionDialog({}, {}) }
        compose.waitForIdle()
        val view = ShadowDialog.getLatestDialog().window!!.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/previews/camera-location-permission.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
