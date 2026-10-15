package com.nhdeveloper.roomdraft.rendering.story

import androidx.compose.ui.graphics.Color
import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.Openings
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Wall
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.rendering.room3d.ActorFaces
import com.nhdeveloper.roomdraft.rendering.room3d.CameraState
import com.nhdeveloper.roomdraft.rendering.room3d.Face
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D
import com.nhdeveloper.roomdraft.rendering.room3d.Vec3
import com.nhdeveloper.roomdraft.rendering.room3d.world
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Item-local (u, v) to plan coordinates, same convention as the 3D builders: v = 0 is the back, +v the front. */
internal class ItemFrame(val f: PlacedFurniture) {
    private val b = f.bounds
    private val rot = ((f.rotationDeg % 360) + 360) % 360
    val w = f.widthM
    val d = f.depthM
    val center = Pt((b.left + b.right) / 2.0, (b.top + b.bottom) / 2.0)
    /** Footprint size along plan x and y. */
    val bx = b.right - b.left
    val by = b.bottom - b.top

    fun plan(u: Double, v: Double): Pt = when (rot) {
        90 -> Pt(b.right - v, b.top + u)
        180 -> Pt(b.right - u, b.bottom - v)
        270 -> Pt(b.left + v, b.bottom - u)
        else -> Pt(b.left + u, b.top + v)
    }

    val dirU: Pt = when (rot) { 90 -> Pt(0.0, 1.0); 180 -> Pt(-1.0, 0.0); 270 -> Pt(0.0, -1.0); else -> Pt(1.0, 0.0) }
    val dirV: Pt = when (rot) { 90 -> Pt(-1.0, 0.0); 180 -> Pt(0.0, -1.0); 270 -> Pt(1.0, 0.0); else -> Pt(0.0, 1.0) }
    val frontHeading: Double get() = kotlin.math.atan2(dirV.y, dirV.x)
}

/** One stretch of the story. [pose] takes progress 0..1 within the segment. */
internal class Seg(val dur: Double, val caption: String, val walking: Boolean, val pose: (Double) -> Pose)

/** The blanket pulled over the sleeping character, laid out in the bed's own frame. */
internal class BlanketSpec(val frame: ItemFrame, val u: Double, val top: Double, val color: Color, val t0: Double, val t1: Double)

/**
 * The scripted story: she opens the door, walks in, looks at the table, sits on the sofa to watch TV for
 * ten seconds, then goes to bed and sleeps. Everything is a pure function of time, so it can be played live
 * or rendered frame by frame into a video.
 */
class Story internal constructor(
    val roomL: Double,
    val roomW: Double,
    private val segs: List<Seg>,
    private val doorKeys: List<Pair<Double, Double>>,
    val doorId: String?,
    private val blanket: BlanketSpec?,
    private val entranceYaw: Double,
    private val entranceEnd: Double
) {
    val duration: Double = segs.sumOf { it.dur }
    private val starts: DoubleArray = DoubleArray(segs.size).also { var t = 0.0; for (i in segs.indices) { it[i] = t; t += segs[i].dur } }

    private fun index(t: Double): Int {
        val c = t.coerceIn(0.0, duration)
        var i = segs.size - 1
        while (i > 0 && starts[i] > c) i--
        return i
    }

    fun poseAt(t: Double): Pose {
        val i = index(t)
        val u = ((t - starts[i]) / segs[i].dur).coerceIn(0.0, 1.0)
        return segs[i].pose(u)
    }

    fun captionAt(t: Double): String = segs[index(t)].caption

    fun isWalking(t: Double): Boolean = segs[index(t)].walking

    /** Seconds at which each segment starts, with its caption (used by tests and the progress bar). */
    fun timeline(): List<Pair<Double, String>> = segs.indices.map { starts[it] to segs[it].caption }

    internal fun walkingSegments(): List<Pair<Double, Double>> = segs.indices.filter { segs[it].walking }.map { starts[it] to starts[it] + segs[it].dur }

    /** Door leaf opening angle in radians (0 = closed). */
    fun doorAngleAt(t: Double): Double {
        if (doorKeys.isEmpty()) return 0.0
        if (t <= doorKeys.first().first) return doorKeys.first().second
        for (k in 1 until doorKeys.size) {
            val (t1, a1) = doorKeys[k]
            if (t <= t1) {
                val (t0, a0) = doorKeys[k - 1]
                val e = Poses.smooth(if (t1 - t0 < 1e-9) 1.0 else (t - t0) / (t1 - t0))
                return a0 + (a1 - a0) * e
            }
        }
        return doorKeys.last().second
    }

    private fun blanketFaces(t: Double): List<Face> {
        val b = blanket ?: return emptyList()
        val p = Poses.smooth((t - b.t0) / (b.t1 - b.t0))
        if (p <= 0.001) return emptyList()
        val fr = b.frame
        val faces = ArrayList<Face>()
        val vEnd = fr.d - 0.12
        val vStart = (vEnd + (0.60 - vEnd) * p)
        val half = minOf(0.34, (fr.w - 0.1) / 2.0)
        fun box(v0: Double, v1: Double, z0: Double, z1: Double, c: Color) {
            if (v1 - v0 < 0.01) return
            val o = fr.plan(b.u - half, v0)
            val ex = fr.plan(b.u + half, v0)
            val ey = fr.plan(b.u - half, v1)
            Room3D.addOrientedBox(
                faces, world(o.x, o.y, z0), world(ex.x, ex.y, z0) - world(o.x, o.y, z0),
                world(ey.x, ey.y, z0) - world(o.x, o.y, z0), Vec3(0.0, 0.0, z1 - z0), c, 0.03f, 0.16
            )
        }
        box(maxOf(vStart, 0.60), minOf(vEnd, 1.20), b.top - 0.01, b.top + 0.25, b.color)
        box(maxOf(vStart, 1.20), vEnd, b.top - 0.01, b.top + 0.21, b.color)
        // folded hem
        box(maxOf(vStart, 0.60), maxOf(vStart, 0.60) + 0.08, b.top + 0.25, b.top + 0.27, Color(0xFFF1E3C9))
        return faces
    }

    /** The character (and the blanket, once it is pulled up) for time [t]. */
    fun actorAt(t: Double): ActorFaces {
        val pose = poseAt(t)
        val a = ActorRenderer.build(pose)
        val extra = blanketFaces(t)
        // lying on the bed: draw her after the furniture so the large mattress faces never hide her
        val lying = pose.up.z < 0.6
        return ActorFaces(a.faces + extra, a.shadows, lying)
    }

    /** Hand-held style cinematic camera that follows her; starts side-on to the entrance wall so the door is visible. */
    fun cameraAt(t: Double): CameraState {
        val base = CameraState.default(roomL, roomW)
        val pel = poseAt(t).pelvis
        val target = Vec3(base.target.x + (pel.x - base.target.x) * 0.55, base.target.y + (pel.y - base.target.y) * 0.55, 0.55)
        val mainYaw = base.yaw + 0.38 * sin(t * 0.11 + 0.6)
        val w = if (doorId == null) 1.0 else Poses.smooth((t - entranceEnd) / 2.8)
        val yaw = entranceYaw + Poses.turnAngle(entranceYaw, mainYaw) * w
        val pitch = 0.95 + (0.80 - 0.95) * w + 0.03 * sin(t * 0.23)
        val dist = base.distance * (0.60 + 0.12 * w)
        return CameraState(yaw = yaw, pitch = pitch, distance = dist, target = target)
    }

    companion object {
        const val WALK_SPEED = 1.0
        const val OPEN_ANGLE = 1.75

        fun build(roomL: Double, roomW: Double, items: List<PlacedFurniture>): Story? = StoryBuilder(roomL, roomW, items).build()
    }
}
