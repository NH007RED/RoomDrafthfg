package com.nhdeveloper.roomdraft.rendering.room3d

import androidx.compose.ui.graphics.Color
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.dim
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.mix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 3D models of traditional Indian furniture. Returns true from [build] when the id is handled here. */
internal object IndianFurniture {
    private val ROPE = Color(0xFFD9B97A)
    private val MAROON = Color(0xFF9E2B25)
    private val MUSTARD = Color(0xFFD9A441)
    private val CREAM = Color(0xFFF1E4C8)
    private val CANE = Color(0xFFD7B87C)

    fun build(fr: Frame, id: String, h: Double, c: Color): Boolean {
        when (id) {
            "charpai" -> charpai(fr, h, c)
            "diwan" -> diwan(fr, h, c)
            "wood_sofa" -> woodSofa(fr, h, c)
            "jhula" -> jhula(fr, h, c)
            "peacock_chair" -> peacockChair(fr, h, c)
            "mandir" -> mandir(fr, h, c)
            "almirah" -> almirah(fr, h, c)
            "sandook" -> sandook(fr, h, c)
            "chowki" -> chowki(fr, h, c)
            "peedha" -> peedha(fr, h, c)
            "gadda" -> gadda(fr, h, c)
            "tulsi" -> tulsi(fr, h, c)
            "urli" -> urli(fr, h, c)
            "dhurrie" -> dhurrie(fr, c)
            else -> return false
        }
        return true
    }

    /** Rope bed: turned wooden legs, rails and a woven rope bed. */
    private fun charpai(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val rail = 0.07
        for ((u, v) in listOf(0.045 to 0.045, w - 0.045 to 0.045, 0.045 to d - 0.045, w - 0.045 to d - 0.045)) {
            fr.prism(u, v, 0.04, 0.04, 0.0, h - 0.04, c, 12, 0.25f)
            fr.prism(u, v, 0.052, 0.052, 0.16, 0.2, c.dim(0.8f), 12, 0.25f)
            fr.prism(u, v, 0.052, 0.052, h - 0.1, h - 0.06, c.dim(0.8f), 12, 0.25f)
        }
        fr.box(0.0, 0.0, w, rail, h - 0.1, h - 0.03, c, 0.3f, FacePattern.GRAIN)
        fr.box(0.0, d - rail, w, d, h - 0.1, h - 0.03, c, 0.3f, FacePattern.GRAIN)
        fr.box(0.0, rail, rail, d - rail, h - 0.1, h - 0.03, c, 0.3f, FacePattern.GRAIN)
        fr.box(w - rail, rail, w, d - rail, h - 0.1, h - 0.03, c, 0.3f, FacePattern.GRAIN)
        fr.box(rail, rail, w - rail, d - rail, h - 0.075, h - 0.05, ROPE, 0.05f, FacePattern.WEAVE)
        // pillow and a folded cotton sheet
        fr.blob(w / 2, 0.3, h - 0.04, w * 0.32, 0.17, 0.07, CREAM, 4, 12)
        fr.box(0.06, d * 0.55, w - 0.06, d - 0.08, h - 0.05, h - 0.02, MAROON.mix(c, 0.1f), 0.03f, FacePattern.STRIPES)
    }

    /** Low sofa-bed with a firm mattress, bolsters and back cushions. */
    private fun diwan(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val wood = Color(0xFF6B3F22)
        fr.legs(0.03, 0.03, w - 0.03, d - 0.03, 0.08, 0.1, wood.dim(0.8f))
        fr.box(0.0, 0.0, w, d, 0.1, 0.3, wood, 0.3f)
        for (i in 0 until 4) {
            val u0 = 0.1 + i * (w - 0.2) / 4
            fr.box(u0 + 0.03, d - 0.004, u0 + (w - 0.2) / 4 - 0.03, d + 0.01, 0.14, 0.26, wood.dim(0.8f), 0.3f)
        }
        fr.box(0.0, d - 0.004, w, d + 0.012, 0.285, 0.3, BRASS, 0.7f)
        fr.box(0.02, 0.02, w - 0.02, d - 0.02, 0.3, 0.45, c, 0.08f)
        fr.box(0.1, 0.1, w - 0.1, d - 0.1, 0.45, 0.452, c.mix(MUSTARD, 0.5f), 0.08f)
        // backrest cushions
        val n = max(3, Math.round(w / 0.55).toInt())
        val cw = (w - 0.4) / n
        for (i in 0 until n) {
            val u = 0.2 + cw * i
            fr.box(u + 0.01, 0.0, u + cw - 0.01, 0.25, 0.45, 0.72, c.dim(0.92f).mix(if (i % 2 == 0) MUSTARD else MAROON, 0.18f), 0.08f)
        }
        // end bolsters
        fr.box(0.0, 0.0, 0.2, d, 0.45, 0.62, c.dim(0.88f), 0.08f)
        fr.box(w - 0.2, 0.0, w, d, 0.45, 0.62, c.dim(0.88f), 0.08f)
        fr.box(0.2, 0.0, 0.208, d, 0.45, 0.455, BRASS)
    }

    /** Carved wooden sofa with cane back panels and cushions. */
    private fun woodSofa(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val frame = c
        val cushion = Color(0xFFE0CBA0)
        fr.box(0.0, 0.0, 0.07, 0.07, 0.0, 0.14, frame.dim(0.7f))
        fr.box(w - 0.07, 0.0, w, 0.07, 0.0, 0.14, frame.dim(0.7f))
        fr.box(0.0, d - 0.07, 0.07, d, 0.0, 0.14, frame.dim(0.7f))
        fr.box(w - 0.07, d - 0.07, w, d, 0.0, 0.14, frame.dim(0.7f))
        fr.box(0.0, 0.0, w, d, 0.14, 0.38, frame, 0.3f)
        fr.box(0.0, d - 0.004, w, d + 0.012, 0.3, 0.32, BRASS.mix(frame, 0.2f), 0.6f)
        // arms
        for (u in listOf(0.0, w - 0.1)) {
            fr.box(u, 0.0, u + 0.1, d, 0.38, 0.62, frame, 0.3f)
            fr.box(u - 0.015, 0.02, u + 0.115, d - 0.02, 0.62, 0.68, frame.dim(0.9f), 0.35f, FacePattern.GRAIN)
        }
        // back frame with cane panels and a carved crest rail
        fr.box(0.0, 0.0, w, 0.07, 0.38, 0.78, frame, 0.3f)
        val panels = max(3, Math.round(w / 0.55).toInt())
        val pw = (w - 0.2) / panels
        for (i in 0 until panels) {
            fr.box(0.1 + pw * i + 0.03, 0.02, 0.1 + pw * (i + 1) - 0.03, 0.075, 0.5, 0.74, CANE, 0.05f)
        }
        fr.box(0.0, 0.0, w, 0.08, 0.78, h - 0.07, frame.dim(0.95f), 0.35f, FacePattern.GRAIN)
        for (i in 0..6) {
            val u = w * (i + 0.5) / 7
            fr.box(u - 0.035, 0.0, u + 0.035, 0.085, h - 0.07, h, frame.dim(0.85f), 0.35f)
        }
        // cushions
        val seats = max(2, Math.round((w - 0.2) / 0.62).toInt())
        val sw = (w - 0.2) / seats
        for (i in 0 until seats) {
            val u = 0.1 + sw * i
            fr.box(u + 0.01, 0.1, u + sw - 0.01, d - 0.02, 0.38, 0.5, cushion, 0.05f)
            fr.box(u + 0.04, 0.09, u + sw - 0.04, 0.2, 0.5, 0.78, if (i % 2 == 0) MAROON else cushion.dim(0.95f), 0.05f)
        }
    }

    /** Hanging wooden swing: carved bench on four brass chains from a ceiling beam. */
    private fun jhula(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val seatZ = 0.45
        fr.box(0.0, 0.0, w, d, seatZ, seatZ + 0.06, c, 0.3f, FacePattern.GRAIN)
        fr.box(0.0, 0.0, w, 0.07, seatZ + 0.06, seatZ + 0.5, c, 0.3f)
        fr.box(0.0, 0.0, w, 0.08, seatZ + 0.5, seatZ + 0.56, c.dim(0.9f), 0.35f, FacePattern.GRAIN)
        for (i in 0..8) {
            val u = 0.12 + (w - 0.24) * i / 8
            fr.box(u - 0.015, 0.0, u + 0.015, 0.075, seatZ + 0.1, seatZ + 0.46, c.dim(0.8f), 0.3f)
        }
        fr.box(0.0, 0.0, 0.07, d, seatZ + 0.06, seatZ + 0.28, c, 0.3f)
        fr.box(w - 0.07, 0.0, w, d, seatZ + 0.06, seatZ + 0.28, c, 0.3f)
        fr.box(0.07, 0.08, w - 0.07, d - 0.02, seatZ + 0.06, seatZ + 0.14, MAROON, 0.06f)
        fr.box(0.2, 0.0, 0.45, 0.18, seatZ + 0.14, seatZ + 0.36, MUSTARD, 0.05f)
        // chains, top beam
        for ((u, v) in listOf(0.035 to 0.03, w - 0.035 to 0.03, 0.035 to d - 0.03, w - 0.035 to d - 0.03)) {
            fr.box(u - 0.008, v - 0.008, u + 0.008, v + 0.008, seatZ + 0.28, h, BRASS, 0.7f)
        }
        fr.box(-0.05, d * 0.5 - 0.04, w + 0.05, d * 0.5 + 0.04, h - 0.06, h, c.dim(0.75f), 0.3f, FacePattern.GRAIN)
        fr.box(0.035 - 0.02, 0.0, 0.035 + 0.02, d, h - 0.06, h - 0.03, c.dim(0.75f), 0.3f)
        fr.box(w - 0.035 - 0.02, 0.0, w - 0.035 + 0.02, d, h - 0.06, h - 0.03, c.dim(0.75f), 0.3f)
    }

    /** Wicker "peacock" chair: flared basket seat with a tall fan-shaped back. */
    private fun peacockChair(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val cu = w / 2; val cv = d * 0.58
        fr.frustum(cu, cv, w * 0.12, d * 0.12, w * 0.3, d * 0.3, 0.0, 0.4, c.dim(0.75f), 18, 0.12f)
        fr.prism(cu, cv, w * 0.3, d * 0.3, 0.4, 0.44, c.dim(0.9f), 22, 0.12f)
        fr.prism(cu, cv, w * 0.26, d * 0.26, 0.44, 0.5, MAROON, 22, 0.05f)
        val cols = 15
        for (i in 0 until cols) {
            val a = (PI * (0.08 + 0.84 * i / (cols - 1)))
            val rr = 0.4
            val u = cu + w * rr * cos(a)
            val v = cv - d * 0.28 * sin(a) * 1.15
            val top = 0.62 + (h - 0.62) * sin(a).let { it * it }
            fr.prism(u, v, 0.045, 0.045, 0.3, top, if (i % 2 == 0) c else c.dim(0.82f), 8, 0.15f)
        }
    }

    /** Wooden home temple: cabinet base, open shrine with pillars and a tiered shikhara roof. */
    private fun mandir(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        val baseH = h * 0.44
        fr.box(0.0, 0.0, w, d, 0.0, 0.05, c.dim(0.6f))
        fr.box(0.0, 0.0, w, d, 0.05, baseH, c, 0.25f)
        for (i in 0..1) {
            val u0 = i * w / 2 + 0.03; val u1 = (i + 1) * w / 2 - 0.03
            fr.box(u0, d - 0.008, u1, d + 0.008, 0.1, baseH - 0.05, c.dim(0.88f), 0.3f)
            fr.box(u0 + 0.06, d + 0.008, u1 - 0.06, d + 0.014, 0.17, baseH - 0.12, c.dim(0.7f), 0.3f)
            fr.box((u0 + u1) / 2 - 0.02 + (if (i == 0) 0.07 else -0.07), d + 0.008, (u0 + u1) / 2 + 0.02 + (if (i == 0) 0.07 else -0.07), d + 0.03, baseH * 0.5, baseH * 0.5 + 0.03, BRASS, 0.8f)
        }
        // counter slab
        fr.box(-0.02, -0.02, w + 0.02, d + 0.03, baseH, baseH + 0.04, c.dim(0.92f), 0.3f, FacePattern.GRAIN)
        val shelfTop = baseH + 0.04
        val topZ = h * 0.84
        // shrine: back wall, side pillars, ceiling slab
        fr.box(0.04, 0.0, w - 0.04, 0.04, shelfTop, topZ, MUSTARD.mix(c, 0.35f), 0.3f)
        fr.box(0.0, 0.0, 0.07, d, shelfTop, topZ, c, 0.3f)
        fr.box(w - 0.07, 0.0, w, d, shelfTop, topZ, c, 0.3f)
        for (u in listOf(0.075, w - 0.135)) fr.prism(u + 0.03, d - 0.05, 0.03, 0.03, shelfTop, topZ, c.dim(0.85f), 10, 0.3f)
        // arch lintel
        fr.box(0.0, d - 0.1, w, d, topZ - 0.1, topZ, c.dim(0.9f), 0.3f)
        for (i in 0..4) {
            val u = 0.12 + (w - 0.24) * i / 4
            fr.prism(u, d - 0.05, 0.035, 0.03, topZ - 0.17, topZ - 0.1, BRASS, 8, 0.7f)
        }
        // deity pedestal, idol, diya and bell
        fr.box(w / 2 - 0.2, 0.1, w / 2 + 0.2, d - 0.14, shelfTop, shelfTop + 0.07, MAROON, 0.1f)
        fr.prism(w / 2, 0.2, 0.05, 0.05, shelfTop + 0.07, shelfTop + 0.14, BRASS, 12, 0.8f)
        fr.blob(w / 2, 0.2, shelfTop + 0.2, 0.06, 0.05, 0.07, BRASS, 4, 10)
        fr.prism(w / 2 - 0.14, d - 0.2, 0.03, 0.03, shelfTop + 0.07, shelfTop + 0.1, BRASS, 10, 0.8f)
        fr.prism(w / 2 - 0.14, d - 0.2, 0.012, 0.012, shelfTop + 0.1, shelfTop + 0.13, Color(0xFFFFB347), 8)
        fr.prism(w / 2 + 0.14, d - 0.2, 0.03, 0.03, shelfTop + 0.07, shelfTop + 0.1, BRASS, 10, 0.8f)
        fr.prism(w / 2 + 0.14, d - 0.2, 0.012, 0.012, shelfTop + 0.1, shelfTop + 0.13, Color(0xFFFFB347), 8)
        // roof: slab, tiered shikhara, kalash
        fr.box(-0.04, -0.03, w + 0.04, d + 0.04, topZ, topZ + 0.05, c.dim(0.85f), 0.35f, FacePattern.GRAIN)
        val cu = w / 2; val cv = d / 2
        fr.frustum(cu, cv, w * 0.46, d * 0.46, w * 0.36, d * 0.36, topZ + 0.05, topZ + 0.14, c, 4, 0.3f)
        fr.frustum(cu, cv, w * 0.34, d * 0.34, w * 0.2, d * 0.2, topZ + 0.14, topZ + 0.25, c.dim(0.95f), 4, 0.3f)
        fr.frustum(cu, cv, w * 0.18, d * 0.18, w * 0.06, d * 0.06, topZ + 0.25, topZ + 0.34, MUSTARD.mix(c, 0.3f), 14, 0.5f)
        fr.prism(cu, cv, 0.025, 0.025, topZ + 0.34, topZ + 0.4, BRASS, 10, 0.8f)
        fr.frustum(cu, cv, 0.03, 0.03, 0.0, 0.0, topZ + 0.4, min(h, topZ + 0.46), BRASS, 10, 0.8f)
    }

    /** Steel almirah: two louvred doors, brass-steel handles and a recessed plinth. */
    private fun almirah(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.03, 0.03, w - 0.03, d - 0.03, 0.0, 0.08, Color(0xFF3A3F44), 0.3f)
        fr.box(0.0, 0.0, w, d, 0.08, h, c, 0.4f)
        val half = w / 2
        for (i in 0..1) {
            val u0 = i * half + 0.012; val u1 = (i + 1) * half - 0.012
            fr.box(u0, d - 0.01, u1, d + 0.008, 0.11, h - 0.04, c.dim(1.05f), 0.5f)
            fr.box(u0 + 0.05, d + 0.008, u1 - 0.05, d + 0.014, h * 0.62, h - 0.12, c.dim(0.8f), 0.4f)
            fr.box(u0 + 0.05, d + 0.008, u1 - 0.05, d + 0.014, 0.2, h * 0.58, c.dim(0.9f), 0.4f)
            // louvre slats on the upper panel
            for (k in 0 until 6) {
                val z = h * 0.64 + k * (h * 0.36 - 0.2) / 6
                fr.box(u0 + 0.07, d + 0.014, u1 - 0.07, d + 0.02, z, z + 0.012, c.dim(0.62f))
            }
        }
        fr.box(half - 0.006, d - 0.01, half + 0.006, d + 0.01, 0.11, h - 0.04, c.dim(0.45f))
        fr.box(half - 0.06, d + 0.008, half - 0.045, d + 0.035, h * 0.46, h * 0.46 + 0.16, STEEL, 0.9f)
        fr.box(half + 0.045, d + 0.008, half + 0.06, d + 0.035, h * 0.46, h * 0.46 + 0.16, STEEL, 0.9f)
        fr.box(0.0, 0.0, w, d, h - 0.02, h, c.dim(0.9f), 0.5f)
    }

    /** Wooden trunk with brass corners, straps and a lock plate. */
    private fun sandook(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.legs(0.02, 0.02, w - 0.02, d - 0.02, 0.06, 0.05, c.dim(0.55f))
        fr.box(0.0, 0.0, w, d, 0.05, h - 0.12, c, 0.25f)
        fr.box(-0.012, -0.012, w + 0.012, d + 0.012, h - 0.12, h, c.dim(0.9f), 0.35f, FacePattern.GRAIN)
        for (u in listOf(-0.012, w - 0.03)) for (v in listOf(-0.012, d - 0.03)) {
            fr.box(u, v, u + 0.042, v + 0.042, 0.05, h, BRASS, 0.75f)
        }
        for (i in 1..2) {
            val u = w * i / 3
            fr.box(u - 0.025, -0.014, u + 0.025, d + 0.014, h - 0.121, h + 0.004, BRASS.dim(0.9f), 0.7f)
        }
        fr.box(w / 2 - 0.04, d, w / 2 + 0.04, d + 0.016, h - 0.2, h - 0.06, BRASS, 0.85f)
        fr.box(w / 2 - 0.012, d + 0.016, w / 2 + 0.012, d + 0.02, h - 0.17, h - 0.12, NEAR_BLACK)
    }

    /** Low wooden platform table used for seating, puja or serving. */
    private fun chowki(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, h - 0.05, h, c, 0.3f, FacePattern.GRAIN)
        fr.box(0.04, 0.04, w - 0.04, d - 0.04, h - 0.05, h - 0.045, c.dim(0.75f))
        fr.box(0.03, 0.03, w - 0.03, d - 0.03, h - 0.095, h - 0.05, c.dim(0.8f))
        fr.legs(0.03, 0.03, w - 0.03, d - 0.03, 0.055, h - 0.095, c.dim(0.65f))
        fr.box(w * 0.4, d * 0.4, w * 0.6, d * 0.6, h, h + 0.002, BRASS.mix(c, 0.2f), 0.6f)
    }

    /** Small round wooden stool (peedha). */
    private fun peedha(fr: Frame, h: Double, c: Color) {
        val cu = fr.w / 2; val cv = fr.d / 2
        fr.prism(cu, cv, fr.w / 2, fr.d / 2, h - 0.045, h, c, 22, 0.3f)
        fr.prism(cu, cv, fr.w * 0.46, fr.d * 0.46, h - 0.055, h - 0.045, c.dim(0.7f), 22)
        for (i in 0 until 4) {
            val a = 2 * PI * i / 4 + PI / 4
            fr.prism(cu + fr.w * 0.3 * cos(a), cv + fr.d * 0.3 * sin(a), 0.026, 0.026, 0.0, h - 0.055, c.dim(0.65f), 10, 0.2f)
        }
        fr.prism(cu, cv, fr.w * 0.34, fr.d * 0.34, 0.07, 0.085, c.dim(0.75f), 16)
    }

    /** Floor mattress with white sheet, bolsters (takiya) and cushions. */
    private fun gadda(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, 0.0, 0.1, c.dim(0.95f), 0.04f)
        fr.box(0.01, 0.01, w - 0.01, d - 0.01, 0.1, 0.12, c, 0.05f)
        fr.box(0.0, 0.0, w, d, 0.06, 0.075, MAROON.mix(c, 0.2f))
        fr.box(0.0, d * 0.45, w, d * 0.55, 0.12, 0.123, MUSTARD.mix(c, 0.4f))
        // back bolsters and end bolsters
        val n = max(2, Math.round(w / 0.7).toInt())
        val bw = (w - 0.1) / n
        for (i in 0 until n) {
            fr.box(0.05 + bw * i + 0.02, 0.02, 0.05 + bw * (i + 1) - 0.02, 0.24, 0.12, 0.38, if (i % 2 == 0) MAROON else MUSTARD.dim(0.92f), 0.07f)
            fr.box(0.05 + bw * i + 0.02, 0.0, 0.05 + bw * (i + 1) - 0.02, 0.025, 0.14, 0.34, BRASS, 0.5f)
        }
        fr.blob(0.22, d * 0.62, 0.17, 0.14, 0.13, 0.05, MAROON.dim(0.9f), 4, 12)
        fr.blob(w - 0.22, d * 0.62, 0.17, 0.14, 0.13, 0.05, MUSTARD, 4, 12)
    }

    /** Tulsi Vrindavan: square planter pillar with a niche, plinth, crown and a tulsi plant. */
    private fun tulsi(fr: Frame, h: Double, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, 0.0, 0.1, c.dim(0.7f), 0.1f)
        fr.box(0.04, 0.04, w - 0.04, d - 0.04, 0.1, 0.18, c.dim(0.85f), 0.1f)
        fr.box(0.08, 0.08, w - 0.08, d - 0.08, 0.18, 0.62, c, 0.15f)
        fr.box(w * 0.3, d - 0.08 - 0.01, w * 0.7, d - 0.07, 0.26, 0.52, Color(0xFF3A1F12))
        fr.box(w * 0.27, d - 0.082, w * 0.3, d - 0.07, 0.24, 0.54, c.dim(1.1f), 0.2f)
        fr.box(w * 0.7, d - 0.082, w * 0.73, d - 0.07, 0.24, 0.54, c.dim(1.1f), 0.2f)
        fr.prism(w / 2, d - 0.18, 0.025, 0.025, 0.26, 0.29, BRASS, 8, 0.8f)
        fr.box(0.03, 0.03, w - 0.03, d - 0.03, 0.62, 0.7, c.dim(0.88f), 0.15f)
        fr.box(0.06, 0.06, w - 0.06, d - 0.06, 0.7, 0.84, c.dim(1.0f), 0.15f)
        fr.box(0.1, 0.1, w - 0.1, d - 0.1, 0.84, 0.845, Color(0xFF3B2A1E))
        for ((u, v) in listOf(0.06 to 0.06, w - 0.06 to 0.06, 0.06 to d - 0.06, w - 0.06 to d - 0.06)) {
            fr.prism(u, v, 0.03, 0.03, 0.7, 0.9, c.dim(0.8f), 10, 0.2f)
        }
        fr.prism(w / 2, d / 2, 0.012, 0.012, 0.84, 0.95, Color(0xFF5A4630), 6)
        val green = Color(0xFF3E8F4E)
        for (i in 0 until 7) {
            val a = 2 * PI * i / 7
            val z = 0.9 + (h - 0.95) * (0.2 + 0.7 * (i % 3) / 2.0)
            fr.blob(w / 2 + 0.08 * cos(a), d / 2 + 0.08 * sin(a), min(z, h - 0.06), 0.085, 0.085, 0.07, green.dim(0.85f + 0.3f * rnd(i, 2)), 3, 8)
        }
        fr.blob(w / 2, d / 2, h - 0.08, 0.1, 0.1, 0.08, green.dim(1.1f), 3, 8)
    }

    /** Shallow brass bowl with water, floating marigolds and a lit diya. */
    private fun urli(fr: Frame, h: Double, c: Color) {
        val cu = fr.w / 2; val cv = fr.d / 2; val r = fr.w / 2
        fr.frustum(cu, cv, r * 0.45, r * 0.45, r * 0.58, r * 0.58, 0.0, 0.03, c.dim(0.8f), 22, 0.7f)
        fr.frustum(cu, cv, r * 0.58, r * 0.58, r, r, 0.03, h, c, 28, 0.8f, capTop = false)
        fr.frustum(cu, cv, r * 0.92, r * 0.92, r * 0.54, r * 0.54, 0.04, h - 0.005, c.dim(0.7f), 28, 0.6f, capTop = false, flip = true)
        fr.prism(cu, cv, r * 0.55, r * 0.55, 0.12, 0.125, Color(0xFF2F6F73), 28, 0.9f)
        for (i in 0 until 9) {
            val a = 2 * PI * i / 9 + rnd(i, 7)
            val rad = r * (0.2 + 0.45 * rnd(i, 3))
            val col = when (i % 3) { 0 -> Color(0xFFF08A1F); 1 -> Color(0xFFF2B51B); else -> Color(0xFFD94A2B) }
            fr.blob(cu + rad * cos(a), cv + rad * sin(a), 0.135, 0.045, 0.045, 0.025, col, 3, 8)
        }
        fr.prism(cu, cv, 0.04, 0.04, 0.125, 0.15, BRASS, 10, 0.8f)
        fr.prism(cu, cv, 0.013, 0.013, 0.15, 0.18, Color(0xFFFFB347), 8)
    }

    /** Flat-woven cotton rug with bands of colour and fringed ends. */
    private fun dhurrie(fr: Frame, c: Color) {
        val w = fr.w; val d = fr.d
        fr.box(0.0, 0.0, w, d, 0.0, 0.02, c, 0.02f, FacePattern.STRIPES)
        fr.box(0.0, 0.0, w, 0.04, 0.0, 0.021, CREAM)
        fr.box(0.0, d - 0.04, w, d, 0.0, 0.021, CREAM)
        val tassels = max(8, (w / 0.06).toInt())
        for (i in 0 until tassels) {
            val u = (i + 0.5) * w / tassels
            fr.box(u - 0.006, -0.025, u + 0.006, 0.0, 0.0, 0.008, CREAM.dim(0.95f))
            fr.box(u - 0.006, d, u + 0.006, d + 0.025, 0.0, 0.008, CREAM.dim(0.95f))
        }
    }
}
