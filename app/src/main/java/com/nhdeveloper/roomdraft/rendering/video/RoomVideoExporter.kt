package com.nhdeveloper.roomdraft.rendering.video

import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.domain.model.RoomType
import com.nhdeveloper.roomdraft.rendering.room3d.CameraPath
import com.nhdeveloper.roomdraft.rendering.room3d.FloorStyle
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D
import com.nhdeveloper.roomdraft.rendering.room3d.Room3D.drawRoom
import com.nhdeveloper.roomdraft.rendering.story.Story
import java.io.File
import kotlin.math.min

/**
 * Renders the 3D room into an MP4: a 30 second, 30 fps fly-around at the best resolution the phone's
 * hardware encoder supports (4K Ultra HD 3840x2160 where available, otherwise 1440p / 1080p).
 */
object RoomVideoExporter {
    const val DURATION_SECONDS = 30
    const val FPS = 30
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    /** All drawing happens in a 1920x1080 coordinate space that is scaled up to the video size. */
    private const val LOGICAL_W = 1920f
    private const val LOGICAL_H = 1080f

    data class Quality(val width: Int, val height: Int, val bitrate: Int, val codecName: String) {
        val label: String get() = when (width) { 3840 -> "4K Ultra HD"; 2560 -> "2K QHD"; else -> "Full HD" } + " ${width}x$height"
    }

    sealed class Result {
        data class Done(val quality: Quality) : Result()
        object Cancelled : Result()
        data class Failed(val message: String) : Result()
    }

    /** Best 16:9 size this device can encode in hardware, or null when it cannot encode H.264 at all. */
    fun bestQuality(): Quality? {
        val infos = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder && MIME in it.supportedTypes }
        } catch (e: Exception) {
            emptyList()
        }
        val ladder = listOf(Triple(3840, 2160, 50_000_000), Triple(2560, 1440, 28_000_000), Triple(1920, 1080, 18_000_000))
        for ((w, h, br) in ladder) {
            for (info in infos) {
                val caps = try { info.getCapabilitiesForType(MIME).videoCapabilities } catch (e: Exception) { null } ?: continue
                val ok = try { caps.isSizeSupported(w, h) && caps.areSizeAndRateSupported(w, h, FPS.toDouble()) } catch (e: Exception) { false }
                if (ok) return Quality(w, h, br.coerceIn(caps.bitrateRange.lower, caps.bitrateRange.upper), info.name)
            }
        }
        return null
    }

    fun floorStyleFor(type: RoomType): FloorStyle = when (type) {
        RoomType.KITCHEN, RoomType.BATHROOM -> FloorStyle.TILE
        RoomType.LIVING_ROOM -> FloorStyle.MARBLE
        else -> FloorStyle.WOOD
    }

    private fun floorColorFor(style: FloorStyle): Color = when (style) {
        FloorStyle.WOOD -> Color(0xFFC89B64)
        FloorStyle.TILE -> Color(0xFFE8E2D6)
        FloorStyle.MARBLE -> Color(0xFFF1EEE8)
    }

    /**
     * Blocking: call from a background thread. [onProgress] gets 0..1, [isCancelled] is polled once per frame.
     * The video is written to [outFile]; on failure or cancel the partial file is deleted.
     */
    fun export(
        project: Project,
        items: List<PlacedFurniture>,
        subtitle: String,
        outFile: File,
        quality: Quality,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
        story: Story? = null
    ): Result {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var surface: Surface? = null
        var muxerStarted = false
        var trackIndex = -1
        var written = 0L
        try {
            outFile.parentFile?.mkdirs()
            if (outFile.exists()) outFile.delete()

            fun newFormat(withProfile: Boolean): MediaFormat =
                MediaFormat.createVideoFormat(MIME, quality.width, quality.height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setInteger("max-bframes", 0)
                    if (withProfile) setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                }

            var enc = MediaCodec.createByCodecName(quality.codecName)
            try {
                enc.configure(newFormat(true), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (e: Exception) {
                enc.release()
                enc = MediaCodec.createByCodecName(quality.codecName)
                enc.configure(newFormat(false), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            encoder = enc
            surface = enc.createInputSurface()
            enc.start()
            val mux = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            val info = MediaCodec.BufferInfo()
            fun drain(endOfStream: Boolean) {
                while (true) {
                    val idx = enc.dequeueOutputBuffer(info, if (endOfStream) 20_000L else 0L)
                    when {
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = mux.addTrack(enc.outputFormat)
                            mux.start()
                            muxerStarted = true
                        }
                        idx >= 0 -> {
                            val buf = enc.getOutputBuffer(idx)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && buf != null && muxerStarted) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                // frames are rendered slower than real time, so stamp them on an exact 30 fps grid
                                info.presentationTimeUs = written * 1_000_000L / FPS
                                written++
                                mux.writeSampleData(trackIndex, buf, info)
                            }
                            enc.releaseOutputBuffer(idx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            val durationSec = if (story != null) kotlin.math.ceil(story.duration + 2.0).toInt() else DURATION_SECONDS
            val total = durationSec * FPS
            val scale = quality.width / LOGICAL_W
            val drawScope = CanvasDrawScope()
            val floorStyle = floorStyleFor(project.roomType)
            val floorColor = floorColorFor(floorStyle)
            val backdrop = Brush.verticalGradient(listOf(Color(0xFFCFDCD5), Color(0xFFF3F6F4)), startY = 0f, endY = LOGICAL_H)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(8f, 0f, 2f, android.graphics.Color.argb(200, 0, 0, 0))
            }
            val subPaint = Paint(textPaint).apply { textSize = 24f; typeface = Typeface.DEFAULT }

            for (i in 0 until total) {
                if (isCancelled()) {
                    return Result.Cancelled
                }
                val progress = i / (total - 1.0)
                val storyT = if (story != null) min(i / FPS.toDouble(), story.duration) else 0.0
                val camera = if (story != null) story.cameraAt(storyT) else CameraPath.at(progress, project.lengthM, project.widthM)
                val canvas = try { surface.lockHardwareCanvas() } catch (e: Exception) { surface.lockCanvas(null) }
                try {
                    canvas.save()
                    canvas.scale(scale, scale)
                    drawScope.draw(Density(1f), LayoutDirection.Ltr, Canvas(canvas), Size(LOGICAL_W, LOGICAL_H)) {
                        drawRect(backdrop, size = size)
                        drawRoom(
                            project.lengthM, project.widthM, items, camera,
                            floorColor = floorColor, gridColor = Color.Black.copy(alpha = 0.12f),
                            floorStyle = floorStyle, dark = false,
                            actor = story?.actorAt(storyT),
                            doorAngles = if (story?.doorId != null) mapOf(story.doorId!! to story.doorAngleAt(storyT)) else emptyMap()
                        )
                    }
                    // title overlay: fades in over the first second and out over the last one
                    val sec = i / FPS.toFloat()
                    val fade = min(1f, min(sec / 1.0f, (durationSec - sec) / 1.0f)).coerceIn(0f, 1f)
                    textPaint.alpha = (255 * fade).toInt()
                    subPaint.alpha = (230 * fade).toInt()
                    canvas.drawText(project.name, 56f, LOGICAL_H - 92f, textPaint)
                    canvas.drawText(subtitle, 56f, LOGICAL_H - 56f, subPaint)
                    if (story != null) {
                        val cap = story.captionAt(storyT)
                        val w = textPaint.measureText(cap)
                        val x = (LOGICAL_W - w) / 2f
                        val y = LOGICAL_H - 150f
                        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((150 * fade).toInt(), 0, 0, 0) }
                        canvas.drawRoundRect(x - 28f, y - 46f, x + w + 28f, y + 22f, 34f, 34f, bg)
                        canvas.drawText(cap, x, y, textPaint)
                    }
                    val black = (255 * (1f - min(1f, min(sec / 0.6f, (durationSec - sec) / 0.6f)).coerceIn(0f, 1f))).toInt()
                    if (black > 0) canvas.drawColor(android.graphics.Color.argb(black, 0, 0, 0))
                    canvas.restore()
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                drain(false)
                onProgress(i / total.toFloat())
            }
            enc.signalEndOfInputStream()
            drain(true)
            onProgress(1f)
            return Result.Done(quality)
        } catch (e: Exception) {
            return Result.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { surface?.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
