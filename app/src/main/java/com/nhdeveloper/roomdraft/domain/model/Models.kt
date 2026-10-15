package com.nhdeveloper.roomdraft.domain.model

import java.util.Locale
import kotlin.math.roundToLong

enum class MeasureUnit(val label: String, val symbol: String, val metersPerUnit: Double) {
    METERS("Meters", "m", 1.0),
    CENTIMETERS("Centimeters", "cm", 0.01),
    FEET("Feet", "ft", 0.3048),
    INCHES("Inches", "in", 0.0254);

    val usesMetricArea: Boolean get() = this == METERS || this == CENTIMETERS

    fun toMeters(value: Double): Double = value * metersPerUnit
    fun fromMeters(meters: Double): Double = meters / metersPerUnit
    fun format(meters: Double): String = "${formatNumber(fromMeters(meters))} $symbol"
    fun formatPair(lengthM: Double, widthM: Double): String =
        "${formatNumber(fromMeters(lengthM))} × ${formatNumber(fromMeters(widthM))} $symbol"
}

fun formatNumber(value: Double): String {
    val rounded = (value * 100).roundToLong() / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
}

const val SQ_METERS_PER_SQ_FEET = 0.09290304

fun areaLabel(areaM2: Double, unit: MeasureUnit): String =
    if (unit.usesMetricArea) "${formatNumber(areaM2)} m²"
    else "${formatNumber(areaM2 / SQ_METERS_PER_SQ_FEET)} ft²"

enum class RoomType(val label: String) {
    LIVING_ROOM("Living Room"),
    BEDROOM("Bedroom"),
    KITCHEN("Kitchen"),
    BATHROOM("Bathroom"),
    OFFICE("Office"),
    OTHER("Other");

    companion object {
        fun fromName(name: String): RoomType = values().firstOrNull { it.name == name } ?: OTHER
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultUnit: MeasureUnit = MeasureUnit.METERS,
    val showGrid: Boolean = true,
    val gridSpacingCm: Int = 50,
    val snapToGrid: Boolean = true
)

data class Project(
    val id: String,
    val name: String,
    val roomType: RoomType,
    val lengthM: Double,
    val widthM: Double,
    val templateId: String?,
    val createdAt: Long,
    val modifiedAt: Long
) {
    val areaM2: Double get() = lengthM * widthM
}

data class RoomPreset(val label: String, val lengthM: Double, val widthM: Double)

data class RoomTemplate(
    val id: String,
    val name: String,
    val description: String,
    val roomType: RoomType,
    val lengthM: Double,
    val widthM: Double
)

object Templates {
    val all: List<RoomTemplate> = listOf(
        RoomTemplate("small_living_room", "Small Living Room", "Cozy living space for a sofa, coffee table and TV unit.", RoomType.LIVING_ROOM, 4.0, 3.5),
        RoomTemplate("modern_bedroom", "Modern Bedroom", "Balanced bedroom with room for a bed, wardrobe and desk.", RoomType.BEDROOM, 4.0, 3.5),
        RoomTemplate("compact_kitchen", "Compact Kitchen", "Efficient kitchen footprint for counters and a dining nook.", RoomType.KITCHEN, 3.0, 2.5),
        RoomTemplate("home_office", "Home Office", "Quiet workspace for a desk, chair and storage.", RoomType.OFFICE, 3.0, 3.0),
        RoomTemplate("studio_apartment", "Studio Apartment", "Open plan studio combining living, sleeping and cooking.", RoomType.OTHER, 6.0, 4.5)
    )

    fun find(id: String): RoomTemplate? = all.firstOrNull { it.id == id }
}

val RoomPresets: List<RoomPreset> = listOf(
    RoomPreset("Small", 3.0, 3.0),
    RoomPreset("Medium", 4.0, 3.5),
    RoomPreset("Large", 5.0, 4.0),
    RoomPreset("Open plan", 6.0, 4.5)
)

object DimensionRules {
    const val MIN_METERS = 0.5
    const val MAX_METERS = 50.0

    /** Returns the value in meters, or an error message. Exactly one side is non-null. */
    fun validate(text: String, unit: MeasureUnit): Pair<Double?, String?> {
        val value = text.trim().replace(',', '.').toDoubleOrNull()
            ?: return null to "Enter a valid number"
        if (!value.isFinite() || value <= 0.0) return null to "Must be greater than 0"
        val meters = unit.toMeters(value)
        if (meters < MIN_METERS) return null to "Minimum is ${unit.format(MIN_METERS)}"
        if (meters > MAX_METERS) return null to "Maximum is ${unit.format(MAX_METERS)}"
        return meters to null
    }
}

object ProjectRules {
    const val MAX_NAME = 40

    fun validateName(name: String): String? {
        val t = name.trim()
        return when {
            t.isEmpty() -> "Enter a project name"
            t.length > MAX_NAME -> "Name is too long (max $MAX_NAME characters)"
            else -> null
        }
    }
}
