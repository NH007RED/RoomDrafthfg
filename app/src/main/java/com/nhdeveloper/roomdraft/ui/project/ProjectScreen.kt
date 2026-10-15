@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.nhdeveloper.roomdraft.domain.model.areaLabel
import com.nhdeveloper.roomdraft.ui.components.AppCard
import com.nhdeveloper.roomdraft.ui.components.ConfirmDialog
import com.nhdeveloper.roomdraft.ui.components.NameDialog
import com.nhdeveloper.roomdraft.ui.components.RoomPreview
import com.nhdeveloper.roomdraft.ui.components.formatDateTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProjectUi {
    object Loading : ProjectUi
    object Missing : ProjectUi
    data class Found(val project: Project) : ProjectUi
}

class ProjectViewModel(
    private val repo: ProjectRepository,
    settingsRepo: SettingsRepository,
    private val id: String
) : ViewModel() {

    val ui: StateFlow<ProjectUi> = repo.observe(id)
        .map<Project?, ProjectUi> { if (it == null) ProjectUi.Missing else ProjectUi.Found(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectUi.Loading)

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val furniture: StateFlow<List<PlacedFurniture>> =
        repo.observeFurniture(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun rename(name: String) {
        viewModelScope.launch {
            try {
                repo.rename(id, name)
            } catch (e: Exception) {
                _messages.send("Could not rename project")
            }
        }
    }

    fun duplicate() {
        viewModelScope.launch {
            try {
                val copy = repo.duplicate(id)
                _messages.send(if (copy != null) "Created \"${copy.name}\"" else "Project no longer exists")
            } catch (e: Exception) {
                _messages.send("Could not duplicate project")
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.delete(id)
                onDone()
            } catch (e: Exception) {
                _messages.send("Could not delete project")
            }
        }
    }
}

@Composable
fun ProjectScreen(projectId: String, onBack: () -> Unit, onOpenEditor: () -> Unit) {
    val c = appContainer()
    val vm: ProjectViewModel = viewModel(
        key = "project_$projectId",
        factory = VMFactory { ProjectViewModel(c.projects, c.settings, projectId) }
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val furniture by vm.furniture.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val current = ui
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (current is ProjectUi.Found) current.project.name else "Project",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when (current) {
            ProjectUi.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ProjectUi.Missing -> Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("This project no longer exists.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Back") }
            }
            is ProjectUi.Found -> {
                val p = current.project
                val unit = settings.defaultUnit
                Column(
                    Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RoomPreview(
                        lengthM = p.lengthM,
                        widthM = p.widthM,
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        showLabels = true,
                        unit = unit,
                        furniture = furniture
                    )
                    Button(onClick = onOpenEditor, modifier = Modifier.fillMaxWidth()) { Text("Open Editor") }
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            InfoRow("Room type", p.roomType.label)
                            InfoRow("Room size", unit.formatPair(p.lengthM, p.widthM))
                            InfoRow("Area", areaLabel(p.areaM2, unit))
                            InfoRow("Created", formatDateTime(p.createdAt))
                            InfoRow("Last modified", formatDateTime(p.modifiedAt))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { renaming = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rename")
                        }
                        OutlinedButton(onClick = { vm.duplicate() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Duplicate")
                        }
                    }
                    OutlinedButton(
                        onClick = { deleting = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete project")
                    }
                }
                if (renaming) {
                    NameDialog(
                        title = "Rename project",
                        initial = p.name,
                        confirmLabel = "Rename",
                        onDismiss = { renaming = false },
                        onConfirm = { vm.rename(it); renaming = false }
                    )
                }
                if (deleting) {
                    ConfirmDialog(
                        title = "Delete project?",
                        message = "\"${p.name}\" will be permanently deleted from this device.",
                        confirmLabel = "Delete",
                        onDismiss = { deleting = false },
                        onConfirm = { deleting = false; vm.delete(onBack) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
