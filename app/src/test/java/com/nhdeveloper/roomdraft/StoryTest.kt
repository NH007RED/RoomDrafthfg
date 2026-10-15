package com.nhdeveloper.roomdraft

import com.nhdeveloper.roomdraft.domain.model.Templates
import com.nhdeveloper.roomdraft.domain.model.TemplateLayouts
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.isFloorCovering
import com.nhdeveloper.roomdraft.domain.model.isOpening
import com.nhdeveloper.roomdraft.rendering.room3d.Vec3
import com.nhdeveloper.roomdraft.rendering.story.Story
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTest {
    private fun stories() = Templates.all.map { tpl ->
        val items = TemplateLayouts.build(tpl)
        Triple(tpl, items, Story.build(tpl.lengthM, tpl.widthM, items))
    }

    @Test fun everyTemplateGetsAStoryOfSensibleLength() {
        for ((tpl, _, story) in stories()) {
            assertNotNull(tpl.id, story)
            assertTrue("${tpl.id} ${story!!.duration}", story.duration in 8.0..90.0)
        }
    }

    @Test fun livingRoomStoryWatchesTvForTenSeconds() {
        val (_, _, story) = stories().first { it.first.id == "small_living_room" }
        val tl = story!!.timeline()
        val i = tl.indexOfFirst { it.second == "She watches TV" }
        assertTrue(i >= 0)
        assertEquals(10.0, tl[i + 1].first - tl[i].first, 0.01)
    }

    @Test fun studioStoryCoversDoorTableSofaAndBed() {
        val (_, _, story) = stories().first { it.first.id == "studio_apartment" }
        val captions = story!!.timeline().map { it.second }
        for (c in listOf("She opens the door", "She looks at the table", "She watches TV", "She lies down", "She falls asleep")) {
            assertTrue(c, c in captions)
        }
        assertTrue(story.duration in 25.0..90.0)
    }

    private fun dist(a: Vec3, b: Vec3) = (a - b).length()

    @Test fun posesAreContinuousAcrossSegments() {
        for ((tpl, _, story) in stories()) {
            story!!
            for ((t0, cap) in story.timeline().drop(1)) {
                val a = story.poseAt(t0 - 1e-4)
                val b = story.poseAt(t0 + 1e-4)
                assertTrue("${tpl.id} @${t0} $cap pelvis ${dist(a.pelvis, b.pelvis)}", dist(a.pelvis, b.pelvis) < 0.06)
                assertTrue("${tpl.id} @${t0} $cap footL ${dist(a.footL, b.footL)}", dist(a.footL, b.footL) < 0.15)
                assertTrue("${tpl.id} @${t0} $cap footR ${dist(a.footR, b.footR)}", dist(a.footR, b.footR) < 0.15)
                assertTrue("${tpl.id} @${t0} $cap handL ${dist(a.handL, b.handL)}", dist(a.handL, b.handL) < 0.2)
                assertTrue("${tpl.id} @${t0} $cap handR ${dist(a.handR, b.handR)}", dist(a.handR, b.handR) < 0.2)
            }
        }
    }

    @Test fun walkingNeverEntersFurniture() {
        for ((tpl, items, story) in stories()) {
            story!!
            val solids = items.filter { !it.isOpening && !it.isFloorCovering }.map { it.id to it.bounds }
            for ((a, b) in story.walkingSegments()) {
                var t = a
                while (t <= b) {
                    val p = story.poseAt(t).pelvis
                    val py = -p.y
                    for ((id, r) in solids) {
                        val inside = p.x > r.left + 0.03 && p.x < r.right - 0.03 && py > r.top + 0.03 && py < r.bottom - 0.03
                        assertTrue("${tpl.id} t=$t inside $id", !inside)
                    }
                    t += 0.1
                }
            }
        }
    }

    @Test fun doorOpensAndClosesAgain() {
        val (_, _, story) = stories().first { it.first.id == "small_living_room" }
        story!!
        assertEquals(0.0, story.doorAngleAt(0.0), 1e-9)
        var max = 0.0
        var t = 0.0
        while (t < story.duration) { max = maxOf(max, story.doorAngleAt(t)); t += 0.05 }
        assertTrue(max > 1.5)
        assertEquals(0.0, story.doorAngleAt(story.duration), 1e-6)
    }

    @Test fun characterGeometryIsFiniteEverywhere() {
        for ((tpl, _, story) in stories()) {
            story!!
            var t = 0.0
            while (t <= story.duration) {
                val actor = story.actorAt(t)
                assertTrue("${tpl.id} t=$t", actor.faces.size > 300)
                for (f in actor.faces) for (p in f.pts) assertTrue(p.x.isFinite() && p.y.isFinite() && p.z.isFinite())
                val cam = story.cameraAt(t)
                assertTrue(cam.distance.isFinite() && cam.pitch.isFinite() && cam.yaw.isFinite())
                t += 0.5
            }
        }
    }
}
