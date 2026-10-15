@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.domain.model.RoomType
import com.nhdeveloper.roomdraft.rendering.room3d.FloorStyle
import com.nhdeveloper.roomdraft.rendering.room3d.CameraState
import com.nhdeveloper.roomdraft.rendering.room3d.Projector
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.drawRoom
import com.nhdeveloper.roomdraft.rendering.story.Story
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun Preview3DScreen(
    project: Project,
    items: List<PlacedFurniture>,
    selectedId: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
    onBeginDrag: (String) -> Unit,
    onDragBy: (Double, Double) -> Unit,
    onEndDrag: () -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    var camera by remember { mutableStateOf(CameraState.default(project.lengthM, project.widthM)) }
    var autoRotate by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var story by remember { mutableStateOf<Story?>(null) }
    var storyOn by remember { mutableStateOf(false) }
    var storyTime by remember { mutableStateOf(0.0) }
    var storyHint by remember { mutableStateOf<String?>(null) }
    val storyNow by rememberUpdatedState(storyOn)
    val itemsNow by rememberUpdatedState(items)
    val editNow by rememberUpdatedState(editMode)
    val overlapIds = remember(items) { FurnitureGeometry.overlappingIds(items) }
    val selected = items.firstOrNull { it.id == selectedId }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    // vitrified tiles in wet rooms and the kitchen (as in most Indian homes), marble in the living room, wood elsewhere
    val floorStyle = when (project.roomType) {
        RoomType.KITCHEN, RoomType.BATHROOM -> FloorStyle.TILE
        RoomType.LIVING_ROOM -> FloorStyle.MARBLE
        else -> FloorStyle.WOOD
    }

    LaunchedEffect(autoRotate) {
        if (autoRotate) {
            var last = 0L
            while (true) {
                val t = withFrameNanos { it }
                if (last != 0L) camera = camera.orbit((t - last) / 1e9 * 0.5, 0.0)
                last = t
            }
        }
    }

    LaunchedEffect(storyOn) {
        if (storyOn) {
            val st = story
            var last = 0L
            while (st != null) {
                val t = withFrameNanos { it }
                if (last != 0L) storyTime += (t - last) / 1e9
                last = t
                if (storyTime >= st.duration + 2.0) { storyOn = false; break }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("3D View") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        FilterChip(
                            selected = editMode,
                            onClick = {
                                editMode = !editMode
                                if (editMode) {
                                    autoRotate = false
                                } else {
                                    onSelect(null)
                                }
                            },
                            label = { Text("Edit") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = false, onClick = onBack, label = { Text("2D") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = true, onClick = {}, label = { Text("3D") })
                        Spacer(Modifier.width(12.dp))
                    }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                if (dark) listOf(Color(0xFF0F1513), Color(0xFF26302B))
                                else listOf(Color(0xFFCFDCD5), Color(0xFFF3F6F4))
                            )
                        )
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (storyNow) {
                                    do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                                    return@awaitEachGesture
                                }
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                var dragId: String? = null
                                var planeZ = 0.0
                                var last: Pair<Double, Double>? = null
                                var started = false
                                var travelled = 0f
                                if (editNow) {
                                    val hit = Room3D.pickItem(itemsNow, camera, w, h, down.position.x, down.position.y)
                                    val f = itemsNow.firstOrNull { it.id == hit }
                                    if (f != null) {
                                        dragId = f.id
                                        planeZ = Room3D.grabPlaneZ(f)
                                        last = Projector(camera, w, h).floorPoint(down.position.x, down.position.y, planeZ)
                                        onSelect(f.id)
                                    }
                                }
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed >= 2) {
                                        if (started) { onEndDrag(); started = false }
                                        dragId = null
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        val focal = min(size.width, size.height) * 1.1
                                        camera = camera.zoom(zoom.toDouble()).pan(pan.x.toDouble(), pan.y.toDouble(), focal)
                                        travelled += 100f
                                        event.changes.forEach { it.consume() }
                                    } else if (pressed == 1) {
                                        val ch = event.changes.first { it.pressed }
                                        val d = ch.positionChange()
                                        travelled += hypot(d.x, d.y)
                                        val id = dragId
                                        if (id != null) {
                                            if (!started && travelled > 6f) { started = true; onBeginDrag(id) }
                                            if (started) {
                                                val now = Projector(camera, w, h).floorPoint(ch.position.x, ch.position.y, planeZ)
                                                val prev = last
                                                if (now != null && prev != null) onDragBy(now.first - prev.first, now.second - prev.second)
                                                if (now != null) last = now
                                            }
                                        } else {
                                            camera = camera.orbit(-d.x * 0.008, d.y * 0.008)
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (started) onEndDrag()
                                else if (editNow && dragId == null && travelled < 10f) onSelect(null)
                            }
                        }
                ) {
                    val st = story
                    val playing = storyOn && st != null
                    val tNow = storyTime.coerceAtMost(st?.duration ?: 0.0)
                    drawRoom(
                        project.lengthM, project.widthM, items, if (playing) st!!.cameraAt(tNow) else camera,
                        floorColor = when (floorStyle) {
                            FloorStyle.WOOD -> if (dark) Color(0xFF9A7A52) else Color(0xFFC89B64)
                            FloorStyle.TILE -> if (dark) Color(0xFFB9B2A4) else Color(0xFFE8E2D6)
                            FloorStyle.MARBLE -> if (dark) Color(0xFFC4C0B8) else Color(0xFFF1EEE8)
                        },
                        gridColor = Color.Black.copy(alpha = 0.12f),
                        floorStyle = floorStyle,
                        dark = dark,
                        selectedId = if (playing) null else selectedId,
                        overlapIds = if (playing) emptySet() else overlapIds,
                        actor = if (playing) st!!.actorAt(tNow) else null,
                        doorAngles = if (playing && st!!.doorId != null) mapOf(st.doorId!! to st.doorAngleAt(tNow)) else emptyMap()
                    )
                }
                val stNow = story
                if (storyOn && stNow != null) {
                    val tShow = storyTime.coerceAtMost(stNow.duration)
                    LinearProgressIndicator(
                        progress = { (tShow / stNow.duration).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            stNow.captionAt(tShow),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                }
                Surface(color = scheme.surface, shadowElevation = 4.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        Text(
                            storyHint ?: if (storyOn) "Story playing: she opens the door, looks at the table, watches TV and sleeps"
                            else if (editMode) "Edit on: drag furniture to move it · empty space to orbit · pinch to zoom"
                            else "Drag to orbit · pinch to zoom · two fingers to pan · tap Edit to move furniture",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                        )
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Action(Icons.Outlined.ViewInAr, if (autoRotate) "Stop" else "Spin", autoRotate) { autoRotate = !autoRotate }
                            Action(Icons.Outlined.Weekend, if (storyOn) "Stop story" else "Story", storyOn) {
                                if (storyOn) {
                                    storyOn = false
                                } else {
                                    val built = Story.build(project.lengthM, project.widthM, items)
                                    if (built == null) {
                                        storyHint = "Add a table, sofa or bed (and a door) to play the story."
                                    } else {
                                        storyHint = null
                                        story = built
                                        storyTime = 0.0
                                        editMode = false
                                        autoRotate = false
                                        onSelect(null)
                                        storyOn = true
                                    }
                                }
                            }
                            Action(Icons.Outlined.ZoomIn, "Zoom in", false) { camera = camera.zoom(1.25) }
                            Action(Icons.Outlined.ZoomOut, "Zoom out", false) { camera = camera.zoom(0.8) }
                            Action(Icons.Outlined.CenterFocusStrong, "Reset", false) {
                                autoRotate = false
                                camera = CameraState.default(project.lengthM, project.widthM)
                            }
                        }
                        if (editMode) {
                            Text(
                                selected?.name ?: "Tap a piece of furniture to select it",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                            )
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Action(Icons.AutoMirrored.Filled.Undo, "Undo", false, enabled = canUndo) { onUndo() }
                                Action(Icons.AutoMirrored.Filled.Redo, "Redo", false, enabled = canRedo) { onRedo() }
                                Action(Icons.Outlined.RotateRight, "Rotate", false, enabled = selected != null) { onRotate() }
                                Action(Icons.Outlined.ContentCopy, "Duplicate", false, enabled = selected != null) { onDuplicate() }
                                Action(Icons.Outlined.Delete, "Delete", false, enabled = selected != null) { onDelete() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val base = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val tint = if (enabled) base else base.copy(alpha = 0.38f)
    Column(
        Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
