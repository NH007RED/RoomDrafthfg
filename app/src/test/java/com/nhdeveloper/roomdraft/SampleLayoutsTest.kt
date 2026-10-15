package com.nhdeveloper.roomdraft

import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.RoomType
import com.nhdeveloper.roomdraft.domain.model.SampleLayouts
import com.nhdeveloper.roomdraft.domain.model.bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleLayoutsTest {
    private val rooms = listOf(4.0 to 3.5, 3.0 to 3.0, 6.0 to 4.5, 2.5 to 2.0)

    @Test fun everySampleFitsInsideTheRoomWithoutOverlap() {
        for (type in RoomType.values()) for (variant in 0..2) for ((l, w) in rooms) {
            val items = SampleLayouts.build(type, variant, l, w)
            items.forEach {
                assertTrue("$type/$variant ${it.name} inside ${l}x$w", FurnitureGeometry.isInside(it.bounds, l, w))
            }
            assertTrue("$type/$variant overlaps in ${l}x$w", FurnitureGeometry.overlappingPairs(items).isEmpty())
        }
    }

    @Test fun typicalRoomsGetFurniture() {
        for (type in RoomType.values()) for (variant in 0..2) {
            assertTrue("$type/$variant is empty", SampleLayouts.build(type, variant, 4.0, 3.5).isNotEmpty())
        }
    }

    @Test fun threeNamesPerType() {
        RoomType.values().forEach { assertEquals(3, SampleLayouts.names(it).size) }
    }

    @Test fun buildsUseFreshIds() {
        val a = SampleLayouts.build(RoomType.BEDROOM, 0, 4.0, 3.5).map { it.id }.toSet()
        val b = SampleLayouts.build(RoomType.BEDROOM, 0, 4.0, 3.5).map { it.id }.toSet()
        assertTrue(a.intersect(b).isEmpty())
    }
}
