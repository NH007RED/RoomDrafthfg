@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.templates

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.TemplateLayouts
import com.nhdeveloper.roomdraft.domain.model.Templates
import com.nhdeveloper.roomdraft.domain.model.areaLabel
import com.nhdeveloper.roomdraft.navigation.Routes
import com.nhdeveloper.roomdraft.ui.components.AppCard
import com.nhdeveloper.roomdraft.ui.components.MainScaffold
import com.nhdeveloper.roomdraft.ui.components.RoomPreview

@Composable
fun TemplatesScreen(onUseTemplate: (String) -> Unit, onNavigate: (String) -> Unit) {
    MainScaffold(
        selectedRoute = Routes.TEMPLATES,
        onNavigate = onNavigate,
        topBar = { TopAppBar(title = { Text("Templates", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(Templates.all, key = { it.id }) { template ->
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        RoomPreview(
                            template.lengthM, template.widthM, Modifier.size(96.dp),
                            furniture = TemplateLayouts.build(template)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "${MeasureUnit.METERS.formatPair(template.lengthM, template.widthM)} · " +
                                    areaLabel(template.lengthM * template.widthM, MeasureUnit.METERS),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.size(8.dp))
                            Button(onClick = { onUseTemplate(template.id) }) { Text("Use Template") }
                        }
                    }
                }
            }
        }
    }
}
