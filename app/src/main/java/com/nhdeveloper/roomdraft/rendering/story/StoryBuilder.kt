package com.nhdeveloper.roomdraft.rendering.story

import androidx.compose.ui.graphics.Color
import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.Openings
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Wall
import com.nhdeveloper.roomdraft.rendering.room3d.Vec3
import com.nhdeveloper.roomdraft.rendering.room3d.world
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Turns a room layout into a [Story]: picks the door, table, sofa, TV and bed, plans the walks and sequences the poses. */
internal class StoryBuilder(private val roomL: Double, private val roomW: Double, private val items: List<PlacedFurniture>) {
    private val pf = Pathfinder(roomL, roomW, items)
    private val segs = ArrayList<Seg>()
    private val doorKeys = ArrayList<Pair<Double, Double>>().apply { add(0.0 to 0.0) }
    private var t = 0.0
    private var pos = Pt(0.0, 0.0)
    private var heading = 0.0
    private var entranceEnd = 0.0
    private var entranceYaw = 0.0
    private var doorId: String? = null
    private var blanket: BlanketSpec? = null

    private val Z = Vec3(0.0, 0.0, 1.0)
    private fun height(f: PlacedFurniture) = Catalog.find(f.catalogId)?.heightM ?: 0.8
    private fun world(p: Pt, z: Double) = world(p.x, p.y, z)
    private fun headingTo(from: Pt, to: Pt) = kotlin.math.atan2(to.y - from.y, to.x - from.x)
    private fun first(vararg ids: String): PlacedFurniture? = ids.firstNotNullOfOrNull { id -> items.firstOrNull { it.catalogId == id } }

    // ------------------------------------------------------------------ segment helpers

    private fun add(s: Seg) { segs += s; t += s.dur }

    private fun walk(route: Route, caption: String, lookAt: ((Pt) -> Vec3?)? = null) {
        if (route.length < 0.05) return
        val d0 = Poses.turnAngle(heading, route.heading(0.0, 0.6))
        if (abs(d0) > 0.5) turnTo(heading + d0, caption)
        val l = route.length
        val h0 = heading
        fun hAt(s: Double) = h0 + Poses.turnAngle(h0, route.heading(s)) * Poses.smooth(s / 0.5)
        val dur = max(0.5, l / Story.WALK_SPEED)
        add(Seg(dur, caption, true) { u ->
            val s = l * (0.5 * u + 0.5 * Poses.smooth(u))
            val amp = min(1.0, min(s / 0.3, (l - s) / 0.35)).coerceIn(0.0, 1.0)
            val p = route.at(s)
            Poses.walking(p, hAt(s), s / Poses.STRIDE * 2.0 * PI, amp, lookAt?.invoke(p))
        })
        pos = route.at(l)
        heading = hAt(l)
    }

    private fun turnTo(target: Double, caption: String, look: Vec3? = null) {
        val d = Poses.turnAngle(heading, target)
        if (abs(d) < 0.05) return
        val h0 = heading
        val p0 = pos
        add(Seg(0.4 + abs(d) / PI * 0.7, caption, false) { u -> Poses.standing(p0, h0 + d * Poses.smooth(u), look) })
        heading = h0 + d
    }

    private fun hold(dur: Double, caption: String, look: (Double) -> Vec3?) {
        val p0 = pos
        val h0 = heading
        add(Seg(dur, caption, false) { u -> Poses.standing(p0, h0, look(u)) })
    }

    /** Blend between two poses; the pelvis moves sideways early (sitting down) or late (standing up). */
    private fun transition(a: Pose, b: Pose, dur: Double, caption: String, lean: Vec3, xyFirst: Boolean, lift: Double = 0.0) {
        add(Seg(dur, caption, false) { u ->
            val e = Poses.smooth(u)
            val q = Pose.lerp(a, b, e)
            val exy = if (xyFirst) Poses.smooth(min(1.0, u * 1.35)) else Poses.smooth(max(0.0, (u - 0.2) / 0.8))
            val pelvis = Vec3(
                a.pelvis.x + (b.pelvis.x - a.pelvis.x) * exy, a.pelvis.y + (b.pelvis.y - a.pelvis.y) * exy,
                a.pelvis.z + (b.pelvis.z - a.pelvis.z) * e + lift * sin(PI * e)
            )
            q.copy(pelvis = pelvis, up = (q.up + lean * (0.55 * sin(PI * e))).normalized())
        })
    }

    // ------------------------------------------------------------------ the door

    private fun entrance(): Boolean {
        val door = first("door_main", "door_room", "door_sliding")
        if (door == null) {
            // no door in the layout: she is simply standing in the first free spot
            var x = 0.5
            while (x < roomL - 0.4) {
                var y = 0.5
                while (y < roomW - 0.4) {
                    if (pf.isFree(Pt(x, y), 0.3)) { pos = Pt(x, y); heading = headingTo(pos, Pt(roomL / 2, roomW / 2)); return false }
                    y += 0.25
                }
                x += 0.25
            }
            pos = Pt(roomL / 2, roomW / 2)
            return false
        }
        doorId = door.id
        val df = ItemFrame(door)
        val tw = Openings.WALL_THICKNESS
        val standU = (df.w - 0.44).coerceIn(min(0.3, df.w / 2), max(df.w / 2, df.w - 0.3))
        pos = df.plan(standU, -tw - 1.45)
        heading = df.frontHeading
        val stand = df.plan(standU, -tw - 0.42)
        val doorCenter = df.plan(df.w / 2, 0.0)
        entranceYaw = entranceYawFor(Wall.fromRotation(door.rotationDeg), doorCenter)
        walk(Route(listOf(pos, stand)), "She arrives at the door")
        val hinged = door.catalogId != "door_sliding"
        if (hinged) openDoor(df)
        // walk through the doorway into the room, then to the nearest free spot (sofas sometimes stand right behind the door)
        val thr = df.plan(standU, 0.15)
        var target = df.plan(df.w / 2, 0.75)
        if (!pf.isFree(target, 0.25)) {
            var best: Pt? = null
            var rad = 0.5
            while (best == null && rad < 3.0) {
                var k = 0
                while (k <= 12 && best == null) {
                    val ang = PI * k / 12.0
                    val cand = df.plan(df.w / 2 + cos(ang) * rad, 0.3 + sin(ang) * rad)
                    if (pf.isFree(cand, 0.25)) best = cand
                    k++
                }
                rad += 0.1
            }
            target = best ?: target
        }
        val t0 = t
        val rest = if (pf.isFree(df.plan(standU, 0.75), 0.2) && target.dist(df.plan(df.w / 2, 0.75)) < 1e-6) listOf(target) else pf.route(thr, target, 0.2, 0.3, 0.2).drop(1)
        walk(Route(listOf(pos, thr) + rest), "She walks into the room")
        if (hinged) {
            val tc = t0 + (t - t0) * 0.45
            doorKeys += tc to Story.OPEN_ANGLE
            doorKeys += (tc + 1.4) to 0.0
        }
        entranceEnd = t
        return true
    }

    private fun openDoor(df: ItemFrame) {
        val dur = 2.4
        val travel = 0.22
        val start = pos
        val inward = Pt(cos(heading), sin(heading))
        val route = Route(listOf(start, start + inward * travel))
        val post = 0.06
        val len = df.w - 2 * post
        val t0 = t
        doorKeys += (t0 + 0.25 * dur) to 0.0
        doorKeys += (t0 + 0.80 * dur) to Story.OPEN_ANGLE
        val h = heading
        add(Seg(dur, "She opens the door", false) { u ->
            val s = travel * Poses.smooth((u - 0.2) / 0.5)
            val amp = min(1.0, min(s / 0.3, (travel - s) / 0.35)).coerceIn(0.0, 1.0)
            val base = Poses.walking(route.at(s), h, s / Poses.STRIDE * 2.0 * PI, amp)
            val th = Story.OPEN_ANGLE * Poses.smooth((u - 0.25) / 0.55)
            val reach = Poses.smooth(u / 0.2) * (1.0 - Poses.smooth((u - 0.62) / 0.25))
            val pu = post + cos(th) * (len - 0.15)
            val pv = -0.075 + sin(th) * (len - 0.15) - 0.03
            val target = world(df.plan(pu, pv), 1.0)
            val hand = base.handR + (target - base.handR) * reach
            base.copy(handR = hand, look = target - (base.pelvis + Z * 1.45))
        })
        pos = route.at(travel)
    }

    /** Camera yaw that sees the entrance wall side-on (from the end nearest the door), leaning towards the room. */
    private fun entranceYawFor(wall: Wall, doorCenter: Pt): Double {
        val bias = 0.28
        return when (wall) {
            Wall.NORTH -> if (doorCenter.x > roomL / 2) PI / 2 - bias else -PI / 2 + bias
            Wall.SOUTH -> if (doorCenter.x > roomL / 2) PI / 2 + bias else -PI / 2 - bias
            Wall.EAST -> if (doorCenter.y > roomW / 2) -bias else PI + bias
            Wall.WEST -> if (doorCenter.y > roomW / 2) bias else PI - bias
        }
    }

    // ------------------------------------------------------------------ the table

    private fun lookAtTable(): Boolean {
        val table = first("coffee_table", "dining_table", "round_table", "chowki", "side_table", "desk", "study_desk") ?: return false
        val tf = ItemFrame(table)
        val b = tf.center
        var stand: Pt? = null
        for ((gap, clear) in listOf(0.5 to 0.2, 0.42 to 0.14, 0.38 to 0.1)) {
            val hx = tf.bx / 2.0 + gap
            val hy = tf.by / 2.0 + gap
            stand = listOf(Pt(b.x, b.y - hy), Pt(b.x, b.y + hy), Pt(b.x - hx, b.y), Pt(b.x + hx, b.y))
                .filter { pf.isFree(it, clear, ignore = table) }
                .minByOrNull { it.dist(pos) }
            if (stand != null) break
        }
        if (stand == null) return false
        walk(Route(pf.route(pos, stand)), "She walks to the table")
        val topZ = height(table) + 0.02
        turnTo(headingTo(pos, b), "She turns to the table")
        val eye = world(pos, 1.45)
        val dirU = tf.dirU
        hold(3.6, "She looks at the table") { u ->
            val k = sin(2.0 * PI * u) * 0.35 * tf.w
            world(Pt(b.x + dirU.x * k, b.y + dirU.y * k), topZ) - eye
        }
        return true
    }

    // ------------------------------------------------------------------ sofa and TV

    private class SeatSpec(val us: List<Double>, val frontV: Double, val pv: Double, val top: Double, val lean: Double)

    private fun seatSpec(f: PlacedFurniture): SeatSpec? {
        val fr = ItemFrame(f)
        val w = fr.w; val d = fr.d
        return when (f.catalogId) {
            "sofa_2", "sofa_3", "armchair" -> {
                val arm = f.catalogId == "armchair"
                val armW = min(0.16, w * 0.14)
                val backT = max(0.18, d * 0.22)
                val n = if (arm) 1 else max(1, Math.round((w - 2 * armW) / 0.7).toInt())
                val sw = (w - 2 * armW) / n
                SeatSpec((0 until n).map { armW + sw * (it + 0.5) }, d, min(d * 0.5, backT + 0.27), 0.52, 0.17)
            }
            "sofa_l" -> {
                val chaise = min(0.9, w * 0.4)
                val armW = 0.14
                val seatW = w - armW - chaise
                val n = max(1, Math.round(seatW / 0.7).toInt())
                val sw = seatW / n
                SeatSpec((0 until n).map { armW + sw * (it + 0.5) }, 0.9, 0.45, 0.52, 0.17)
            }
            "wood_sofa" -> SeatSpec(listOf(w / 2), d, min(d * 0.5, 0.4), 0.45, 0.08)
            "diwan" -> SeatSpec(listOf(w / 2), d, min(d * 0.55, 0.5), 0.452, 0.2)
            else -> null
        }
    }

    private fun pickSofa(tv: PlacedFurniture?): PlacedFurniture? {
        val order = listOf("sofa_3", "sofa_2", "sofa_l", "wood_sofa", "diwan", "armchair")
        val all = items.filter { it.catalogId in order }
        if (all.isEmpty()) return null
        if (tv == null) return all.minByOrNull { order.indexOf(it.catalogId) }
        val tc = ItemFrame(tv).center
        return all.maxByOrNull { s ->
            val sf = ItemFrame(s)
            val to = tc - sf.center
            val dist = to.length().coerceAtLeast(0.01)
            val facing = (sf.dirV.x * to.x + sf.dirV.y * to.y) / dist
            facing * 2.0 - dist * 0.15 + (6 - order.indexOf(s.catalogId)) * 0.05
        }
    }

    private fun tvTarget(tv: PlacedFurniture): Vec3 {
        val tf = ItemFrame(tv)
        return world(tf.plan(tf.w / 2, tf.d * 0.3), height(tv) + 0.08 + 0.2 * tf.w)
    }

    private fun watchTv(tv: PlacedFurniture?): Boolean {
        val sofa = pickSofa(tv) ?: return false
        val spec = seatSpec(sofa) ?: return false
        val sf = ItemFrame(sofa)
        val standOf = { u: Double -> sf.plan(u, spec.frontV + 0.08) }
        val u = spec.us.maxByOrNull { pf.clearance(standOf(it).x, standOf(it).y, sofa) } ?: spec.us.first()
        val stand = standOf(u)
        walk(Route(pf.route(pos, stand)), "She walks to the sofa")
        turnTo(sf.frontHeading + PI, "She reaches the sofa")
        turnTo(sf.frontHeading, "She turns to sit down")
        val seatPlan = sf.plan(u, spec.pv)
        val pelvis = world(seatPlan, spec.top + 0.05)
        val look = tv?.let { tvTarget(it) - (pelvis + Z * 0.58) }
        val sit = Poses.sitting(pelvis, sf.frontHeading, spec.lean, 0.45, look)
        val standPose = Poses.standing(pos, sf.frontHeading)
        val fwd = Poses.dir(sf.frontHeading)
        transition(standPose, sit, 1.9, "She sits down on the sofa", fwd, xyFirst = true)
        add(Seg(10.0, if (tv != null) "She watches TV" else "She relaxes on the sofa", false) { uu ->
            sit.copy(sway = uu)
        })
        transition(sit, standPose, 1.7, "She gets up", fwd, xyFirst = false)
        return true
    }

    // ------------------------------------------------------------------ bed

    private class BedSide(val stand: Pt, val sit: Pt, val outward: Double, val sitU: Double)

    private fun sleep(): Boolean {
        val bed = items.filter { it.catalogId.startsWith("bed_") }.firstOrNull() ?: first("charpai") ?: return false
        val bf = ItemFrame(bed)
        val h = height(bed)
        val top = if (bed.catalogId.startsWith("bed_")) h + 0.04 else h - 0.03
        val vS = bf.d * 0.62
        val sides = listOf(
            BedSide(bf.plan(-0.12, vS), bf.plan(0.20, vS), kotlin.math.atan2(-bf.dirU.y, -bf.dirU.x), 0.0),
            BedSide(bf.plan(bf.w + 0.12, vS), bf.plan(bf.w - 0.20, vS), kotlin.math.atan2(bf.dirU.y, bf.dirU.x), bf.w),
            BedSide(bf.plan(bf.w / 2, bf.d + 0.12), bf.plan(bf.w / 2, bf.d - 0.20), bf.frontHeading, bf.w / 2)
        )
        val side = sides.filter { pf.isFree(it.stand, 0.15, ignore = bed) }.minByOrNull { it.stand.dist(pos) } ?: sides[2]
        walk(Route(pf.route(pos, side.stand)), "She walks to the bed")
        turnTo(side.outward + PI, "She reaches the bed")
        turnTo(side.outward, "She turns to sit on the bed")
        val sitPelvis = world(side.sit, top + 0.05)
        val sit = Poses.sitting(sitPelvis, side.outward, 0.12, 0.45, null)
        val standPose = Poses.standing(pos, side.outward)
        val fwd = Poses.dir(side.outward)
        transition(standPose, sit, 1.7, "She sits on the edge of the bed", fwd, xyFirst = true)
        add(Seg(1.0, "She sits on the edge of the bed", false) { sit })

        val n = if (bf.w > 1.2) 2 else 1
        val pillowU = (0 until n).map { bf.w * (it + 0.5) / n }.minByOrNull { abs(it - side.sitU) } ?: bf.w / 2
        val headPlan = bf.plan(pillowU, 0.32)
        val headDir = Vec3(-bf.dirV.x, bf.dirV.y, 0.0)
        val headC = world(headPlan, top + 0.14)
        val lie = Poses.lying(headC, headDir, 1.0)
        transition(sit, lie, 2.8, "She lies down", Vec3(headDir.x, headDir.y, 0.0), xyFirst = false, lift = 0.10)
        val tPull = t
        add(Seg(2.8, "She pulls the blanket over herself", false) { uu ->
            Poses.lying(headC, headDir, 1.0, 0.004 * sin(uu * 2.0 * PI))
        })
        blanket = BlanketSpec(bf, pillowU, top, Color(0xFFD9826B), tPull, tPull + 2.8)
        add(Seg(5.0, "She falls asleep", false) { uu ->
            Poses.lying(headC, headDir, 1.0, 0.005 * sin(uu * 5.0 * PI))
        })
        return true
    }

    // ------------------------------------------------------------------ whole story

    fun build(): Story? {
        val tv = items.firstOrNull { it.catalogId.startsWith("tv_unit") }
        val hasTable = first("coffee_table", "dining_table", "round_table", "chowki", "side_table", "desk", "study_desk") != null
        val hasSofa = pickSofa(tv) != null
        val hasBed = items.any { it.catalogId.startsWith("bed_") || it.catalogId == "charpai" }
        if (!hasTable && !hasSofa && !hasBed) return null
        val hasDoor = entrance()
        if (!hasDoor) {
            // nothing to open: begin with a short pause so the first frame is calm
            val p0 = pos; val h0 = heading
            add(Seg(1.0, "She is in the room", false) { Poses.standing(p0, h0) })
            entranceEnd = t
        }
        val a = lookAtTable()
        val b = watchTv(tv)
        val c = sleep()
        if (!a && !b && !c) return null
        return Story(roomL, roomW, segs, doorKeys.sortedBy { it.first }, doorId, blanket, entranceYaw, entranceEnd)
    }
}
