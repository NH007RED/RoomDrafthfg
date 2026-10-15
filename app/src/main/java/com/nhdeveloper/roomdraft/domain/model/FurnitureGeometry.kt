package com.nhdeveloper.roomdraft.domain.model

import kotlin.math.abs
import kotlin.math.hypot

data class Bounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top

    fun contains(x: Double, y: Double): Boolean = x >= left && x <= right && y >= top && y <= bottom

    fun intersects(o: Bounds, eps: Double = 1e-6): Boolean =
        left < o.right - eps && right > o.left + eps && top < o.bottom - eps && bottom > o.top + eps
}

data class WallDistances(val left: Double, val right: Double, val top: Double, val bottom: Double)

val PlacedFurniture.effectiveWidth: Double get() = if (rotationDeg % 180 == 0) widthM else depthM
val PlacedFurniture.effectiveDepth: Double get() = if (rotationDeg % 180 == 0) depthM else widthM

val PlacedFurniture.bounds: Bounds
    get() {
        val hw = effectiveWidth / 2.0
        val hh = effectiveDepth / 2.0
        return Bounds(centerX - hw, centerY - hh, centerX + hw, centerY + hh)
    }

val PlacedFurniture.isFloorCovering: Boolean get() = Catalog.find(catalogId)?.floorCovering == true

val PlacedFurniture.isOpening: Boolean get() = Catalog.find(catalogId)?.opening == true

fun List<PlacedFurniture>.drawOrder(): List<PlacedFurniture> = sortedBy { if (it.isFloorCovering) 0 else 1 }

object FurnitureGeometry {
    const val MIN_SIZE = 0.2
    const val MAX_SIZE = 10.0

    fun snap(value: Double, step: Double): Double =
        if (step <= 0.0) value else Math.round(value / step) * step

    fun clampCenter(cx: Double, cy: Double, ew: Double, eh: Double, roomL: Double, roomW: Double): Pair<Double, Double> {
        val minX = ew / 2.0
        val maxX = roomL - ew / 2.0
        val minY = eh / 2.0
        val maxY = roomW - eh / 2.0
        val x = if (maxX < minX) roomL / 2.0 else cx.coerceIn(minX, maxX)
        val y = if (maxY < minY) roomW / 2.0 else cy.coerceIn(minY, maxY)
        return x to y
    }

    fun isInside(b: Bounds, roomL: Double, roomW: Double, eps: Double = 1e-6): Boolean =
        b.left >= -eps && b.top >= -eps && b.right <= roomL + eps && b.bottom <= roomW + eps

    fun overlaps(a: PlacedFurniture, b: PlacedFurniture): Boolean =
        !a.isFloorCovering && !b.isFloorCovering && !a.isOpening && !b.isOpening && a.bounds.intersects(b.bounds)

    fun overlappingPairs(items: List<PlacedFurniture>): List<Pair<PlacedFurniture, PlacedFurniture>> {
        val result = mutableListOf<Pair<PlacedFurniture, PlacedFurniture>>()
        for (i in items.indices) for (j in i + 1 until items.size) {
            if (overlaps(items[i], items[j])) result += items[i] to items[j]
        }
        return result
    }

    fun overlappingIds(items: List<PlacedFurniture>): Set<String> =
        overlappingPairs(items).flatMap { listOf(it.first.id, it.second.id) }.toSet()

    fun wallDistances(b: Bounds, roomL: Double, roomW: Double) =
        WallDistances(left = b.left, right = roomL - b.right, top = b.top, bottom = roomW - b.bottom)

    fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double = hypot(x2 - x1, y2 - y1)

    /** Nearest free center position (searching outward from the room center) on a 0.25 m lattice. */
    fun findFreeSpot(
        ew: Double, eh: Double, items: List<PlacedFurniture>,
        roomL: Double, roomW: Double, floorCovering: Boolean
    ): Pair<Double, Double> {
        val (startX, startY) = clampCenter(roomL / 2.0, roomW / 2.0, ew, eh, roomL, roomW)
        if (floorCovering) return startX to startY
        fun free(cx: Double, cy: Double): Boolean {
            val b = Bounds(cx - ew / 2.0, cy - eh / 2.0, cx + ew / 2.0, cy + eh / 2.0)
            return items.none { !it.isFloorCovering && !it.isOpening && it.bounds.intersects(b) }
        }
        if (free(startX, startY)) return startX to startY
        val step = 0.25
        val candidates = mutableListOf<Pair<Double, Double>>()
        var x = ew / 2.0
        while (x <= roomL - ew / 2.0 + 1e-9) {
            var y = eh / 2.0
            while (y <= roomW - eh / 2.0 + 1e-9) {
                candidates += x to y
                y += step
            }
            x += step
        }
        val best = candidates
            .sortedBy { abs(it.first - startX) + abs(it.second - startY) }
            .firstOrNull { free(it.first, it.second) }
        return best ?: (startX to startY)
    }
}
