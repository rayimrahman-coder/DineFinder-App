package com.example.dinefinder.ui

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class DiscoveryMarkerLayoutTest {
    @Test fun measuredCardsStayInsideCameraSpaceAndDoNotOverlap() {
        val sizes = listOf(IntSize(180, 90), IntSize(180, 110))
        val result = packDiscoveryMarkers(393, 420, sizes, listOf(.7f, .1f), 64, 58, 16, 14)
        assertEquals(2, result.size)
        result.forEach { (i, offset) ->
            assertTrue(offset.x >= 16 && offset.x + sizes[i].width <= 377)
            assertTrue(offset.y >= 64 && offset.y + sizes[i].height <= 362)
        }
        assertTrue(result.getValue(0).y + sizes[0].height < result.getValue(1).y)
    }
    @Test fun expandedSheetLeavesNoRoomForMarkers() {
        assertTrue(packDiscoveryMarkers(393, 0, listOf(IntSize(180, 90)), listOf(.5f), 64, 58, 16, 14).isEmpty())
    }
    @Test fun longMeasuredNamesAreNotClippedToForceAMarkerIntoSmallSpace() {
        val result = packDiscoveryMarkers(393, 300, listOf(IntSize(180, 400), IntSize(180, 80)), listOf(.5f,.5f), 64, 58, 16, 14)
        assertFalse(result.containsKey(0))
        assertTrue(result.containsKey(1))
    }
    @Test fun outOfRangeHorizontalPositionsAreClampedToTheCamera() {
        val result = packDiscoveryMarkers(393, 600, listOf(IntSize(180, 90),IntSize(180,90)), listOf(-1f,2f),64,58,16,14)
        assertEquals(16,result.getValue(0).x)
        assertEquals(197,result.getValue(1).x)
    }
}
