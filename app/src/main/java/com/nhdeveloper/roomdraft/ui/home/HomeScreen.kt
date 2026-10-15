@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.nhdeveloper.roomdraft.data.export.FileIo
import com.nhdeveloper.roomdraft.data.export.ImportResult
import com.nhdeveloper.roomdraft.data.export.ProjectJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nhdeveloper.roomdraft.VMFactory
import com.nhdeveloper.roomdraft.appContainer
import com.nhdeveloper.roomdraft.data.repository.ProjectRepository
import com.nhdeveloper.roomdraft.data.repository.SettingsRepository
import com.nhdeveloper.roomdraft.domain.model.AppSettings
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.navigation.Routes
import com.nhdeveloper.roomdraft.ui.components.AppCard
import com.nhdeveloper.roomdraft.ui.components.ConfirmDialog
import com.nhdeveloper.roomdraft.ui.components.LogoBadge
import com.nhdeveloper.roomdraft.ui.components.MainScaffold
import com.nhdeveloper.roomdraft.ui.components.NameDialog
import com.nhdeveloper.roomdraft.ui.components.RoomPreview
import com.nhdeveloper.roomdraft.ui.components.formatDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: ProjectRepository,
    settingsRepo: SettingsRepository
) : ViewModel() {

    val projects: StateFlow<List<Project>?> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val furniture: StateFlow<Map<String, List<PlacedFurniture>>> =
        repo.observeAllFurniture().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun importJson(text: String?) {
        viewModelScope.launch {
            if (text == null) {
                _messages.send("Could not read that file (it is unreadable or larger than 5 MB)")
                return@launch
            }
            when (val result = ProjectJson.parse(text)) {
                is ImportResult.Failure -> _messages.send(result.message)
                is ImportResult.Success -> try {
                    val project = repo.importProject(result.data)
                    val extra = if (result.warnings.isEmpty()) "" else " " + result.warnings.joinToString(" ")
                    _messages.send("Imported \"${project.name}\".$extra")
                } catch (e: Exception) {
                    _messages.send("Could not import project")
                }
            }
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            try {
                repo.rename(id, name)
            } catch (e: Exception) {
                _messages.send("Could not rename project")
            }
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            try {
                val copy = repo.duplicate(id)
                _messages.send(if (copy != null) "Created \"${copy.name}\"" else "Project no longer exists")
            } catch (e: Exception) {
                _messages.send("Could not duplicate project")
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repo.delete(id)
                _messages.send("Project deleted")
            } catch (e: Exception) {
                _messages.send("Could not delete project")
            }
        }
    }
}

@Composable
fun HomeScreen(
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val c = appContainer()
    val vm: HomeViewModel = viewModel(factory = VMFactory { HomeViewModel(c.projects, c.settings) })
    val projects by vm.projects.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val furnitureMap by vm.furniture.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { vm.importJson(withContext(Dispatchers.IO) { FileIo.readTextLimited(context, uri) }) }
        }
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    MainScaffold(
        selectedRoute = Routes.HOME,
        onNavigate = onNavigate,
        snackbarHostState = snackbar,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoBadge(size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("RoomDraft", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "Import project file")
                    }
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        val list = projects
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Projects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = onNewProject) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Project")
                }
            }
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> EmptyState(
                    onNewProject = onNewProject,
                    onBrowseTemplates = { onNavigate(Routes.TEMPLATES) }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            settings = settings,
                            furniture = furnitureMap[project.id].orEmpty(),
                            onDetails = { onOpenDetails(project.id) },
                            onOpen = { onOpenProject(project.id) },
                            onRename = { renameTarget = project },
                            onDuplicate = { vm.duplicate(project.id) },
                            onDelete = { deleteTarget = project }
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename project",
            initial = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                vm.rename(target.id, newName)
                renameTarget = null
            }
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete project?",
            message = "\"${target.name}\" will be permanently deleted from this device.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                vm.delete(target.id)
                deleteTarget = null
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    settings: AppSettings,
    furniture: List<PlacedFurniture>,
    onDetails: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoomPreview(project.lengthM, project.widthM, Modifier.size(76.dp), furniture = furniture)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${project.roomType.label} · ${settings.defaultUnit.formatPair(project.lengthM, project.widthM)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Modified ${formatDate(project.modifiedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                        onClick = { menuOpen = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        onClick = { menuOpen = false; onDetails() }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onNewProject: () -> Unit, onBrowseTemplates: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.size(20.dp))
        Text("No projects yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(
            "Create your first room or start from a ready-made template. Everything stays on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onNewProject) { Text("New Project") }
        TextButton(onClick = onBrowseTemplates) { Text("Browse templates") }
    }
}
