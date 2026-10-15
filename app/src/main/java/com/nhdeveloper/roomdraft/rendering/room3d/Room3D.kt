package com.nhdeveloper.roomdraft.rendering.room3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nhdeveloper.roomdraft.domain.model.Bounds
import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.Openings
import com.nhdeveloper.roomdraft.domain.model.Wall
import com.nhdeveloper.roomdraft.domain.model.isOpening
import com.nhdeveloper.roomdraft.domain.model.FurnitureShape
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.drawOrder
import com.nhdeveloper.roomdraft.domain.model.isFloorCovering
import com.nhdeveloper.roomdraft.ui.components.shape
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized(): Vec3 { val l = length(); return if (l < 1e-12) this else this * (1.0 / l) }
}

/**
 * World axes: X = room length (right), Y = north (plan y flipped), Z = up.
 * A plan point (px, py) maps to (px, -py).
 */
fun world(px: Double, py: Double, z: Double) = Vec3(px, -py, z)

data class CameraState(val yaw: Double, val pitch: Double, val distance: Double, val target: Vec3) {
    fun eye(): Vec3 = target + Vec3(sin(yaw) * cos(pitch), -cos(yaw) * cos(pitch), sin(pitch)) * distance

    fun forward(): Vec3 = (target - eye()).normalized()
    fun right(): Vec3 = forward().cross(Vec3(0.0, 0.0, 1.0)).normalized()
    fun up(): Vec3 = right().cross(forward())

    fun orbit(dYaw: Double, dPitch: Double) = copy(yaw = yaw + dYaw, pitch = (pitch + dPitch).coerceIn(0.15, 1.5))

    fun zoom(factor: Double) = copy(distance = (distance / factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE))

    fun pan(dxPx: Double, dyPx: Double, focalPx: Double): CameraState {
        val k = distance / focalPx
        return copy(target = target - right() * (dxPx * k) + up() * (dyPx * k))
    }

    companion object {
        const val MIN_DISTANCE = 1.5
        const val MAX_DISTANCE = 80.0

        fun default(roomL: Double, roomW: Double) = CameraState(
            yaw = 0.55, pitch = 0.85,
            distance = max(roomL, roomW) * 1.7 + 2.0,
            target = world(roomL / 2.0, roomW / 2.0, 0.3)
        )
    }
}

class Projector(private val camera: CameraState, private val widthPx: Float, private val heightPx: Float) {
    private val eye = camera.eye()
    private val fwd = camera.forward()
    private val right = camera.right()
    private val up = camera.up()
    val focal: Double = min(widthPx, heightPx) * 1.1

    /** Returns null when the point is behind (or too close to) the camera. */
    fun project(p: Vec3): Offset? {
        val v = p - eye
        val zc = v dot fwd
        if (zc < 0.1) return null
        val xc = v dot right
        val yc = v dot up
        return Offset(
            (widthPx / 2.0 + focal * xc / zc).toFloat(),
            (heightPx / 2.0 - focal * yc / zc).toFloat()
        )
    }

    /**
     * Inverse of [project] for points on the horizontal plane z = [planeZ]. Returns PLAN coordinates (x, y in
     * metres, y pointing down the plan) or null when the ray never reaches the plane.
     */
    fun floorPoint(sx: Float, sy: Float, planeZ: Double): Pair<Double, Double>? {
        val dir = fwd + right * ((sx - widthPx / 2.0) / focal) + up * (-(sy - heightPx / 2.0) / focal)
        if (dir.z > -1e-6) return null
        val t = (planeZ - eye.z) / dir.z
        val p = eye + dir * t
        return p.x to -p.y
    }
}

/** Surface detail drawn on top of a face. */
enum class FacePattern { NONE, GRAIN, WEAVE, STRIPES }

enum class FloorStyle { WOOD, TILE, MARBLE }

class Face(
    val pts: List<Vec3>,
    val base: Color,
    val normal: Vec3,
    val gloss: Float = 0f,
    val pattern: FacePattern = FacePattern.NONE,
    val emissive: Boolean = false,
    /** Metres subtracted from the sort distance so small details win against the surface they sit on. */
    val bias: Double = 0.0
) {
    val centroid: Vec3 = pts.fold(Vec3(0.0, 0.0, 0.0)) { a, b -> a + b } * (1.0 / pts.size)
}

/** Blob shadow cast by one part of the animated character: plan position, height above the floor, radii. */
class ShadowSpot(val x: Double, val y: Double, val z: Double, val rx: Double, val ry: Double, val alpha: Float)

/** The animated character for one frame: its faces (world coordinates) and soft shadow spots. */
class ActorFaces(val faces: List<Face>, val shadows: List<ShadowSpot>, val onTop: Boolean = false)

object Room3D {
    private const val WALL_HEIGHT = 2.4
    private const val WALL_THICKNESS = 0.12

    /** Direction TOWARDS the key light (warm, upper left of the scene). */
    private val LIGHT = Vec3(-0.4, -0.5, 0.8).normalized()
    /** Soft cool fill light from the opposite side. */
    private val FILL = Vec3(0.55, 0.45, 0.35).normalized()

    // ------------------------------------------------------------------ geometry helpers

    fun addBox(
        into: MutableList<Face>, x0: Double, y0: Double, x1: Double, y1: Double, z0: Double, z1: Double, c: Color,
        gloss: Float = 0f, topPattern: FacePattern = FacePattern.NONE
    ) {
        if (x1 - x0 < 1e-6 || y1 - y0 < 1e-6 || z1 - z0 < 1e-6) return
        into += Face(listOf(world(x0, y0, z1), world(x1, y0, z1), world(x1, y1, z1), world(x0, y1, z1)), c, Vec3(0.0, 0.0, 1.0), gloss, topPattern)
        into += Face(listOf(world(x0, y0, z0), world(x1, y0, z0), world(x1, y0, z1), world(x0, y0, z1)), c, Vec3(0.0, 1.0, 0.0), gloss)
        into += Face(listOf(world(x0, y1, z0), world(x1, y1, z0), world(x1, y1, z1), world(x0, y1, z1)), c, Vec3(0.0, -1.0, 0.0), gloss)
        into += Face(listOf(world(x0, y0, z0), world(x0, y1, z0), world(x0, y1, z1), world(x0, y0, z1)), c, Vec3(-1.0, 0.0, 0.0), gloss)
        into += Face(listOf(world(x1, y0, z0), world(x1, y1, z0), world(x1, y1, z1), world(x1, y0, z1)), c, Vec3(1.0, 0.0, 0.0), gloss)
    }

    fun addBox(
        into: MutableList<Face>, b: Bounds, z0: Double, z1: Double, c: Color,
        gloss: Float = 0f, topPattern: FacePattern = FacePattern.NONE
    ) = addBox(into, b.left, b.top, b.right, b.bottom, z0, z1, c, gloss, topPattern)

    /** Cone / cylinder segment with (possibly different) bottom and top radii. */
    fun addFrustum(
        into: MutableList<Face>, cx: Double, cy: Double,
        rx0: Double, ry0: Double, rx1: Double, ry1: Double,
        z0: Double, z1: Double, c: Color, n: Int = 24, gloss: Float = 0f, capTop: Boolean = true,
        flip: Boolean = false
    ) {
        if (z1 - z0 < 1e-6 || (rx0 < 1e-6 && rx1 < 1e-6)) return
        val top = ArrayList<Vec3>(n)
        val slope = ((rx0 + ry0) - (rx1 + ry1)) / 2.0 / (z1 - z0)
        for (i in 0 until n) {
            val a0 = 2.0 * Math.PI * i / n
            val a1 = 2.0 * Math.PI * (i + 1) / n
            val am = (a0 + a1) / 2.0
            val b0x = cx + rx0 * cos(a0); val b0y = cy + ry0 * sin(a0)
            val b1x = cx + rx0 * cos(a1); val b1y = cy + ry0 * sin(a1)
            val t0x = cx + rx1 * cos(a0); val t0y = cy + ry1 * sin(a0)
            val t1x = cx + rx1 * cos(a1); val t1y = cy + ry1 * sin(a1)
            into += Face(
                listOf(world(b0x, b0y, z0), world(b1x, b1y, z0), world(t1x, t1y, z1), world(t0x, t0y, z1)),
                c, Vec3(cos(am), -sin(am), slope).normalized().let { if (flip) it * -1.0 else it }, gloss
            )
            top += world(t0x, t0y, z1)
        }
        if (capTop && rx1 > 1e-6) into += Face(top, c, Vec3(0.0, 0.0, if (flip) -1.0 else 1.0), gloss)
    }

    fun addPrism(
        into: MutableList<Face>, cx: Double, cy: Double, rx: Double, ry: Double, z0: Double, z1: Double, c: Color,
        n: Int = 24, gloss: Float = 0f
    ) = addFrustum(into, cx, cy, rx, ry, rx, ry, z0, z1, c, n, gloss)

    /** Cheap sphere: stacked frustums. */
    fun addBlob(
        into: MutableList<Face>, cx: Double, cy: Double, cz: Double, rx: Double, ry: Double, rz: Double, c: Color,
        rings: Int = 5, n: Int = 14
    ) {
        for (i in 0 until rings) {
            val a0 = -Math.PI / 2 + Math.PI * i / rings
            val a1 = -Math.PI / 2 + Math.PI * (i + 1) / rings
            val r0 = cos(a0).coerceAtLeast(0.05); val r1 = cos(a1).coerceAtLeast(0.0)
            addFrustum(
                into, cx, cy, rx * r0, ry * r0, rx * r1, ry * r1,
                cz + rz * sin(a0), cz + rz * sin(a1), c, n, 0f, capTop = i == rings - 1
            )
        }
    }


    // ---- free-form primitives (world coordinates), used for the animated character

    private fun outward(pts: List<Vec3>, center: Vec3): Vec3 {
        val n = ((pts[2] - pts[0]) cross (pts[3 % pts.size] - pts[1])).normalized()
        val c = pts.fold(Vec3(0.0, 0.0, 0.0)) { a, b -> a + b } * (1.0 / pts.size)
        return if (n dot (c - center) < 0.0) n * -1.0 else n
    }

    private fun perpendicular(a: Vec3): Pair<Vec3, Vec3> {
        val helper = if (kotlin.math.abs(a.z) < 0.9) Vec3(0.0, 0.0, 1.0) else Vec3(1.0, 0.0, 0.0)
        val u = (a cross helper).normalized()
        return u to (a cross u).normalized()
    }

    /** Tapered tube between two world points. */
    fun addTube(
        into: MutableList<Face>, a: Vec3, b: Vec3, r0: Double, r1: Double, c: Color,
        n: Int = 8, gloss: Float = 0f, caps: Boolean = true, bias: Double = 0.0
    ) {
        val axis = b - a
        if (axis.length() < 1e-6) return
        val dir = axis.normalized()
        val (u, v) = perpendicular(dir)
        fun ring(center: Vec3, r: Double, i: Int): Vec3 {
            val ang = 2.0 * Math.PI * i / n
            return center + (u * cos(ang) + v * sin(ang)) * r
        }
        for (i in 0 until n) {
            val pts = listOf(ring(a, r0, i), ring(a, r0, i + 1), ring(b, r1, i + 1), ring(b, r1, i))
            val cen = (pts[0] + pts[1] + pts[2] + pts[3]) * 0.25
            into += Face(pts, c, outward(pts, a + dir * ((cen - a) dot dir)), gloss, bias = bias)
        }
        if (caps) {
            if (r1 > 1e-4) into += Face((0 until n).map { ring(b, r1, it) }, c, dir, gloss, bias = bias)
            if (r0 > 1e-4) into += Face((0 until n).map { ring(a, r0, n - 1 - it) }, c, dir * -1.0, gloss, bias = bias)
        }
    }

    /** Ellipsoid with arbitrary orientation: [ax] [ay] [az] are the semi-axis vectors, [ay] points to the "pole". */
    fun addEllipsoid(
        into: MutableList<Face>, c: Vec3, ax: Vec3, ay: Vec3, az: Vec3, color: Color,
        rings: Int = 4, n: Int = 10, gloss: Float = 0f, bias: Double = 0.0
    ) {
        fun pt(i: Int, j: Int): Vec3 {
            val lat = -Math.PI / 2 + Math.PI * i / rings
            val lon = 2.0 * Math.PI * j / n
            return c + ax * (cos(lat) * cos(lon)) + az * (cos(lat) * sin(lon)) + ay * sin(lat)
        }
        for (i in 0 until rings) for (j in 0 until n) {
            val pts = listOf(pt(i, j), pt(i, j + 1), pt(i + 1, j + 1), pt(i + 1, j))
            val uniq = pts.distinctBy { Triple(Math.round(it.x * 1e5), Math.round(it.y * 1e5), Math.round(it.z * 1e5)) }
            if (uniq.size < 3) continue
            val nrm = if (uniq.size == 3) ((uniq[1] - uniq[0]) cross (uniq[2] - uniq[0])).normalized().let {
                val cc = (uniq[0] + uniq[1] + uniq[2]) * (1.0 / 3.0)
                if (it dot (cc - c) < 0.0) it * -1.0 else it
            } else outward(uniq, c)
            into += Face(uniq, color, nrm, gloss, bias = bias)
        }
    }

    /** Axis-aligned-in-its-own-frame box: corner [o] plus three edge vectors. */
    fun addOrientedBox(
        into: MutableList<Face>, o: Vec3, ex: Vec3, ey: Vec3, ez: Vec3, c: Color, gloss: Float = 0f, bias: Double = 0.0
    ) {
        val centre = o + (ex + ey + ez) * 0.5
        fun q(a: Vec3, b: Vec3, d: Vec3, e: Vec3) {
            val pts = listOf(a, b, d, e)
            into += Face(pts, c, outward(pts, centre), gloss, bias = bias)
        }
        val p000 = o; val p100 = o + ex; val p110 = o + ex + ey; val p010 = o + ey
        val p001 = o + ez; val p101 = o + ex + ez; val p111 = o + ex + ey + ez; val p011 = o + ey + ez
        q(p000, p100, p110, p010); q(p001, p101, p111, p011)
        q(p000, p100, p101, p001); q(p010, p110, p111, p011)
        q(p000, p010, p011, p001); q(p100, p110, p111, p101)
    }

    fun addBlobW(into: MutableList<Face>, p: Vec3, rx: Double, ry: Double, rz: Double, c: Color, rings: Int = 4, n: Int = 10) =
        addBlob(into, p.x, -p.y, p.z, rx, ry, rz, c, rings, n)

    fun Color.dim(f: Float) = Color((red * f).coerceIn(0f, 1f), (green * f).coerceIn(0f, 1f), (blue * f).coerceIn(0f, 1f), alpha)

    fun Color.mix(o: Color, t: Float) = Color(
        red + (o.red - red) * t, green + (o.green - green) * t, blue + (o.blue - blue) * t, alpha
    )

    private val faceCache = object : LinkedHashMap<PlacedFurniture, List<Face>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PlacedFurniture, List<Face>>?) = size > 160
    }

    fun addFurniture(into: MutableList<Face>, f: PlacedFurniture) {
        val faces = faceCache.getOrPut(f) { mutableListOf<Face>().also { FurnitureBuilder.build(it, f) } }
        into.addAll(faces)
    }

    // ------------------------------------------------------------------ room shell

    internal fun debugWalls(l: Double, w: Double, eye: Vec3, openings: List<PlacedFurniture>) = buildWalls(l, w, eye, Color(0xFFF6F1E8), openings)

    private class Cut(val a: Double, val b: Double, val sill: Double, val top: Double)

    private fun cutsFor(openings: List<PlacedFurniture>, wall: Wall): List<Cut> =
        openings.filter { Wall.fromRotation(it.rotationDeg) == wall }.map {
            val bd = it.bounds
            val horizontal = wall == Wall.NORTH || wall == Wall.SOUTH
            Cut(if (horizontal) bd.left else bd.top, if (horizontal) bd.right else bd.bottom, Openings.sill(it.catalogId), Openings.top(it.catalogId))
        }.sortedBy { it.a }

    /** Pieces of a wall strip between zLo and zHi once the openings are cut out: (from, to, z0, z1). */
    private fun pieces(lo: Double, hi: Double, zLo: Double, zHi: Double, cuts: List<Cut>): List<DoubleArray> {
        val out = ArrayList<DoubleArray>()
        var cursor = lo
        for (c in cuts) {
            val a = max(c.a, cursor); val b = min(max(c.b, a), hi)
            if (a > cursor) out += doubleArrayOf(cursor, a, zLo, zHi)
            if (c.sill > zLo + 1e-6) out += doubleArrayOf(a, b, zLo, min(c.sill, zHi))
            if (c.top < zHi - 1e-6) out += doubleArrayOf(a, b, max(c.top, zLo), zHi)
            cursor = max(cursor, b)
        }
        if (cursor < hi) out += doubleArrayOf(cursor, hi, zLo, zHi)
        return out
    }

    /** Box on a wall: [d0,d1] is measured from the inner face, negative = into the wall. */
    private fun wallBox(f: MutableList<Face>, wall: Wall, s: Double, e: Double, z0: Double, z1: Double, d0: Double, d1: Double, c: Color, roomL: Double, roomW: Double) {
        when (wall) {
            Wall.NORTH -> addBox(f, s, d0, e, d1, z0, z1, c)
            Wall.SOUTH -> addBox(f, s, roomW - d1, e, roomW - d0, z0, z1, c)
            Wall.WEST -> addBox(f, d0, s, d1, e, z0, z1, c)
            Wall.EAST -> addBox(f, roomL - d1, s, roomL - d0, e, z0, z1, c)
        }
    }

    private fun isLowered(wall: Wall, eye: Vec3, roomL: Double, roomW: Double): Boolean = when (wall) {
        Wall.NORTH -> -eye.y < 0.0
        Wall.SOUTH -> -eye.y > roomW
        Wall.WEST -> eye.x < 0.0
        Wall.EAST -> eye.x > roomL
    }

    private fun buildWalls(roomL: Double, roomW: Double, eye: Vec3, wallColor: Color, openings: List<PlacedFurniture>): List<Face> {
        val faces = mutableListOf<Face>()
        val t = WALL_THICKNESS
        val skirt = Color(0xFFFFFFFF).mix(wallColor, 0.1f)
        val cap = wallColor.dim(0.9f)

        // slab ledge around the room
        val s = 0.08
        val slab = Color(0xFF3A3F42)
        addBox(faces, -t - s, -t - s, roomL + t + s, -t, -0.14, 0.0, slab)
        addBox(faces, -t - s, roomW + t, roomL + t + s, roomW + t + s, -0.14, 0.0, slab)
        addBox(faces, -t - s, -t, -t, roomW + t, -0.14, 0.0, slab)
        addBox(faces, roomL + t, -t, roomL + t + s, roomW + t, -0.14, 0.0, slab)

        for (wall in Wall.values()) {
            val horizontal = wall == Wall.NORTH || wall == Wall.SOUTH
            val lo = if (horizontal) -t else 0.0
            val hi = if (horizontal) roomL + t else roomW
            val len = if (horizontal) roomL else roomW
            val lowered = isLowered(wall, eye, roomL, roomW)
            val height = if (lowered) 0.15 else WALL_HEIGHT
            val cuts = if (lowered) emptyList() else cutsFor(openings, wall)
            pieces(lo, hi, 0.0, height, cuts).forEach {
                wallBox(faces, wall, it[0], it[1], it[2], it[3], -t, 0.0, wallColor, roomL, roomW)
            }
            if (lowered) continue
            // skirting boards and crown moulding on the inside, interrupted by doors
            val sk = 0.018
            pieces(0.0, len, 0.0, 0.1, cuts).forEach { wallBox(faces, wall, it[0], it[1], it[2], it[3], 0.0, sk, skirt, roomL, roomW) }
            pieces(0.0, len, WALL_HEIGHT - 0.05, WALL_HEIGHT, cuts).forEach { wallBox(faces, wall, it[0], it[1], it[2], it[3], 0.0, sk, cap, roomL, roomW) }
        }
        return faces
    }

    // ------------------------------------------------------------------ lighting

    private fun shaded(face: Face, eye: Vec3): Color {
        if (face.emissive) return face.base
        val n = face.normal
        val key = max(0.0, n dot LIGHT).toFloat()
        val fill = max(0.0, n dot FILL).toFloat()
        val hemi = (0.5 + 0.5 * n.z).toFloat()
        val amb = 0.34f + 0.20f * hemi
        val c = face.base
        var r = c.red * (amb + 0.16f * fill * 0.9f + 0.60f * key * 1.04f)
        var g = c.green * (amb + 0.16f * fill * 0.95f + 0.60f * key)
        var b = c.blue * (amb + 0.16f * fill * 1.05f + 0.60f * key * 0.93f)
        if (face.gloss > 0f) {
            val view = (eye - face.centroid).normalized()
            val half = (LIGHT + view).normalized()
            val spec = max(0.0, n dot half).pow(28.0).toFloat() * face.gloss
            r += spec; g += spec; b += spec
        }
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), c.alpha)
    }

    private fun polyPath(pts: List<Offset>): Path {
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        return path
    }

    private fun lerp(a: Vec3, b: Vec3, t: Double) = a + (b - a) * t

    private fun DrawScope.drawPattern(face: Face, color: Color, proj: Projector) {
        if (face.pts.size != 4) return
        val p0 = face.pts[0]; val p1 = face.pts[1]; val p2 = face.pts[2]; val p3 = face.pts[3]
        val l01 = (p1 - p0).length()
        val l12 = (p2 - p1).length()
        fun line(a: Vec3, b: Vec3, col: Color, w: Float) {
            val pa = proj.project(a) ?: return
            val pb = proj.project(b) ?: return
            drawLine(col, pa, pb, strokeWidth = w)
        }
        when (face.pattern) {
            FacePattern.GRAIN -> {
                val dark = color.dim(0.86f).copy(alpha = 0.55f)
                if (l01 >= l12) {
                    val n = (l12 / 0.07).toInt().coerceIn(2, 10)
                    for (k in 1..n) { val t = k / (n + 1.0); line(lerp(p0, p3, t), lerp(p1, p2, t), dark, 0.8f) }
                } else {
                    val n = (l01 / 0.07).toInt().coerceIn(2, 10)
                    for (k in 1..n) { val t = k / (n + 1.0); line(lerp(p0, p1, t), lerp(p3, p2, t), dark, 0.8f) }
                }
            }
            FacePattern.WEAVE -> {
                val rope = Color(0xFFE2C48F)
                val ropeDark = Color(0xFF9A7740)
                val na = (l01 / 0.07).toInt().coerceIn(4, 28)
                val nb = (l12 / 0.07).toInt().coerceIn(4, 28)
                for (k in 1..na) { val t = k / (na + 1.0); line(lerp(p0, p3, t), lerp(p1, p2, t), rope.copy(alpha = 0.95f), 1.6f) }
                for (k in 1..nb) { val t = k / (nb + 1.0); line(lerp(p0, p1, t), lerp(p3, p2, t), ropeDark.copy(alpha = 0.75f), 1.2f) }
            }
            FacePattern.STRIPES -> {
                val cream = Color(0xFFF1E4C8)
                val bands = 11
                // stripes run across the short direction, repeated along the long one
                val alongFirst = l01 >= l12
                for (k in 0 until bands) {
                    val t0 = k / bands.toDouble(); val t1 = (k + 1) / bands.toDouble()
                    val col = when {
                        k == 0 || k == bands - 1 -> color.dim(0.55f)
                        k % 2 == 1 -> color.mix(cream, 0.65f)
                        else -> color
                    }
                    if (col == color) continue
                    val q = if (alongFirst) listOf(lerp(p0, p1, t0), lerp(p0, p1, t1), lerp(p3, p2, t1), lerp(p3, p2, t0))
                    else listOf(lerp(p0, p3, t0), lerp(p0, p3, t1), lerp(p1, p2, t1), lerp(p1, p2, t0))
                    val pp = q.map { proj.project(it) }
                    if (pp.any { it == null }) continue
                    drawPath(polyPath(pp.map { it!! }), col)
                }
            }
            FacePattern.NONE -> Unit
        }
    }

    private fun DrawScope.drawFaces(faces: List<Face>, proj: Projector, eye: Vec3) {
        faces
            .filter { (eye - it.centroid) dot it.normal > 0.0 }
            .sortedByDescending { (it.centroid - eye).length() - it.bias }
            .forEach { face ->
                val pts = face.pts.map { proj.project(it) }
                if (pts.any { it == null }) return@forEach
                val sp = pts.map { it!! }
                val path = polyPath(sp)
                val color = shaded(face, eye)
                val vertical = kotlin.math.abs(face.normal.z) < 0.5
                var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                for (p in sp) { minY = min(minY, p.y); maxY = max(maxY, p.y) }
                if (vertical && maxY - minY > 20f) {
                    // ambient occlusion: surfaces get darker towards the floor
                    drawPath(path, Brush.verticalGradient(listOf(color.dim(1.07f), color.dim(0.80f)), startY = minY, endY = maxY))
                } else {
                    drawPath(path, color)
                }
                if (face.pattern != FacePattern.NONE) drawPattern(face, color, proj)
                // hairline in the face's own (darker) colour closes seams without cartoon outlines
                drawPath(path, color.dim(0.72f).copy(alpha = 0.55f), style = Stroke(width = 0.7f))
            }
    }

    // ------------------------------------------------------------------ floor

    private fun hash01(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    private fun DrawScope.quad(proj: Projector, x0: Double, y0: Double, x1: Double, y1: Double, color: Color, edge: Color? = null) {
        val p = listOf(world(x0, y0, 0.0), world(x1, y0, 0.0), world(x1, y1, 0.0), world(x0, y1, 0.0)).map { proj.project(it) }
        if (p.any { it == null }) return
        val path = polyPath(p.map { it!! })
        drawPath(path, color)
        if (edge != null) drawPath(path, edge, style = Stroke(width = 0.8f))
    }

    private fun DrawScope.drawFloor(proj: Projector, roomL: Double, roomW: Double, base: Color, style: FloorStyle) {
        when (style) {
            FloorStyle.WOOD -> {
                val pw = max(0.18, roomW / 40.0)
                val pl = max(1.2, roomL / 14.0)
                var row = 0
                var y = 0.0
                while (y < roomW - 1e-6) {
                    val y1 = min(roomW, y + pw)
                    var x = -((row * 0.37) % pl)
                    var col = 0
                    while (x < roomL - 1e-6) {
                        val xa = max(0.0, x); val xb = min(roomL, x + pl)
                        if (xb - xa > 1e-6) {
                            val j = 0.90f + 0.18f * hash01(row, col)
                            quad(proj, xa, y, xb, y1, base.dim(j), Color.Black.copy(alpha = 0.22f))
                        }
                        x += pl; col++
                    }
                    y += pw; row++
                }
            }
            FloorStyle.TILE, FloorStyle.MARBLE -> {
                val ts = if (style == FloorStyle.TILE) max(0.6, max(roomL, roomW) / 18.0) else max(0.8, max(roomL, roomW) / 14.0)
                val grout = Color.Black.copy(alpha = if (style == FloorStyle.TILE) 0.28f else 0.14f)
                var ix = 0
                var x = 0.0
                while (x < roomL - 1e-6) {
                    var iy = 0
                    var y = 0.0
                    while (y < roomW - 1e-6) {
                        val jitter = if (style == FloorStyle.TILE) 0.96f + 0.06f * hash01(ix, iy) else 0.97f + 0.05f * hash01(ix * 3, iy * 7)
                        val chk = if ((ix + iy) % 2 == 0) 1.0f else 0.975f
                        quad(proj, x, y, min(roomL, x + ts), min(roomW, y + ts), base.dim(jitter * chk), grout)
                        y += ts; iy++
                    }
                    x += ts; ix++
                }
            }
        }
        // soft gloss / light pool in the middle of the floor
        val c = proj.project(world(roomL / 2.0, roomW / 2.0, 0.0))
        val corner = proj.project(world(0.0, 0.0, 0.0))
        if (c != null && corner != null) {
            val rad = (c - corner).getDistance() * 1.05f
            val a = if (style == FloorStyle.WOOD) 0.10f else 0.17f
            val fl = listOf(world(0.0, 0.0, 0.0), world(roomL, 0.0, 0.0), world(roomL, roomW, 0.0), world(0.0, roomW, 0.0)).map { proj.project(it) }
            if (fl.none { it == null }) {
                drawPath(polyPath(fl.map { it!! }), Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), center = c, radius = rad.coerceAtLeast(1f)))
            }
        }
    }

    /** Dark gradient on the floor where it meets each wall (ambient occlusion). */
    private fun DrawScope.drawWallContactShadows(proj: Projector, roomL: Double, roomW: Double) {
        val d = 0.45
        val dark = Color.Black.copy(alpha = 0.28f)
        fun strip(a: Vec3, b: Vec3, c: Vec3, e: Vec3, from: Vec3, to: Vec3) {
            val pts = listOf(a, b, c, e).map { proj.project(it) }
            val s = proj.project(from); val t = proj.project(to)
            if (pts.any { it == null } || s == null || t == null) return
            drawPath(polyPath(pts.map { it!! }), Brush.linearGradient(listOf(dark, Color.Transparent), start = s, end = t))
        }
        strip(world(0.0, 0.0, 0.0), world(roomL, 0.0, 0.0), world(roomL, d, 0.0), world(0.0, d, 0.0), world(roomL / 2, 0.0, 0.0), world(roomL / 2, d, 0.0))
        strip(world(0.0, roomW, 0.0), world(roomL, roomW, 0.0), world(roomL, roomW - d, 0.0), world(0.0, roomW - d, 0.0), world(roomL / 2, roomW, 0.0), world(roomL / 2, roomW - d, 0.0))
        strip(world(0.0, 0.0, 0.0), world(0.0, roomW, 0.0), world(d, roomW, 0.0), world(d, 0.0, 0.0), world(0.0, roomW / 2, 0.0), world(d, roomW / 2, 0.0))
        strip(world(roomL, 0.0, 0.0), world(roomL, roomW, 0.0), world(roomL - d, roomW, 0.0), world(roomL - d, 0.0, 0.0), world(roomL, roomW / 2, 0.0), world(roomL - d, roomW / 2, 0.0))
    }

    // ------------------------------------------------------------------ shadows

    private fun hull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val p = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return p
        fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Double, Double>>()
        for (pt in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], pt) <= 0) lower.removeAt(lower.size - 1)
            lower.add(pt)
        }
        val upper = ArrayList<Pair<Double, Double>>()
        for (pt in p.reversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], pt) <= 0) upper.removeAt(upper.size - 1)
            upper.add(pt)
        }
        lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
        return lower + upper
    }

    /** Sutherland-Hodgman clip of a polygon against the rectangle [0,l] x [0,w]. */
    private fun clipToRoom(poly: List<Pair<Double, Double>>, l: Double, w: Double): List<Pair<Double, Double>> {
        var out = poly
        fun clip(inside: (Pair<Double, Double>) -> Boolean, cut: (Pair<Double, Double>, Pair<Double, Double>) -> Pair<Double, Double>) {
            val input = out
            val res = ArrayList<Pair<Double, Double>>()
            for (i in input.indices) {
                val cur = input[i]; val prev = input[(i + input.size - 1) % input.size]
                if (inside(cur)) {
                    if (!inside(prev)) res.add(cut(prev, cur))
                    res.add(cur)
                } else if (inside(prev)) res.add(cut(prev, cur))
            }
            out = res
        }
        fun atX(x: Double) = { a: Pair<Double, Double>, b: Pair<Double, Double> ->
            val t = (x - a.first) / (b.first - a.first); Pair(x, a.second + (b.second - a.second) * t)
        }
        fun atY(y: Double) = { a: Pair<Double, Double>, b: Pair<Double, Double> ->
            val t = (y - a.second) / (b.second - a.second); Pair(a.first + (b.first - a.first) * t, y)
        }
        clip({ it.first >= 0.0 }, atX(0.0)); if (out.isEmpty()) return out
        clip({ it.first <= l }, atX(l)); if (out.isEmpty()) return out
        clip({ it.second >= 0.0 }, atY(0.0)); if (out.isEmpty()) return out
        clip({ it.second <= w }, atY(w))
        return out
    }

    private fun DrawScope.drawShadow(proj: Projector, f: PlacedFurniture, roomL: Double, roomW: Double) {
        val b = f.bounds
        val h = FurnitureBuilder.shadowHeight(f.catalogId, Catalog.find(f.catalogId)?.heightM ?: 0.8)
        val cx = (b.left + b.right) / 2.0; val cy = (b.top + b.bottom) / 2.0
        val foot: List<Pair<Double, Double>> = if (f.shape() == FurnitureShape.ROUND) {
            (0 until 20).map { i ->
                val a = 2.0 * Math.PI * i / 20
                Pair(cx + b.width / 2.0 * cos(a), cy + b.height / 2.0 * sin(a))
            }
        } else listOf(Pair(b.left, b.top), Pair(b.right, b.top), Pair(b.right, b.bottom), Pair(b.left, b.bottom))
        // light travels along -LIGHT; plan y is the negative of world y
        val dx = -h * LIGHT.x / LIGHT.z
        val dy = h * LIGHT.y / LIGHT.z
        val poly = clipToRoom(hull(foot + foot.map { Pair(it.first + dx, it.second + dy) }), roomL, roomW)
        if (poly.size < 3) return
        val mx = poly.sumOf { it.first } / poly.size
        val my = poly.sumOf { it.second } / poly.size
        val layers = listOf(1.14 to 0.05f, 1.07 to 0.07f, 1.0 to 0.13f)
        for ((scale, alpha) in layers) {
            val pts = poly.map { world(mx + (it.first - mx) * scale, my + (it.second - my) * scale, 0.004) }.map { proj.project(it) }
            if (pts.any { it == null }) continue
            drawPath(polyPath(pts.map { it!! }), Color.Black.copy(alpha = alpha))
        }
        // tight contact shadow right under the piece
        val cpts = foot.map { world(it.first, it.second, 0.006) }.map { proj.project(it) }
        if (cpts.none { it == null }) drawPath(polyPath(cpts.map { it!! }), Color.Black.copy(alpha = 0.22f))
    }

    // ------------------------------------------------------------------ picking & selection

    /** Vertical extent of an item in metres. */
    fun zRange(f: PlacedFurniture): Pair<Double, Double> = when {
        f.isOpening -> Openings.sill(f.catalogId) to Openings.top(f.catalogId)
        f.isFloorCovering -> 0.0 to 0.03
        else -> 0.0 to (Catalog.find(f.catalogId)?.heightM ?: 0.8)
    }

    /** Height of the horizontal plane used when dragging [f]: about the middle of the piece. */
    fun grabPlaneZ(f: PlacedFurniture): Double {
        val (a, b) = zRange(f)
        return if (f.isFloorCovering) 0.0 else (a + min(b, 1.2)) / 2.0
    }

    private fun insideConvex(poly: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var pos = false; var neg = false
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            val cr = (b.first - a.first) * (y - a.second) - (b.second - a.second) * (x - a.first)
            if (cr > 1e-9) pos = true else if (cr < -1e-9) neg = true
            if (pos && neg) return false
        }
        return true
    }

    /** Which piece of furniture is under the screen point? Rugs only win when nothing else is hit. */
    fun pickItem(items: List<PlacedFurniture>, camera: CameraState, widthPx: Float, heightPx: Float, sx: Float, sy: Float): String? {
        val proj = Projector(camera, widthPx, heightPx)
        val eye = camera.eye()
        var best: String? = null
        var bestD = Double.MAX_VALUE
        for (f in items) {
            val (z0, z1) = zRange(f)
            val b = f.bounds
            val pts = ArrayList<Pair<Double, Double>>()
            var ok = true
            loop@ for ((x, y) in listOf(b.left to b.top, b.right to b.top, b.right to b.bottom, b.left to b.bottom)) {
                for (z in listOf(z0, z1)) {
                    val p = proj.project(world(x, y, z))
                    if (p == null) { ok = false; break@loop }
                    pts += p.x.toDouble() to p.y.toDouble()
                }
            }
            if (!ok) continue
            val h = hull(pts)
            if (h.size < 3 || !insideConvex(h, sx.toDouble(), sy.toDouble())) continue
            val c = world((b.left + b.right) / 2.0, (b.top + b.bottom) / 2.0, (z0 + z1) / 2.0)
            val d = (c - eye).length() + if (f.isFloorCovering) 1000.0 else 0.0
            if (d < bestD) { bestD = d; best = f.id }
        }
        return best
    }

    private fun DrawScope.drawSelection(proj: Projector, f: PlacedFurniture, color: Color) {
        val (z0, z1) = zRange(f)
        val b = f.bounds
        val e = 0.02
        val xs = listOf(b.left - e, b.right + e)
        val ys = listOf(b.top - e, b.bottom + e)
        fun pt(i: Int, j: Int, z: Double) = proj.project(world(xs[i], ys[j], z))
        val zs = listOf(z0, z1 + e)
        for (k in 0..1) {
            val ring = listOf(pt(0, 0, zs[k]), pt(1, 0, zs[k]), pt(1, 1, zs[k]), pt(0, 1, zs[k]))
            if (ring.none { it == null }) drawPath(polyPath(ring.map { it!! }), color, style = Stroke(width = 3f))
        }
        for ((i, j) in listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1)) {
            val a = pt(i, j, zs[0]); val c = pt(i, j, zs[1])
            if (a != null && c != null) drawLine(color, a, c, strokeWidth = 2.5f)
        }
        val floor = listOf(pt(0, 0, 0.01), pt(1, 0, 0.01), pt(1, 1, 0.01), pt(0, 1, 0.01))
        if (floor.none { it == null }) drawPath(polyPath(floor.map { it!! }), color.copy(alpha = 0.22f))
    }

    /** Which wall is [p] (world) behind, if it lies outside the room rectangle. */
    private fun outsideWall(p: Vec3, roomL: Double, roomW: Double): Wall? {
        val py = -p.y
        return when {
            py < 0.0 -> Wall.NORTH
            py > roomW -> Wall.SOUTH
            p.x < 0.0 -> Wall.WEST
            p.x > roomL -> Wall.EAST
            else -> null
        }
    }

    private fun DrawScope.drawActorShadows(proj: Projector, actor: ActorFaces, roomL: Double, roomW: Double) {
        for (sp in actor.shadows) {
            val x = sp.x - sp.z * LIGHT.x / LIGHT.z
            val y = sp.y + sp.z * LIGHT.y / LIGHT.z
            if (x < 0.0 || x > roomL || y < 0.0 || y > roomW) continue
            for ((scale, a) in listOf(1.5 to 0.35f, 1.15 to 0.4f, 0.8 to 0.5f)) {
                val pts = (0 until 14).map {
                    val ang = 2.0 * Math.PI * it / 14
                    proj.project(world(x + sp.rx * scale * cos(ang), y + sp.ry * scale * sin(ang), 0.007))
                }
                if (pts.any { it == null }) continue
                drawPath(polyPath(pts.map { it!! }), Color.Black.copy(alpha = sp.alpha * a))
            }
        }
    }

    // ------------------------------------------------------------------ entry point

    fun DrawScope.drawRoom(
        roomL: Double, roomW: Double, items: List<PlacedFurniture>, camera: CameraState,
        floorColor: Color, gridColor: Color,
        floorStyle: FloorStyle = FloorStyle.WOOD,
        dark: Boolean = false,
        selectedId: String? = null,
        overlapIds: Set<String> = emptySet(),
        actor: ActorFaces? = null,
        doorAngles: Map<String, Double> = emptyMap()
    ) {
        val proj = Projector(camera, size.width, size.height)
        val eye = camera.eye()

        drawFloor(proj, roomL, roomW, floorColor, floorStyle)
        drawWallContactShadows(proj, roomL, roomW)

        // rugs lie on the floor, so they go under every shadow
        val coverFaces = mutableListOf<Face>()
        items.filter { it.isFloorCovering }.forEach { addFurniture(coverFaces, it) }
        drawFaces(coverFaces, proj, eye)

        items.filter { !it.isFloorCovering && !it.isOpening }.forEach { drawShadow(proj, it, roomL, roomW) }
        actor?.let { drawActorShadows(proj, it, roomL, roomW) }

        // parts of the character that are outside the room are hidden by the walls; when the wall they stand
        // behind is lowered (camera outside) they are simply part of the normal sort instead
        val actorBehind = mutableListOf<Face>()
        val actorWith = mutableListOf<Face>()
        actor?.faces?.forEach { face ->
            val w = outsideWall(face.centroid, roomL, roomW)
            if (w != null && !isLowered(w, eye, roomL, roomW)) actorBehind += face else actorWith += face
        }
        drawFaces(actorBehind, proj, eye)

        // walls first (always behind furniture), then furniture sorted back to front
        val wallColor = if (dark) Color(0xFFD9D2C6) else Color(0xFFF6F1E8)
        val openings = items.filter { it.isOpening }
        drawFaces(buildWalls(roomL, roomW, eye, wallColor, openings), proj, eye)
        val furnitureFaces = mutableListOf<Face>()
        items.drawOrder().filter {
            !it.isFloorCovering && !(it.isOpening && isLowered(Wall.fromRotation(it.rotationDeg), eye, roomL, roomW))
        }.forEach {
            val ang = doorAngles[it.id]
            if (ang != null && ang > 0.001) FurnitureBuilder.build(furnitureFaces, it, ang) else addFurniture(furnitureFaces, it)
        }
        if (actor?.onTop != true) furnitureFaces.addAll(actorWith)
        drawFaces(furnitureFaces, proj, eye)
        if (actor?.onTop == true) drawFaces(actorWith, proj, eye)

        // selection / overlap highlights on top
        items.filter { it.id in overlapIds && it.id != selectedId }.forEach { drawSelection(proj, it, Color(0xFFE53935)) }
        items.firstOrNull { it.id == selectedId }?.let {
            drawSelection(proj, it, if (it.id in overlapIds) Color(0xFFE53935) else Color(0xFF2F9E7B))
        }
    }
}
