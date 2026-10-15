package com.nhdeveloper.roomdraft

import com.nhdeveloper.roomdraft.data.export.ExportedLayout
import com.nhdeveloper.roomdraft.data.export.ImportResult
import com.nhdeveloper.roomdraft.data.export.ProjectExport
import com.nhdeveloper.roomdraft.data.export.ProjectJson
import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectJsonTest {
    private fun sample() = ProjectExport(
        "Living", RoomType.LIVING_ROOM, 5.0, 4.0, null, 123L,
        listOf(
            ExportedLayout("A", listOf(Catalog.place("sofa_2", 1.0, 1.0), Catalog.place("rug", 2.0, 2.0, 90))),
            ExportedLayout("B", emptyList())
        )
    )

    @Test fun roundTripKeepsEverything() {
        val result = ProjectJson.parse(ProjectJson.toJson(sample())) as ImportResult.Success
        assertEquals("Living", result.data.name)
        assertEquals(RoomType.LIVING_ROOM, result.data.roomType)
        assertEquals(5.0, result.data.lengthM, 1e-9)
        assertEquals(2, result.data.layouts.size)
        assertEquals("A", result.data.layouts[0].name)
        assertEquals(2, result.data.layouts[0].furniture.size)
        assertEquals(90, result.data.layouts[0].furniture[1].rotationDeg)
        assertTrue(result.warnings.isEmpty())
    }

    @Test fun rejectsGarbage() = assertTrue(ProjectJson.parse("not json") is ImportResult.Failure)

    @Test fun rejectsOtherFormats() = assertTrue(ProjectJson.parse("{\"format\":\"something-else\"}") is ImportResult.Failure)

    @Test fun rejectsNewerVersion() {
        val text = ProjectJson.toJson(sample()).replace("\"version\": 1", "\"version\": 99")
        assertTrue(ProjectJson.parse(text) is ImportResult.Failure)
    }

    @Test fun rejectsInvalidDimensions() {
        val text = ProjectJson.toJson(sample()).replace("\"lengthMeters\": 5", "\"lengthMeters\": -5")
        assertTrue(ProjectJson.parse(text) is ImportResult.Failure)
    }

    @Test fun skipsInvalidFurnitureWithWarning() {
        val text = ProjectJson.toJson(sample()).replace("\"rotationDeg\": 90", "\"rotationDeg\": 45")
        val result = ProjectJson.parse(text) as ImportResult.Success
        assertEquals(1, result.data.layouts[0].furniture.size)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test fun importedItemsGetFreshIds() {
        val original = sample()
        val result = ProjectJson.parse(ProjectJson.toJson(original)) as ImportResult.Success
        val oldIds = original.layouts.flatMap { it.furniture }.map { it.id }.toSet()
        assertTrue(result.data.layouts.flatMap { it.furniture }.none { it.id in oldIds })
    }
}
