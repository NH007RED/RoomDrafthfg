package com.nhdeveloper.roomdraft.domain.model

import java.util.UUID

data class Layout(val id: String, val projectId: String, val name: String, val sortIndex: Int)

const val MAX_LAYOUT_NAME = 30

/** Optional starting arrangements built for the actual room size and type. */
object SampleLayouts {
    private enum class Anchor { TOP, BOTTOM, LEFT, RIGHT, CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private data class Spot(
        val id: String,
        val anchor: Anchor,
        val offset: Double = 0.0,
        val dy: Double = 0.0,
        val inset: Double = 0.0,
        val rot: Int? = null
    )

    private const val GAP = 0.03

    fun names(type: RoomType): List<String> = when (type) {
        RoomType.LIVING_ROOM -> listOf("TV on top wall", "TV on left wall", "Corner sofa")
        RoomType.BEDROOM -> listOf("Bed on top wall", "Bed on left wall", "Bed on right wall")
        RoomType.KITCHEN -> listOf("Galley", "L-shaped", "Dining focus")
        RoomType.OFFICE -> listOf("Desk on top wall", "Desk on left wall", "Desk and lounge")
        RoomType.BATHROOM -> listOf("Tub on top", "Tub on left", "Tub on bottom")
        RoomType.OTHER -> listOf("Living focus", "Sleeping focus", "Work focus")
    }

    private fun spots(type: RoomType, variant: Int): List<Spot> = when (type) {
        RoomType.LIVING_ROOM -> when (variant) {
            0 -> listOf(
                Spot("rug", Anchor.CENTER), Spot("tv_unit", Anchor.TOP), Spot("sofa_3", Anchor.BOTTOM),
                Spot("coffee_table", Anchor.CENTER), Spot("armchair", Anchor.LEFT),
                Spot("plant_medium", Anchor.TOP_RIGHT), Spot("floor_lamp", Anchor.BOTTOM_RIGHT)
            )
            1 -> listOf(
                Spot("rug", Anchor.CENTER), Spot("tv_unit", Anchor.LEFT), Spot("sofa_3", Anchor.RIGHT),
                Spot("coffee_table", Anchor.CENTER, rot = 90), Spot("armchair", Anchor.TOP),
                Spot("plant_large", Anchor.BOTTOM_LEFT), Spot("floor_lamp", Anchor.TOP_RIGHT)
            )
            else -> listOf(
                Spot("rug", Anchor.CENTER), Spot("sofa_l", Anchor.BOTTOM_LEFT), Spot("tv_unit_wide", Anchor.TOP),
                Spot("coffee_table", Anchor.CENTER), Spot("side_table", Anchor.BOTTOM_RIGHT),
                Spot("plant_medium", Anchor.TOP_LEFT)
            )
        }
        RoomType.BEDROOM -> when (variant) {
            0 -> listOf(
                Spot("bed_queen", Anchor.TOP), Spot("side_table", Anchor.TOP, -1.25), Spot("side_table", Anchor.TOP, 1.25),
                Spot("wardrobe", Anchor.BOTTOM), Spot("desk", Anchor.LEFT, 0.8), Spot("plant_small", Anchor.BOTTOM_RIGHT)
            )
            1 -> listOf(
                Spot("bed_double", Anchor.LEFT), Spot("side_table", Anchor.LEFT, -1.0), Spot("side_table", Anchor.LEFT, 1.0),
                Spot("wardrobe", Anchor.RIGHT), Spot("desk", Anchor.BOTTOM, 0.6), Spot("rug", Anchor.CENTER)
            )
            else -> listOf(
                Spot("bed_queen", Anchor.RIGHT), Spot("side_table", Anchor.RIGHT, -1.25), Spot("side_table", Anchor.RIGHT, 1.25),
                Spot("wardrobe", Anchor.LEFT), Spot("desk", Anchor.TOP, -0.6), Spot("plant_medium", Anchor.BOTTOM_LEFT)
            )
        }
        RoomType.KITCHEN -> when (variant) {
            0 -> listOf(
                Spot("kitchen_cabinet", Anchor.TOP, -0.9), Spot("kitchen_cabinet", Anchor.TOP, -0.3),
                Spot("kitchen_cabinet", Anchor.TOP, 0.3), Spot("fridge", Anchor.TOP_RIGHT),
                Spot("round_table", Anchor.CENTER, dy = 0.5)
            )
            1 -> listOf(
                Spot("kitchen_cabinet", Anchor.TOP_LEFT), Spot("kitchen_cabinet", Anchor.TOP, -0.2),
                Spot("kitchen_cabinet", Anchor.LEFT, 0.3), Spot("kitchen_cabinet", Anchor.LEFT, 0.9),
                Spot("fridge", Anchor.TOP_RIGHT), Spot("dining_table", Anchor.CENTER, 0.3, 0.5)
            )
            else -> listOf(
                Spot("kitchen_cabinet", Anchor.LEFT, -0.6), Spot("kitchen_cabinet", Anchor.LEFT, 0.0),
                Spot("kitchen_cabinet", Anchor.LEFT, 0.6), Spot("fridge", Anchor.BOTTOM_LEFT),
                Spot("dining_table", Anchor.CENTER, 0.4), Spot("dining_chair", Anchor.CENTER, 0.4, -0.75, rot = 180)
            )
        }
        RoomType.OFFICE -> when (variant) {
            0 -> listOf(
                Spot("desk", Anchor.TOP), Spot("office_chair", Anchor.TOP, inset = 0.7), Spot("bookshelf", Anchor.RIGHT),
                Spot("plant_medium", Anchor.BOTTOM_LEFT), Spot("sofa_2", Anchor.BOTTOM)
            )
            1 -> listOf(
                Spot("desk", Anchor.LEFT), Spot("office_chair", Anchor.LEFT, inset = 0.7), Spot("bookshelf", Anchor.TOP, 0.6),
                Spot("plant_medium", Anchor.BOTTOM_RIGHT), Spot("floor_lamp", Anchor.TOP_RIGHT)
            )
            else -> listOf(
                Spot("l_desk", Anchor.TOP_RIGHT), Spot("office_chair", Anchor.CENTER, 0.0, -0.3),
                Spot("bookshelf", Anchor.LEFT), Spot("sofa_2", Anchor.BOTTOM), Spot("plant_large", Anchor.BOTTOM_LEFT)
            )
        }
        RoomType.BATHROOM -> when (variant) {
            0 -> listOf(Spot("bathtub", Anchor.TOP), Spot("toilet", Anchor.BOTTOM_RIGHT), Spot("vanity", Anchor.LEFT, 0.3), Spot("washer", Anchor.BOTTOM_LEFT))
            1 -> listOf(Spot("bathtub", Anchor.LEFT, rot = 270), Spot("toilet", Anchor.TOP_RIGHT), Spot("vanity", Anchor.RIGHT), Spot("washer", Anchor.BOTTOM_RIGHT))
            else -> listOf(Spot("bathtub", Anchor.BOTTOM), Spot("toilet", Anchor.TOP_LEFT), Spot("vanity", Anchor.TOP, 0.8), Spot("washer", Anchor.TOP_RIGHT))
        }
        RoomType.OTHER -> when (variant) {
            0 -> listOf(
                Spot("rug", Anchor.CENTER), Spot("tv_unit", Anchor.TOP), Spot("sofa_3", Anchor.BOTTOM),
                Spot("coffee_table", Anchor.CENTER), Spot("plant_medium", Anchor.TOP_RIGHT), Spot("floor_lamp", Anchor.BOTTOM_RIGHT)
            )
            1 -> listOf(
                Spot("bed_double", Anchor.TOP_LEFT), Spot("side_table", Anchor.TOP, -1.2), Spot("wardrobe", Anchor.BOTTOM_LEFT),
                Spot("desk", Anchor.RIGHT), Spot("plant_medium", Anchor.BOTTOM_RIGHT)
            )
            else -> listOf(
                Spot("desk", Anchor.TOP), Spot("office_chair", Anchor.TOP, inset = 0.7), Spot("bookshelf", Anchor.LEFT),
                Spot("sofa_2", Anchor.BOTTOM), Spot("plant_large", Anchor.BOTTOM_RIGHT)
            )
        }
    }

    /** Builds a layout for the given room. Items that do not fit or would overlap are skipped. */
    fun build(type: RoomType, variant: Int, roomL: Double, roomW: Double): List<PlacedFurniture> {
        val result = mutableListOf<PlacedFurniture>()
        for (spot in spots(type, variant.coerceIn(0, 2))) {
            val cat = Catalog.find(spot.id) ?: continue
            val rot = spot.rot ?: when (spot.anchor) {
                Anchor.TOP, Anchor.TOP_LEFT, Anchor.TOP_RIGHT, Anchor.CENTER -> 0
                Anchor.BOTTOM, Anchor.BOTTOM_LEFT, Anchor.BOTTOM_RIGHT -> 180
                Anchor.LEFT -> 270
                Anchor.RIGHT -> 90
            }
            val ew = if (rot % 180 == 0) cat.widthM else cat.depthM
            val eh = if (rot % 180 == 0) cat.depthM else cat.widthM
            val (cx, cy) = when (spot.anchor) {
                Anchor.TOP -> (roomL / 2 + spot.offset) to (eh / 2 + GAP + spot.inset)
                Anchor.BOTTOM -> (roomL / 2 + spot.offset) to (roomW - eh / 2 - GAP - spot.inset)
                Anchor.LEFT -> (ew / 2 + GAP + spot.inset) to (roomW / 2 + spot.offset)
                Anchor.RIGHT -> (roomL - ew / 2 - GAP - spot.inset) to (roomW / 2 + spot.offset)
                Anchor.CENTER -> (roomL / 2 + spot.offset) to (roomW / 2 + spot.dy)
                Anchor.TOP_LEFT -> (ew / 2 + GAP) to (eh / 2 + GAP)
                Anchor.TOP_RIGHT -> (roomL - ew / 2 - GAP) to (eh / 2 + GAP)
                Anchor.BOTTOM_LEFT -> (ew / 2 + GAP) to (roomW - eh / 2 - GAP)
                Anchor.BOTTOM_RIGHT -> (roomL - ew / 2 - GAP) to (roomW - eh / 2 - GAP)
            }
            val candidate = PlacedFurniture(
                UUID.randomUUID().toString(), cat.id, cat.name, cx, cy,
                cat.widthM, cat.depthM, rot, cat.defaultColor
            )
            if (!FurnitureGeometry.isInside(candidate.bounds, roomL, roomW)) continue
            if (result.any { FurnitureGeometry.overlaps(it, candidate) }) continue
            result += candidate
        }
        return result
    }
}
