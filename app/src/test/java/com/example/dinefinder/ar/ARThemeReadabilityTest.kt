package com.example.dinefinder.ar

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.example.dinefinder.R
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ARThemeReadabilityTest {
    @Test
    fun restaurantMarkerNamesAndDistancesRemainReadableInBothModes() {
        val light = scanMarker(Configuration.UI_MODE_NIGHT_NO)
        val dark = scanMarker(Configuration.UI_MODE_NIGHT_YES)
        assertNotEquals(light.backgroundColor, dark.backgroundColor)
        assertReadable(light.textColor, light.backgroundColor)
        assertReadable(dark.textColor, dark.backgroundColor)
        assertReadable(light.distanceTextColor, light.backgroundColor)
        assertReadable(dark.distanceTextColor, dark.backgroundColor)
    }

    @Test
    fun navigationInstructionsAndMutedTextRemainReadableInBothModes() {
        listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES).forEach { mode ->
            val context = themedContext(mode)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_ar_navigation, null)
            val panel = root.findViewById<LinearLayout>(R.id.instructionContainer)
            val overlayColor = (panel.background as GradientDrawable).color!!.defaultColor
            val backgroundColor = ColorUtils.compositeColors(overlayColor, context.getColor(R.color.ar_camera_background))
            assertReadable(root.findViewById<TextView>(R.id.directionText).currentTextColor, backgroundColor)
            assertReadable(root.findViewById<TextView>(R.id.instructionText).currentTextColor, backgroundColor)
            if (mode == Configuration.UI_MODE_NIGHT_YES) {
                assertReadable(root.findViewById<TextView>(R.id.distanceValue).currentTextColor, backgroundColor)
            }
        }
    }

    private fun scanMarker(mode: Int): PanelColors {
        val context = themedContext(mode)
        return PanelColors(
            (context.getDrawable(R.drawable.restaurant_scan_marker_background) as GradientDrawable).color!!.defaultColor,
            context.getColor(R.color.ar_text),
            context.getColor(R.color.ar_text_secondary)
        )
    }

    private fun themedContext(mode: Int): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        return ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.Theme_DineFinder)
    }

    private fun assertReadable(foreground: Int, background: Int) {
        assertTrue("Small panel text must have at least 4.5:1 contrast", ColorUtils.calculateContrast(foreground, background) >= 4.5)
    }

    private data class PanelColors(
        val backgroundColor: Int,
        val textColor: Int,
        val distanceTextColor: Int
    )
}
