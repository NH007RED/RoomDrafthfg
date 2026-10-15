@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.nhdeveloper.roomdraft.domain.model.DimensionRules
import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.TemplateLayouts
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.ProjectRules
import com.nhdeveloper.roomdraft.domain.model.RoomPreset
import com.nhdeveloper.roomdraft.domain.model.RoomPresets
import com.nhdeveloper.roomdraft.domain.model.RoomType
import com.nhdeveloper.roomdraft.domain.model.Templates
import com.nhdeveloper.roomdraft.domain.model.areaLabel
import com.nhdeveloper.roomdraft.domain.model.formatNumber
import com.nhdeveloper.roomdraft.ui.components.RoomPreview
import com.nhdeveloper.roomdraft.ui.components.roomTypeIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateUiState(
    val ready: Boolean = false,
    val name: String = "",
    val roomType: RoomType = RoomType.LIVING_ROOM,
    val unit: MeasureUnit = MeasureUnit.METERS,
    val length: String = "",
    val width: String = "",
    val lengthM: Double? = null,
    val widthM: Double? = null,
    val nameError: String? = null,
    val lengthError: String? = null,
    val widthError: String? = null,
    val saving: Boolean = false,
    val error: String? = null
)

private fun CreateUiState.validated(): CreateUiState {
    val (lm, le) = DimensionRules.validate(length, unit)
    val (wm, we) = DimensionRules.validate(width, unit)
    return copy(
        lengthM = lm, widthM = wm,
        lengthError = le, widthError = we,
        nameError = ProjectRules.validateName(name)
    )
}

class CreateProjectViewModel(
    private val repo: ProjectRepository,
    settingsRepo: SettingsRepository,
    private val templateId: String?
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = try {
                settingsRepo.settings.first()
            } catch (e: Exception) {
                AppSettings()
            }
            val template = templateId?.let { Templates.find(it) }
            val l = template?.lengthM ?: 5.0
            val w = template?.widthM ?: 4.0
            val unit = settings.defaultUnit
            _state.value = CreateUiState(
                ready = true,
                name = template?.name ?: "My Room",
                roomType = template?.roomType ?: RoomType.LIVING_ROOM,
                unit = unit,
                length = formatNumber(unit.fromMeters(l)),
                width = formatNumber(unit.fromMeters(w))
            ).validated()
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, error = null).validated() }
    fun onType(type: RoomType) = _state.update { it.copy(roomType = type) }
    fun onLength(value: String) = _state.update { it.copy(length = value, error = null).validated() }
    fun onWidth(value: String) = _state.update { it.copy(width = value, error = null).validated() }

    fun onUnit(newUnit: MeasureUnit) = _state.update { s ->
        s.copy(
            unit = newUnit,
            length = s.lengthM?.let { formatNumber(newUnit.fromMeters(it)) } ?: s.length,
            width = s.widthM?.let { formatNumber(newUnit.fromMeters(it)) } ?: s.width
        ).validated()
    }

    fun applyPreset(preset: RoomPreset) = _state.update { s ->
        s.copy(
            length = formatNumber(s.unit.fromMeters(preset.lengthM)),
            width = formatNumber(s.unit.fromMeters(preset.widthM)),
            error = null
        ).validated()
    }

    fun create(onCreated: (String) -> Unit) {
        val s = _state.value.validated()
        _state.value = s
        val l = s.lengthM
        val w = s.widthM
        if (s.nameError != null || l == null || w == null || s.saving) return
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val starter = templateId?.let { Templates.find(it) }?.let { t ->
                    TemplateLayouts.build(t).filter { f -> FurnitureGeometry.isInside(f.bounds, l, w) }
                }.orEmpty()
                val project = repo.createWithFurniture(s.name.trim(), s.roomType, l, w, templateId, starter)
                onCreated(project.id)
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = "Could not save project: ${e.message ?: "unknown error"}") }
            }
        }
    }
}

@Composable
fun CreateProjectScreen(templateId: String?, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val c = appContainer()
    val vm: CreateProjectViewModel = viewModel(
        factory = VMFactory { CreateProjectViewModel(c.projects, c.settings, templateId) }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!state.ready) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onName,
                    label = { Text("Project name") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = { state.nameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                SectionTitle("Room type")
                RoomType.values().toList().chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { type ->
                            RoomTypeCard(
                                type = type,
                                selected = state.roomType == type,
                                onClick = { vm.onType(type) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                SectionTitle("Room presets")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoomPresets.forEach { preset ->
                        AssistChip(
                            onClick = { vm.applyPreset(preset) },
                            label = {
                                Text("${preset.label} · ${formatNumber(preset.lengthM)} × ${formatNumber(preset.widthM)} m")
                            }
                        )
                    }
                }

                SectionTitle("Measurement unit")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MeasureUnit.values().forEach { unit ->
                        FilterChip(
                            selected = state.unit == unit,
                            onClick = { vm.onUnit(unit) },
                            label = { Text("${unit.label} (${unit.symbol})") }
                        )
                    }
                }

                SectionTitle("Room dimensions")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.length,
                        onValueChange = vm::onLength,
                        label = { Text("Length (${state.unit.symbol})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.lengthError != null,
                        supportingText = { state.lengthError?.let { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.width,
                        onValueChange = vm::onWidth,
                        label = { Text("Width (${state.unit.symbol})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.widthError != null,
                        supportingText = { state.widthError?.let { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )
                }

                val lm = state.lengthM
                val wm = state.widthM
                if (lm != null && wm != null) {
                    RoomPreview(
                        lengthM = lm,
                        widthM = wm,
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                        showLabels = true,
                        unit = state.unit
                    )
                    Text(
                        "Area: ${areaLabel(lm * wm, state.unit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { vm.create(onCreated) },
                    enabled = !state.saving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (state.saving) "Creating…" else "Create", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun RoomTypeCard(type: RoomType, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                roomTypeIcon(type),
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                type.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) scheme.primary else scheme.onSurface,
                maxLines = 1
            )
        }
    }
}
