@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhdeveloper.roomdraft.VMFactory
import com.nhdeveloper.roomdraft.appContainer
import com.nhdeveloper.roomdraft.domain.model.CatalogItem
import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.FurniturePalette
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.domain.model.areaLabel
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.drawOrder
import com.nhdeveloper.roomdraft.domain.model.formatNumber
import com.nhdeveloper.roomdraft.ui.components.ConfirmDialog
import com.nhdeveloper.roomdraft.ui.components.category
import com.nhdeveloper.roomdraft.ui.components.drawFurniture
import com.nhdeveloper.roomdraft.ui.components.shape
import kotlin.math.floor
import kotlin.math.min

private sealed interface EditorPanel {
    object None : EditorPanel
    object Catalog : EditorPanel
    data class Add(val item: CatalogItem) : EditorPanel
    object Properties : EditorPanel
    object Grid : EditorPanel
    object ConfirmDelete : EditorPanel
    object Layouts : EditorPanel
    object Preview3D : EditorPanel
    object Export : EditorPanel
}

@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit) {
    val c = appContainer()
    val vm: EditorViewModel = viewModel(
        key = "editor_$projectId",
        factory = VMFactory { EditorViewModel(c.projects, c.settings, projectId) }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val project = state.project

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        project == null -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(state.loadError ?: "Project not found", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back") }
        }
        else -> EditorContent(state, project, vm, onBack)
    }
}

@Composable
private fun EditorContent(state: EditorState, project: Project, vm: EditorViewModel, onBack: () -> Unit) {
    var panel by remember { mutableStateOf<EditorPanel>(EditorPanel.None) }
    var resetSignal by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val unit = state.settings.defaultUnit
    val selected = state.items.firstOrNull { it.id == state.selectedId }
    val overlaps = remember(state.items) { FurnitureGeometry.overlappingPairs(state.items) }
    val outside = selected != null &&
        !FurnitureGeometry.isInside(selected.bounds, project.lengthM, project.widthM)

    BackHandler(
        enabled = panel is EditorPanel.Catalog || panel is EditorPanel.Add || panel is EditorPanel.Layouts ||
            panel is EditorPanel.Preview3D || panel is EditorPanel.Export
    ) {
        panel = if (panel is EditorPanel.Add) EditorPanel.Catalog else EditorPanel.None
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text(
                                "${unit.formatPair(project.lengthM, project.widthM)} · ${areaLabel(project.areaM2, unit)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        IconButton(onClick = vm::undo, enabled = state.canUndo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = vm::redo, enabled = state.canRedo) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PlanCanvas(state, project, vm, resetSignal, Modifier.fillMaxSize())
                    val warning = when {
                        overlaps.isNotEmpty() -> {
                            val (a, b) = overlaps.first()
                            "${a.name} overlaps ${b.name}" + if (overlaps.size > 1) " (+${overlaps.size - 1} more)" else ""
                        }
                        outside -> "${selected?.name} is outside the room"
                        else -> null
                    }
                    if (warning != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "⚠ $warning",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                if (state.tool == EditorTool.MEASURE) {
                    MeasurePanel(state, project, unit, selected, vm::clearMeasure)
                } else if (selected != null) {
                    SelectionBar(
                        item = selected, unit = unit,
                        onRotate = vm::rotateSelected,
                        onDuplicate = vm::duplicateSelected,
                        onEdit = { panel = EditorPanel.Properties },
                        onDelete = { panel = EditorPanel.ConfirmDelete }
                    )
                }
                EditorToolbar(
                    tool = state.tool,
                    onSelectTool = { vm.setTool(EditorTool.SELECT) },
                    onFurniture = { panel = EditorPanel.Catalog },
                    onMeasure = { vm.setTool(EditorTool.MEASURE) },
                    onReset = { resetSignal++ },
                    onSettings = { panel = EditorPanel.Grid },
                    onLayouts = { panel = EditorPanel.Layouts },
                    on3D = { panel = EditorPanel.Preview3D },
                    onExport = { panel = EditorPanel.Export }
                )
            }
        }

        when (val p = panel) {
            is EditorPanel.Catalog -> CatalogScreen(unit, onBack = { panel = EditorPanel.None }, onPick = { panel = EditorPanel.Add(it) })
            is EditorPanel.Add -> AddFurnitureScreen(
                item = p.item, unit = unit,
                onBack = { panel = EditorPanel.Catalog },
                onAdd = { color, rot, w, d ->
                    vm.setTool(EditorTool.SELECT)
                    vm.addFurniture(p.item, color, rot, w, d)
                    panel = EditorPanel.None
                }
            )
            is EditorPanel.Layouts -> LayoutsScreen(
                state = state, project = project,
                onBack = { panel = EditorPanel.None },
                onSwitch = vm::switchLayout, onCreate = vm::createLayout,
                onRename = vm::renameLayout, onDuplicate = vm::duplicateLayout,
                onDelete = vm::deleteLayout, onSave = vm::saveNow
            )
            is EditorPanel.Preview3D -> Preview3DScreen(
                project = project, items = state.items, selectedId = state.selectedId,
                canUndo = state.canUndo, canRedo = state.canRedo,
                onBack = { panel = EditorPanel.None },
                onSelect = vm::select, onBeginDrag = vm::beginDrag, onDragBy = vm::dragBy, onEndDrag = vm::endDrag,
                onRotate = vm::rotateSelected, onDuplicate = vm::duplicateSelected, onDelete = vm::deleteSelected,
                onUndo = vm::undo, onRedo = vm::redo
            )
            is EditorPanel.Export -> ExportScreen(state, project, vm, onBack = { panel = EditorPanel.None })
            else -> Unit
        }
    }

    if (panel is EditorPanel.Properties && selected != null) {
        PropertiesSheet(
            item = selected, unit = unit,
            onDismiss = { panel = EditorPanel.None },
            onApply = { color, rot, w, d ->
                vm.applyProperties(selected.id, color, rot, w, d)
                panel = EditorPanel.None
            }
        )
    }
    if (panel is EditorPanel.Grid) {
        GridSheet(state, vm) { panel = EditorPanel.None }
    }
    if (panel is EditorPanel.ConfirmDelete && selected != null) {
        ConfirmDialog(
            title = "Delete ${selected.name}?",
            message = "This removes it from the room. You can undo it afterwards.",
            confirmLabel = "Delete",
            onDismiss = { panel = EditorPanel.None },
            onConfirm = { vm.deleteSelected(); panel = EditorPanel.None }
        )
    }
}

// ---------------------------------------------------------------- canvas

private fun hitTest(items: List<PlacedFurniture>, x: Double, y: Double): PlacedFurniture? =
    items.drawOrder().asReversed().firstOrNull { it.bounds.contains(x, y) }

@Composable
private fun PlanCanvas(state: EditorState, project: Project, vm: EditorViewModel, resetSignal: Int, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val scaleState = remember { mutableFloatStateOf(1f) }
    val fitState = remember { mutableFloatStateOf(1f) }
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    val roomL = project.lengthM
    val roomW = project.widthM
    val unit = state.settings.defaultUnit
    val dark = scheme.background.luminance() < 0.5f

    LaunchedEffect(canvasSize, resetSignal, roomL, roomW) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val pad = with(density) { 56.dp.toPx() }
            val s = min(
                (canvasSize.width - 2 * pad) / roomL.toFloat(),
                (canvasSize.height - 2 * pad) / roomW.toFloat()
            ).coerceAtLeast(1f)
            fitState.floatValue = s
            scaleState.floatValue = s
            offsetState.value = Offset(
                (canvasSize.width - roomL.toFloat() * s) / 2f,
                (canvasSize.height - roomW.toFloat() * s) / 2f
            )
        }
    }

    val stateRef by rememberUpdatedState(state)

    val gestureModifier = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val handleRadius = 28.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val st = stateRef
            fun world(p: Offset) = Offset(
                (p.x - offsetState.value.x) / scaleState.floatValue,
                (p.y - offsetState.value.y) / scaleState.floatValue
            )
            val w0 = world(down.position)
            val selected = st.items.firstOrNull { it.id == st.selectedId }
            var mode = 0 // 0 pan, 1 drag item, 2 resize, 3 pinch
            var corner = -1
            if (st.tool == EditorTool.SELECT && selected != null) {
                val b = selected.bounds
                val s = scaleState.floatValue
                val o = offsetState.value
                val corners = listOf(
                    Offset(b.left.toFloat() * s + o.x, b.top.toFloat() * s + o.y),
                    Offset(b.right.toFloat() * s + o.x, b.top.toFloat() * s + o.y),
                    Offset(b.right.toFloat() * s + o.x, b.bottom.toFloat() * s + o.y),
                    Offset(b.left.toFloat() * s + o.x, b.bottom.toFloat() * s + o.y)
                )
                corner = corners.indexOfFirst { (it - down.position).getDistance() <= handleRadius }
                if (corner >= 0) mode = 2
            }
            val hit = hitTest(st.items, w0.x.toDouble(), w0.y.toDouble())
            if (mode != 2 && st.tool == EditorTool.SELECT && hit != null) mode = 1

            var started = false
            var moved = 0f
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    if (started && (mode == 1 || mode == 2)) vm.endDrag()
                    mode = 3
                    started = true
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid()
                    val fit = fitState.floatValue
                    val oldScale = scaleState.floatValue
                    val newScale = (oldScale * zoom).coerceIn(fit * 0.5f, fit * 12f)
                    val f = newScale / oldScale
                    offsetState.value = (offsetState.value - centroid) * f + centroid + pan
                    scaleState.floatValue = newScale
                    event.changes.forEach { it.consume() }
                } else if (mode == 3) {
                    event.changes.forEach { it.consume() }
                } else {
                    val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                    val delta = change.positionChange()
                    moved += delta.getDistance()
                    if (!started && moved > slop) {
                        started = true
                        if (mode == 1) hit?.let { vm.beginDrag(it.id) }
                        if (mode == 2) selected?.let { vm.beginResize(it.id, corner) }
                    }
                    if (started) {
                        when (mode) {
                            1 -> vm.dragBy((delta.x / scaleState.floatValue).toDouble(), (delta.y / scaleState.floatValue).toDouble())
                            2 -> {
                                val w = world(change.position)
                                vm.resizeTo(w.x.toDouble(), w.y.toDouble())
                            }
                            else -> offsetState.value = offsetState.value + delta
                        }
                        change.consume()
                    }
                }
            } while (event.changes.any { it.pressed })

            if (!started) {
                if (stateRef.tool == EditorTool.MEASURE) vm.measureTap(w0.x.toDouble(), w0.y.toDouble())
                else vm.select(hit?.id)
            } else if (mode == 1 || mode == 2) {
                vm.endDrag()
            }
        }
    }

    Canvas(
        modifier
            .background(scheme.background)
            .onSizeChanged { canvasSize = it }
            .then(gestureModifier)
    ) {
        val s = scaleState.floatValue
        val o = offsetState.value
        fun mx(x: Double) = o.x + x.toFloat() * s
        fun my(y: Double) = o.y + y.toFloat() * s
        val roomTL = Offset(mx(0.0), my(0.0))
        val roomSize = Size(roomL.toFloat() * s, roomW.toFloat() * s)

        // floor
        drawRect(if (dark) Color(0xFF8E7352) else Color(0xFFE3C9A0), roomTL, roomSize)

        // grid
        if (state.settings.showGrid) {
            val base = state.settings.gridSpacingCm / 100f * s
            var g = base
            while (g < 10f) g += base
            val gridColor = scheme.onSurface.copy(alpha = if (dark) 0.14f else 0.10f)
            var k = floor(-o.x / g).toInt()
            while (o.x + k * g <= size.width) {
                val x = o.x + k * g
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                k++
            }
            k = floor(-o.y / g).toInt()
            while (o.y + k * g <= size.height) {
                val y = o.y + k * g
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                k++
            }
        }

        // furniture
        val overlapIds = FurnitureGeometry.overlappingIds(state.items)
        state.items.drawOrder().forEach { f ->
            val center = Offset(mx(f.centerX), my(f.centerY))
            drawFurniture(
                f.category(), f.shape(), Color(f.colorArgb),
                wPx = f.widthM.toFloat() * s, hPx = f.depthM.toFloat() * s,
                center = center, rotationDeg = f.rotationDeg.toFloat(),
                itemId = f.catalogId
            )
            val b = f.bounds
            val tl = Offset(mx(b.left), my(b.top))
            val sz = Size(b.width.toFloat() * s, b.height.toFloat() * s)
            if (f.id in overlapIds) {
                drawRect(Color(0x33E53935), tl, sz)
                drawRect(Color(0xFFE53935), tl, sz, style = Stroke(2.dp.toPx()))
            }
            if (sz.width > 56.dp.toPx() && sz.height > 22.dp.toPx()) {
                val textColor = if (Color(f.colorArgb).luminance() > 0.5f) Color.Black.copy(alpha = 0.75f) else Color.White
                val layout = measurer.measure(
                    text = f.name,
                    style = TextStyle(fontSize = 10.sp, color = textColor),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = (sz.width * 0.9f).toInt().coerceAtLeast(1))
                )
                drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
            }
        }

        // walls
        drawRect(
            scheme.onSurface.copy(alpha = 0.85f), roomTL, roomSize,
            style = Stroke(width = 6.dp.toPx())
        )

        // room dimension labels
        val labelColor = scheme.onSurfaceVariant
        drawLabel(measurer, unit.format(roomL), Offset(roomTL.x + roomSize.width / 2f, roomTL.y - 18.dp.toPx()), labelColor)
        rotate(-90f, pivot = Offset(roomTL.x - 18.dp.toPx(), roomTL.y + roomSize.height / 2f)) {
            drawLabel(measurer, unit.format(roomW), Offset(roomTL.x - 18.dp.toPx(), roomTL.y + roomSize.height / 2f), labelColor)
        }

        // selection + handles
        val sel = state.items.firstOrNull { it.id == state.selectedId }
        if (sel != null) {
            val b = sel.bounds
            val tl = Offset(mx(b.left), my(b.top))
            val sz = Size(b.width.toFloat() * s, b.height.toFloat() * s)
            drawRect(scheme.primary, tl, sz, style = Stroke(2.dp.toPx()))
            if (state.tool == EditorTool.SELECT) {
                listOf(tl, Offset(tl.x + sz.width, tl.y), Offset(tl.x + sz.width, tl.y + sz.height), Offset(tl.x, tl.y + sz.height))
                    .forEach { p ->
                        drawCircle(Color.White, 8.dp.toPx(), p)
                        drawCircle(scheme.primary, 8.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                    }
            }
            // distances to walls
            if (state.tool == EditorTool.MEASURE || state.interacting) {
                val d = FurnitureGeometry.wallDistances(b, roomL, roomW)
                val cx = (b.left + b.right) / 2.0
                val cy = (b.top + b.bottom) / 2.0
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                fun guide(x1: Double, y1: Double, x2: Double, y2: Double, dist: Double) {
                    if (dist <= 0.005) return
                    drawLine(scheme.primary, Offset(mx(x1), my(y1)), Offset(mx(x2), my(y2)), strokeWidth = 2f, pathEffect = dash)
                    drawLabel(
                        measurer, unit.format(dist),
                        Offset((mx(x1) + mx(x2)) / 2f, (my(y1) + my(y2)) / 2f),
                        Color.White, scheme.primary, 10f
                    )
                }
                guide(0.0, cy, b.left, cy, d.left)
                guide(b.right, cy, roomL, cy, d.right)
                guide(cx, 0.0, cx, b.top, d.top)
                guide(cx, b.bottom, cx, roomW, d.bottom)
            }
        }

        // measurement line
        val red = Color(0xFFE53935)
        state.measureA?.let { a ->
            drawCircle(red, 6.dp.toPx(), Offset(mx(a.x), my(a.y)))
            state.measureB?.let { b ->
                drawLine(red, Offset(mx(a.x), my(a.y)), Offset(mx(b.x), my(b.y)), strokeWidth = 3.dp.toPx())
                drawCircle(red, 6.dp.toPx(), Offset(mx(b.x), my(b.y)))
                val dist = FurnitureGeometry.distance(a.x, a.y, b.x, b.y)
                drawLabel(
                    measurer, unit.format(dist),
                    Offset((mx(a.x) + mx(b.x)) / 2f, (my(a.y) + my(b.y)) / 2f - 14.dp.toPx()),
                    Color.White, red
                )
            }
        }
    }
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer, text: String, center: Offset, color: Color, bg: Color? = null, fontSp: Float = 11f
) {
    val layout = measurer.measure(text, TextStyle(fontSize = fontSp.sp, color = color, fontWeight = FontWeight.Medium))
    val tl = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    if (bg != null) {
        val pad = 4.dp.toPx()
        drawRoundRect(
            bg, Offset(tl.x - pad, tl.y - pad / 2f),
            Size(layout.size.width + 2 * pad, layout.size.height + pad), CornerRadius(6.dp.toPx())
        )
    }
    drawText(layout, topLeft = tl)
}

// ---------------------------------------------------------------- bars

@Composable
private fun SelectionBar(
    item: PlacedFurniture, unit: MeasureUnit,
    onRotate: () -> Unit, onDuplicate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "${item.name} · ${unit.formatPair(item.widthM, item.depthM)} · ${item.rotationDeg}°",
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BarAction(Icons.Outlined.RotateRight, "Rotate", onRotate)
                BarAction(Icons.Outlined.ContentCopy, "Duplicate", onDuplicate)
                BarAction(Icons.Outlined.Edit, "Edit", onEdit)
                BarAction(Icons.Outlined.Delete, "Delete", onDelete, MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun MeasurePanel(state: EditorState, project: Project, unit: MeasureUnit, selected: PlacedFurniture?, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Measurement Tools", fontWeight = FontWeight.Bold)
            val a = state.measureA
            val b = state.measureB
            Text(
                when {
                    a == null -> "Tap two points on the plan to measure the distance."
                    b == null -> "Tap a second point."
                    else -> "Distance: ${unit.format(FurnitureGeometry.distance(a.x, a.y, b.x, b.y))}"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Room: ${unit.formatPair(project.lengthM, project.widthM)} · Area ${areaLabel(project.areaM2, unit)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selected != null) {
                val d = FurnitureGeometry.wallDistances(selected.bounds, project.lengthM, project.widthM)
                Text(
                    "${selected.name}: ${unit.formatPair(selected.widthM, selected.depthM)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "To walls: left ${unit.format(d.left)}, right ${unit.format(d.right)}, " +
                        "top ${unit.format(d.top)}, bottom ${unit.format(d.bottom)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Select furniture with the Select tool to see its size and wall distances here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (a != null) TextButton(onClick = onClear) { Text("Clear measurement") }
        }
    }
}

@Composable
private fun EditorToolbar(
    tool: EditorTool,
    onSelectTool: () -> Unit, onFurniture: () -> Unit, onMeasure: () -> Unit,
    onReset: () -> Unit, onSettings: () -> Unit,
    onLayouts: () -> Unit, on3D: () -> Unit, onExport: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)
        ) {
            val m = Modifier.width(76.dp)
            ToolItem(Icons.Outlined.PanTool, "Select", tool == EditorTool.SELECT, onSelectTool, m)
            ToolItem(Icons.Outlined.Weekend, "Furniture", false, onFurniture, m)
            ToolItem(Icons.Outlined.Straighten, "Measure", tool == EditorTool.MEASURE, onMeasure, m)
            ToolItem(Icons.Outlined.Layers, "Layouts", false, onLayouts, m)
            ToolItem(Icons.Outlined.ViewInAr, "3D", false, on3D, m)
            ToolItem(Icons.Outlined.CenterFocusStrong, "Fit view", false, onReset, m)
            ToolItem(Icons.Outlined.Share, "Export", false, onExport, m)
            ToolItem(Icons.Outlined.Tune, "Settings", false, onSettings, m)
        }
    }
}

@Composable
private fun ToolItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

// ---------------------------------------------------------------- sheets

@Composable
private fun PropertiesSheet(
    item: PlacedFurniture, unit: MeasureUnit,
    onDismiss: () -> Unit,
    onApply: (Long, Int, Double, Double) -> Unit
) {
    var color by remember(item.id) { mutableStateOf(item.colorArgb) }
    var rotation by remember(item.id) { mutableStateOf(item.rotationDeg) }
    var wText by remember(item.id) { mutableStateOf(formatNumber(unit.fromMeters(item.widthM))) }
    var dText by remember(item.id) { mutableStateOf(formatNumber(unit.fromMeters(item.depthM))) }
    val w = parseSizeMeters(wText, unit)
    val d = parseSizeMeters(dText, unit)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Color", fontWeight = FontWeight.SemiBold)
            ColorSwatches(color) { color = it }
            Text("Rotation", fontWeight = FontWeight.SemiBold)
            RotationChips(rotation) { rotation = it }
            Text("Size", fontWeight = FontWeight.SemiBold)
            SizeFields(wText, dText, unit, { wText = it }, { dText = it })
            Button(
                onClick = { if (w != null && d != null) onApply(color, rotation, w, d) },
                enabled = w != null && d != null,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Apply") }
        }
    }
}

@Composable
private fun GridSheet(state: EditorState, vm: EditorViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Editor settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ToggleRow("Show grid", state.settings.showGrid, vm::setShowGrid)
            ToggleRow("Snap to grid", state.settings.snapToGrid, vm::setSnap)
            Text("Grid spacing")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 100).forEach { cm ->
                    FilterChip(
                        selected = state.settings.gridSpacingCm == cm,
                        onClick = { vm.setGridSpacing(cm) },
                        label = { Text("$cm cm") }
                    )
                }
            }
            ToggleRow("Allow furniture outside the room", state.allowOutOfBounds, vm::setAllowOutOfBounds)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
