package com.example.dinefinder.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantMarkerLayoutTest {
    private val bounds = MarkerLayoutBounds(16, 80, 984, 920)

    @Test
    fun oneCardKeepsItsProjectedBearingAndTheCommonHorizon() {
        val placements = RestaurantMarkerLayout.place(listOf(item(1, 225.0)), bounds, 500, 12)

        assertEquals(MarkerPlacement(125, 468, 0), placements[1])
    }

    @Test
    fun nearbyBearingsUseSeparateRowsAroundTheHorizon() {
        val items = listOf(item(1, 490.0), item(2, 500.0), item(3, 510.0), item(4, 505.0))

        val placements = RestaurantMarkerLayout.place(items, bounds, 500, 12)

        assertEquals(listOf(0, 1, -1, 2), placements.values.map { it.row })
        assertEquals(listOf(390, 400, 410, 405), placements.values.map { it.left })
        assertFitsWithoutOverlap(items, placements, bounds, 12)
    }

    @Test
    fun separatedBearingsShareTheCentralRowAndClampAtBothScreenEdges() {
        val items = listOf(item(1, -1_000.0), item(2, 2_000.0))

        val placements = RestaurantMarkerLayout.place(items, bounds, 500, 12)

        assertEquals(MarkerPlacement(16, 468, 0), placements[1])
        assertEquals(MarkerPlacement(784, 468, 0), placements[2])
        assertFitsWithoutOverlap(items, placements, bounds, 12)
    }

    @Test
    fun measuredTallAndShortCardsHaveAConsistentRowPitchAndGap() {
        val items = listOf(item(1, height = 60), item(2, height = 121), item(3, height = 80))

        val placements = RestaurantMarkerLayout.place(items, bounds, 500, 9)

        assertEquals(MarkerPlacement(400, 470, 0), placements[1])
        assertEquals(MarkerPlacement(400, 570, 1), placements[2])
        assertEquals(MarkerPlacement(400, 330, -1), placements[3])
        assertFitsWithoutOverlap(items, placements, bounds, 9)
    }

    @Test
    fun previousRowsPreventCardsFromSwappingWhenDistancePriorityOrBearingChangesSlightly() {
        val original = listOf(item(1, 490.0), item(2, 500.0), item(3, 510.0))
        val initial = RestaurantMarkerLayout.place(original, bounds, 500, 12)
        val moved = listOf(item(3, 509.0), item(1, 491.0), item(2, 502.0))

        val placements = RestaurantMarkerLayout.place(moved, bounds, 500, 12,
            initial.mapValues { it.value.row })

        assertEquals(initial.mapValues { it.value.row }, placements.mapValues { it.value.row })
        assertEquals(409, placements.getValue(3).left)
        assertEquals(391, placements.getValue(1).left)
        assertEquals(402, placements.getValue(2).left)
        assertFitsWithoutOverlap(moved, placements, bounds, 12)
    }

    @Test
    fun crowdedViewKeepsNearestPriorityAndOmitsCardsWithoutAnAvailableRow() {
        val smallBounds = MarkerLayoutBounds(10, 20, 210, 240)
        val items = (1..6).map { item(it, 110.0, 200, 60) }

        val placements = RestaurantMarkerLayout.place(items, smallBounds, 130, 10)

        assertEquals(listOf(1, 2, 3), placements.keys.toList())
        assertEquals(listOf(0, 1, -1), placements.values.map { it.row })
        assertFitsWithoutOverlap(items, placements, smallBounds, 10)
    }

    @Test
    fun outOfBoundsPreviousRowsAreReassignedAfterTheViewportShrinks() {
        val smallBounds = MarkerLayoutBounds(0, 0, 200, 64)
        val items = listOf(item(1), item(2))

        val placements = RestaurantMarkerLayout.place(items, smallBounds, 500, 12,
            mapOf(1 to 3, 2 to 0))

        assertEquals(mapOf(1 to MarkerPlacement(0, 0, 0)), placements)
        assertFitsWithoutOverlap(items, placements, smallBounds, 12)
    }

    @Test
    fun invalidAndOversizedItemsDoNotPreventValidCardsFromBeingPlaced() {
        val items = listOf(
            item(1, width = 2_000), item(2, height = 2_000), item(3, width = 0),
            item(4, height = -1), item(5, Double.NaN), item(6, Double.POSITIVE_INFINITY),
            item(7), item(8)
        )

        val placements = RestaurantMarkerLayout.place(items, bounds, 500, 12)

        assertEquals(listOf(7, 8), placements.keys.toList())
        assertEquals(listOf(0, 1), placements.values.map { it.row })
        assertFitsWithoutOverlap(items, placements, bounds, 12)
    }

    @Test
    fun emptyTinyAndInvertedViewportsReturnNoPlacements() {
        for (unusable in listOf(
            MarkerLayoutBounds(0, 0, 0, 500), MarkerLayoutBounds(0, 0, 500, 0),
            MarkerLayoutBounds(20, 80, 10, 70), MarkerLayoutBounds(0, 0, 100, 40)
        )) {
            assertTrue(RestaurantMarkerLayout.place(listOf(item(1)), unusable, 100, 12).isEmpty())
        }
        assertTrue(RestaurantMarkerLayout.place(emptyList(), bounds, 500, 12).isEmpty())
    }

    @Test
    fun repeatedInputsAreDeterministicAndAnExactHorizontalGapCanShareARow() {
        val items = listOf(item(1, 200.0), item(2, 412.0), item(3, 400.0))
        val initial = RestaurantMarkerLayout.place(items, bounds, 500, 12)

        repeat(10) {
            assertEquals(initial, RestaurantMarkerLayout.place(items, bounds, 500, 12))
        }
        assertEquals(0, initial.getValue(1).row)
        assertEquals(0, initial.getValue(2).row)
        assertEquals(1, initial.getValue(3).row)
        assertFitsWithoutOverlap(items, initial, bounds, 12)
    }

    @Test
    fun removedCardsReleaseTheirRowsAndNewCardsAvoidExistingRows() {
        val original = listOf(item(1), item(2), item(3))
        val initial = RestaurantMarkerLayout.place(original, bounds, 500, 12)
        val changed = listOf(item(2), item(3), item(4))

        val placements = RestaurantMarkerLayout.place(changed, bounds, 500, 12,
            initial.mapValues { it.value.row })

        assertEquals(1, placements.getValue(2).row)
        assertEquals(-1, placements.getValue(3).row)
        assertEquals(0, placements.getValue(4).row)
        assertFitsWithoutOverlap(changed, placements, bounds, 12)
    }

    private fun item(id: Int, centerX: Double = 500.0, width: Int = 200, height: Int = 64) =
        MarkerLayoutItem(id, centerX, width, height)

    private fun assertFitsWithoutOverlap(
        items: List<MarkerLayoutItem>,
        placements: Map<Int, MarkerPlacement>,
        bounds: MarkerLayoutBounds,
        gap: Int
    ) {
        val visible = items.filter { it.id in placements }
        for ((index, item) in visible.withIndex()) {
            val position = placements.getValue(item.id)
            assertTrue(position.left >= bounds.left)
            assertTrue(position.top >= bounds.top)
            assertTrue(position.left + item.width <= bounds.right)
            assertTrue(position.top + item.height <= bounds.bottom)
            for (other in visible.drop(index + 1)) {
                val otherPosition = placements.getValue(other.id)
                assertTrue("Cards ${item.id} and ${other.id} overlap or have insufficient spacing",
                    position.left + item.width + gap <= otherPosition.left ||
                        otherPosition.left + other.width + gap <= position.left ||
                        position.top + item.height + gap <= otherPosition.top ||
                        otherPosition.top + other.height + gap <= position.top)
            }
        }
    }
}
