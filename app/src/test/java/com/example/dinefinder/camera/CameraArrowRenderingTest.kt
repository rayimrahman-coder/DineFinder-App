package com.example.dinefinder.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraArrowRenderingTest {
    @Test
    fun sameViewRepaintsBundledModelFromStraightToBothTurnsAndBack() {
        val view = createView()

        // Reuse the view and its projected mesh cache, as a navigation session does.
        for ((angle, instruction) in listOf(
            0f to "Continue straight for 63 m",
            90f to "Now: Turn right",
            -90f to "Now: Turn left",
            0f to "Continue straight for 32 m"
        )) {
            view.setManeuver(angle, instruction)
            val pixels = renderModelPixels(view)
            assertDirection(pixels, angle)
        }

        view.clearDirection()
        assertTrue("Clearing guidance must remove the visible model", renderModelPixels(view).isEmpty())
        view.setManeuver(-90f, "Now: Turn left")
        assertDirection(renderModelPixels(view), -90f)
    }

    @Test
    fun clearingManeuverAndReturningResetsTheModel() {
        val view = createView()
        view.setManeuver(0f, "Continue straight")
        val original = renderModelPixels(view)
        assertTrue(original.isNotEmpty())
        view.clearDirection()
        assertTrue(renderModelPixels(view).isEmpty())
        view.setManeuver(0f, "Continue straight")
        assertEquals(original, renderModelPixels(view))
    }

    @Test
    fun invalidDirectionClearsTheModelAndOverlayDoesNotConsumeTouches() {
        val view = createView()
        view.setManeuver(0f, "Continue straight")
        assertTrue(renderModelPixels(view).isNotEmpty())
        for ((angle, text) in listOf(Float.NaN to "Turn right", 90f to "  ", Float.POSITIVE_INFINITY to "Turn right")) {
            view.setManeuver(angle, text)
            assertTrue(renderModelPixels(view).isEmpty())
        }
        assertFalse(view.isClickable)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 128f, 128f, 0)
        try {
            assertFalse(view.onTouchEvent(down))
        } finally {
            down.recycle()
        }
    }

    private fun createView(): CameraArrowView = CameraArrowView(RuntimeEnvironment.getApplication()).apply {
        val size = View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY)
        measure(size, size)
        layout(0, 0, SIZE, SIZE)
    }

    private fun renderModelPixels(view: CameraArrowView): List<Pixel> {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return buildList {
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) >= 200 && color != Color.TRANSPARENT && color != Color.WHITE) add(Pixel(x, y))
            }
        }.also { bitmap.recycle() }
    }

    private fun assertDirection(pixels: List<Pixel>, angle: Float) {
        assertTrue("The bundled arrow must be visible for $angle degrees", pixels.size > 500)
        val minX = pixels.minOf { it.x }
        val maxX = pixels.maxOf { it.x }
        val minY = pixels.minOf { it.y }
        val maxY = pixels.maxOf { it.y }
        val vertical = angle == 0f

        // A pointed head has much less colored area at its end than the flat
        // tail. Inspect rendered pixels rather than an angle field
        // or expected projection, so a stale/incorrect visible model fails.
        val start = if (vertical) minY else minX
        val end = if (vertical) maxY else maxX
        val endBand = maxOf(2f, (end - start + 1) * .15f)
        val first = pixels.count { (if (vertical) it.y else it.x) < start + endBand }
        val last = pixels.count { (if (vertical) it.y else it.x) > end - endBand }
        val head = if (vertical || angle < 0f) first else last
        val stem = if (vertical || angle < 0f) last else first
        assertTrue("Arrow $angle model must render clearly (head=$head, stem=$stem)", head > 0 && stem > 0)
    }

    private data class Pixel(val x: Int, val y: Int)

    private companion object {
        const val SIZE = 256
    }
}
