@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.roomdraft.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nhdeveloper.roomdraft.data.export.FileIo
import com.nhdeveloper.roomdraft.data.export.ProjectJson
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.domain.model.areaLabel
import com.nhdeveloper.roomdraft.domain.model.isOpening
import com.nhdeveloper.roomdraft.rendering.room2d.PlanExport
import com.nhdeveloper.roomdraft.rendering.video.RoomVideoExporter
import com.nhdeveloper.roomdraft.rendering.story.Story
import com.nhdeveloper.roomdraft.ui.components.AppCard
import com.nhdeveloper.roomdraft.ui.components.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ExportScreen(state: EditorState, project: Project, vm: EditorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val unit = state.settings.defaultUnit
    val layoutName = state.layouts.firstOrNull { it.id == state.activeLayoutId }?.name ?: "Layout 1"
    val preview = remember(state.items, project.lengthM, project.widthM, unit) {
        PlanExport.renderPlan(project, state.items, unit, 900, 700).asImageBitmap()
    }
    val baseName = FileIo.safeFileName(project.name)

    fun report(ok: Boolean, what: String) {
        scope.launch { snackbar.showSnackbar(if (ok) "$what exported" else "Could not write the file. Check storage and try again.") }
    }

    val pngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { FileIo.write(context, uri) { PlanExport.writePng(it, project, state.items, unit) } }
            report(ok, "Floor plan image")
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                FileIo.write(context, uri) { PlanExport.writePdf(it, project, layoutName, state.items, unit) }
            }
            report(ok, "PDF summary")
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val export = vm.buildExport()
            if (export == null) {
                snackbar.showSnackbar("Could not read the project to export.")
            } else {
                val text = ProjectJson.toJson(export)
                val ok = withContext(Dispatchers.IO) {
                    FileIo.write(context, uri) { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                report(ok, "Project data")
            }
        }
    }

    var videoProgress by remember { mutableStateOf<Float?>(null) }
    val cancelFlag = remember { AtomicBoolean(false) }
    val view = LocalView.current
    val videoQuality = remember { RoomVideoExporter.bestQuality() }
    var storyMode by remember { mutableStateOf(false) }
    var videoSeconds by remember { mutableStateOf(30) }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val quality = videoQuality
        if (uri != null && quality != null) scope.launch {
            val story = if (storyMode) Story.build(project.lengthM, project.widthM, state.items) else null
            if (storyMode && story == null) {
                snackbar.showSnackbar("Add a table, sofa or bed (and a door) to make the story video.")
                try { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) } catch (_: Exception) {}
                return@launch
            }
            videoSeconds = if (story != null) kotlin.math.ceil(story.duration + 2.0).toInt() else RoomVideoExporter.DURATION_SECONDS
            cancelFlag.set(false)
            videoProgress = 0f
            view.keepScreenOn = true
            val temp = File(context.cacheDir, "video_export/${baseName}_${if (storyMode) "story" else "3d"}.mp4")
            val items = state.items
            val subtitle = "${unit.formatPair(project.lengthM, project.widthM)}  |  ${items.count { !it.isOpening }} items  |  RoomDraft"
            var saved = false
            try {
                val result = withContext(Dispatchers.Default) {
                    RoomVideoExporter.export(
                        project, items, subtitle, temp, quality,
                        onProgress = { videoProgress = it },
                        isCancelled = { cancelFlag.get() || !isActive },
                        story = story
                    )
                }
                when (result) {
                    is RoomVideoExporter.Result.Done -> {
                        val ok = withContext(Dispatchers.IO) {
                            FileIo.write(context, uri) { out -> temp.inputStream().use { it.copyTo(out) } }
                        }
                        saved = ok
                        snackbar.showSnackbar(
                            if (ok) "${if (storyMode) "Story" else "3D"} video saved (${result.quality.label}, ${videoSeconds} s)" else "Could not write the video. Check storage and try again."
                        )
                    }
                    is RoomVideoExporter.Result.Cancelled -> snackbar.showSnackbar("Video export cancelled")
                    is RoomVideoExporter.Result.Failed -> snackbar.showSnackbar("Video export failed: ${result.message}")
                }
            } finally {
                temp.delete()
                videoProgress = null
                view.keepScreenOn = false
                if (!saved) {
                    try { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) } catch (_: Exception) {}
                }
            }
        }
    }

    videoProgress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Rendering 3D video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${videoQuality?.label ?: ""} · ${videoSeconds} seconds · 30 fps", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${(p * 100).toInt()}%  Keep the app open and the screen on. This can take a few minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { cancelFlag.set(true) }) { Text("Cancel") } }
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Save & Export") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    bitmap = preview,
                    contentDescription = "Floor plan preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                )
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Info("Project Name", project.name)
                        Info("Layout", layoutName)
                        Info("Room Size", unit.formatPair(project.lengthM, project.widthM))
                        Info("Area", areaLabel(project.areaM2, unit))
                        Info("Furniture", "${state.items.size} items")
                        Info("Created On", formatDateTime(project.createdAt))
                    }
                }
                Button(
                    onClick = { vm.saveNow() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("  Save", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pngLauncher.launch("${baseName}_plan.png") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Export Image")
                    }
                    OutlinedButton(onClick = { pdfLauncher.launch("${baseName}_summary.pdf") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Export PDF")
                    }
                }
                OutlinedButton(onClick = { jsonLauncher.launch("$baseName.roomdraft.json") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Export project data (JSON, all layouts)")
                }
                OutlinedButton(
                    onClick = {
                        if (videoQuality == null) {
                            scope.launch { snackbar.showSnackbar("This phone cannot encode video.") }
                        } else {
                            storyMode = false
                            videoLauncher.launch("${baseName}_3d.mp4")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Export 3D video (30 s, ${videoQuality?.label ?: "HD"})")
                }
                OutlinedButton(
                    onClick = {
                        if (videoQuality == null) {
                            scope.launch { snackbar.showSnackbar("This phone cannot encode video.") }
                        } else {
                            storyMode = true
                            videoLauncher.launch("${baseName}_story.mp4")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Weekend, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Export story video (she enters, watches TV, sleeps)")
                }
                Text(
                    "Files are saved wherever you choose. Nothing is uploaded. Import a project file from the Home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
