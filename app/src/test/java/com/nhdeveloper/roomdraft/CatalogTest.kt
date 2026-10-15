package com.nhdeveloper.roomdraft

import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.FurnitureCategory
import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.Openings
import com.nhdeveloper.roomdraft.domain.model.Wall
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.rendering.room3d.Face
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test fun catalogIdsAreUnique() {
        assertEquals(Catalog.items.size, Catalog.items.map { it.id }.toSet().size)
    }

    @Test fun indianFurnitureIsAvailable() {
        val indian = Catalog.byCategory(FurnitureCategory.INDIAN)
        assertTrue(indian.size >= 10)
        listOf("charpai", "diwan", "jhula", "mandir", "almirah", "dhurrie").forEach {
            assertTrue("$it missing", Catalog.find(it) != null)
        }
    }

    @Test fun everyItemBuildsFacesInEveryRotation() {
        Catalog.items.forEach { item ->
            listOf(0, 90, 180, 270).forEach { rot ->
                val faces = mutableListOf<Face>()
                Room3D.addFurniture(faces, Catalog.place(item.id, 3.0, 2.0, rot))
                assertTrue("${item.id}@$rot", faces.size >= 5)
            }
        }
    }

    @Test fun doorsAndWindowsSnapToTheNearestWall() {
        val win = Catalog.place("window", 2.0, 1.4)
        val top = Openings.snapToWall(win, 2.0, 0.3, 4.0, 3.5)
        assertEquals(0, top.rotationDeg)
        assertEquals(0.06, top.centerY, 1e-9)
        val right = Openings.snapToWall(win, 3.9, 1.7, 4.0, 3.5)
        assertEquals(90, right.rotationDeg)
        assertEquals(4.0 - 0.06, right.centerX, 1e-9)
        val bottom = Openings.snapToWall(win, 1.0, 3.4, 4.0, 3.5)
        assertEquals(180, bottom.rotationDeg)
        assertTrue(FurnitureGeometry.isInside(bottom.bounds, 4.0, 3.5))
    }

    @Test fun openingsNeverCountAsOverlap() {
        val sofa = Catalog.place("sofa_3", 2.0, 0.45)
        val win = Openings.onWall(Catalog.place("window", 2.0, 0.06), Wall.NORTH, 2.0, 4.0, 3.5)
        assertTrue(FurnitureGeometry.overlappingPairs(listOf(sofa, win)).isEmpty())
    }
}
