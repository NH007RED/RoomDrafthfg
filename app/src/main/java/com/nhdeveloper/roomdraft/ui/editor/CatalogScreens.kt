@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.CatalogItem
import com.nhdeveloper.roomdraft.domain.model.FurnitureCategory
import com.nhdeveloper.roomdraft.domain.model.FurnitureGeometry
import com.nhdeveloper.roomdraft.domain.model.FurniturePalette
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.formatNumber
import com.nhdeveloper.roomdraft.ui.components.FurnitureThumb

fun parseSizeMeters(text: String, unit: MeasureUnit): Double? {
    val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val m = unit.toMeters(v)
    return if (m in FurnitureGeometry.MIN_SIZE..FurnitureGeometry.MAX_SIZE) m else null
}

@Composable
fun ColorSwatches(selected: Long, onSelect: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FurniturePalette.forEach { c ->
            val isSel = c == selected
            Spacer(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        if (isSel) 3.dp else 1.dp,
                        if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
                    .clickable { onSelect(c) }
            )
        }
    }
}

@Composable
fun RotationChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 90, 180, 270).forEach { deg ->
            FilterChip(selected = selected == deg, onClick = { onSelect(deg) }, label = { Text("$deg°") })
        }
    }
}

@Composable
fun SizeFields(
    widthText: String, depthText: String, unit: MeasureUnit,
    onWidth: (String) -> Unit, onDepth: (String) -> Unit
) {
    val wOk = parseSizeMeters(widthText, unit) != null
    val dOk = parseSizeMeters(depthText, unit) != null
    val range = "${unit.format(FurnitureGeometry.MIN_SIZE)} to ${unit.format(FurnitureGeometry.MAX_SIZE)}"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = widthText, onValueChange = onWidth, singleLine = true,
            label = { Text("Width (${unit.symbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = !wOk, supportingText = { if (!wOk) Text(range) },
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = depthText, onValueChange = onDepth, singleLine = true,
            label = { Text("Depth (${unit.symbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = !dOk, supportingText = { if (!dOk) Text(range) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CatalogScreen(unit: MeasureUnit, onBack: () -> Unit, onPick: (CatalogItem) -> Unit) {
    var category by remember { mutableStateOf(FurnitureCategory.SOFAS) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Furniture") },
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
                    FurnitureCategory.values().forEach { c ->
                        FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(Catalog.byCategory(category), key = { it.id }) { item ->
                        Surface(
                            onClick = { onPick(item) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 1.dp
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                FurnitureThumb(
                                    item.category, item.shape, Color(item.defaultColor),
                                    item.widthM, item.depthM,
                                    modifier = Modifier.fillMaxWidth().height(96.dp),
                                    itemId = item.id
                                )
                                Text(item.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                Text(
                                    unit.formatPair(item.widthM, item.depthM),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddFurnitureScreen(
    item: CatalogItem,
    unit: MeasureUnit,
    onBack: () -> Unit,
    onAdd: (colorArgb: Long, rotationDeg: Int, widthM: Double, depthM: Double) -> Unit
) {
    var color by remember { mutableStateOf(item.defaultColor) }
    var rotation by remember { mutableStateOf(0) }
    var widthText by remember { mutableStateOf(formatNumber(unit.fromMeters(item.widthM))) }
    var depthText by remember { mutableStateOf(formatNumber(unit.fromMeters(item.depthM))) }
    val w = parseSizeMeters(widthText, unit)
    val d = parseSizeMeters(depthText, unit)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Add Furniture") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FurnitureThumb(
                            item.category, item.shape, Color(color),
                            w ?: item.widthM, d ?: item.depthM, rotation,
                            Modifier.size(120.dp),
                            itemId = item.id
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                unit.formatPair(w ?: item.widthM, d ?: item.depthM),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Height ${unit.format(item.heightM)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text("Color", fontWeight = FontWeight.SemiBold)
                ColorSwatches(color) { color = it }
                Text("Rotation", fontWeight = FontWeight.SemiBold)
                RotationChips(rotation) { rotation = it }
                Text("Size", fontWeight = FontWeight.SemiBold)
                SizeFields(widthText, depthText, unit, { widthText = it }, { depthText = it })
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { if (w != null && d != null) onAdd(color, rotation, w, d) },
                    enabled = w != null && d != null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Add to Room", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
