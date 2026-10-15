package com.nhdeveloper.roomdraft.rendering.story

import com.nhdeveloper.roomdraft.domain.model.Bounds
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.isFloorCovering
import com.nhdeveloper.roomdraft.domain.model.isOpening
import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A point in plan coordinates (metres). */
data class Pt(val x: Double, val y: Double) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Double) = Pt(x * k, y * k)
    fun length() = hypot(x, y)
    fun dist(o: Pt) = hypot(x - o.x, y - o.y)
}

/** Walkable-floor queries and an A* planner on a 10 cm grid. Rugs and openings never block. */
internal class Pathfinder(private val roomL: Double, private val roomW: Double, items: List<PlacedFurniture>) {
    private val res = 0.1
    private val nx = ceil(roomL / res).toInt().coerceAtLeast(1)
    private val ny = ceil(roomW / res).toInt().coerceAtLeast(1)
    val obstacles: List<Pair<PlacedFurniture, Bounds>> =
        items.filter { !it.isOpening && !it.isFloorCovering }.map { it to it.bounds }

    private fun rectDist(b: Bounds, x: Double, y: Double): Double {
        val dx = max(max(b.left - x, 0.0), x - b.right)
        val dy = max(max(b.top - y, 0.0), y - b.bottom)
        return hypot(dx, dy)
    }

    /** Distance from (x, y) to the nearest furniture edge or wall (0 inside furniture / outside the room). */
    fun clearance(x: Double, y: Double, ignore: PlacedFurniture? = null): Double {
        var d = minOf(x, roomL - x, y, roomW - y).coerceAtLeast(0.0)
        for ((f, b) in obstacles) {
            if (f === ignore) continue
            d = min(d, rectDist(b, x, y))
        }
        return d
    }

    fun isFree(p: Pt, radius: Double, ignore: PlacedFurniture? = null) = clearance(p.x, p.y, ignore) >= radius

    private fun cellCenter(i: Int, j: Int) = Pt((i + 0.5) * res, (j + 0.5) * res)
    private fun cellOf(p: Pt) = (p.x / res).toInt().coerceIn(0, nx - 1) to (p.y / res).toInt().coerceIn(0, ny - 1)

    /**
     * Shortest collision-free route from [from] to [to] for a body of the given [radius]. The cells close to
     * either end are always passable so that the route may finish right at a sofa or a bed.
     */
    fun route(from: Pt, to: Pt, radius: Double = 0.22, endZone: Double = 0.45, startZone: Double = endZone): List<Pt> {
        for (r in listOf(radius, 0.17, 0.12, 0.08)) {
            plan(from, to, r, endZone, startZone)?.let { return it }
        }
        return listOf(from, to)
    }

    private fun passable(p: Pt, from: Pt, to: Pt, r: Double, zone: Double, zoneStart: Double = zone): Boolean {
        if (p.x < 0.1 || p.y < 0.1 || p.x > roomL - 0.1 || p.y > roomW - 0.1) return false
        return p.dist(to) < zone || p.dist(from) < zoneStart || isFree(p, r)
    }

    private fun segmentClear(a: Pt, b: Pt, from: Pt, to: Pt, r: Double, zone: Double, zs: Double): Boolean {
        val n = max(1, (a.dist(b) / 0.05).toInt())
        for (k in 0..n) {
            val t = k.toDouble() / n
            if (!passable(Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), from, to, r, zone, zs)) return false
        }
        return true
    }

    private fun plan(from: Pt, to: Pt, r: Double, zone: Double, zs: Double): List<Pt>? {
        val (si, sj) = cellOf(from)
        val (gi, gj) = cellOf(to)
        val total = nx * ny
        val g = DoubleArray(total) { Double.MAX_VALUE }
        val parent = IntArray(total) { -1 }
        val closed = BooleanArray(total)
        fun id(i: Int, j: Int) = j * nx + i
        val open = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        g[id(si, sj)] = 0.0
        open.add(0.0 to id(si, sj))
        val goalId = id(gi, gj)
        var found = false
        while (open.isNotEmpty()) {
            val (_, cur) = open.poll()
            if (closed[cur]) continue
            closed[cur] = true
            if (cur == goalId) { found = true; break }
            val ci = cur % nx; val cj = cur / nx
            for (di in -1..1) for (dj in -1..1) {
                if (di == 0 && dj == 0) continue
                val ni = ci + di; val nj = cj + dj
                if (ni !in 0 until nx || nj !in 0 until ny) continue
                val nid = id(ni, nj)
                if (closed[nid]) continue
                if (!passable(cellCenter(ni, nj), from, to, r, zone, zs)) continue
                if (di != 0 && dj != 0 &&
                    (!passable(cellCenter(ci + di, cj), from, to, r, zone, zs) || !passable(cellCenter(ci, cj + dj), from, to, r, zone, zs))
                ) continue
                val step = if (di != 0 && dj != 0) 1.4142 else 1.0
                val ng = g[cur] + step
                if (ng < g[nid]) {
                    g[nid] = ng
                    parent[nid] = cur
                    val h = sqrt(((ni - gi) * (ni - gi) + (nj - gj) * (nj - gj)).toDouble())
                    open.add((ng + h) to nid)
                }
            }
        }
        if (!found) return null
        val cells = ArrayList<Pt>()
        var c = goalId
        while (c != -1) { cells += cellCenter(c % nx, c / nx); c = parent[c] }
        cells.reverse()
        cells[0] = from
        cells[cells.size - 1] = to
        // line-of-sight smoothing
        val out = ArrayList<Pt>()
        var i = 0
        out += cells[0]
        while (i < cells.size - 1) {
            var j = cells.size - 1
            while (j > i + 1 && !segmentClear(cells[i], cells[j], from, to, r, zone, zs)) j--
            out += cells[j]
            i = j
        }
        return out
    }
}

/** A polyline in plan coordinates with position / heading lookup by travelled distance. */
internal class Route(val pts: List<Pt>) {
    private val cum = DoubleArray(pts.size)
    val length: Double

    init {
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + pts[i].dist(pts[i - 1])
        length = cum.last()
    }

    fun at(s: Double): Pt {
        val d = s.coerceIn(0.0, length)
        if (pts.size == 1 || length < 1e-9) return pts[0]
        var i = 1
        while (i < pts.size - 1 && cum[i] < d) i++
        val seg = cum[i] - cum[i - 1]
        val t = if (seg < 1e-9) 0.0 else (d - cum[i - 1]) / seg
        return pts[i - 1] + (pts[i] - pts[i - 1]) * t
    }

    /** Direction of travel (radians, plan angle) a short distance ahead of [s]. */
    fun heading(s: Double, lookAhead: Double = 0.35): Double {
        val a = at(s)
        var b = at(min(length, s + lookAhead))
        if (a.dist(b) < 1e-3) b = at(length).let { end -> if (a.dist(end) < 1e-3) pts.last() + (pts.last() - pts[max(0, pts.size - 2)]) else end }
        if (a.dist(b) < 1e-3) return 0.0
        return kotlin.math.atan2(b.y - a.y, b.x - a.x)
    }

    fun finalHeading(): Double {
        var i = pts.size - 1
        while (i > 0 && pts[i].dist(pts[i - 1]) < 1e-3) i--
        if (i == 0) return 0.0
        return kotlin.math.atan2(pts[i].y - pts[i - 1].y, pts[i].x - pts[i - 1].x)
    }
}
