@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.drawOrder
import com.nhdeveloper.roomdraft.domain.model.ProjectRules
import com.nhdeveloper.roomdraft.domain.model.RoomType
import com.nhdeveloper.roomdraft.navigation.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.HOME, "Home", Icons.Outlined.Home),
    TabItem(Routes.TEMPLATES, "Templates", Icons.Outlined.GridView),
    TabItem(Routes.SETTINGS, "Settings", Icons.Outlined.Settings)
)

@Composable
fun MainScaffold(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = topBar,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab.route,
                        onClick = { onNavigate(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        content = content
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = modifier, shape = shape,
            color = MaterialTheme.colorScheme.surface, border = border, shadowElevation = 1.dp
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = modifier, shape = shape,
            color = MaterialTheme.colorScheme.surface, border = border, shadowElevation = 1.dp
        ) { Column(content = content) }
    }
}

@Composable
fun LogoBadge(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

fun roomTypeIcon(type: RoomType): ImageVector = when (type) {
    RoomType.LIVING_ROOM -> Icons.Outlined.Weekend
    RoomType.BEDROOM -> Icons.Outlined.Bed
    RoomType.KITCHEN -> Icons.Outlined.Kitchen
    RoomType.BATHROOM -> Icons.Outlined.Bathtub
    RoomType.OFFICE -> Icons.Outlined.Work
    RoomType.OTHER -> Icons.Outlined.MoreHoriz
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))

fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    label: String = "Project name",
    maxLength: Int = ProjectRules.MAX_NAME
) {
    var text by remember { mutableStateOf(initial) }
    val error = when {
        text.trim().isEmpty() -> "Enter a name"
        text.trim().length > maxLength -> "Name is too long (max $maxLength characters)"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(label) },
                isError = error != null,
                supportingText = { if (error != null) Text(error) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = error == null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Top-down, to-scale preview of a rectangular room (length horizontal, width vertical). */
@Composable
fun RoomPreview(
    lengthM: Double,
    widthM: Double,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false,
    unit: MeasureUnit = MeasureUnit.METERS,
    furniture: List<PlacedFurniture> = emptyList()
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val floor = if (dark) Color(0xFFB8935F) else Color(0xFFDDBF93)
    val gridColor = Color.Black.copy(alpha = 0.12f)
    val wallColor = scheme.onSurface.copy(alpha = 0.85f)
    val labelColor = scheme.onSurfaceVariant
    val bg = scheme.surfaceVariant

    Canvas(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(bg)) {
        val small = 8.dp.toPx()
        val big = 34.dp.toPx()
        val padLeft = if (showLabels) big else small
        val padTop = if (showLabels) big else small
        val padRight = if (showLabels) 14.dp.toPx() else small
        val padBottom = if (showLabels) 14.dp.toPx() else small
        val availW = size.width - padLeft - padRight
        val availH = size.height - padTop - padBottom
        if (availW <= 0f || availH <= 0f) return@Canvas

        val l = lengthM.toFloat()
        val w = widthM.toFloat()
        val scale = min(availW / l, availH / w)
        val rw = l * scale
        val rh = w * scale
        val left = padLeft + (availW - rw) / 2f
        val top = padTop + (availH - rh) / 2f

        drawRect(floor, topLeft = Offset(left, top), size = Size(rw, rh))

        val step = 0.5f * scale
        if (step >= 8f) {
            var x = left + step
            while (x < left + rw - 0.5f) {
                drawLine(gridColor, Offset(x, top), Offset(x, top + rh), strokeWidth = 1f)
                x += step
            }
            var y = top + step
            while (y < top + rh - 0.5f) {
                drawLine(gridColor, Offset(left, y), Offset(left + rw, y), strokeWidth = 1f)
                y += step
            }
        }

        furniture.drawOrder().forEach { f ->
            drawFurniture(
                f.category(), f.shape(), Color(f.colorArgb),
                wPx = f.widthM.toFloat() * scale, hPx = f.depthM.toFloat() * scale,
                center = Offset(left + f.centerX.toFloat() * scale, top + f.centerY.toFloat() * scale),
                rotationDeg = f.rotationDeg.toFloat(),
                itemId = f.catalogId
            )
        }

        drawRect(
            wallColor,
            topLeft = Offset(left, top),
            size = Size(rw, rh),
            style = Stroke(width = if (showLabels) 6.dp.toPx() else 3.dp.toPx())
        )

        if (showLabels) {
            val style = TextStyle(fontSize = 11.sp, color = labelColor)
            val top1 = measurer.measure(unit.format(lengthM), style)
            drawText(
                top1,
                topLeft = Offset(left + rw / 2f - top1.size.width / 2f, top - top1.size.height - 8.dp.toPx())
            )
            val side = measurer.measure(unit.format(widthM), style)
            val cx = left - 14.dp.toPx()
            val cy = top + rh / 2f
            rotate(-90f, pivot = Offset(cx, cy)) {
                drawText(side, topLeft = Offset(cx - side.size.width / 2f, cy - side.size.height / 2f))
            }
        }
    }
}
