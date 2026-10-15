package com.nhdeveloper.roomdraft.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhdeveloper.roomdraft.data.export.ProjectExport
import com.nhdeveloper.roomdraft.data.repository.ProjectRepository
import com.nhdeveloper.roomdraft.data.repository.SettingsRepository
import com.nhdeveloper.roomdraft.domain.model.AppSettings
import com.nhdeveloper.roomdraft.domain.model.CatalogItem
import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.Layout
import com.nhdeveloper.roomdraft.domain.model.Openings
import com.nhdeveloper.roomdraft.domain.model.isOpening
import com.nhdeveloper.roomdraft.domain.model.MAX_LAYOUT_NAME
import com.nhdeveloper.roomdraft.domain.model.SampleLayouts
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.effectiveDepth
import com.nhdeveloper.roomdraft.domain.model.effectiveWidth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class EditorTool { SELECT, MEASURE }

data class Pt(val x: Double, val y: Double)

data class EditorState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val project: Project? = null,
    val items: List<PlacedFurniture> = emptyList(),
    val selectedId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val tool: EditorTool = EditorTool.SELECT,
    val measureA: Pt? = null,
    val measureB: Pt? = null,
    val allowOutOfBounds: Boolean = false,
    val interacting: Boolean = false,
    val layouts: List<Layout> = emptyList(),
    val activeLayoutId: String? = null,
    val otherLayoutItems: Map<String, List<PlacedFurniture>> = emptyMap(),
    val settings: AppSettings = AppSettings()
)

class EditorViewModel(
    private val repo: ProjectRepository,
    private val settingsRepo: SettingsRepository,
    private val projectId: String
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val undoStack = ArrayDeque<List<PlacedFurniture>>()
    private val redoStack = ArrayDeque<List<PlacedFurniture>>()
    private var dragSnapshot: List<PlacedFurniture>? = null
    private var dragRaw = Pt(0.0, 0.0)
    private var resizeCorner = 0
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            try {
                val project = repo.getProject(projectId)
                var layouts: List<Layout> = emptyList()
                var items: List<PlacedFurniture> = emptyList()
                var others: Map<String, List<PlacedFurniture>> = emptyMap()
                if (project != null) {
                    layouts = repo.ensureLayouts(projectId)
                    items = repo.loadLayoutItems(layouts.first().id)
                    others = layouts.drop(1).associate { l -> l.id to repo.loadLayoutItems(l.id) }
                }
                _state.update {
                    it.copy(
                        loading = false, project = project, items = items,
                        layouts = layouts, activeLayoutId = layouts.firstOrNull()?.id, otherLayoutItems = others,
                        loadError = if (project == null) "This project no longer exists." else null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadError = "Could not load project: ${e.message ?: "unknown error"}") }
            }
        }
    }

    private val items: List<PlacedFurniture> get() = _state.value.items

    // ---------- selection / tools ----------
    fun select(id: String?) = _state.update { it.copy(selectedId = id) }

    fun setTool(tool: EditorTool) = _state.update { it.copy(tool = tool, measureA = null, measureB = null) }

    fun clearMeasure() = _state.update { it.copy(measureA = null, measureB = null) }

    fun measureTap(x: Double, y: Double) {
        val s = _state.value
        val step = s.settings.gridSpacingCm / 100.0
        val p = if (s.settings.snapToGrid) Pt(FurnitureGeometry.snap(x, step), FurnitureGeometry.snap(y, step)) else Pt(x, y)
        _state.update {
            when {
                it.measureA == null -> it.copy(measureA = p, measureB = null)
                it.measureB == null -> it.copy(measureB = p)
                else -> it.copy(measureA = p, measureB = null)
            }
        }
    }

    fun setAllowOutOfBounds(value: Boolean) = _state.update { it.copy(allowOutOfBounds = value) }
    fun setShowGrid(v: Boolean) { viewModelScope.launch { settingsRepo.setShowGrid(v) } }
    fun setSnap(v: Boolean) { viewModelScope.launch { settingsRepo.setSnapToGrid(v) } }
    fun setGridSpacing(cm: Int) { viewModelScope.launch { settingsRepo.setGridSpacing(cm) } }

    // ---------- placement helpers ----------
    private fun place(item: PlacedFurniture, rawX: Double, rawY: Double, snap: Boolean): PlacedFurniture {
        val s = _state.value
        val project = s.project ?: return item
        if (item.isOpening) return Openings.snapToWall(item, rawX, rawY, project.lengthM, project.widthM)
        val ew = item.effectiveWidth
        val eh = item.effectiveDepth
        var cx = rawX
        var cy = rawY
        if (snap && s.settings.snapToGrid) {
            val step = s.settings.gridSpacingCm / 100.0
            cx = FurnitureGeometry.snap(rawX - ew / 2.0, step) + ew / 2.0
            cy = FurnitureGeometry.snap(rawY - eh / 2.0, step) + eh / 2.0
        }
        if (!s.allowOutOfBounds) {
            val (x, y) = FurnitureGeometry.clampCenter(cx, cy, ew, eh, project.lengthM, project.widthM)
            cx = x
            cy = y
        }
        return item.copy(centerX = cx, centerY = cy)
    }

    private fun commit(newItems: List<PlacedFurniture>, before: List<PlacedFurniture> = _state.value.items) {
        if (newItems == before) {
            _state.update { it.copy(items = newItems) }
            return
        }
        undoStack.addLast(before)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        _state.update { it.copy(items = newItems, canUndo = true, canRedo = false) }
        persist()
    }

    private fun persist() {
        val st = _state.value
        val layoutId = st.activeLayoutId ?: return
        val snapshot = st.items
        viewModelScope.launch {
            withContext(NonCancellable) {
                saveMutex.withLock {
                    try {
                        repo.saveLayoutItems(layoutId, projectId, snapshot)
                    } catch (e: Exception) {
                        _messages.send("Could not save changes")
                    }
                }
            }
        }
    }

    // ---------- drag / resize ----------
    fun beginDrag(id: String) {
        val item = items.firstOrNull { it.id == id } ?: return
        dragSnapshot = items
        dragRaw = Pt(item.centerX, item.centerY)
        _state.update { it.copy(selectedId = id, interacting = true) }
    }

    fun dragBy(dx: Double, dy: Double) {
        val id = _state.value.selectedId ?: return
        if (dragSnapshot == null) return
        dragRaw = Pt(dragRaw.x + dx, dragRaw.y + dy)
        _state.update { s ->
            s.copy(items = s.items.map { if (it.id == id) place(it, dragRaw.x, dragRaw.y, snap = true) else it })
        }
    }

    fun beginResize(id: String, corner: Int) {
        if (items.none { it.id == id }) return
        dragSnapshot = items
        resizeCorner = corner
        _state.update { it.copy(selectedId = id, interacting = true) }
    }

    /** px/py are the pointer position in room coordinates (meters). */
    fun resizeTo(px: Double, py: Double) {
        val id = _state.value.selectedId ?: return
        val orig = dragSnapshot?.firstOrNull { it.id == id } ?: return
        val s = _state.value
        val project = s.project ?: return
        val b = orig.bounds
        val oppX = if (resizeCorner == 0 || resizeCorner == 3) b.right else b.left
        val oppY = if (resizeCorner == 0 || resizeCorner == 1) b.bottom else b.top
        var tx = px
        var ty = py
        if (s.settings.snapToGrid) {
            val step = s.settings.gridSpacingCm / 100.0
            tx = FurnitureGeometry.snap(tx, step)
            ty = FurnitureGeometry.snap(ty, step)
        }
        if (!s.allowOutOfBounds) {
            tx = tx.coerceIn(0.0, project.lengthM)
            ty = ty.coerceIn(0.0, project.widthM)
        }
        val minS = FurnitureGeometry.MIN_SIZE
        val w = maxOf(minS, kotlin.math.abs(tx - oppX))
        val h = maxOf(minS, kotlin.math.abs(ty - oppY))
        val left = if (tx >= oppX) oppX else oppX - w
        val top = if (ty >= oppY) oppY else oppY - h
        val horizontal = orig.rotationDeg % 180 == 0
        val resized = orig.copy(
            centerX = left + w / 2.0,
            centerY = top + h / 2.0,
            widthM = if (horizontal) w else h,
            depthM = if (horizontal) h else w
        )
        _state.update { st -> st.copy(items = st.items.map { if (it.id == id) resized else it }) }
    }

    fun endDrag() {
        val before = dragSnapshot ?: return
        dragSnapshot = null
        _state.update { it.copy(interacting = false) }
        commit(_state.value.items, before)
    }

    // ---------- item actions ----------
    private fun updateItem(id: String, transform: (PlacedFurniture) -> PlacedFurniture) {
        val before = items
        commit(before.map { if (it.id == id) transform(it) else it }, before)
    }

    fun rotateSelected() {
        val id = _state.value.selectedId ?: return
        val project = _state.value.project ?: return
        updateItem(id) { f ->
            if (f.isOpening) Openings.nextWall(f, project.lengthM, project.widthM)
            else {
                val r = f.copy(rotationDeg = (f.rotationDeg + 90) % 360)
                place(r, r.centerX, r.centerY, snap = false)
            }
        }
    }

    fun duplicateSelected() {
        val s = _state.value
        val src = s.items.firstOrNull { it.id == s.selectedId } ?: return
        val copy = src.copy(id = UUID.randomUUID().toString())
        val moved = place(copy, src.centerX + 0.3, src.centerY + 0.3, snap = false)
        commit(s.items + moved)
        select(moved.id)
    }

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        commit(items.filterNot { it.id == id })
        select(null)
    }

    fun applyProperties(id: String, colorArgb: Long, rotationDeg: Int, widthM: Double, depthM: Double) {
        updateItem(id) { f ->
            val changed = f.copy(colorArgb = colorArgb, rotationDeg = rotationDeg, widthM = widthM, depthM = depthM)
            place(changed, f.centerX, f.centerY, snap = false)
        }
    }

    fun addFurniture(cat: CatalogItem, colorArgb: Long, rotationDeg: Int, widthM: Double, depthM: Double) {
        val project = _state.value.project ?: return
        val base = PlacedFurniture(
            UUID.randomUUID().toString(), cat.id, cat.name,
            project.lengthM / 2.0, project.widthM / 2.0, widthM, depthM, rotationDeg, colorArgb
        )
        val placed = if (cat.opening) {
            Openings.freeSlot(base, items, project.lengthM, project.widthM)
        } else {
            val (x, y) = FurnitureGeometry.findFreeSpot(
                base.effectiveWidth, base.effectiveDepth, items, project.lengthM, project.widthM, cat.floorCovering
            )
            place(base, x, y, snap = true)
        }
        val newItems = items + placed
        commit(newItems)
        select(placed.id)
        if (!cat.floorCovering && !cat.opening && FurnitureGeometry.overlappingIds(newItems).contains(placed.id)) {
            viewModelScope.launch { _messages.send("No free space left, so ${cat.name} overlaps other furniture") }
        }
    }

    // ---------- undo / redo ----------
    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        redoStack.addLast(items)
        applyHistory(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(items)
        applyHistory(next)
    }

    private fun applyHistory(list: List<PlacedFurniture>) {
        _state.update {
            it.copy(
                items = list,
                selectedId = it.selectedId?.takeIf { id -> list.any { f -> f.id == id } },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        persist()
    }

    // ---------- layouts ----------
    fun switchLayout(id: String) {
        val s = _state.value
        val oldId = s.activeLayoutId ?: return
        if (id == oldId || s.layouts.none { it.id == id }) return
        undoStack.clear()
        redoStack.clear()
        _state.update { st ->
            st.copy(
                activeLayoutId = id,
                items = st.otherLayoutItems[id].orEmpty(),
                otherLayoutItems = st.otherLayoutItems - id + (oldId to st.items),
                selectedId = null, canUndo = false, canRedo = false, measureA = null, measureB = null
            )
        }
    }

    /** variant == null creates an empty layout; 0..2 start from a sample arrangement. */
    fun createLayout(variant: Int?) {
        val s = _state.value
        val project = s.project ?: return
        val oldId = s.activeLayoutId ?: return
        val name = if (variant == null) "Layout ${s.layouts.size + 1}"
        else SampleLayouts.names(project.roomType)[variant.coerceIn(0, 2)]
        val items = if (variant == null) emptyList()
        else SampleLayouts.build(project.roomType, variant, project.lengthM, project.widthM)
        viewModelScope.launch {
            try {
                val (layout, fresh) = repo.addLayout(projectId, name, items)
                undoStack.clear()
                redoStack.clear()
                _state.update { st ->
                    st.copy(
                        layouts = st.layouts + layout,
                        otherLayoutItems = st.otherLayoutItems + (oldId to st.items),
                        activeLayoutId = layout.id, items = fresh,
                        selectedId = null, canUndo = false, canRedo = false, measureA = null, measureB = null
                    )
                }
            } catch (e: Exception) {
                _messages.send("Could not create layout")
            }
        }
    }

    fun duplicateLayout(id: String) {
        val s = _state.value
        val src = s.layouts.firstOrNull { it.id == id } ?: return
        val srcItems = if (id == s.activeLayoutId) s.items else s.otherLayoutItems[id].orEmpty()
        viewModelScope.launch {
            try {
                val (layout, fresh) = repo.addLayout(projectId, "${src.name} (Copy)".take(MAX_LAYOUT_NAME), srcItems)
                _state.update { it.copy(layouts = it.layouts + layout, otherLayoutItems = it.otherLayoutItems + (layout.id to fresh)) }
                _messages.send("Created \"${layout.name}\"")
            } catch (e: Exception) {
                _messages.send("Could not duplicate layout")
            }
        }
    }

    fun renameLayout(id: String, name: String) {
        val n = name.trim().take(MAX_LAYOUT_NAME)
        if (n.isEmpty()) return
        viewModelScope.launch {
            try {
                repo.renameLayout(id, n)
                _state.update { st -> st.copy(layouts = st.layouts.map { l -> if (l.id == id) l.copy(name = n) else l }) }
            } catch (e: Exception) {
                _messages.send("Could not rename layout")
            }
        }
    }

    fun deleteLayout(id: String) {
        if (_state.value.layouts.size <= 1) {
            viewModelScope.launch { _messages.send("A project needs at least one layout") }
            return
        }
        viewModelScope.launch {
            try {
                repo.deleteLayout(id, projectId)
                _state.update { st ->
                    val remaining = st.layouts.filterNot { it.id == id }
                    if (id == st.activeLayoutId) {
                        val next = remaining.first()
                        undoStack.clear()
                        redoStack.clear()
                        st.copy(
                            layouts = remaining, activeLayoutId = next.id,
                            items = st.otherLayoutItems[next.id].orEmpty(),
                            otherLayoutItems = st.otherLayoutItems - next.id,
                            selectedId = null, canUndo = false, canRedo = false
                        )
                    } else {
                        st.copy(layouts = remaining, otherLayoutItems = st.otherLayoutItems - id)
                    }
                }
            } catch (e: Exception) {
                _messages.send("Could not delete layout")
            }
        }
    }

    // ---------- save / export ----------
    fun saveNow() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                saveMutex.withLock {
                    try {
                        val st = _state.value
                        val id = st.activeLayoutId
                        if (id != null) repo.saveLayoutItems(id, projectId, st.items)
                        _messages.send("Saved")
                    } catch (e: Exception) {
                        _messages.send("Could not save changes")
                    }
                }
            }
        }
    }

    /** Waits for pending saves, then reads every layout from the database. */
    suspend fun buildExport(): ProjectExport? = try {
        saveMutex.withLock { repo.buildExport(projectId) }
    } catch (e: Exception) {
        null
    }
}
