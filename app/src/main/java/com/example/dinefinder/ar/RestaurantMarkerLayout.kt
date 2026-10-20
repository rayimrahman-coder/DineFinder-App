package com.example.dinefinder.ar

import kotlin.math.roundToInt

internal data class MarkerLayoutBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Desired horizontal center and measured dimensions, in container pixels. */
internal data class MarkerLayoutItem(val id: Int, val centerX: Double, val width: Int, val height: Int)

internal data class MarkerPlacement(val left: Int, val top: Int, val row: Int)

internal object RestaurantMarkerLayout {
    /** Items are in nearest-first priority order; cards that cannot fit are omitted. */
    fun place(
        items: List<MarkerLayoutItem>,
        bounds: MarkerLayoutBounds,
        centerY: Int,
        gap: Int,
        previousRows: Map<Int, Int> = emptyMap()
    ): Map<Int, MarkerPlacement> {
        val availableWidth = bounds.right.toLong() - bounds.left
        val availableHeight = bounds.bottom.toLong() - bounds.top
        if (availableWidth <= 0 || availableHeight <= 0) return emptyMap()
        val fitting = items.filter {
            it.centerX.isFinite() && it.width > 0 && it.height > 0 &&
                it.width <= availableWidth && it.height <= availableHeight
        }.distinctBy { it.id }
        if (fitting.isEmpty()) return emptyMap()

        val spacing = gap.coerceAtLeast(0).toLong()
        val tallest = fitting.maxOf { it.height }
        val rowPitch = tallest.toLong() + spacing
        // Keep the central row inside the usable camera area even in landscape.
        val horizon = centerY.toLong().coerceIn(
            bounds.top.toLong() + tallest / 2,
            bounds.bottom.toLong() - (tallest - tallest / 2)
        )
        val maximumRow = (availableHeight / rowPitch + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val result = linkedMapOf<Int, MarkerPlacement>()
        val placed = mutableListOf<Pair<MarkerLayoutItem, MarkerPlacement>>()

        for (item in fitting) {
            val left = (item.centerX - item.width / 2.0)
                .coerceIn(bounds.left.toDouble(), (bounds.right.toLong() - item.width).toDouble())
                .roundToInt()
            val candidates = sequence {
                previousRows[item.id]?.let { yield(it) }
                yield(0)
                for (offset in 1..maximumRow) {
                    yield(offset)
                    yield(-offset)
                }
            }.distinct()
            for (row in candidates) {
                val top = horizon + row.toLong() * rowPitch - item.height / 2
                if (top < bounds.top || top + item.height > bounds.bottom) continue
                val overlaps = placed.any { (other, position) ->
                    left.toLong() < position.left.toLong() + other.width + spacing &&
                        left.toLong() + item.width + spacing > position.left &&
                        top < position.top.toLong() + other.height + spacing &&
                        top + item.height + spacing > position.top
                }
                if (overlaps) continue
                val placement = MarkerPlacement(left, top.toInt(), row)
                result[item.id] = placement
                placed += item to placement
                break
            }
        }
        return result
    }
}
