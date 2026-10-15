package com.nhdeveloper.roomdraft.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.nhdeveloper.roomdraft.domain.model.Catalog
import com.nhdeveloper.roomdraft.domain.model.FurnitureCategory
import com.nhdeveloper.roomdraft.domain.model.FurnitureShape
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun Color.shade(f: Float): Color =
    Color((red * f).coerceIn(0f, 1f), (green * f).coerceIn(0f, 1f), (blue * f).coerceIn(0f, 1f), alpha)

fun PlacedFurniture.category(): FurnitureCategory = Catalog.find(catalogId)?.category ?: FurnitureCategory.OTHER
fun PlacedFurniture.shape(): FurnitureShape = Catalog.find(catalogId)?.shape ?: FurnitureShape.RECT

/**
 * Draws a top-down furniture symbol. wPx/hPx are the UNROTATED footprint in pixels; the top edge is the
 * furniture's back/head side. The whole symbol is rotated by rotationDeg around [center].
 */
fun DrawScope.drawFurniture(
    category: FurnitureCategory,
    shape: FurnitureShape,
    color: Color,
    wPx: Float,
    hPx: Float,
    center: Offset,
    rotationDeg: Float,
    itemId: String? = null
) {
    if (wPx < 1f || hPx < 1f) return
    rotate(rotationDeg, pivot = center) {
        val tl = Offset(center.x - wPx / 2f, center.y - hPx / 2f)
        val full = Size(wPx, hPx)
        val minSide = min(wPx, hPx)
        val edge = color.shade(0.65f)
        val dark = color.shade(0.82f)
        val stroke = Stroke(width = 1.5f)

        if (shape == FurnitureShape.ROUND) {
            drawOval(color, tl, full)
            when (category) {
                FurnitureCategory.INDIAN -> {
                    drawCircle(dark, minSide * 0.34f, center)
                    drawCircle(color.shade(1.12f), minSide * 0.2f, center)
                    drawCircle(Color(0xFFC9A227), minSide * 0.07f, center)
                }
                FurnitureCategory.PLANTS -> {
                    for (i in 0 until 6) {
                        val a = (i * 60f) * 0.0174533f
                        drawCircle(
                            color.shade(0.78f), minSide * 0.18f,
                            Offset(center.x + cos(a) * minSide * 0.24f, center.y + sin(a) * minSide * 0.24f)
                        )
                    }
                    drawCircle(color.shade(0.6f), minSide * 0.13f, center)
                }
                FurnitureCategory.LAMPS -> drawCircle(Color.White.copy(alpha = 0.6f), minSide * 0.25f, center)
                FurnitureCategory.CHAIRS -> drawCircle(dark, minSide * 0.32f, center)
                else -> drawCircle(dark, minSide * 0.3f, center, style = stroke)
            }
            drawOval(edge, tl, full, style = stroke)
            return@rotate
        }

        val r = CornerRadius(minSide * 0.1f)
        if (category == FurnitureCategory.OPENINGS) {
            drawOpeningSymbol(itemId, color, tl, full, center)
            return@rotate
        }
        drawRoundRect(color, tl, full, r)
        if (category == FurnitureCategory.INDIAN && itemId != null) {
            drawIndianDetail(itemId, color, tl, full, center)
            drawRoundRect(edge, tl, full, r, style = stroke)
            return@rotate
        }
        when (category) {
            FurnitureCategory.SOFAS -> {
                val backH = hPx * 0.3f
                drawRoundRect(dark, tl, Size(wPx, backH), r)
                val armW = min(wPx * 0.1f, minSide * 0.2f)
                val armTop = tl.y + backH * 0.6f
                drawRoundRect(dark, Offset(tl.x, armTop), Size(armW, hPx - backH * 0.6f), r)
                drawRoundRect(dark, Offset(tl.x + wPx - armW, armTop), Size(armW, hPx - backH * 0.6f), r)
                if (wPx / hPx > 1.8f) {
                    val seats = if (wPx / hPx > 2.2f) 3 else 2
                    for (i in 1 until seats) {
                        val x = tl.x + armW + (wPx - 2 * armW) * i / seats
                        drawLine(edge, Offset(x, tl.y + backH), Offset(x, tl.y + hPx), strokeWidth = 1.2f)
                    }
                }
            }
            FurnitureCategory.BEDS -> {
                val single = wPx / hPx < 0.6f
                val n = if (single) 1 else 2
                val gap = wPx * 0.04f
                val pw = (wPx - gap * (n + 1)) / n
                val ph = hPx * 0.16f
                for (i in 0 until n) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.88f),
                        Offset(tl.x + gap + i * (pw + gap), tl.y + hPx * 0.04f),
                        Size(pw, ph), CornerRadius(ph * 0.3f)
                    )
                }
                drawRoundRect(dark, Offset(tl.x, tl.y + hPx * 0.28f), Size(wPx, hPx * 0.72f), r)
            }
            FurnitureCategory.CHAIRS -> drawRoundRect(dark, tl, Size(wPx, hPx * 0.26f), r)
            FurnitureCategory.TV_UNITS -> drawRect(
                Color(0xFF1B1B1B), Offset(tl.x + wPx * 0.15f, tl.y + hPx * 0.2f), Size(wPx * 0.7f, hPx * 0.25f)
            )
            FurnitureCategory.DESKS -> drawRect(
                Color(0xFF1B1B1B), Offset(center.x - wPx * 0.15f, tl.y + hPx * 0.08f), Size(wPx * 0.3f, hPx * 0.12f)
            )
            FurnitureCategory.TABLES, FurnitureCategory.OTHER -> drawRoundRect(
                dark,
                Offset(tl.x + minSide * 0.12f, tl.y + minSide * 0.12f),
                Size(wPx - minSide * 0.24f, hPx - minSide * 0.24f), r, style = stroke
            )
            FurnitureCategory.STORAGE, FurnitureCategory.CABINETS -> {
                if (wPx >= hPx * 1.4f) {
                    drawLine(edge, Offset(center.x, tl.y), Offset(center.x, tl.y + hPx), strokeWidth = 1.2f)
                    drawCircle(edge, minSide * 0.05f, Offset(center.x - wPx * 0.04f, center.y))
                    drawCircle(edge, minSide * 0.05f, Offset(center.x + wPx * 0.04f, center.y))
                } else {
                    drawRoundRect(
                        dark, Offset(tl.x + minSide * 0.1f, tl.y + minSide * 0.1f),
                        Size(wPx - minSide * 0.2f, hPx - minSide * 0.2f), r, style = stroke
                    )
                }
            }
            else -> Unit
        }
        drawRoundRect(edge, tl, full, r, style = stroke)
    }
}

/** Plan symbol for doors (leaf + swing arc into the room) and windows (glazing lines). Top edge = outside. */
private fun DrawScope.drawOpeningSymbol(id: String?, color: Color, tl: Offset, full: Size, center: Offset) {
    val w = full.width; val h = full.height
    val ink = color.shade(0.55f)
    // wall gap
    drawRect(Color.White.copy(alpha = 0.9f), tl, full)
    drawRect(ink, tl, full, style = Stroke(width = 1.5f))
    if (id != null && id.startsWith("door")) {
        if (id == "door_sliding") {
            drawRect(color.shade(0.9f), Offset(tl.x, center.y - h * 0.3f), Size(w * 0.52f, h * 0.25f))
            drawRect(color.shade(0.75f), Offset(tl.x + w * 0.48f, center.y + h * 0.05f), Size(w * 0.52f, h * 0.25f))
        } else {
            val hinge = Offset(tl.x, center.y)
            drawLine(ink, hinge, Offset(tl.x, center.y + w), strokeWidth = 3f)
            drawArc(
                ink.copy(alpha = 0.7f), startAngle = 0f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(hinge.x - w, hinge.y - w), size = Size(2 * w, 2 * w),
                style = Stroke(width = 1.2f)
            )
        }
    } else {
        drawLine(ink, Offset(tl.x, center.y), Offset(tl.x + w, center.y), strokeWidth = 1.2f)
        drawLine(ink, Offset(tl.x, tl.y + h * 0.25f), Offset(tl.x + w, tl.y + h * 0.25f), strokeWidth = 1f)
        drawLine(ink, Offset(tl.x, tl.y + h * 0.75f), Offset(tl.x + w, tl.y + h * 0.75f), strokeWidth = 1f)
        if (id == "window_grill") {
            var x = tl.x + w / 8f
            while (x < tl.x + w) { drawLine(ink, Offset(x, tl.y), Offset(x, tl.y + h), strokeWidth = 1f); x += w / 8f }
        }
    }
}

/** Top-down details for the traditional Indian pieces. Same orientation rules as [drawFurniture]: top edge = back. */
private fun DrawScope.drawIndianDetail(id: String, color: Color, tl: Offset, full: Size, center: Offset) {
    val w = full.width; val h = full.height
    val dark = color.shade(0.72f)
    val light = color.shade(1.18f)
    val thin = Stroke(width = 1.2f)
    when (id) {
        "charpai" -> {
            drawRect(Color(0xFFD9B97A), Offset(tl.x + w * 0.1f, tl.y + h * 0.05f), Size(w * 0.8f, h * 0.9f))
            val n = 9
            for (i in 1 until n) {
                val x = tl.x + w * 0.1f + w * 0.8f * i / n
                drawLine(Color(0xFF9A7740), Offset(x, tl.y + h * 0.05f), Offset(x, tl.y + h * 0.95f), strokeWidth = 1f)
                val y = tl.y + h * 0.05f + h * 0.9f * i / (n * 2)
                drawLine(Color(0xFF9A7740), Offset(tl.x + w * 0.1f, y), Offset(tl.x + w * 0.9f, y), strokeWidth = 1f)
            }
            drawRoundRect(Color(0xFFF1E4C8), Offset(tl.x + w * 0.2f, tl.y + h * 0.08f), Size(w * 0.6f, h * 0.1f), CornerRadius(h * 0.04f))
        }
        "diwan", "wood_sofa" -> {
            drawRoundRect(dark, tl, Size(w, h * 0.28f), CornerRadius(h * 0.05f))
            val n = if (w / h > 2f) 4 else 3
            for (i in 0 until n) {
                drawRoundRect(light, Offset(tl.x + w * 0.1f + (w * 0.8f) * i / n, tl.y + h * 0.04f), Size(w * 0.8f / n - 2f, h * 0.2f), CornerRadius(h * 0.04f))
            }
            drawRoundRect(dark, Offset(tl.x, tl.y), Size(w * 0.09f, h), CornerRadius(h * 0.05f))
            drawRoundRect(dark, Offset(tl.x + w * 0.91f, tl.y), Size(w * 0.09f, h), CornerRadius(h * 0.05f))
            drawRect(Color(0xFFC9A227), Offset(tl.x + w * 0.1f, tl.y + h * 0.3f), Size(w * 0.8f, 2f))
        }
        "jhula" -> {
            drawRoundRect(dark, tl, Size(w, h * 0.2f), CornerRadius(h * 0.05f))
            drawRoundRect(Color(0xFF9E2B25), Offset(tl.x + w * 0.06f, tl.y + h * 0.28f), Size(w * 0.88f, h * 0.64f), CornerRadius(h * 0.08f))
            for (p in listOf(0.05f to 0.1f, 0.95f to 0.1f, 0.05f to 0.9f, 0.95f to 0.9f)) {
                drawCircle(Color(0xFFC9A227), h * 0.07f, Offset(tl.x + w * p.first, tl.y + h * p.second))
            }
        }
        "mandir" -> {
            drawRect(Color(0xFFD9A441), Offset(tl.x + w * 0.1f, tl.y + h * 0.12f), Size(w * 0.8f, h * 0.7f))
            drawCircle(Color(0xFFC9A227), h * 0.16f, center)
            drawCircle(color.shade(0.6f), h * 0.07f, center)
            drawLine(dark, Offset(center.x, tl.y + h * 0.6f), Offset(center.x, tl.y + h), strokeWidth = 1.2f)
        }
        "almirah" -> {
            drawLine(dark, Offset(center.x, tl.y), Offset(center.x, tl.y + h), strokeWidth = 1.5f)
            for (i in 1..4) {
                val y = tl.y + h * i / 5f
                drawLine(dark, Offset(tl.x + w * 0.1f, y), Offset(tl.x + w * 0.9f, y), strokeWidth = 1f)
            }
        }
        "sandook" -> {
            for (i in 1..2) {
                val x = tl.x + w * i / 3f
                drawRect(Color(0xFFC9A227), Offset(x - 2f, tl.y), Size(4f, h))
            }
            for (cx in listOf(tl.x + w * 0.04f, tl.x + w * 0.96f)) for (cy in listOf(tl.y + h * 0.1f, tl.y + h * 0.9f)) {
                drawCircle(Color(0xFFC9A227), h * 0.07f, Offset(cx, cy))
            }
        }
        "chowki" -> {
            drawRoundRect(dark, Offset(tl.x + w * 0.1f, tl.y + h * 0.1f), Size(w * 0.8f, h * 0.8f), CornerRadius(w * 0.05f), style = thin)
            drawCircle(Color(0xFFC9A227), min(w, h) * 0.1f, center)
        }
        "gadda" -> {
            drawRect(Color(0xFF9E2B25), Offset(tl.x, tl.y), Size(w, h * 0.22f))
            val n = if (w / h > 1.5f) 3 else 2
            for (i in 0 until n) {
                drawCircle(if (i % 2 == 0) Color(0xFFD9A441) else Color(0xFF9E2B25), h * 0.1f, Offset(tl.x + w * (i + 0.5f) / n, tl.y + h * 0.11f))
            }
            drawRect(dark, Offset(tl.x + w * 0.04f, tl.y + h * 0.3f), Size(w * 0.92f, h * 0.66f), style = thin)
        }
        "tulsi" -> {
            drawRect(dark, Offset(tl.x + w * 0.12f, tl.y + h * 0.12f), Size(w * 0.76f, h * 0.76f), style = thin)
            for (i in 0 until 6) {
                val a = i * 60f * 0.0174533f
                drawCircle(Color(0xFF3E8F4E), min(w, h) * 0.13f, Offset(center.x + cos(a) * min(w, h) * 0.2f, center.y + sin(a) * min(w, h) * 0.2f))
            }
            drawCircle(Color(0xFF2F7A3E), min(w, h) * 0.1f, center)
        }
        "dhurrie" -> {
            val bands = 9
            for (i in 0 until bands) {
                if (i % 2 == 1) drawRect(Color(0xFFF1E4C8).copy(alpha = 0.75f), Offset(tl.x + w * i / bands, tl.y), Size(w / bands, h))
            }
            drawRect(dark, tl, full, style = Stroke(width = 3f))
        }
        else -> drawRoundRect(dark, Offset(tl.x + w * 0.1f, tl.y + h * 0.1f), Size(w * 0.8f, h * 0.8f), CornerRadius(h * 0.05f), style = thin)
    }
}

@Composable
fun FurnitureThumb(
    category: FurnitureCategory,
    shape: FurnitureShape,
    color: Color,
    widthM: Double,
    depthM: Double,
    rotationDeg: Int = 0,
    modifier: Modifier = Modifier,
    itemId: String? = null
) {
    Canvas(modifier) {
        val ew = if (rotationDeg % 180 == 0) widthM else depthM
        val eh = if (rotationDeg % 180 == 0) depthM else widthM
        val scale = min(size.width * 0.78f / ew.toFloat(), size.height * 0.78f / eh.toFloat())
        drawFurniture(
            category, shape, color,
            wPx = widthM.toFloat() * scale, hPx = depthM.toFloat() * scale,
            center = Offset(size.width / 2f, size.height / 2f), rotationDeg = rotationDeg.toFloat(),
            itemId = itemId
        )
    }
}
