package com.nhdeveloper.roomdraft.rendering.room3d

import androidx.compose.ui.graphics.Color
import com.nhdeveloper.roomdraft.domain.model.Openings
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.dim
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.mix
import kotlin.math.max

/**
 * Doors and windows. Frame coordinates: u runs along the wall, v = 0 is the INNER face of the wall,
 * negative v goes into / through the wall (wall thickness = [Openings.WALL_THICKNESS]).
 */
internal object OpeningBuilder {
    private val GLASS = Color(0xFFBFE3F2).copy(alpha = 0.32f)
    private val SKY = Color(0xFFCBE6F6)
    private val STONE = Color(0xFFD9D4CA)
    private val IRON = Color(0xFF2B2B2B)
    private const val T = Openings.WALL_THICKNESS

    fun build(fr: Frame, id: String, c: Color, openAngle: Double = 0.0): Boolean {
        val sill = Openings.sill(id)
        val top = Openings.top(id)
        when (id) {
            "door_main" -> door(fr, c, top, main = true, openAngle = openAngle)
            "door_room" -> door(fr, c, top, main = false, openAngle = openAngle)
            "door_sliding" -> slidingDoor(fr, c, top)
            "window" -> window(fr, c, sill, top, grill = false, curtain = Color(0xFFB5483C))
            "window_large" -> window(fr, c, sill, top, grill = false, curtain = Color(0xFF5E8C7B))
            "window_grill" -> window(fr, c, sill, top, grill = true, curtain = Color(0xFFD9A441))
            "ventilator" -> ventilator(fr, c, sill, top)
            else -> return false
        }
        return true
    }

    /** Trim around the inner face of the opening. */
    private fun architrave(fr: Frame, c: Color, z0: Double, z1: Double) {
        val w = fr.w
        val a = 0.05
        val trim = c.dim(1.03f)
        fr.box(-a, 0.0, 0.0, 0.02, z0, z1 + a, trim, 0.3f)
        fr.box(w, 0.0, w + a, 0.02, z0, z1 + a, trim, 0.3f)
        fr.box(-a, 0.0, w + a, 0.02, z1, z1 + a, trim, 0.3f)
        if (z0 > 0.01) fr.box(-a, 0.0, w + a, 0.02, z0 - a, z0, trim, 0.3f)
    }

    private fun door(fr: Frame, c: Color, top: Double, main: Boolean, openAngle: Double = 0.0) {
        val w = fr.w
        val frame = c.dim(0.8f)
        val post = 0.06
        // frame lining the opening
        fr.box(0.0, -T, post, 0.0, 0.0, top, frame, 0.3f)
        fr.box(w - post, -T, w, 0.0, 0.0, top, frame, 0.3f)
        fr.box(0.0, -T, w, 0.0, top - post, top, frame, 0.3f)
        architrave(fr, frame, 0.0, top)
        fr.box(0.0, -T, w, 0.06, 0.0, 0.015, Color(0xFF8D8A84), 0.4f)
        // leaf
        val zt = top - post
        if (openAngle > 0.001) {
            openLeaf(fr, c, post, zt, openAngle, main)
            return
        }
        fr.box(post, -0.075, w - post, -0.035, 0.0, zt, c, 0.3f)
        val cols = 2
        val rows = if (main) 4 else 3
        val iw = (w - 2 * post - 0.2) / cols
        val ih = (zt - 0.3) / rows
        for (i in 0 until cols) for (j in 0 until rows) {
            val u0 = post + 0.08 + i * (iw + 0.04)
            val z0 = 0.12 + j * ih
            fr.box(u0, -0.035, u0 + iw - 0.02, -0.018, z0 + 0.02, z0 + ih - 0.04, c.dim(0.9f), 0.35f)
        }
        // handle, lock plate, hinges
        fr.box(w - post - 0.09, -0.035, w - post - 0.06, 0.0, 0.9, 1.15, BRASS, 0.8f)
        fr.box(w - post - 0.2, 0.0, w - post - 0.06, 0.035, 1.0, 1.03, BRASS, 0.85f)
        if (main) {
            fr.box(w / 2 - 0.1, -0.035, w / 2 + 0.1, -0.025, zt - 0.28, zt - 0.2, BRASS, 0.8f)
            fr.prism(w / 2, -0.03, 0.012, 0.01, 1.55, 1.57, NEAR_BLACK, 8, 0.9f)
            fr.box(post + 0.0, -0.035, post + 0.012, 0.0, 0.0, 0.12, BRASS, 0.7f)
        }
        for (z in listOf(0.25, 1.0, zt - 0.3)) fr.box(post - 0.004, -0.04, post + 0.02, -0.02, z, z + 0.1, BRASS, 0.7f)
    }

    /** Leaf swung into the room by [angle] radians around its hinge post. */
    private fun openLeaf(fr: Frame, c: Color, post: Double, zt: Double, angle: Double, main: Boolean) {
        val du = kotlin.math.cos(angle); val dv = kotlin.math.sin(angle)
        val len = fr.w - 2 * post
        fun part(a0: Double, a1: Double, o0: Double, o1: Double, z0: Double, z1: Double, col: Color, gloss: Float) {
            // origin on the leaf's hinge-side outer corner, shifted along the leaf by a0 and across it by o0
            val u0 = post + du * a0 - dv * o0
            val v0 = -0.075 + dv * a0 + du * o0
            fr.obox(u0, v0, du, dv, a1 - a0, o1 - o0, z0, z1, col, gloss)
        }
        part(0.0, len, 0.0, 0.04, 0.0, zt, c, 0.3f)
        val rows = if (main) 4 else 3
        val ih = (zt - 0.3) / rows
        val iw = (len - 0.2) / 2
        for (i in 0 until 2) for (j in 0 until rows) {
            val a0 = 0.08 + i * (iw + 0.04)
            val z0 = 0.12 + j * ih
            part(a0, a0 + iw - 0.02, 0.04, 0.054, z0 + 0.02, z0 + ih - 0.04, c.dim(0.9f), 0.35f)
            part(a0, a0 + iw - 0.02, -0.014, 0.0, z0 + 0.02, z0 + ih - 0.04, c.dim(0.9f), 0.35f)
        }
        part(len - 0.12, len - 0.06, -0.03, 0.07, 0.9, 1.15, BRASS, 0.8f)
        part(len - 0.2, len - 0.04, 0.04, 0.075, 1.0, 1.03, BRASS, 0.85f)
        part(len - 0.2, len - 0.04, -0.035, 0.0, 1.0, 1.03, BRASS, 0.85f)
        for (z in listOf(0.25, 1.0, zt - 0.3)) part(0.0, 0.03, -0.02, 0.06, z, z + 0.1, BRASS, 0.7f)
    }

    private fun slidingDoor(fr: Frame, c: Color, top: Double) {
        val w = fr.w
        val fw = 0.05
        fr.box(0.0, -T, fw, 0.0, 0.0, top, c, 0.5f)
        fr.box(w - fw, -T, w, 0.0, 0.0, top, c, 0.5f)
        fr.box(0.0, -T, w, 0.0, top - fw, top, c, 0.5f)
        fr.box(0.0, -T, w, 0.03, 0.0, 0.03, c.dim(0.8f), 0.5f)
        architrave(fr, c.dim(0.95f), 0.0, top)
        fr.skyPanel(fw, w - fw, 0.03, top - fw, -T + 0.004, SKY)
        // two overlapping sashes
        val half = (w - 2 * fw) / 2 + 0.03
        val sashes = listOf(fw to -0.055, (w - fw - half) to -0.03)
        for ((u0, v0) in sashes) {
            val u1 = u0 + half
            val t = 0.035
            fr.box(u0, v0, u0 + t, v0 + 0.02, 0.04, top - fw - 0.01, c.dim(0.9f), 0.6f)
            fr.box(u1 - t, v0, u1, v0 + 0.02, 0.04, top - fw - 0.01, c.dim(0.9f), 0.6f)
            fr.box(u0, v0, u1, v0 + 0.02, 0.04, 0.04 + t, c.dim(0.9f), 0.6f)
            fr.box(u0, v0, u1, v0 + 0.02, top - fw - 0.01 - t, top - fw - 0.01, c.dim(0.9f), 0.6f)
            fr.box(u0 + t, v0 + 0.007, u1 - t, v0 + 0.013, 0.04 + t, top - fw - 0.01 - t, GLASS, 0.9f)
        }
        fr.box(w / 2 - 0.02, -0.03, w / 2 - 0.005, 0.0, 0.9, 1.25, STEEL, 0.9f)
    }

    private fun curtains(fr: Frame, sill: Double, top: Double, color: Color) {
        val w = fr.w
        val over = 0.16
        val rodZ = top + 0.12
        fr.box(-over, 0.065, w + over, 0.08, rodZ, rodZ + 0.015, BRASS, 0.8f)
        fr.prism(-over, 0.0725, 0.02, 0.02, rodZ - 0.01, rodZ + 0.025, BRASS, 8, 0.8f)
        fr.prism(w + over, 0.0725, 0.02, 0.02, rodZ - 0.01, rodZ + 0.025, BRASS, 8, 0.8f)
        val panel = max(0.3, w * 0.26)
        for (side in 0..1) {
            val n = (panel / 0.065).toInt().coerceAtLeast(4)
            val sw = panel / n
            val start = if (side == 0) -0.12 else w + 0.12 - panel
            for (i in 0 until n) {
                val fold = i % 2 == 0
                val v0 = if (fold) 0.07 else 0.095
                val col = color.dim(if (fold) 1.0f else 0.84f)
                fr.box(start + i * sw, v0, start + (i + 1) * sw, v0 + 0.035, 0.03, rodZ, col, 0.04f)
            }
        }
        // tie-backs
        fr.box(-0.12, 0.065, -0.12 + panel, 0.14, sill * 0.55 + 0.35, sill * 0.55 + 0.38, color.dim(0.6f))
        fr.box(w + 0.12 - panel, 0.065, w + 0.12, 0.14, sill * 0.55 + 0.35, sill * 0.55 + 0.38, color.dim(0.6f))
    }

    private fun window(fr: Frame, c: Color, sill: Double, top: Double, grill: Boolean, curtain: Color) {
        val w = fr.w
        val fw = 0.05
        // frame
        fr.box(0.0, -T + 0.02, fw, -0.01, sill, top, c, 0.4f)
        fr.box(w - fw, -T + 0.02, w, -0.01, sill, top, c, 0.4f)
        fr.box(0.0, -T + 0.02, w, -0.01, top - fw, top, c, 0.4f)
        fr.box(0.0, -T + 0.02, w, -0.01, sill, sill + fw, c, 0.4f)
        architrave(fr, c, sill, top)
        fr.box(-0.05, 0.0, w + 0.05, 0.075, sill - 0.035, sill, STONE, 0.45f)
        fr.skyPanel(fw, w - fw, sill + fw, top - fw, -T + 0.025, SKY)
        // glass and mullions
        fr.box(fw, -0.062, w - fw, -0.052, sill + fw, top - fw, GLASS, 0.9f)
        val panes = if (w > 1.5) 3 else if (w > 0.9) 2 else 1
        for (i in 1 until panes) {
            val u = fw + (w - 2 * fw) * i / panes
            fr.box(u - 0.018, -0.07, u + 0.018, -0.03, sill + fw, top - fw, c.dim(0.95f), 0.4f)
        }
        val tz = sill + (top - sill) * 0.72
        fr.box(fw, -0.07, w - fw, -0.03, tz - 0.015, tz + 0.015, c.dim(0.95f), 0.4f)
        // grill on the outside of the opening
        if (grill) {
            val n = (w / 0.1).toInt()
            for (i in 1 until n) {
                val u = w * i / n
                fr.box(u - 0.007, -T - 0.012, u + 0.007, -T + 0.01, sill, top, IRON, 0.35f)
            }
            for (k in 1..2) {
                val z = sill + (top - sill) * k / 3.0
                fr.box(0.0, -T - 0.016, w, -T + 0.012, z - 0.01, z + 0.01, IRON, 0.35f)
            }
            fr.box(0.0, -T - 0.016, w, -T + 0.012, sill, sill + 0.02, IRON, 0.35f)
            fr.box(0.0, -T - 0.016, w, -T + 0.012, top - 0.02, top, IRON, 0.35f)
        } else {
            curtains(fr, sill, top, curtain)
        }
    }

    private fun ventilator(fr: Frame, c: Color, sill: Double, top: Double) {
        val w = fr.w
        val fw = 0.04
        fr.box(0.0, -T + 0.02, fw, -0.01, sill, top, c, 0.4f)
        fr.box(w - fw, -T + 0.02, w, -0.01, sill, top, c, 0.4f)
        fr.box(0.0, -T + 0.02, w, -0.01, top - fw, top, c, 0.4f)
        fr.box(0.0, -T + 0.02, w, -0.01, sill, sill + fw, c, 0.4f)
        architrave(fr, c, sill, top)
        fr.skyPanel(fw, w - fw, sill + fw, top - fw, -T + 0.025, SKY)
        val n = 5
        for (i in 0 until n) {
            val z = sill + fw + (top - sill - 2 * fw) * (i + 0.5) / n
            fr.box(fw, -0.07, w - fw, -0.03, z - 0.012, z + 0.012, GLASS.mix(Color.White, 0.3f).copy(alpha = 0.7f), 0.9f)
        }
        fr.box(-0.04, 0.0, w + 0.04, 0.045, sill - 0.025, sill, STONE, 0.4f)
    }
}
