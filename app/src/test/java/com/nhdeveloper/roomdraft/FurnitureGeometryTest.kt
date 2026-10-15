package com.nhdeveloper.roomdraft

import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.TemplateLayouts
import com.nhdeveloper.roomdraft.domain.model.Templates
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.effectiveWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureGeometryTest {
    @Test fun rotationSwapsExtents() {
        val f = Catalog.place("sofa_3", 2.0, 2.0, 90)
        assertEquals(0.9, f.effectiveWidth, 1e-9)
        assertEquals(2.2, f.bounds.height, 1e-9)
    }

    @Test fun overlapDetected() {
        val a = Catalog.place("sofa_2", 1.0, 1.0)
        val b = Catalog.place("coffee_table", 1.2, 1.2)
        assertTrue(FurnitureGeometry.overlaps(a, b))
    }

    @Test fun touchingIsNotOverlap() {
        val a = Catalog.place("sofa_2", 1.0, 1.0)
        val b = Catalog.place("sofa_2", 2.6, 1.0)
        assertFalse(FurnitureGeometry.overlaps(a, b))
    }

    @Test fun rugNeverOverlaps() {
        val rug = Catalog.place("rug", 1.0, 1.0)
        val table = Catalog.place("coffee_table", 1.0, 1.0)
        assertFalse(FurnitureGeometry.overlaps(rug, table))
    }

    @Test fun clampKeepsInsideRoom() {
        val (x, y) = FurnitureGeometry.clampCenter(-5.0, 99.0, 2.0, 1.0, 5.0, 4.0)
        assertEquals(1.0, x, 1e-9)
        assertEquals(3.5, y, 1e-9)
    }

    @Test fun snapRoundsToStep() = assertEquals(1.5, FurnitureGeometry.snap(1.62, 0.5), 1e-9)

    @Test fun wallDistances() {
        val f = Catalog.place("armchair", 1.0, 1.0)
        val d = FurnitureGeometry.wallDistances(f.bounds, 5.0, 4.0)
        assertEquals(0.575, d.left, 1e-9)
        assertEquals(5.0 - 1.425, d.right, 1e-9)
    }

    @Test fun freeSpotAvoidsExistingFurniture() {
        val existing = listOf(Catalog.place("sofa_3", 2.5, 2.0))
        val (x, y) = FurnitureGeometry.findFreeSpot(0.5, 0.5, existing, 5.0, 4.0, false)
        val b = com.nhdeveloper.roomdraft.domain.model.Bounds(x - 0.25, y - 0.25, x + 0.25, y + 0.25)
        assertFalse(existing.first().bounds.intersects(b))
    }

    @Test fun templatesAreValid() {
        Templates.all.forEach { t ->
            val items = TemplateLayouts.build(t)
            assertTrue("${t.id} has furniture", items.isNotEmpty())
            items.forEach { f ->
                assertTrue("${t.id}/${f.name} inside room", FurnitureGeometry.isInside(f.bounds, t.lengthM, t.widthM))
            }
            assertTrue("${t.id} has no overlaps", FurnitureGeometry.overlappingPairs(items).isEmpty())
        }
    }
}
