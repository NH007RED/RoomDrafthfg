package com.nhdeveloper.roomdraft

import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.rendering.room3d.CameraState
import com.nhdeveloper.roomdraft.rendering.room3d.Face
import com.nhdeveloper.roomdraft.rendering.room3d.Projector
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D
import com.nhdeveloper.roomdraft.rendering.room3d.world
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Room3DTest {
    @Test fun cameraTargetProjectsToScreenCenter() {
        val cam = CameraState.default(5.0, 4.0)
        val p = Projector(cam, 1000f, 800f).project(cam.target)
        assertNotNull(p)
        assertEquals(500f, p!!.x, 0.01f)
        assertEquals(400f, p.y, 0.01f)
    }

    @Test fun planXIncreasesToTheRightOnScreen() {
        val cam = CameraState.default(5.0, 4.0).copy(yaw = 0.0)
        val proj = Projector(cam, 1000f, 800f)
        val a = proj.project(world(1.0, 2.0, 0.0))!!
        val b = proj.project(world(4.0, 2.0, 0.0))!!
        assertTrue(b.x > a.x)
    }

    @Test fun planYIncreasesDownTheScreen() {
        val cam = CameraState.default(5.0, 4.0).copy(yaw = 0.0)
        val proj = Projector(cam, 1000f, 800f)
        val top = proj.project(world(2.5, 0.5, 0.0))!!
        val bottom = proj.project(world(2.5, 3.5, 0.0))!!
        assertTrue(bottom.y > top.y)
    }

    @Test fun pitchAndZoomAreClamped() {
        val cam = CameraState.default(5.0, 4.0)
        assertEquals(1.5, cam.orbit(0.0, 10.0).pitch, 1e-9)
        assertEquals(0.15, cam.orbit(0.0, -10.0).pitch, 1e-9)
        assertEquals(CameraState.MIN_DISTANCE, cam.zoom(1000.0).distance, 1e-9)
        assertEquals(CameraState.MAX_DISTANCE, cam.zoom(0.0001).distance, 1e-9)
    }

    @Test fun everyCatalogItemProducesFaces() {
        Catalog.items.forEach { item ->
            val faces = mutableListOf<Face>()
            Room3D.addFurniture(faces, Catalog.place(item.id, 2.0, 2.0))
            assertTrue("${item.id} has faces", faces.isNotEmpty())
        }
    }

    @Test fun floorPointInvertsProjection() {
        val cam = CameraState.default(5.0, 4.0).copy(yaw = 0.8, pitch = 0.9)
        val proj = Projector(cam, 1000f, 800f)
        for ((x, y, z) in listOf(Triple(1.0, 1.0, 0.0), Triple(3.5, 2.5, 0.4), Triple(4.2, 0.7, 1.1))) {
            val p = proj.project(world(x, y, z))!!
            val back = proj.floorPoint(p.x, p.y, z)!!
            assertEquals(x, back.first, 1e-3)
            assertEquals(y, back.second, 1e-3)
        }
    }

    @Test fun pickItemFindsTheFurnitureUnderTheFinger() {
        val cam = CameraState.default(5.0, 4.0)
        val sofa = Catalog.place("sofa_3", 2.5, 2.0)
        val chair = Catalog.place("armchair", 0.6, 0.6)
        val proj = Projector(cam, 1000f, 800f)
        val onSofa = proj.project(world(2.5, 2.0, 0.3))!!
        assertEquals(sofa.id, Room3D.pickItem(listOf(chair, sofa), cam, 1000f, 800f, onSofa.x, onSofa.y))
        assertEquals(null, Room3D.pickItem(listOf(chair, sofa), cam, 1000f, 800f, 2f, 2f))
    }

    @Test fun videoCameraPathLoopsAndStaysInRange() {
        val a = com.nhdeveloper.roomdraft.rendering.room3d.CameraPath.at(0.0, 5.0, 4.0)
        val b = com.nhdeveloper.roomdraft.rendering.room3d.CameraPath.at(1.0, 5.0, 4.0)
        assertEquals(a.pitch, b.pitch, 1e-9)
        assertEquals(a.distance, b.distance, 1e-9)
        assertEquals(2 * Math.PI, b.yaw - a.yaw, 1e-9)
        for (i in 0..300) {
            val c = com.nhdeveloper.roomdraft.rendering.room3d.CameraPath.at(i / 300.0, 5.0, 4.0)
            assertTrue(c.pitch in 0.2..1.2)
            assertTrue(c.distance > CameraState.MIN_DISTANCE && c.distance < CameraState.MAX_DISTANCE)
        }
    }
}
