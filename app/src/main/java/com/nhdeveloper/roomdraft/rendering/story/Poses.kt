package com.nhdeveloper.roomdraft.rendering.story

import com.nhdeveloper.roomdraft.rendering.room3d.Vec3
import com.nhdeveloper.roomdraft.rendering.room3d.world
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Ready-made body poses. Positions are given in plan coordinates; headings are plan angles (atan2(dy, dx)). */
internal object Poses {
    private val Z = Vec3(0.0, 0.0, 1.0)
    const val STRIDE = 1.08
    private const val A = 0.27

    fun dir(heading: Double) = Vec3(cos(heading), -sin(heading), 0.0)
    fun p(pt: Pt, z: Double) = world(pt.x, pt.y, z)

    fun smooth(t: Double): Double { val c = t.coerceIn(0.0, 1.0); return c * c * (3.0 - 2.0 * c) }

    private fun side(fwd: Vec3) = fwd cross Z

    /** Standing (or walking when [amp] > 0): [phase] is the gait phase in radians. */
    fun walking(pos: Pt, heading: Double, phase: Double, amp: Double, look: Vec3? = null, sway: Double = 0.0): Pose {
        val fwd = dir(heading)
        val right = side(fwd)
        val bob = -0.012 * amp * cos(2.0 * phase)
        val pelvis = p(pos, Body.STAND_HIP - 0.05 * amp + bob)
        val shoulderMid = pelvis + Z * (Body.TORSO - 0.04)
        fun foot(sgn: Double): Pair<Vec3, Vec3> {
            val ph = phase + if (sgn > 0) 0.0 else PI
            val off = A * amp * cos(ph)
            val lift = max(0.0, -sin(ph)) * 0.09 * amp
            val xy = p(pos, 0.0) + right * (0.10 * sgn) + fwd * off
            val toe = (fwd + right * (0.12 * sgn)).normalized()
            return Vec3(xy.x, xy.y, Body.ANKLE + lift) to toe
        }
        val (fr, tr) = foot(1.0)
        val (fl, tl) = foot(-1.0)
        fun hand(sgn: Double): Vec3 {
            val ph = phase + if (sgn > 0) 0.0 else PI
            val swing = -0.20 * amp * cos(ph)
            val sh = shoulderMid + right * (Body.SHOULDER_HALF * sgn)
            return sh + right * (0.03 * sgn) - Z * (0.485 - 0.02 * amp) + fwd * (0.02 + swing)
        }
        return Pose(
            pelvis, Z, fwd, look ?: fwd, fl, fr, tl, tr, hand(-1.0), hand(1.0), fwd,
            sway = sway + phase
        )
    }

    fun standing(pos: Pt, heading: Double, look: Vec3? = null) = walking(pos, heading, 0.0, 0.0, look)

    /** Sitting with thighs roughly horizontal and feet on the floor [footDist] in front of the pelvis. */
    fun sitting(pelvis: Vec3, heading: Double, lean: Double, footDist: Double, look: Vec3? = null, hands: Double = 0.0): Pose {
        val fwd = dir(heading)
        val right = side(fwd)
        val up = (Z * cos(lean) - fwd * sin(lean)).normalized()
        val base = Vec3(pelvis.x, pelvis.y, 0.0)
        fun foot(sgn: Double) = base + fwd * footDist + right * (0.11 * sgn) + Vec3(0.0, 0.0, Body.ANKLE)
        fun hand(sgn: Double): Vec3 =
            pelvis + fwd * (0.30 + 0.04 * hands) + right * (0.15 * sgn) + Z * (0.11 + 0.03 * hands)
        return Pose(
            pelvis, up, fwd, look ?: fwd, foot(-1.0), foot(1.0), fwd, fwd, hand(-1.0), hand(1.0),
            (Z + fwd * 0.6).normalized()
        )
    }

    /** Lying on the back; [headC] is the head centre, [headDir] the horizontal direction pointing along the body to the head. */
    fun lying(headC: Vec3, headDir: Vec3, closed: Double, breath: Double = 0.0): Pose {
        val hd = Vec3(headDir.x, headDir.y, 0.0).normalized()
        val up = (hd + Z * 0.12).normalized()
        val pelvis = headC - hd * 0.60 - Z * 0.05
        val right = Z cross up
        fun foot(sgn: Double) = pelvis - hd * 0.76 + right * (0.10 * sgn) + Vec3(0.0, 0.0, -0.03)
        val toe = (Z * 0.9 - hd * 0.3).normalized()
        fun hand(sgn: Double) = pelvis + up * 0.29 + right * (0.045 * sgn) + Z * (0.15 + breath)
        return Pose(
            pelvis, up, Z, Z, foot(-1.0), foot(1.0), toe, toe, hand(-1.0), hand(1.0), Z,
            eyesClosed = closed
        )
    }

    fun turnAngle(from: Double, to: Double): Double {
        var d = (to - from) % (2.0 * PI)
        if (d > PI) d -= 2.0 * PI
        if (d < -PI) d += 2.0 * PI
        return d
    }
}
