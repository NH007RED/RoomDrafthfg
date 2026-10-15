package com.nhdeveloper.roomdraft.rendering.room3d

import androidx.compose.ui.graphics.Color
import com.nhdeveloper.roomdraft.domain.model.Bounds
import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.FurnitureCategory
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Openings
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.dim
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.mix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal val WOOD_DARK = Color(0xFF5A3A22)
internal val BRASS = Color(0xFFC9A227)
internal val OFF_WHITE = Color(0xFFF4F1EA)
internal val NEAR_BLACK = Color(0xFF111214)
internal val STEEL = Color(0xFFB9BEC2)

internal fun rnd(i: Int, seed: Int = 0): Float {
    var h = i * 374761393 + seed * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}

/**
 * Item-local coordinate frame. u runs along the item's width, v runs from its back (0) to its front (depth),
 * both in metres and in the UNROTATED item. Placement rotation (0/90/180/270) is applied when emitting geometry,
 * so every builder can be written as if the item faced "down" the plan with its back against the top wall.
 */
internal class Frame(
    private val b: Bounds, rotationDeg: Int,
    val w: Double, val d: Double,
    private val into: MutableList<Face>
) {
    private val rot = ((rotationDeg % 360) + 360) % 360
    private val swapped = rot % 180 != 0

    private fun px(u: Double, v: Double) = when (rot) { 90 -> b.right - v; 180 -> b.right - u; 270 -> b.left + v; else -> b.left + u }
    private fun py(u: Double, v: Double) = when (rot) { 90 -> b.top + u; 180 -> b.bottom - v; 270 -> b.bottom - u; else -> b.top + v }

    fun box(
        u0: Double, v0: Double, u1: Double, v1: Double, z0: Double, z1: Double, c: Color,
        gloss: Float = 0f, pat: FacePattern = FacePattern.NONE
    ) {
        val xa = px(u0, v0); val xb = px(u1, v1)
        val ya = py(u0, v0); val yb = py(u1, v1)
        Room3D.addBox(into, min(xa, xb), min(ya, yb), max(xa, xb), max(ya, yb), z0, z1, c, gloss, pat)
    }

    fun prism(cu: Double, cv: Double, ru: Double, rv: Double, z0: Double, z1: Double, c: Color, n: Int = 24, gloss: Float = 0f) =
        frustum(cu, cv, ru, rv, ru, rv, z0, z1, c, n, gloss)

    fun frustum(
        cu: Double, cv: Double, ru0: Double, rv0: Double, ru1: Double, rv1: Double,
        z0: Double, z1: Double, c: Color, n: Int = 24, gloss: Float = 0f,
        capTop: Boolean = true, flip: Boolean = false
    ) {
        val x = px(cu, cv); val y = py(cu, cv)
        if (swapped) Room3D.addFrustum(into, x, y, rv0, ru0, rv1, ru1, z0, z1, c, n, gloss, capTop, flip)
        else Room3D.addFrustum(into, x, y, ru0, rv0, ru1, rv1, z0, z1, c, n, gloss, capTop, flip)
    }

    fun blob(cu: Double, cv: Double, cz: Double, ru: Double, rv: Double, rz: Double, c: Color, rings: Int = 4, n: Int = 12) {
        val x = px(cu, cv); val y = py(cu, cv)
        if (swapped) Room3D.addBlob(into, x, y, cz, rv, ru, rz, c, rings, n)
        else Room3D.addBlob(into, x, y, cz, ru, rv, rz, c, rings, n)
    }

    /** Bright, unlit backdrop (sky seen through a window) facing into the room at depth v. */
    fun skyPanel(u0: Double, u1: Double, z0: Double, z1: Double, v: Double, c: Color) {
        val pts = listOf(
            world(px(u0, v), py(u0, v), z0), world(px(u1, v), py(u1, v), z0),
            world(px(u1, v), py(u1, v), z1), world(px(u0, v), py(u0, v), z1)
        )
        val nx = when (rot) { 90 -> -1.0; 270 -> 1.0; else -> 0.0 }
        val ny = when (rot) { 0 -> 1.0; 180 -> -1.0; else -> 0.0 }
        into += Face(pts, c, Vec3(nx, -ny, 0.0), emissive = true)
    }

    /** World point of item-local (u, v, z). */
    fun pt(u: Double, v: Double, z: Double): Vec3 = world(px(u, v), py(u, v), z)

    /**
     * Box whose footprint is a rectangle rotated in the (u, v) plane: starts at (u0, v0), runs [len] along
     * direction (du, dv) and [thick] along the perpendicular (-dv, du).
     */
    fun obox(u0: Double, v0: Double, du: Double, dv: Double, len: Double, thick: Double, z0: Double, z1: Double, c: Color, gloss: Float = 0f) {
        val o = pt(u0, v0, z0)
        val ex = pt(u0 + du * len, v0 + dv * len, z0) - o
        val ey = pt(u0 - dv * thick, v0 + du * thick, z0) - o
        Room3D.addOrientedBox(into, o, ex, ey, Vec3(0.0, 0.0, z1 - z0), c, gloss)
    }

    /** Four square legs at the corners of the rectangle. */
    fun legs(u0: Double, v0: Double, u1: Double, v1: Double, t: Double, z1: Double, c: Color, z0: Double = 0.0) {
        box(u0, v0, u0 + t, v0 + t, z0, z1, c)
        box(u1 - t, v0, u1, v0 + t, z0, z1, c)
        box(u0, v1 - t, u0 + t, v1, z0, z1, c)
        box(u1 - t, v1 - t, u1, v1, z0, z1, c)
    }
}

object FurnitureBuilder {

    fun build(into: MutableList<Face>, f: PlacedFurniture, openAngle: Double = 0.0) {
        val item = Catalog.find(f.catalogId)
        val h = item?.heightM ?: 0.8
        val c = Color(f.colorArgb)
        val fr = Frame(f.bounds, f.rotationDeg, f.widthM, f.depthM, into)

        if (OpeningBuilder.build(fr, f.catalogId, c, openAngle)) return
        if (IndianFurniture.build(fr, f.catalogId, h, c)) return
        when (f.catalogId) {
            "sofa_2", "sofa_3" -> sofa(fr, h, c)
            "sofa_l" -> lSofa(fr, h, c)
            "armchair" -> sofa(fr, h, c, arm = true)
            "bed_single", "bed_double", "bed_queen", "bed_king" -> bed(fr, h, c)
            "coffee_table" -> coffeeTable(fr, h, c)
            "dining_table" -> diningTable(fr, h, c)
            "round_table" -> roundTable(fr, h, c)
            "side_table" -> sideTable(fr, h, c)
            "dining_chair" -> diningChair(fr, h, c)
            "office_chair" -> officeChair(fr, h, c)
            "stool" -> stool(fr, h, c)
            "bookshelf" -> bookshelf(fr, h, c)
            "shoe_rack" -> shoeRack(fr, h, c)
            "chest" -> chest(fr, h, c)
            "wardrobe" -> wardrobe(fr, h, c)
            "kitchen_cabinet" -> kitchenCabinet(fr, h, c)
            "sideboard" -> sideboard(fr, h, c)
            "tv_unit", "tv_unit_small", "tv_unit_wide" -> tvUnit(fr, h, c)
            "desk", "study_desk" -> desk(fr, h, c, laptop = f.catalogId == "desk")
            "l_desk" -> lDesk(fr, h, c)
            "floor_lamp" -> floorLamp(fr, h, c)
            "table_lamp" -> tableLamp(fr, h, c)
            "arc_lamp" -> arcLamp(fr, h, c)
            "plant_small" -> plant(fr, h, c, 3)
            "plant_medium" -> plant(fr, h, c, 6)
            "plant_large" -> plant(fr, h, c, 10)
            "rug" -> rug(fr, c)
            "fridge" -> fridge(fr, h, c)
            "washer" -> washer(fr, h, c)
            "bathtub" -> bathtub(fr, h, c)
            "toilet" -> toilet(fr, h, c)
            "vanity" -> vanity(fr, h, c)
            else -> fallback(fr, h, c, item?.category)
        }
    }

    /** Height used for the cast shadow (tall hanging pieces would otherwise throw absurdly long shadows). */
    fun shadowHeight(catalogId: String, h: Double): Double = when (catalogId) {
        "jhula" -> 0.9
        "arc_lamp" -> 1.4
        else -> min(h, 2.0)
    }

    // ---------------------------------------------------------------- seating

    private fun sofa(fr: Frame, h: Double, c: Color, arm: Boolean = false) {
        val w = fr.w; val d = fr.d
        val frame = c.dim(0.8f)
        val cushion = c
        val armW = min(0.16, w * 0.14)
        val backT = max(0.18, d * 0.22)
        fr.legs(0.05, 0.05, w - 0.05, d - 0.05, 0.05, 0.1, WOOD_DARK)
        fr.box(0.0, 0.0, w, d, 0.1, 0.4, frame, 0.04f)
        fr.box(0.0, 0.0, w, backT, 0.4, h, frame, 0.04f)
        fr.box(0.0, 0.0, armW, d, 0.4, 0.64, frame, 0.04f)
        fr.box(w - armW, 0.0, w, d, 0.4, 0.64, frame, 0.04f)
        val seats = if (arm) 1 else max(1, Math.round((w - 2 * armW) / 0.7).toInt())
        val sw = (w - 2 * armW) / seats
        for (i in 0 until seats) {
            val u0 = armW + sw * i + 0.01; val u1 = armW + sw * (i + 1) - 0.01
            fr.box(u0, backT, u1, d - 0.02, 0.4, 0.52, cushion, 0.06f)
            fr.box(u0 + 0.02, backT * 0.55, u1 - 0.02, backT + 0.16, 0.52, h - 0.07, cushion.dim(0.95f), 0.06f)
        }
        if (!arm && w > 1.4) {
            fr.box(armW + 0.05, backT + 0.14, armW + 0.25, backT + 0.26, 0.52, 0.76, c.mix(Color(0xFFD9A441), 0.55f).dim(0.95f), 0.05f)
        }
    }

    private fun lSofa(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val frame = c.dim(0.8f)
        val rowD = 0.9; val chaise = min(0.9, w * 0.4)
        val armW = 0.14; val backT = 0.2
        fr.legs(0.05, 0.05, w - 0.05, rowD - 0.05, 0.05, 0.1, WOOD_DARK)
        fr.legs(w - chaise + 0.05, rowD, w - 0.05, d - 0.05, 0.05, 0.1, WOOD_DARK)
        fr.box(0.0, 0.0, w, rowD, 0.1, 0.4, frame, 0.04f)
        fr.box(w - chaise, rowD, w, d, 0.1, 0.4, frame, 0.04f)
        fr.box(0.0, 0.0, w, backT, 0.4, h, frame, 0.04f)
        fr.box(0.0, 0.0, armW, rowD, 0.4, 0.64, frame, 0.04f)
        fr.box(w - armW, rowD, w, d, 0.4, 0.62, frame, 0.04f)
        val seatW = w - armW - chaise
        val seats = max(1, Math.round(seatW / 0.7).toInt())
        val sw = seatW / seats
        for (i in 0 until seats) {
            val u0 = armW + sw * i + 0.01; val u1 = armW + sw * (i + 1) - 0.01
            fr.box(u0, backT, u1, rowD - 0.02, 0.4, 0.52, c, 0.06f)
            fr.box(u0 + 0.02, backT * 0.55, u1 - 0.02, backT + 0.16, 0.52, h - 0.07, c.dim(0.95f), 0.06f)
        }
        fr.box(w - chaise + 0.01, backT, w - armW - 0.01, d - 0.02, 0.4, 0.52, c, 0.06f)
        fr.box(w - chaise + 0.03, backT * 0.55, w - armW - 0.03, backT + 0.16, 0.52, h - 0.07, c.dim(0.95f), 0.06f)
    }

    private fun diningChair(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val t = 0.035
        fr.legs(0.0, 0.0, w, d, t, 0.44, c.dim(0.7f))
        fr.box(0.0, 0.0, w, d, 0.42, 0.47, c, 0.18f)
        fr.box(0.03, 0.06, w - 0.03, d - 0.03, 0.47, 0.51, c.mix(Color(0xFFB5483C), 0.45f), 0.05f)
        fr.box(0.0, 0.0, t, t + 0.01, 0.47, h, c.dim(0.7f))
        fr.box(w - t, 0.0, w, t + 0.01, 0.47, h, c.dim(0.7f))
        fr.box(0.0, 0.0, w, 0.04, h - 0.1, h, c.dim(0.75f), 0.15f)
        for (i in 1..3) {
            val u = w * i / 4.0
            fr.box(u - 0.015, 0.005, u + 0.015, 0.03, 0.52, h - 0.1, c.dim(0.8f))
        }
    }

    private fun officeChair(fr: Frame, h: Double, c: Color) {
        val cu = fr.w / 2; val cv = fr.d / 2
        val ru = fr.w / 2; val rv = fr.d / 2
        fr.prism(cu, cv, ru * 0.85, rv * 0.85, 0.05, 0.09, Color(0xFF2B2B2B), 5, 0.3f)
        fr.prism(cu, cv, ru * 0.08, rv * 0.08, 0.09, 0.42, STEEL, 10, 0.6f)
        fr.prism(cu, cv, ru * 0.92, rv * 0.92, 0.42, 0.52, c, 20, 0.2f)
        fr.box(fr.w * 0.12, 0.0, fr.w * 0.88, 0.09, 0.55, h, c.dim(0.85f), 0.2f)
        fr.box(fr.w * 0.44, 0.0, fr.w * 0.56, 0.09, 0.5, 0.56, STEEL)
        for (i in 0 until 5) {
            val a = 2 * Math.PI * i / 5 + 0.3
            fr.prism(cu + ru * 0.78 * cos(a), cv + rv * 0.78 * sin(a), 0.03, 0.03, 0.0, 0.05, NEAR_BLACK, 8)
        }
    }

    private fun stool(fr: Frame, h: Double, c: Color) {
        val cu = fr.w / 2; val cv = fr.d / 2
        fr.prism(cu, cv, fr.w / 2, fr.d / 2, h - 0.05, h, c, 20, 0.2f)
        for (i in 0 until 3) {
            val a = 2 * Math.PI * i / 3 + 0.5
            fr.prism(cu + fr.w * 0.3 * cos(a), cv + fr.d * 0.3 * sin(a), 0.022, 0.022, 0.0, h - 0.05, c.dim(0.7f), 8)
        }
    }

    private fun bed(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val wood = Color(0xFF6B4F36)
        fr.box(0.0, 0.0, w, d, 0.08, 0.3, wood.mix(c, 0.1f), 0.2f)
        fr.legs(0.0, 0.0, w, d, 0.06, 0.1, wood.dim(0.7f))
        fr.box(0.02, 0.04, w - 0.02, d - 0.02, 0.3, h - 0.02, OFF_WHITE.mix(c, 0.05f), 0.02f)
        // duvet
        fr.box(0.0, d * 0.34, w, d - 0.01, 0.3, h + 0.04, c, 0.05f)
        fr.box(0.0, d * 0.34, w, d * 0.34 + 0.16, h + 0.0, h + 0.07, c.mix(Color.White, 0.3f), 0.05f)
        // throw blanket across the foot
        fr.box(-0.01, d * 0.78, w + 0.01, d * 0.93, 0.3, h + 0.065, c.dim(0.72f), 0.03f)
        // pillows
        val n = if (w > 1.2) 2 else 1
        for (i in 0 until n) {
            val cu = w * (i + 0.5) / n
            fr.blob(cu, 0.3, h + 0.06, (w / n) * 0.38, 0.2, 0.09, OFF_WHITE, 4, 12)
        }
        // headboard & footboard
        fr.box(0.0, 0.0, w, 0.07, 0.0, h + 0.38, wood, 0.25f)
        fr.box(0.04, 0.0, w - 0.04, 0.075, h + 0.05, h + 0.34, wood.mix(c, 0.3f), 0.1f)
        fr.box(0.0, d - 0.05, w, d, 0.0, 0.36, wood, 0.25f)
    }

    // ---------------------------------------------------------------- tables & desks

    private fun coffeeTable(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, h - 0.045, h, c, 0.3f, FacePattern.GRAIN)
        fr.box(0.04, 0.04, w - 0.04, d - 0.04, h - 0.1, h - 0.045, c.dim(0.7f))
        fr.legs(0.03, 0.03, w - 0.03, d - 0.03, 0.05, h - 0.045, c.dim(0.62f))
        fr.box(0.08, 0.08, w - 0.08, d - 0.08, 0.1, 0.13, c.dim(0.75f), 0.15f)
    }

    private fun diningTable(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, h - 0.045, h, c, 0.3f, FacePattern.GRAIN)
        fr.box(0.08, 0.08, w - 0.08, d - 0.08, h - 0.13, h - 0.045, c.dim(0.7f))
        fr.legs(0.05, 0.05, w - 0.05, d - 0.05, 0.075, h - 0.045, c.dim(0.62f))
    }

    private fun roundTable(fr: Frame, h: Double, c: Color) {
        val cu = fr.w / 2; val cv = fr.d / 2
        fr.prism(cu, cv, fr.w / 2, fr.d / 2, h - 0.04, h, c, 28, 0.3f)
        fr.frustum(cu, cv, fr.w * 0.14, fr.d * 0.14, fr.w * 0.07, fr.d * 0.07, 0.06, h - 0.04, c.dim(0.65f), 12, 0.2f)
        fr.frustum(cu, cv, fr.w * 0.3, fr.d * 0.3, fr.w * 0.14, fr.d * 0.14, 0.0, 0.06, c.dim(0.6f), 20, 0.2f)
    }

    private fun sideTable(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, h - 0.035, h, c, 0.3f, FacePattern.GRAIN)
        fr.box(0.03, 0.03, w - 0.03, d - 0.03, h * 0.42, h * 0.46, c.dim(0.8f))
        fr.legs(0.02, 0.02, w - 0.02, d - 0.02, 0.04, h - 0.035, c.dim(0.62f))
        fr.box(0.08, d - 0.045, w - 0.08, d - 0.02, h - 0.17, h - 0.055, c.dim(0.85f), 0.2f)
        fr.box(w / 2 - 0.03, d - 0.02, w / 2 + 0.03, d, h - 0.12, h - 0.1, BRASS, 0.7f)
    }

    private fun desk(fr: Frame, h: Double, c: Color, laptop: Boolean) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, h - 0.035, h, c, 0.3f, FacePattern.GRAIN)
        val ped = min(0.4, w * 0.35)
        fr.box(w - ped, 0.02, w - 0.02, d - 0.02, 0.0, h - 0.035, c.dim(0.8f), 0.12f)
        for (i in 0 until 3) {
            val z0 = 0.05 + i * (h - 0.15) / 3
            fr.box(w - ped + 0.02, d - 0.025, w - 0.04, d - 0.01, z0 + 0.02, z0 + (h - 0.15) / 3 - 0.02, c.dim(0.9f), 0.2f)
            fr.box(w - ped / 2 - 0.04, d - 0.01, w - ped / 2 + 0.04, d, z0 + (h - 0.15) / 6, z0 + (h - 0.15) / 6 + 0.015, BRASS, 0.7f)
        }
        fr.box(0.02, 0.02, 0.06, d - 0.02, 0.0, h - 0.035, c.dim(0.7f))
        fr.box(0.02, 0.02, w - ped, 0.05, 0.3, h - 0.035, c.dim(0.7f))
        if (laptop) {
            val lu = w * 0.3
            fr.box(lu, d * 0.35, lu + 0.34, d * 0.35 + 0.23, h, h + 0.015, Color(0xFFA9ADB2), 0.5f)
            fr.box(lu, d * 0.35, lu + 0.34, d * 0.35 + 0.01, h + 0.015, h + 0.23, Color(0xFF9A9EA3), 0.5f)
            fr.box(lu + 0.015, d * 0.35 + 0.01, lu + 0.325, d * 0.35 + 0.014, h + 0.03, h + 0.215, Color(0xFF1B2733), 0.9f)
        }
    }

    private fun lDesk(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val t = 0.6
        fr.box(0.0, 0.0, w, t, h - 0.035, h, c, 0.3f, FacePattern.GRAIN)
        fr.box(0.0, t, t, d, h - 0.035, h, c, 0.3f, FacePattern.GRAIN)
        fr.legs(0.02, 0.02, w - 0.02, t - 0.02, 0.05, h - 0.035, c.dim(0.62f))
        fr.legs(0.02, t, t - 0.02, d - 0.02, 0.05, h - 0.035, c.dim(0.62f))
        fr.box(w * 0.55, 0.15, w * 0.55 + 0.4, 0.17, h + 0.12, h + 0.45, NEAR_BLACK, 0.8f)
        fr.box(w * 0.55 + 0.17, 0.12, w * 0.55 + 0.23, 0.18, h, h + 0.12, STEEL, 0.5f)
    }

    // ---------------------------------------------------------------- storage

    private fun bookshelf(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val t = 0.025
        fr.box(0.0, 0.0, w, 0.012, 0.0, h, c.dim(0.7f))
        fr.box(0.0, 0.0, t, d, 0.0, h, c, 0.2f)
        fr.box(w - t, 0.0, w, d, 0.0, h, c, 0.2f)
        fr.box(0.0, 0.0, w, d, h - t, h, c, 0.25f, FacePattern.GRAIN)
        val shelves = max(3, (h / 0.36).toInt())
        val gap = (h - t) / shelves
        for (s in 0 until shelves) {
            val z = s * gap
            fr.box(t, 0.0, w - t, d, z, z + t, c, 0.2f)
            if (s == 0) continue
            var u = t + 0.02
            var i = 0
            while (u < w - t - 0.05) {
                val bw = 0.025 + rnd(i, s) * 0.03
                val bh = (gap - t) * (0.62 + 0.33 * rnd(i, s + 9))
                if (rnd(i, s + 3) > 0.17) {
                    val col = Color(
                        red = 0.19f + 0.69f * rnd(i, s + 5),
                        green = 0.19f + 0.63f * rnd(i, s + 6),
                        blue = 0.19f + 0.69f * rnd(i, s + 7)
                    )
                    fr.box(u, 0.04, min(u + bw, w - t - 0.01), d - 0.03, z + t, z + t + bh, col, 0.15f)
                }
                u += bw + 0.002; i++
            }
        }
    }

    private fun shoeRack(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, 0.025, d, 0.0, h, c, 0.15f)
        fr.box(w - 0.025, 0.0, w, d, 0.0, h, c, 0.15f)
        fr.box(0.0, 0.0, w, 0.01, 0.0, h, c.dim(0.7f))
        fr.box(0.0, 0.0, w, d, h - 0.025, h, c, 0.25f, FacePattern.GRAIN)
        for (s in 0 until 3) {
            val z = 0.06 + s * (h - 0.1) / 3
            fr.box(0.02, 0.0, w - 0.02, d, z, z + 0.02, c.dim(0.9f), 0.15f)
            if (s > 0) {
                var u = 0.08
                var i = 0
                while (u < w - 0.2) {
                    fr.box(u, 0.07, u + 0.1, d - 0.03, z + 0.02, z + 0.02 + 0.07 + 0.03 * rnd(i, s), Color(0xFF2B2B2B).mix(c, 0.15f * rnd(i, 4)), 0.3f)
                    u += 0.15; i++
                }
            }
        }
    }

    private fun chest(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, 0.04, h - 0.1, c, 0.2f)
        fr.box(-0.01, -0.01, w + 0.01, d + 0.01, h - 0.1, h, c.dim(0.92f), 0.3f, FacePattern.GRAIN)
        fr.legs(0.02, 0.02, w - 0.02, d - 0.02, 0.05, 0.05, WOOD_DARK)
        fr.box(w / 2 - 0.04, d, w / 2 + 0.04, d + 0.012, h - 0.2, h - 0.08, BRASS, 0.7f)
    }

    private fun wardrobe(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.02, 0.02, w - 0.02, d - 0.02, 0.0, 0.07, c.dim(0.55f))
        fr.box(0.0, 0.0, w, d, 0.07, h - 0.06, c, 0.14f)
        fr.box(-0.015, -0.015, w + 0.015, d + 0.01, h - 0.06, h, c.dim(0.9f), 0.2f, FacePattern.GRAIN)
        val half = w / 2
        for (i in 0..1) {
            val u0 = i * half + 0.015; val u1 = (i + 1) * half - 0.015
            fr.box(u0, d - 0.012, u1, d + 0.006, 0.1, h - 0.09, c.dim(0.93f), 0.22f)
            fr.box(u0 + 0.05, d + 0.006, u1 - 0.05, d + 0.012, 0.2, h - 0.2, c.dim(0.86f), 0.25f)
        }
        fr.box(half - 0.005, d - 0.012, half + 0.005, d + 0.008, 0.1, h - 0.09, c.dim(0.5f))
        fr.box(half - 0.07, d + 0.006, half - 0.05, d + 0.03, h * 0.45, h * 0.45 + 0.18, BRASS, 0.8f)
        fr.box(half + 0.05, d + 0.006, half + 0.07, d + 0.03, h * 0.45, h * 0.45 + 0.18, BRASS, 0.8f)
    }

    private fun kitchenCabinet(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.02, 0.0, w - 0.02, d - 0.05, 0.0, 0.08, Color(0xFF2B2B2B))
        fr.box(0.0, 0.0, w, d, 0.08, h - 0.04, c, 0.2f)
        fr.box(-0.005, -0.005, w + 0.005, d + 0.025, h - 0.04, h, Color(0xFF8B8F93), 0.5f)
        fr.box(0.02, d - 0.01, w - 0.02, d + 0.01, 0.1, h - 0.07, c.dim(0.94f), 0.3f)
        fr.box(w / 2 - 0.1, d + 0.01, w / 2 + 0.1, d + 0.03, h - 0.15, h - 0.13, STEEL, 0.8f)
    }

    private fun sideboard(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.legs(0.04, 0.04, w - 0.04, d - 0.04, 0.05, 0.14, c.dim(0.55f))
        fr.box(0.0, 0.0, w, d, 0.14, h - 0.035, c, 0.2f)
        fr.box(-0.01, -0.01, w + 0.01, d + 0.01, h - 0.035, h, c.dim(0.92f), 0.3f, FacePattern.GRAIN)
        val n = 3
        for (i in 0 until n) {
            val u0 = w * i / n + 0.02; val u1 = w * (i + 1) / n - 0.02
            fr.box(u0, d - 0.01, u1, d + 0.008, 0.17, h - 0.06, c.dim(0.9f), 0.25f)
            fr.box((u0 + u1) / 2 - 0.03, d + 0.008, (u0 + u1) / 2 + 0.03, d + 0.02, h * 0.62, h * 0.62 + 0.02, BRASS, 0.8f)
        }
    }

    private fun tvUnit(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.legs(0.04, 0.04, w - 0.04, d - 0.04, 0.045, 0.1, WOOD_DARK)
        fr.box(0.0, 0.0, w, d, 0.1, h - 0.03, c, 0.25f)
        fr.box(-0.01, -0.01, w + 0.01, d + 0.01, h - 0.03, h, c.dim(0.92f), 0.35f, FacePattern.GRAIN)
        val n = if (w > 1.4) 3 else 2
        for (i in 0 until n) {
            val u0 = w * i / n + 0.02; val u1 = w * (i + 1) / n - 0.02
            fr.box(u0, d - 0.012, u1, d + 0.006, 0.13, h - 0.05, c.dim(0.88f), 0.3f)
            fr.box((u0 + u1) / 2 - 0.04, d + 0.006, (u0 + u1) / 2 + 0.04, d + 0.016, h * 0.7, h * 0.7 + 0.015, BRASS, 0.8f)
        }
        // television: stand, bezel and glossy screen
        val su0 = w * 0.14; val su1 = w * 0.86
        fr.box(w / 2 - 0.14, d * 0.2, w / 2 + 0.14, d * 0.5, h, h + 0.02, NEAR_BLACK, 0.4f)
        fr.box(w / 2 - 0.03, d * 0.3, w / 2 + 0.03, d * 0.4, h + 0.02, h + 0.1, NEAR_BLACK, 0.4f)
        fr.box(su0, d * 0.28, su1, d * 0.28 + 0.035, h + 0.08, h + 0.08 + (su1 - su0) * 0.56, NEAR_BLACK, 0.5f)
        fr.box(su0 + 0.015, d * 0.28 + 0.035, su1 - 0.015, d * 0.28 + 0.038, h + 0.095, h + 0.065 + (su1 - su0) * 0.56, Color(0xFF16222E), 0.95f)
    }

    // ---------------------------------------------------------------- lamps & plants

    private fun floorLamp(fr: Frame, h: Double, c: Color) {
        val cu = fr.w / 2; val cv = fr.d / 2; val r = fr.w / 2
        fr.prism(cu, cv, r * 0.55, r * 0.55, 0.0, 0.03, Color(0xFF2E2E2E), 20, 0.4f)
        fr.prism(cu, cv, 0.014, 0.014, 0.03, h * 0.78, BRASS, 8, 0.7f)
        fr.frustum(cu, cv, r * 0.62, r * 0.62, r * 0.42, r * 0.42, h * 0.76, h, Color(0xFFF2E6BE).mix(c, 0.2f), 24, 0f)
        fr.prism(cu, cv, r * 0.38, r * 0.38, h - 0.002, h + 0.003, Color(0xFFFFF6D0), 20)
    }

    private fun tableLamp(fr: Frame, h: Double, c: Color) {
        val cu = fr.w / 2; val cv = fr.d / 2; val r = fr.w / 2
        fr.frustum(cu, cv, r * 0.5, r * 0.5, r * 0.3, r * 0.3, 0.0, h * 0.4, c.dim(0.7f), 16, 0.6f)
        fr.prism(cu, cv, 0.012, 0.012, h * 0.4, h * 0.55, BRASS, 8, 0.6f)
        fr.frustum(cu, cv, r * 0.95, r * 0.95, r * 0.55, r * 0.55, h * 0.5, h, Color(0xFFF7EBC6), 20, 0f)
        fr.prism(cu, cv, r * 0.5, r * 0.5, h - 0.002, h + 0.003, Color(0xFFFFF6D0), 16)
    }

    private fun arcLamp(fr: Frame, h: Double, c: Color) {
        val cu = fr.w * 0.3; val cv = fr.d / 2
        fr.prism(cu, cv, fr.w * 0.2, fr.d * 0.2, 0.0, 0.05, NEAR_BLACK, 20, 0.4f)
        fr.prism(cu, cv, 0.015, 0.015, 0.05, h * 0.95, c, 8, 0.7f)
        fr.box(cu - 0.015, cv - 0.015, fr.w * 0.8, cv + 0.015, h * 0.93, h * 0.95, c, 0.7f)
        fr.frustum(fr.w * 0.8, cv, fr.w * 0.17, fr.d * 0.17, fr.w * 0.1, fr.d * 0.1, h * 0.8, h * 0.93, Color(0xFFF2E6BE), 20)
    }

    private fun plant(fr: Frame, h: Double, c: Color, blobs: Int) {
        val cu = fr.w / 2; val cv = fr.d / 2; val r = fr.w / 2
        val potH = h * 0.28
        fr.frustum(cu, cv, r * 0.6, r * 0.6, r * 0.78, r * 0.78, 0.0, potH, Color(0xFFB5653A), 20, 0.1f)
        fr.prism(cu, cv, r * 0.8, r * 0.8, potH - 0.025, potH, Color(0xFFA2562E), 20)
        fr.prism(cu, cv, r * 0.7, r * 0.7, potH, potH + 0.005, Color(0xFF3B2A1E), 16)
        fr.prism(cu, cv, 0.012 + r * 0.04, 0.012 + r * 0.04, potH, h * 0.6, Color(0xFF5A4630), 8)
        for (i in 0 until blobs) {
            val a = 2 * Math.PI * i / blobs + rnd(i, 1)
            val rad = if (blobs <= 3) r * 0.25 else r * (0.2 + 0.5 * rnd(i, 2))
            val z = potH + (h - potH) * (0.35 + 0.55 * (i.toDouble() / blobs) + 0.1 * rnd(i, 3))
            val g = c.mix(Color(0xFFB9C75A), 0.2f * rnd(i, 4)).dim(0.8f + 0.35f * rnd(i, 5))
            val s = r * (0.45 + 0.3 * rnd(i, 6))
            fr.blob(cu + rad * cos(a), cv + rad * sin(a), z.coerceAtMost(h - s * 0.9), s, s, s * 1.05, g, 4, 10)
        }
        fr.blob(cu, cv, h - r * 0.45, r * 0.5, r * 0.5, r * 0.5, c.dim(1.05f), 4, 10)
    }

    // ---------------------------------------------------------------- floor coverings & appliances

    private fun rug(fr: Frame, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, 0.0, 0.018, c.dim(0.72f))
        fr.box(0.06, 0.06, w - 0.06, d - 0.06, 0.018, 0.021, c)
        fr.box(0.18, 0.18, w - 0.18, d - 0.18, 0.021, 0.023, c.mix(Color.White, 0.18f))
        fr.box(0.26, 0.26, w - 0.26, d - 0.26, 0.023, 0.025, c.dim(0.9f))
    }

    private fun fridge(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.02, 0.02, w - 0.02, d - 0.02, 0.0, 0.05, Color(0xFF2B2B2B))
        fr.box(0.0, 0.0, w, d, 0.05, h, c, 0.4f)
        fr.box(0.0, d - 0.004, w, d + 0.012, h * 0.35, h * 0.355, Color(0xFF55585B))
        fr.box(w - 0.1, d + 0.012, w - 0.075, d + 0.045, h * 0.4, h * 0.62, STEEL, 0.8f)
        fr.box(w - 0.1, d + 0.012, w - 0.075, d + 0.045, h * 0.7, h * 0.9, STEEL, 0.8f)
    }

    private fun washer(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, 0.0, h, c, 0.4f)
        fr.box(0.0, d - 0.01, w, d + 0.004, h - 0.14, h - 0.01, Color(0xFFCFD2D6), 0.3f)
        fr.box(w - 0.18, d + 0.004, w - 0.06, d + 0.012, h - 0.11, h - 0.04, Color(0xFF2A3440), 0.7f)
        fr.box(w * 0.22, d - 0.01, w * 0.78, d + 0.012, 0.1, h - 0.2, Color(0xFF3B4A5A), 0.9f)
        fr.box(w * 0.3, d + 0.012, w * 0.7, d + 0.016, 0.16, h - 0.26, Color(0xFF1A2530), 0.95f)
    }

    private fun bathtub(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val t = 0.08
        fr.box(0.0, 0.0, w, t, 0.0, h, c, 0.5f)
        fr.box(0.0, d - t, w, d, 0.0, h, c, 0.5f)
        fr.box(0.0, t, t, d - t, 0.0, h, c, 0.5f)
        fr.box(w - t, t, w, d - t, 0.0, h, c, 0.5f)
        fr.box(t, t, w - t, d - t, 0.0, 0.2, Color(0xFFD3E4EA), 0.6f)
        fr.prism(w - 0.2, d / 2, 0.025, 0.025, 0.2, 0.202, STEEL, 10, 0.8f)
        fr.box(0.07, d / 2 - 0.02, 0.17, d / 2 + 0.02, h, h + 0.15, STEEL, 0.8f)
    }

    private fun toilet(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(w * 0.12, 0.0, w * 0.88, 0.2, 0.0, h, c, 0.55f)
        fr.box(w * 0.1, 0.0, w * 0.9, 0.2, h - 0.04, h, c.dim(0.97f), 0.6f)
        fr.frustum(w / 2, d * 0.6, w * 0.34, d * 0.3, w * 0.46, d * 0.34, 0.0, 0.4, c, 24, 0.55f)
        fr.prism(w / 2, d * 0.62, w * 0.46, d * 0.34, 0.4, 0.43, c.dim(0.97f), 24, 0.55f)
        fr.prism(w / 2, d * 0.62, w * 0.3, d * 0.24, 0.43, 0.432, Color(0xFFCDDDE3), 24, 0.6f)
        fr.box(w / 2 - 0.03, 0.02, w / 2 + 0.03, 0.06, h, h + 0.02, STEEL, 0.8f)
    }

    private fun vanity(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, 0.0, h - 0.04, c, 0.2f)
        fr.box(-0.01, -0.01, w + 0.01, d + 0.015, h - 0.04, h, OFF_WHITE, 0.6f)
        fr.prism(w / 2, d * 0.52, w * 0.28, d * 0.3, h, h + 0.012, OFF_WHITE, 24, 0.7f)
        fr.prism(w / 2, d * 0.52, w * 0.23, d * 0.25, h, h + 0.014, Color(0xFFC9D8DE), 24, 0.7f)
        fr.box(w / 2 - 0.015, 0.03, w / 2 + 0.015, 0.07, h, h + 0.17, STEEL, 0.8f)
        fr.box(w / 2 - 0.015, 0.03, w / 2 + 0.015, 0.2, h + 0.15, h + 0.17, STEEL, 0.8f)
        fr.box(0.04, d - 0.012, w - 0.04, d + 0.008, 0.06, h - 0.07, c.dim(0.93f), 0.25f)
    }

    private fun fallback(fr: Frame, h: Double, c: Color, cat: FurnitureCategory?) {
        val g = if (cat == FurnitureCategory.STORAGE || cat == FurnitureCategory.CABINETS) 0.2f else 0.08f
        fr.box(0.0, 0.0, fr.w, fr.d, 0.0, h, c, g)
    }
}
