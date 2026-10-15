package com.nhdeveloper.roomdraft.data.repository

import androidx.room.withTransaction
import com.nhdeveloper.roomdraft.data.export.ExportedLayout
import com.nhdeveloper.roomdraft.data.export.ProjectExport
import com.nhdeveloper.roomdraft.data.local.AppDatabase
import com.nhdeveloper.roomdraft.data.local.FurnitureEntity
import com.nhdeveloper.roomdraft.data.local.LayoutEntity
import com.nhdeveloper.roomdraft.data.local.ProjectEntity
import com.nhdeveloper.roomdraft.domain.model.Layout
import com.nhdeveloper.roomdraft.domain.model.MAX_LAYOUT_NAME
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.domain.model.ProjectRules
import com.nhdeveloper.roomdraft.domain.model.RoomType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private fun ProjectEntity.toDomain() = Project(
    id = id, name = name, roomType = RoomType.fromName(roomType),
    lengthM = lengthMeters, widthM = widthMeters, templateId = templateId,
    createdAt = createdAt, modifiedAt = modifiedAt
)

private fun LayoutEntity.toDomain() = Layout(id, projectId, name, sortIndex)

private fun FurnitureEntity.toDomain() = PlacedFurniture(
    id = id, catalogId = catalogId, name = name, centerX = centerX, centerY = centerY,
    widthM = widthM, depthM = depthM, rotationDeg = rotationDeg, colorArgb = colorArgb
)

private fun PlacedFurniture.toEntity(layoutId: String, index: Int) = FurnitureEntity(
    id = id, layoutId = layoutId, catalogId = catalogId, name = name, centerX = centerX, centerY = centerY,
    widthM = widthM, depthM = depthM, rotationDeg = rotationDeg, colorArgb = colorArgb, sortIndex = index
)

/** Furniture ids are primary keys, so every copy needs fresh ids. */
private fun List<PlacedFurniture>.withFreshIds() = map { it.copy(id = UUID.randomUUID().toString()) }

class ProjectRepository(private val db: AppDatabase) {
    private val dao = db.projectDao()
    private val layoutDao = db.layoutDao()
    private val furnitureDao = db.furnitureDao()

    fun observeAll(): Flow<List<Project>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(id: String): Flow<Project?> = dao.observe(id).map { it?.toDomain() }

    suspend fun getProject(id: String): Project? = dao.get(id)?.toDomain()

    suspend fun create(name: String, type: RoomType, lengthM: Double, widthM: Double, templateId: String?): Project =
        createWithFurniture(name, type, lengthM, widthM, templateId, emptyList())

    suspend fun createWithFurniture(
        name: String, type: RoomType, lengthM: Double, widthM: Double,
        templateId: String?, furniture: List<PlacedFurniture>
    ): Project {
        val now = System.currentTimeMillis()
        val entity = ProjectEntity(
            id = UUID.randomUUID().toString(), name = name.trim(), roomType = type.name,
            lengthMeters = lengthM, widthMeters = widthM, templateId = templateId,
            createdAt = now, modifiedAt = now
        )
        db.withTransaction {
            dao.insert(entity)
            insertLayoutWithItems(entity.id, "Layout 1", 0, now, furniture.withFreshIds())
        }
        return entity.toDomain()
    }

    private suspend fun insertLayoutWithItems(
        projectId: String, name: String, sortIndex: Int, now: Long, items: List<PlacedFurniture>
    ): Layout {
        val layout = LayoutEntity(UUID.randomUUID().toString(), projectId, name, sortIndex, now)
        layoutDao.insert(layout)
        if (items.isNotEmpty()) furnitureDao.insertAll(items.mapIndexed { i, f -> f.toEntity(layout.id, i) })
        return layout.toDomain()
    }

    suspend fun rename(id: String, newName: String) {
        val current = dao.get(id) ?: return
        dao.update(current.copy(name = newName.trim().take(ProjectRules.MAX_NAME), modifiedAt = System.currentTimeMillis()))
    }

    suspend fun duplicate(id: String): Project? {
        val source = dao.get(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (Copy)".take(ProjectRules.MAX_NAME),
            createdAt = now, modifiedAt = now
        )
        db.withTransaction {
            dao.insert(copy)
            layoutDao.getForProject(id).forEach { l ->
                val items = furnitureDao.getForLayout(l.id).map { it.toDomain() }.withFreshIds()
                insertLayoutWithItems(copy.id, l.name, l.sortIndex, now, items)
            }
        }
        return copy.toDomain()
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()

    // ---------------- layouts ----------------
    suspend fun ensureLayouts(projectId: String): List<Layout> {
        val existing = layoutDao.getForProject(projectId)
        if (existing.isNotEmpty()) return existing.map { it.toDomain() }
        return listOf(insertLayoutWithItems(projectId, "Layout 1", 0, System.currentTimeMillis(), emptyList()))
    }

    suspend fun loadLayoutItems(layoutId: String): List<PlacedFurniture> =
        furnitureDao.getForLayout(layoutId).map { it.toDomain() }

    /** Adds a layout after the existing ones. Returns the layout and its items (with fresh ids). */
    suspend fun addLayout(projectId: String, name: String, items: List<PlacedFurniture>): Pair<Layout, List<PlacedFurniture>> {
        val fresh = items.withFreshIds()
        var result: Layout? = null
        db.withTransaction {
            val next = (layoutDao.getForProject(projectId).maxOfOrNull { it.sortIndex } ?: -1) + 1
            result = insertLayoutWithItems(projectId, name.trim().take(MAX_LAYOUT_NAME), next, System.currentTimeMillis(), fresh)
            dao.touch(projectId, System.currentTimeMillis())
        }
        return requireNotNull(result) to fresh
    }

    suspend fun renameLayout(layoutId: String, name: String) =
        layoutDao.rename(layoutId, name.trim().take(MAX_LAYOUT_NAME))

    suspend fun deleteLayout(layoutId: String, projectId: String) {
        db.withTransaction {
            layoutDao.delete(layoutId)
            dao.touch(projectId, System.currentTimeMillis())
        }
    }

    /** Replaces all furniture of a layout and bumps the project's modified time, atomically. */
    suspend fun saveLayoutItems(layoutId: String, projectId: String, items: List<PlacedFurniture>) {
        db.withTransaction {
            if (dao.get(projectId) == null) return@withTransaction
            if (layoutDao.getForProject(projectId).none { it.id == layoutId }) return@withTransaction
            furnitureDao.deleteForLayout(layoutId)
            furnitureDao.insertAll(items.mapIndexed { i, f -> f.toEntity(layoutId, i) })
            dao.touch(projectId, System.currentTimeMillis())
        }
    }

    /** Furniture of the first layout, used for thumbnails and the overview. */
    fun observeFurniture(projectId: String): Flow<List<PlacedFurniture>> =
        furnitureDao.observeFirstLayout(projectId).map { list -> list.map { it.toDomain() } }

    fun observeAllFurniture(): Flow<Map<String, List<PlacedFurniture>>> =
        furnitureDao.observeAllFirstLayouts().map { rows ->
            rows.groupBy { it.projectId }.mapValues { entry -> entry.value.map { it.furniture.toDomain() } }
        }

    // ---------------- export / import ----------------
    suspend fun buildExport(projectId: String): ProjectExport? {
        val p = dao.get(projectId) ?: return null
        val layouts = ensureLayouts(projectId).map { l -> ExportedLayout(l.name, loadLayoutItems(l.id)) }
        return ProjectExport(p.name, RoomType.fromName(p.roomType), p.lengthMeters, p.widthMeters, p.templateId, p.createdAt, layouts)
    }

    /** Always creates a brand new project; nothing existing is ever overwritten. */
    suspend fun importProject(data: ProjectExport): Project {
        val now = System.currentTimeMillis()
        val entity = ProjectEntity(
            id = UUID.randomUUID().toString(), name = data.name, roomType = data.roomType.name,
            lengthMeters = data.lengthM, widthMeters = data.widthM, templateId = data.templateId,
            createdAt = data.createdAt, modifiedAt = now
        )
        db.withTransaction {
            dao.insert(entity)
            data.layouts.forEachIndexed { i, l -> insertLayoutWithItems(entity.id, l.name, i, now, l.furniture.withFreshIds()) }
        }
        return entity.toDomain()
    }
}
