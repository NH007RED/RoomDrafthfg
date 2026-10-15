package com.nhdeveloper.roomdraft.domain.model

import kotlin.math.abs

/** Which wall an opening sits in. Rotation 0 = back against the top (north) wall, then clockwise. */
enum class Wall(val rotation: Int) { NORTH(0), EAST(90), SOUTH(180), WEST(270);
    companion object { fun fromRotation(r: Int): Wall = values().firstOrNull { it.rotation == ((r % 360) + 360) % 360 } ?: NORTH }
}

object Openings {
    const val WALL_THICKNESS = 0.12

    /** Height of the lower edge above the floor. Doors start at the floor. */
    fun sill(catalogId: String): Double = when (catalogId) {
        "window", "window_grill" -> 0.9
        "window_large" -> 0.75
        "ventilator" -> 1.6
        else -> 0.0
    }

    fun top(catalogId: String): Double = sill(catalogId) + (Catalog.find(catalogId)?.heightM ?: 2.1)
    fun isDoor(catalogId: String): Boolean = catalogId.startsWith("door")

    private fun along(item: PlacedFurniture, wall: Wall, roomL: Double, roomW: Double, v: Double): Double {
        val half = item.widthM / 2.0
        return if (wall == Wall.NORTH || wall == Wall.SOUTH) v.coerceIn(half, maxOf(half, roomL - half))
        else v.coerceIn(half, maxOf(half, roomW - half))
    }

    /** Puts the opening flush against [wall] with its centre at [pos] (metres along that wall). */
    fun onWall(item: PlacedFurniture, wall: Wall, pos: Double, roomL: Double, roomW: Double): PlacedFurniture {
        val p = along(item, wall, roomL, roomW, pos)
        val half = item.depthM / 2.0
        val moved = item.copy(rotationDeg = wall.rotation)
        return when (wall) {
            Wall.NORTH -> moved.copy(centerX = p, centerY = half)
            Wall.SOUTH -> moved.copy(centerX = p, centerY = roomW - half)
            Wall.WEST -> moved.copy(centerX = half, centerY = p)
            Wall.EAST -> moved.copy(centerX = roomL - half, centerY = p)
        }
    }

    /** Snaps to whichever wall is closest to the given point. */
    fun snapToWall(item: PlacedFurniture, x: Double, y: Double, roomL: Double, roomW: Double): PlacedFurniture {
        val d = listOf(Wall.NORTH to y, Wall.SOUTH to roomW - y, Wall.WEST to x, Wall.EAST to roomL - x)
        val wall = d.minByOrNull { abs(it.second) }!!.first
        val pos = if (wall == Wall.NORTH || wall == Wall.SOUTH) x else y
        return onWall(item, wall, pos, roomL, roomW)
    }

    /** Moves the opening to the next wall (clockwise), centred. Used by the Rotate button. */
    fun nextWall(item: PlacedFurniture, roomL: Double, roomW: Double): PlacedFurniture {
        val next = Wall.fromRotation(item.rotationDeg + 90)
        val mid = if (next == Wall.NORTH || next == Wall.SOUTH) roomL / 2.0 else roomW / 2.0
        return onWall(item, next, mid, roomL, roomW)
    }

    /** First wall where a new opening of this width doesn't collide with another opening. */
    fun freeSlot(item: PlacedFurniture, existing: List<PlacedFurniture>, roomL: Double, roomW: Double): PlacedFurniture {
        for (wall in Wall.values()) {
            val len = if (wall == Wall.NORTH || wall == Wall.SOUTH) roomL else roomW
            val mid = len / 2.0
            for (off in listOf(0.0, -len * 0.25, len * 0.25, -len * 0.4, len * 0.4)) {
                val c = onWall(item, wall, mid + off, roomL, roomW)
                if (existing.none { it.isOpening && it.bounds.intersects(c.bounds.let { b -> b.copy(left = b.left - 0.05, right = b.right + 0.05, top = b.top - 0.05, bottom = b.bottom + 0.05) }) }) return c
            }
        }
        return onWall(item, Wall.NORTH, roomL / 2.0, roomL, roomW)
    }
}
