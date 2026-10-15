package com.nhdeveloper.roomdraft.rendering.story

import androidx.compose.ui.graphics.Color
import com.nhdeveloper.roomdraft.rendering.room3d.ActorFaces
import com.nhdeveloper.roomdraft.rendering.room3d.Face
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.dim
import com.nhdeveloper.roomdraft.rendering.room3d.ShadowSpot
import com.nhdeveloper.roomdraft.rendering.room3d.Vec3
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything the renderer needs to draw the character for one instant, in world coordinates
 * (X right, Y = minus plan-y, Z up). "Right" is the character's own right: facing x up.
 */
data class Pose(
    /** Hip joint centre. */
    val pelvis: Vec3,
    /** Unit vector pelvis -> neck (the spine). */
    val up: Vec3,
    /** Unit vector the chest points to. */
    val facing: Vec3,
    /** Direction the face looks at (clamped to what a neck can do). */
    val look: Vec3,
    val footL: Vec3, val footR: Vec3,
    val toeL: Vec3, val toeR: Vec3,
    val handL: Vec3, val handR: Vec3,
    /** Preferred bend direction of the knees. */
    val kneeHint: Vec3,
    val eyesClosed: Double = 0.0,
    /** Phase of the hair / dupatta sway (radians). */
    val sway: Double = 0.0
) {
    companion object {
        private fun mix(a: Vec3, b: Vec3, t: Double) = a + (b - a) * t
        private fun dir(a: Vec3, b: Vec3, t: Double): Vec3 = mix(a, b, t).let { if (it.length() < 1e-6) a else it.normalized() }

        fun lerp(a: Pose, b: Pose, t: Double): Pose = Pose(
            mix(a.pelvis, b.pelvis, t), dir(a.up, b.up, t), dir(a.facing, b.facing, t), dir(a.look, b.look, t),
            mix(a.footL, b.footL, t), mix(a.footR, b.footR, t), dir(a.toeL, b.toeL, t), dir(a.toeR, b.toeR, t),
            mix(a.handL, b.handL, t), mix(a.handR, b.handR, t), dir(a.kneeHint, b.kneeHint, t),
            a.eyesClosed + (b.eyesClosed - a.eyesClosed) * t, a.sway + (b.sway - a.sway) * t
        )
    }
}

/** Body proportions of a ~1.58 m woman. */
object Body {
    const val THIGH = 0.40
    const val SHIN = 0.40
    const val ANKLE = 0.07
    const val UPPER_ARM = 0.27
    const val FOREARM = 0.25
    const val HIP_HALF = 0.09
    const val SHOULDER_HALF = 0.165
    const val TORSO = 0.47
    /** Pelvis height when standing straight. */
    const val STAND_HIP = ANKLE + THIGH + SHIN - 0.01
}

object ActorRenderer {
    private val SKIN = Color(0xFFC68E63)
    private val SKIN_DARK = Color(0xFFB57C54)
    private val HAIR = Color(0xFF1B1210)
    private val KURTI = Color(0xFF1E7F8C)
    private val KURTI_TRIM = Color(0xFFE8B84A)
    private val LEGGINGS = Color(0xFFEFE6D2)
    private val DUPATTA = Color(0xFFD4A02E)
    private val GOLD = Color(0xFFD9B04A)
    private val LIP = Color(0xFFB5384A)
    private val BINDI = Color(0xFFC0172B)
    private val SOLE = Color(0xFF5A3A24)
    private val WHITE = Color(0xFFF2EEE8)
    private const val B = 0.06
    private const val FB = 0.14

    private val Z = Vec3(0.0, 0.0, 1.0)

    private fun lerp(a: Vec3, b: Vec3, t: Double) = a + (b - a) * t

    /** Two-bone IK: the middle joint for a chain root -> end with bone lengths [l1], [l2], bending towards [hint]. */
    fun ik(root: Vec3, end: Vec3, l1: Double, l2: Double, hint: Vec3): Vec3 {
        val delta = end - root
        val d = delta.length().coerceIn(0.05, l1 + l2 - 1e-3)
        val dir = if (delta.length() < 1e-6) Vec3(0.0, 0.0, -1.0) else delta.normalized()
        val x = (l1 * l1 - l2 * l2 + d * d) / (2.0 * d)
        val h = sqrt((l1 * l1 - x * x).coerceAtLeast(0.0))
        var perp = hint - dir * (hint dot dir)
        if (perp.length() < 1e-6) perp = Z - dir * (Z dot dir)
        return root + dir * x + perp.normalized() * h
    }

    /** Look direction limited to what a neck can do relative to the chest. */
    private fun clampLook(look: Vec3, right: Vec3, fwd: Vec3, up: Vec3): Vec3 {
        val l = look.normalized()
        val yaw = atan2(l dot right, l dot fwd).coerceIn(-1.25, 1.25)
        val pitch = asin((l dot up).coerceIn(-1.0, 1.0)).coerceIn(-0.65, 0.6)
        return (right * (sin(yaw) * cos(pitch)) + fwd * (cos(yaw) * cos(pitch)) + up * sin(pitch)).normalized()
    }

    fun build(p: Pose): ActorFaces {
        val f = ArrayList<Face>(900)
        val up = p.up.normalized()
        val fwd = (p.facing - up * (p.facing dot up)).normalized()
        val right = fwd cross up

        // ---- joints
        val hipR = p.pelvis + right * Body.HIP_HALF
        val hipL = p.pelvis - right * Body.HIP_HALF
        val neck = p.pelvis + up * Body.TORSO
        val shoulderMid = p.pelvis + up * (Body.TORSO - 0.04)
        val shR = shoulderMid + right * Body.SHOULDER_HALF
        val shL = shoulderMid - right * Body.SHOULDER_HALF

        // ---- legs
        for (side in 0..1) {
            val hip = if (side == 0) hipR else hipL
            val ankle = if (side == 0) p.footR else p.footL
            val toe = (if (side == 0) p.toeR else p.toeL).normalized()
            val knee = ik(hip, ankle, Body.THIGH, Body.SHIN, p.kneeHint)
            Room3D.addTube(f, hip, lerp(hip, knee, 0.9), 0.092, 0.078, KURTI, 8, 0.06f, bias = B)
            Room3D.addTube(f, lerp(hip, knee, 0.8), knee, 0.074, 0.054, LEGGINGS, 8, 0.05f, bias = B)
            Room3D.addTube(f, lerp(hip, knee, 0.86), lerp(hip, knee, 0.92), 0.08, 0.08, KURTI_TRIM, 8, 0.2f, caps = false, bias = B + 0.01)
            Room3D.addEllipsoid(f, knee, Vec3(0.056, 0.0, 0.0), Vec3(0.0, 0.0, 0.056), Vec3(0.0, 0.056, 0.0), LEGGINGS, 3, 8, bias = B)
            Room3D.addTube(f, knee, ankle, 0.053, 0.034, LEGGINGS, 8, 0.05f, bias = B)
            // foot with sandal sole
            val side3 = (toe cross Z).let { if (it.length() < 1e-6) right else it.normalized() }
            val centre = ankle + toe * 0.045 + Vec3(0.0, 0.0, -0.036)
            Room3D.addEllipsoid(f, centre, side3 * 0.04, Z * 0.032, toe * 0.105, SKIN, 3, 8, bias = B)
            Room3D.addEllipsoid(f, ankle + toe * 0.05 + Vec3(0.0, 0.0, -0.066), side3 * 0.043, Z * 0.008, toe * 0.118, SOLE, 2, 8, bias = B)
        }

        // ---- torso (kurti)
        fun ell(c: Vec3, rw: Double, rh: Double, rd: Double, col: Color, g: Float = 0.06f) =
            Room3D.addEllipsoid(f, c, right * rw, up * rh, fwd * rd, col, 4, 10, g, B)
        ell(p.pelvis + up * 0.03, 0.178, 0.105, 0.108, KURTI)
        ell(p.pelvis + up * 0.19, 0.145, 0.10, 0.092, KURTI)
        ell(p.pelvis + up * 0.335, 0.168, 0.125, 0.104, KURTI)
        Room3D.addTube(f, shL, shR, 0.045, 0.045, KURTI, 8, 0.06f, bias = B)
        // embroidered neckline / hem band
        Room3D.addEllipsoid(f, p.pelvis + up * 0.43 + fwd * 0.04, right * 0.07, up * 0.035, fwd * 0.07, KURTI_TRIM, 3, 8, 0.2f, B + 0.02)

        // ---- neck and head
        val look = clampLook(p.look, right, fwd, up)
        val hu = (up - look * (up dot look)).let { if (it.length() < 1e-6) up else it.normalized() }
        val hr = look cross hu
        val neckTop = neck + up * 0.04
        Room3D.addTube(f, neck - up * 0.04, neckTop, 0.036, 0.032, SKIN, 8, bias = B)
        val hc = neckTop + hu * 0.088 - look * 0.004
        Room3D.addEllipsoid(f, hc, hr * 0.078, hu * 0.098, look * 0.088, SKIN, 4, 10, 0.08f, B)
        Room3D.addEllipsoid(f, hc - look * 0.022 + hu * 0.012, hr * 0.083, hu * 0.101, look * 0.083, HAIR, 4, 10, 0.35f, B + 0.02)
        // face
        fun feature(off: Vec3, ax: Vec3, ay: Vec3, az: Vec3, col: Color, g: Float = 0f) =
            Room3D.addEllipsoid(f, hc + off, ax, ay, az, col, 2, 6, g, FB)
        val closed = p.eyesClosed > 0.5
        for (s in listOf(-1.0, 1.0)) {
            val eye = hr * (0.032 * s) + hu * 0.012 + look * 0.0785
            if (closed) {
                feature(eye, hr * 0.012, hu * 0.0025, look * 0.003, HAIR)
            } else {
                feature(eye, hr * 0.012, hu * 0.0085, look * 0.004, WHITE, 0.1f)
                feature(eye + look * 0.003, hr * 0.0062, hu * 0.0062, look * 0.003, Color(0xFF2A160E), 0.5f)
            }
            feature(hr * (0.032 * s) + hu * (if (closed) 0.034 else 0.036) + look * 0.0695, hr * 0.016, hu * 0.0045, look * 0.004, HAIR)
            // gold ear stud
            feature(hr * (0.077 * s) - hu * 0.025, hr * 0.008, hu * 0.009, look * 0.008, GOLD, 0.8f)
        }
        feature(-hu * 0.012 + look * 0.088, hr * 0.011, hu * 0.015, look * 0.012, SKIN_DARK)
        feature(-hu * 0.046 + look * 0.0765, hr * 0.02, hu * 0.0065, look * 0.006, LIP, 0.3f)
        feature(hu * 0.045 + look * 0.0795, hr * 0.0055, hu * 0.0055, look * 0.003, BINDI, 0.3f)

        // ---- braid (only when upright), swaying a little
        if (up.z > 0.5) {
            var prev = neck - fwd * 0.07
            for (k in 0 until 7) {
                val lateral = sin(p.sway + k * 0.55) * 0.012 * (k + 1) / 3.0
                val pos = neck - fwd * (0.105 + 0.003 * k) - up * (0.025 + 0.062 * k) + right * lateral
                Room3D.addTube(f, prev, pos, 0.03 - 0.0016 * k, 0.03 - 0.0016 * (k + 1), HAIR, 6, 0.3f, bias = B)
                prev = pos
            }
            Room3D.addEllipsoid(f, prev - up * 0.02, right * 0.012, up * 0.02, fwd * 0.012, BINDI, 2, 6, 0.2f, FB)
        }

        // ---- arms
        for (side in 0..1) {
            val sh = if (side == 0) shR else shL
            val hand = if (side == 0) p.handR else p.handL
            val sgn = if (side == 0) 1.0 else -1.0
            val hint = (right * (0.7 * sgn) - fwd * 0.35 - up * 0.6).normalized()
            val elbow = ik(sh, hand, Body.UPPER_ARM, Body.FOREARM, hint)
            Room3D.addEllipsoid(f, sh, right * 0.05, up * 0.05, fwd * 0.05, KURTI, 3, 8, 0.06f, B)
            Room3D.addTube(f, sh, lerp(sh, elbow, 0.72), 0.047, 0.041, KURTI, 8, 0.06f, bias = B)
            Room3D.addTube(f, lerp(sh, elbow, 0.70), lerp(sh, elbow, 0.76), 0.043, 0.043, KURTI_TRIM, 8, 0.2f, caps = false, bias = B + 0.01)
            Room3D.addTube(f, lerp(sh, elbow, 0.72), elbow, 0.036, 0.031, SKIN, 8, bias = B)
            Room3D.addEllipsoid(f, elbow, Vec3(0.031, 0.0, 0.0), Vec3(0.0, 0.0, 0.031), Vec3(0.0, 0.031, 0.0), SKIN, 3, 8, bias = B)
            Room3D.addTube(f, elbow, hand, 0.031, 0.022, SKIN, 8, bias = B)
            val fd = (hand - elbow).normalized()
            val palmSide = (fd cross Z).let { if (it.length() < 1e-6) right else it.normalized() }
            Room3D.addEllipsoid(f, hand + fd * 0.045, palmSide * 0.03, (palmSide cross fd).normalized() * 0.014, fd * 0.052, SKIN, 3, 8, bias = B)
            Room3D.addTube(f, hand - fd * 0.045, hand - fd * 0.01, 0.027, 0.027, GOLD, 8, 0.8f, caps = false, bias = B + 0.01)
        }

        // ---- dupatta draped across the chest and down the back
        val fL = shL + up * 0.015 + fwd * 0.02
        val mid = p.pelvis + up * 0.30 + fwd * 0.125
        val wR = p.pelvis + right * 0.16 + up * 0.04 + fwd * 0.06
        Room3D.addTube(f, fL, mid, 0.022, 0.026, DUPATTA, 8, 0.1f, bias = B + 0.02)
        Room3D.addTube(f, mid, wR, 0.026, 0.022, DUPATTA, 8, 0.1f, bias = B + 0.02)
        val sway = sin(p.sway * 1.3) * 0.02
        val tailTop = shR + up * 0.01 - fwd * 0.08
        val tailMid = p.pelvis + up * 0.2 - fwd * 0.12 + right * (0.15 + sway)
        val tailEnd = p.pelvis + up * 0.02 - fwd * 0.13 + right * (0.14 + 2 * sway)
        Room3D.addTube(f, tailTop, tailMid, 0.022, 0.026, DUPATTA, 6, 0.1f, bias = B + 0.02)
        Room3D.addTube(f, tailMid, tailEnd, 0.026, 0.03, DUPATTA, 6, 0.1f, bias = B + 0.02)

        val shadows = listOf(
            ShadowSpot(p.pelvis.x, -p.pelvis.y, p.pelvis.z, 0.24, 0.2, 0.75f),
            ShadowSpot(p.pelvis.x + up.x * 0.35, -(p.pelvis.y + up.y * 0.35), p.pelvis.z + up.z * 0.35, 0.19, 0.14, 0.6f),
            ShadowSpot(hc.x, -hc.y, hc.z, 0.1, 0.1, 0.5f),
            ShadowSpot(p.footL.x, -p.footL.y, 0.06, 0.1, 0.06, 0.8f),
            ShadowSpot(p.footR.x, -p.footR.y, 0.06, 0.1, 0.06, 0.8f)
        )
        return ActorFaces(f, shadows)
    }
}
