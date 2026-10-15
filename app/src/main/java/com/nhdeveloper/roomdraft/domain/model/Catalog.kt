package com.nhdeveloper.roomdraft.domain.model

import java.util.UUID

enum class FurnitureCategory(val label: String) {
    SOFAS("Sofas"), TABLES("Tables"), CHAIRS("Chairs"), BEDS("Beds"), STORAGE("Storage"),
    CABINETS("Cabinets"), TV_UNITS("TV Units"), DESKS("Desks"), LAMPS("Lamps"),
    PLANTS("Plants"), INDIAN("Indian"), OPENINGS("Doors & Windows"), OTHER("Other")
}

enum class FurnitureShape { RECT, ROUND }

data class CatalogItem(
    val id: String,
    val name: String,
    val category: FurnitureCategory,
    val widthM: Double,
    val depthM: Double,
    val heightM: Double,
    val defaultColor: Long,
    val shape: FurnitureShape = FurnitureShape.RECT,
    val floorCovering: Boolean = false,
    /** Doors and windows: they sit inside a wall and are cut out of it in 3D. */
    val opening: Boolean = false
)

data class PlacedFurniture(
    val id: String,
    val catalogId: String,
    val name: String,
    val centerX: Double,
    val centerY: Double,
    val widthM: Double,
    val depthM: Double,
    val rotationDeg: Int,
    val colorArgb: Long
)

val FurniturePalette: List<Long> = listOf(
    0xFFC2B09A, 0xFF7C8F9A, 0xFF2F7F8F, 0xFF1F3A5F,
    0xFF2B2B2B, 0xFF9B5E3C, 0xFF3F8F5A, 0xFFB5483C,
    0xFF9E2B25, 0xFFD9A441, 0xFF6B3E26, 0xFFF1E4C8
)

object Catalog {
    private val R = FurnitureShape.RECT
    private val O = FurnitureShape.ROUND

    val items: List<CatalogItem> = listOf(
        CatalogItem("sofa_2", "Sofa (2-seater)", FurnitureCategory.SOFAS, 1.6, 0.9, 0.85, 0xFFB8A58C, R),
        CatalogItem("sofa_3", "Sofa (3-seater)", FurnitureCategory.SOFAS, 2.2, 0.9, 0.85, 0xFFA89A86, R),
        CatalogItem("sofa_l", "L-shaped Sofa", FurnitureCategory.SOFAS, 2.6, 1.6, 0.85, 0xFF7C8F9A, R),
        CatalogItem("armchair", "Armchair", FurnitureCategory.SOFAS, 0.85, 0.85, 0.9, 0xFF3D4A55, R),

        CatalogItem("coffee_table", "Coffee Table", FurnitureCategory.TABLES, 1.1, 0.6, 0.45, 0xFFC9A675, R),
        CatalogItem("dining_table", "Dining Table", FurnitureCategory.TABLES, 1.6, 0.9, 0.75, 0xFF9B6B43, R),
        CatalogItem("round_table", "Round Table", FurnitureCategory.TABLES, 1.0, 1.0, 0.75, 0xFFB98B5E, O),
        CatalogItem("side_table", "Side Table", FurnitureCategory.TABLES, 0.5, 0.5, 0.55, 0xFFC9A675, R),

        CatalogItem("dining_chair", "Dining Chair", FurnitureCategory.CHAIRS, 0.45, 0.5, 0.9, 0xFF8A6A4A, R),
        CatalogItem("office_chair", "Office Chair", FurnitureCategory.CHAIRS, 0.6, 0.6, 1.0, 0xFF2B2B2B, O),
        CatalogItem("stool", "Stool", FurnitureCategory.CHAIRS, 0.4, 0.4, 0.65, 0xFFB98B5E, O),

        CatalogItem("bed_single", "Single Bed", FurnitureCategory.BEDS, 0.9, 2.0, 0.5, 0xFF9DB4C0, R),
        CatalogItem("bed_double", "Double Bed", FurnitureCategory.BEDS, 1.4, 2.0, 0.5, 0xFF9DB4C0, R),
        CatalogItem("bed_queen", "Queen Bed", FurnitureCategory.BEDS, 1.6, 2.0, 0.5, 0xFFB7C4BC, R),
        CatalogItem("bed_king", "King Bed", FurnitureCategory.BEDS, 1.8, 2.0, 0.5, 0xFFC2B09A, R),

        CatalogItem("bookshelf", "Bookshelf", FurnitureCategory.STORAGE, 0.9, 0.3, 1.8, 0xFF9B6B43, R),
        CatalogItem("shoe_rack", "Shoe Rack", FurnitureCategory.STORAGE, 0.8, 0.3, 0.6, 0xFFB98B5E, R),
        CatalogItem("chest", "Storage Chest", FurnitureCategory.STORAGE, 1.0, 0.5, 0.5, 0xFF8A6A4A, R),

        CatalogItem("wardrobe", "Wardrobe", FurnitureCategory.CABINETS, 1.2, 0.6, 2.0, 0xFFC2B09A, R),
        CatalogItem("kitchen_cabinet", "Kitchen Cabinet", FurnitureCategory.CABINETS, 0.6, 0.6, 0.9, 0xFFD8D2C8, R),
        CatalogItem("sideboard", "Sideboard", FurnitureCategory.CABINETS, 1.4, 0.45, 0.8, 0xFF9B6B43, R),

        CatalogItem("tv_unit", "TV Unit", FurnitureCategory.TV_UNITS, 1.6, 0.4, 0.5, 0xFF7A4F2E, R),
        CatalogItem("tv_unit_small", "TV Unit (Small)", FurnitureCategory.TV_UNITS, 1.2, 0.4, 0.45, 0xFF9B6B43, R),
        CatalogItem("tv_unit_wide", "TV Unit (Wide)", FurnitureCategory.TV_UNITS, 2.0, 0.45, 0.5, 0xFF3D3D3D, R),

        CatalogItem("desk", "Desk", FurnitureCategory.DESKS, 1.2, 0.6, 0.75, 0xFFC9A675, R),
        CatalogItem("l_desk", "L-shaped Desk", FurnitureCategory.DESKS, 1.5, 1.4, 0.75, 0xFF9B6B43, R),
        CatalogItem("study_desk", "Study Desk", FurnitureCategory.DESKS, 1.0, 0.5, 0.75, 0xFFB98B5E, R),

        CatalogItem("floor_lamp", "Floor Lamp", FurnitureCategory.LAMPS, 0.35, 0.35, 1.6, 0xFFE0C36A, O),
        CatalogItem("table_lamp", "Table Lamp", FurnitureCategory.LAMPS, 0.25, 0.25, 0.45, 0xFFE0C36A, O),
        CatalogItem("arc_lamp", "Arc Lamp", FurnitureCategory.LAMPS, 0.5, 0.5, 2.0, 0xFF2B2B2B, O),

        CatalogItem("plant_small", "Small Plant", FurnitureCategory.PLANTS, 0.35, 0.35, 0.5, 0xFF3F8F5A, O),
        CatalogItem("plant_medium", "Medium Plant", FurnitureCategory.PLANTS, 0.5, 0.5, 1.0, 0xFF3F8F5A, O),
        CatalogItem("plant_large", "Large Plant", FurnitureCategory.PLANTS, 0.8, 0.8, 1.6, 0xFF2F7A4A, O),

        // Indian / traditional furniture
        CatalogItem("charpai", "Charpai (Khat)", FurnitureCategory.INDIAN, 1.0, 1.95, 0.45, 0xFF8B5A2B, R),
        CatalogItem("diwan", "Diwan (Divan)", FurnitureCategory.INDIAN, 1.95, 0.9, 0.75, 0xFF9E2B25, R),
        CatalogItem("wood_sofa", "Carved Wooden Sofa", FurnitureCategory.INDIAN, 1.9, 0.8, 0.95, 0xFF8C3B2A, R),
        CatalogItem("jhula", "Jhula (Wooden Swing)", FurnitureCategory.INDIAN, 1.5, 0.65, 2.2, 0xFF7A4A24, R),
        CatalogItem("peacock_chair", "Peacock Chair", FurnitureCategory.INDIAN, 0.85, 0.85, 1.4, 0xFFC79A5B, O),
        CatalogItem("mandir", "Puja Mandir", FurnitureCategory.INDIAN, 0.9, 0.45, 1.7, 0xFF8A5528, R),
        CatalogItem("almirah", "Almirah (Steel)", FurnitureCategory.INDIAN, 0.95, 0.5, 1.85, 0xFF6F8794, R),
        CatalogItem("sandook", "Sandook (Wooden Trunk)", FurnitureCategory.INDIAN, 0.95, 0.5, 0.5, 0xFF7B4B2A, R),
        CatalogItem("chowki", "Chowki (Low Table)", FurnitureCategory.INDIAN, 0.6, 0.6, 0.3, 0xFFA66A35, R),
        CatalogItem("peedha", "Peedha (Low Stool)", FurnitureCategory.INDIAN, 0.3, 0.3, 0.22, 0xFFB5793C, O),
        CatalogItem("gadda", "Gadda & Takiya (Floor Seating)", FurnitureCategory.INDIAN, 1.9, 0.95, 0.22, 0xFFF1E4C8, R),
        CatalogItem("tulsi", "Tulsi Vrindavan", FurnitureCategory.INDIAN, 0.5, 0.5, 1.1, 0xFFC96F3B, R),
        CatalogItem("urli", "Brass Urli", FurnitureCategory.INDIAN, 0.7, 0.7, 0.2, 0xFFC9A227, O),
        CatalogItem("dhurrie", "Dhurrie (Cotton Rug)", FurnitureCategory.INDIAN, 2.0, 1.4, 0.02, 0xFFC0522D, R, floorCovering = true),

        // Doors & windows (depth = wall thickness; heightM = opening height, sill from OpeningSpecs)
        CatalogItem("door_main", "Main Door", FurnitureCategory.OPENINGS, 1.0, 0.12, 2.1, 0xFF7A4A24, R, opening = true),
        CatalogItem("door_room", "Room Door", FurnitureCategory.OPENINGS, 0.9, 0.12, 2.1, 0xFF9B6B43, R, opening = true),
        CatalogItem("door_sliding", "Sliding Balcony Door", FurnitureCategory.OPENINGS, 1.8, 0.12, 2.1, 0xFFB8C4CC, R, opening = true),
        CatalogItem("window", "Window", FurnitureCategory.OPENINGS, 1.2, 0.12, 1.2, 0xFFE9E4DA, R, opening = true),
        CatalogItem("window_large", "Large Window", FurnitureCategory.OPENINGS, 1.8, 0.12, 1.35, 0xFFE9E4DA, R, opening = true),
        CatalogItem("window_grill", "Window with Grill", FurnitureCategory.OPENINGS, 1.2, 0.12, 1.2, 0xFF8A5A33, R, opening = true),
        CatalogItem("ventilator", "Ventilator", FurnitureCategory.OPENINGS, 0.6, 0.12, 0.5, 0xFFE9E4DA, R, opening = true),

        CatalogItem("rug", "Rug", FurnitureCategory.OTHER, 2.0, 1.4, 0.02, 0xFF7C8F9A, R, floorCovering = true),
        CatalogItem("fridge", "Refrigerator", FurnitureCategory.OTHER, 0.7, 0.7, 1.8, 0xFFD8D2C8, R),
        CatalogItem("washer", "Washing Machine", FurnitureCategory.OTHER, 0.6, 0.6, 0.85, 0xFFE4E4E4, R),
        CatalogItem("bathtub", "Bathtub", FurnitureCategory.OTHER, 1.7, 0.75, 0.55, 0xFFE9EEF0, R),
        CatalogItem("toilet", "Toilet", FurnitureCategory.OTHER, 0.4, 0.7, 0.8, 0xFFE9EEF0, R),
        CatalogItem("vanity", "Vanity Sink", FurnitureCategory.OTHER, 0.6, 0.5, 0.85, 0xFFD8D2C8, R)
    )

    fun find(id: String): CatalogItem? = items.firstOrNull { it.id == id }
    fun byCategory(category: FurnitureCategory): List<CatalogItem> = items.filter { it.category == category }

    fun place(catalogId: String, cx: Double, cy: Double, rotationDeg: Int = 0): PlacedFurniture {
        val c = requireNotNull(find(catalogId)) { "Unknown catalog item $catalogId" }
        return PlacedFurniture(UUID.randomUUID().toString(), c.id, c.name, cx, cy, c.widthM, c.depthM, rotationDeg, c.defaultColor)
    }
}

/** Starter furniture for each template. Positions are room coordinates in meters (origin top-left). */
object TemplateLayouts {
    fun build(template: RoomTemplate): List<PlacedFurniture> = when (template.id) {
        "small_living_room" -> listOf(
            Catalog.place("rug", 2.0, 2.0),
            Catalog.place("tv_unit", 2.0, 0.21),
            Catalog.place("sofa_3", 2.0, 3.04, 180),
            Catalog.place("coffee_table", 2.0, 2.0),
            Catalog.place("armchair", 0.55, 1.45, 270),
            Catalog.place("plant_medium", 3.65, 0.35),
            Catalog.place("floor_lamp", 3.6, 3.2),
            Catalog.place("door_main", 0.9, 3.44, 180),
            Catalog.place("window_large", 3.94, 1.8, 90)
        )
        "modern_bedroom" -> listOf(
            Catalog.place("bed_queen", 2.0, 1.0),
            Catalog.place("side_table", 0.85, 0.3),
            Catalog.place("side_table", 3.15, 0.3),
            Catalog.place("wardrobe", 3.3, 3.2, 180),
            Catalog.place("desk", 0.3, 2.6, 90),
            Catalog.place("office_chair", 0.95, 2.6),
            Catalog.place("door_room", 3.94, 1.3, 90),
            Catalog.place("window", 1.5, 3.44, 180)
        )
        "compact_kitchen" -> listOf(
            Catalog.place("kitchen_cabinet", 0.3, 0.3),
            Catalog.place("kitchen_cabinet", 0.9, 0.3),
            Catalog.place("kitchen_cabinet", 1.5, 0.3),
            Catalog.place("fridge", 2.55, 0.35),
            Catalog.place("round_table", 1.5, 1.7),
            Catalog.place("dining_chair", 0.75, 1.7, 90),
            Catalog.place("dining_chair", 2.25, 1.7, 270),
            Catalog.place("door_room", 0.7, 2.44, 180),
            Catalog.place("window", 2.94, 1.5, 90)
        )
        "home_office" -> listOf(
            Catalog.place("desk", 1.5, 0.3),
            Catalog.place("office_chair", 1.5, 0.95),
            Catalog.place("bookshelf", 2.85, 1.5, 90),
            Catalog.place("sofa_2", 1.5, 2.55, 180),
            Catalog.place("plant_medium", 0.4, 2.6),
            Catalog.place("floor_lamp", 2.65, 2.65),
            Catalog.place("door_room", 0.06, 1.5, 270),
            Catalog.place("window", 1.5, 0.06, 0)
        )
        "studio_apartment" -> listOf(
            Catalog.place("bed_double", 0.8, 1.0),
            Catalog.place("wardrobe", 0.7, 3.9, 180),
            Catalog.place("plant_large", 0.5, 2.7),
            Catalog.place("tv_unit_small", 3.5, 0.21),
            Catalog.place("coffee_table", 3.5, 2.5),
            Catalog.place("sofa_2", 3.5, 3.5, 180),
            Catalog.place("dining_table", 5.2, 3.8),
            Catalog.place("kitchen_cabinet", 4.5, 0.3),
            Catalog.place("kitchen_cabinet", 5.1, 0.3),
            Catalog.place("kitchen_cabinet", 5.7, 0.3),
            Catalog.place("door_main", 2.2, 4.44, 180),
            Catalog.place("door_sliding", 5.94, 1.3, 90),
            Catalog.place("window", 4.5, 4.44, 180)
        )
        else -> emptyList()
    }
}
