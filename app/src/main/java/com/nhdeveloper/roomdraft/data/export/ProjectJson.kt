package com.nhdeveloper.roomdraft.data.export

import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.DimensionRules
import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.MAX_LAYOUT_NAME
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.ProjectRules
import com.nhdeveloper.roomdraft.domain.model.RoomType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

data class ExportedLayout(val name: String, val furniture: List<PlacedFurniture>)

data class ProjectExport(
    val name: String,
    val roomType: RoomType,
    val lengthM: Double,
    val widthM: Double,
    val templateId: String?,
    val createdAt: Long,
    val layouts: List<ExportedLayout>
)

sealed interface ImportResult {
    data class Success(val data: ProjectExport, val warnings: List<String>) : ImportResult
    data class Failure(val message: String) : ImportResult
}

object ProjectJson {
    const val FORMAT = "roomdraft-project"
    const val VERSION = 1
    const val MAX_FILE_BYTES = 5_000_000
    private const val MAX_LAYOUTS = 20
    private const val MAX_ITEMS = 500
    private val ROTATIONS = setOf(0, 90, 180, 270)

    fun toJson(e: ProjectExport): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put(
            "project",
            JSONObject()
                .put("name", e.name)
                .put("roomType", e.roomType.name)
                .put("lengthMeters", e.lengthM)
                .put("widthMeters", e.widthM)
                .put("templateId", e.templateId ?: JSONObject.NULL)
                .put("createdAt", e.createdAt)
        )
        val layouts = JSONArray()
        e.layouts.forEach { layout ->
            val items = JSONArray()
            layout.furniture.forEach { f ->
                items.put(
                    JSONObject()
                        .put("catalogId", f.catalogId)
                        .put("name", f.name)
                        .put("centerX", f.centerX)
                        .put("centerY", f.centerY)
                        .put("widthM", f.widthM)
                        .put("depthM", f.depthM)
                        .put("rotationDeg", f.rotationDeg)
                        .put("colorArgb", f.colorArgb)
                )
            }
            layouts.put(JSONObject().put("name", layout.name).put("furniture", items))
        }
        root.put("layouts", layouts)
        return root.toString(2)
    }

    fun parse(text: String): ImportResult {
        val root = try {
            JSONObject(text)
        } catch (ex: JSONException) {
            return ImportResult.Failure("This file is not valid JSON.")
        }
        if (root.optString("format") != FORMAT) {
            return ImportResult.Failure("This is not a RoomDraft project file.")
        }
        val version = root.optInt("version", -1)
        if (version < 1) return ImportResult.Failure("Unsupported file version.")
        if (version > VERSION) {
            return ImportResult.Failure("This file was made by a newer version of RoomDraft. Please update the app.")
        }
        val project = root.optJSONObject("project") ?: return ImportResult.Failure("The project details are missing.")
        val length = DimensionRules.validate(project.opt("lengthMeters").toString(), MeasureUnit.METERS).first
        val width = DimensionRules.validate(project.opt("widthMeters").toString(), MeasureUnit.METERS).first
        if (length == null || width == null) {
            return ImportResult.Failure("The room dimensions in this file are invalid.")
        }
        val warnings = mutableListOf<String>()
        var name = project.optString("name").trim().take(ProjectRules.MAX_NAME)
        if (ProjectRules.validateName(name) != null) {
            name = "Imported Room"
            warnings += "The project name was missing, so a default name was used."
        }
        val type = RoomType.fromName(project.optString("roomType"))
        val templateId = project.optString("templateId").takeIf { it.isNotBlank() && it != "null" }
        val createdAt = project.optLong("createdAt", System.currentTimeMillis())

        val layoutArray = root.optJSONArray("layouts") ?: JSONArray()
        if (layoutArray.length() > MAX_LAYOUTS) {
            return ImportResult.Failure("This file has too many layouts (max $MAX_LAYOUTS).")
        }
        var skipped = 0
        var unknown = 0
        val layouts = mutableListOf<ExportedLayout>()
        for (i in 0 until layoutArray.length()) {
            val obj = layoutArray.optJSONObject(i) ?: continue
            var layoutName = obj.optString("name").trim().take(MAX_LAYOUT_NAME)
            if (layoutName.isEmpty()) layoutName = "Layout ${i + 1}"
            val arr = obj.optJSONArray("furniture") ?: JSONArray()
            if (arr.length() > MAX_ITEMS) {
                return ImportResult.Failure("A layout in this file has too many items (max $MAX_ITEMS).")
            }
            val items = mutableListOf<PlacedFurniture>()
            for (j in 0 until arr.length()) {
                val f = arr.optJSONObject(j)
                val parsed = f?.let { parseFurniture(it) }
                if (parsed == null) {
                    skipped++
                } else {
                    if (Catalog.find(parsed.catalogId) == null) unknown++
                    items += parsed
                }
            }
            layouts += ExportedLayout(layoutName, items)
        }
        if (layouts.isEmpty()) layouts += ExportedLayout("Layout 1", emptyList())
        if (skipped > 0) warnings += "$skipped invalid furniture item(s) were skipped."
        if (unknown > 0) warnings += "$unknown item(s) are not in this version's catalog and are drawn as generic furniture."
        return ImportResult.Success(
            ProjectExport(name, type, length, width, templateId, createdAt, layouts),
            warnings
        )
    }

    private fun parseFurniture(f: JSONObject): PlacedFurniture? {
        val catalogId = f.optString("catalogId").trim()
        if (catalogId.isEmpty()) return null
        val cx = f.optDouble("centerX", Double.NaN)
        val cy = f.optDouble("centerY", Double.NaN)
        val w = f.optDouble("widthM", Double.NaN)
        val d = f.optDouble("depthM", Double.NaN)
        if (!cx.isFinite() || !cy.isFinite() || !w.isFinite() || !d.isFinite()) return null
        if (kotlin.math.abs(cx) > 1000 || kotlin.math.abs(cy) > 1000) return null
        if (w !in FurnitureGeometry.MIN_SIZE..FurnitureGeometry.MAX_SIZE) return null
        if (d !in FurnitureGeometry.MIN_SIZE..FurnitureGeometry.MAX_SIZE) return null
        val rot = f.optInt("rotationDeg", 0)
        if (rot !in ROTATIONS) return null
        val color = f.optLong("colorArgb", Catalog.find(catalogId)?.defaultColor ?: 0xFFC2B09AL)
        val name = f.optString("name").trim().ifEmpty { Catalog.find(catalogId)?.name ?: "Furniture" }.take(40)
        return PlacedFurniture(UUID.randomUUID().toString(), catalogId, name, cx, cy, w, d, rot, color)
    }
}
