@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.ThemeMode
import com.nhdeveloper.roomdraft.navigation.Routes
import com.nhdeveloper.roomdraft.ui.components.AppCard
import com.nhdeveloper.roomdraft.ui.components.ConfirmDialog
import com.nhdeveloper.roomdraft.ui.components.MainScaffold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val projects: ProjectRepository,
    private val repo: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val projectCount: StateFlow<Int> =
        projects.observeAll().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { repo.setThemeMode(mode) } }
    fun setUnit(unit: MeasureUnit) { viewModelScope.launch { repo.setDefaultUnit(unit) } }
    fun setShowGrid(show: Boolean) { viewModelScope.launch { repo.setShowGrid(show) } }
    fun setGridSpacing(cm: Int) { viewModelScope.launch { repo.setGridSpacing(cm) } }
    fun setSnap(snap: Boolean) { viewModelScope.launch { repo.setSnapToGrid(snap) } }
    fun deleteAllProjects() { viewModelScope.launch { projects.deleteAll() } }
}

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    val c = appContainer()
    val vm: SettingsViewModel = viewModel(factory = VMFactory { SettingsViewModel(c.projects, c.settings) })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val count by vm.projectCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    MainScaffold(
        selectedRoute = Routes.SETTINGS,
        onNavigate = onNavigate,
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { vm.setTheme(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
            SettingsSection("Measurements") {
                Text("Default unit", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeasureUnit.values().forEach { unit ->
                        FilterChip(
                            selected = settings.defaultUnit == unit,
                            onClick = { vm.setUnit(unit) },
                            label = { Text("${unit.label} (${unit.symbol})") }
                        )
                    }
                }
            }
            SettingsSection("Grid") {
                SwitchRow("Show grid", settings.showGrid, vm::setShowGrid)
                SwitchRow("Snap to grid", settings.snapToGrid, vm::setSnap)
                Text("Grid spacing", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 100).forEach { cm ->
                        FilterChip(
                            selected = settings.gridSpacingCm == cm,
                            onClick = { vm.setGridSpacing(cm) },
                            label = { Text("$cm cm") }
                        )
                    }
                }
            }
            SettingsSection("Local data") {
                Text(
                    "$count project${if (count == 1) "" else "s"} stored on this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = count > 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete all projects") }
            }
            SettingsSection("About") {
                Text("RoomDraft · Plan, Arrange, Design", fontWeight = FontWeight.SemiBold)
                Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Works completely offline. No account, no analytics, no internet permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete all projects?",
            message = "All $count projects will be permanently removed from this device. This cannot be undone.",
            confirmLabel = "Delete all",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.deleteAllProjects() }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
