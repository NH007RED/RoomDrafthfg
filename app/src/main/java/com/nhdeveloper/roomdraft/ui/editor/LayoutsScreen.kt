@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nhdeveloper.roomdraft.domain.model.Layout
import com.nhdeveloper.roomdraft.domain.model.MAX_LAYOUT_NAME
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.domain.model.SampleLayouts
import com.nhdeveloper.roomdraft.ui.components.ConfirmDialog
import com.nhdeveloper.roomdraft.ui.components.NameDialog
import com.nhdeveloper.roomdraft.ui.components.RoomPreview

@Composable
fun LayoutsScreen(
    state: EditorState,
    project: Project,
    onBack: () -> Unit,
    onSwitch: (String) -> Unit,
    onCreate: (Int?) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSave: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<Layout?>(null) }
    var deleteTarget by remember { mutableStateOf<Layout?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val unit = state.settings.defaultUnit

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Layout Variations") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.layouts.forEach { l ->
                        FilterChip(selected = l.id == state.activeLayoutId, onClick = { onSwitch(l.id) }, label = { Text(l.name) })
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.layouts, key = { it.id }) { layout ->
                        val active = layout.id == state.activeLayoutId
                        val layoutItems = if (active) state.items else state.otherLayoutItems[layout.id].orEmpty()
                        var menu by remember { mutableStateOf(false) }
                        Surface(
                            onClick = { onSwitch(layout.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shadowElevation = 1.dp
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                RoomPreview(
                                    project.lengthM, project.widthM,
                                    Modifier.fillMaxWidth().height(120.dp),
                                    furniture = layoutItems
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(start = 4.dp, top = 6.dp)) {
                                        Text(
                                            layout.name, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            (if (active) "Active · " else "") + "${layoutItems.size} items",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menu = true }) {
                                            Icon(Icons.Filled.MoreVert, contentDescription = "Layout options")
                                        }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                                onClick = { menu = false; renameTarget = layout }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Duplicate") },
                                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                                onClick = { menu = false; onDuplicate(layout.id) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                },
                                                onClick = { menu = false; deleteTarget = layout }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showNew = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("  New layout")
                    }
                    Button(onClick = onSave, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Save Layout", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New layout") },
            text = {
                Column {
                    Text("Start empty, or from a sample arrangement sized for this room:", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showNew = false; onCreate(null) }) { Text("Empty layout") }
                    SampleLayouts.names(project.roomType).forEachIndexed { i, n ->
                        TextButton(onClick = { showNew = false; onCreate(i) }) { Text("Sample: $n") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "Rename layout", initial = target.name, confirmLabel = "Rename", label = "Layout name",
            maxLength = MAX_LAYOUT_NAME,
            onDismiss = { renameTarget = null },
            onConfirm = { onRename(target.id, it); renameTarget = null }
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete \"${target.name}\"?",
            message = if (state.layouts.size <= 1) "A project needs at least one layout, so this one cannot be deleted."
            else "This layout and its furniture will be permanently deleted.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = { onDelete(target.id); deleteTarget = null }
        )
    }
}
