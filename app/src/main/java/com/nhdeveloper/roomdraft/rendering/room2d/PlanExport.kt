package com.nhdeveloper.roomdraft.rendering.room2d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.nhdeveloper.roomdraft.domain.model.FurnitureShape
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.PlacedFurniture
import com.nhdeveloper.roomdraft.domain.model.Project
import com.nhdeveloper.roomdraft.domain.model.areaLabel
import com.nhdeveloper.roomdraft.domain.model.bounds
import com.nhdeveloper.roomdraft.domain.model.drawOrder
import com.nhdeveloper.roomdraft.ui.components.shape
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

object PlanExport {

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, width: Float = 1f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.style = style; strokeWidth = width }

    private fun shade(argb: Int, f: Float): Int =
        Color.argb(Color.alpha(argb), (Color.red(argb) * f).toInt(), (Color.green(argb) * f).toInt(), (Color.blue(argb) * f).toInt())

    /** Draws a to-scale floor plan with grid, walls, furniture and dimension labels. */
    fun renderPlan(project: Project, items: List<PlacedFurniture>, unit: MeasureUnit, widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        val margin = min(widthPx, heightPx) * 0.12f
        val availW = widthPx - 2 * margin
        val availH = heightPx - 2 * margin
        val l = project.lengthM.toFloat()
        val w = project.widthM.toFloat()
        val scale = min(availW / l, availH / w)
        val left = margin + (availW - l * scale) / 2f
        val top = margin + (availH - w * scale) / 2f
        val room = RectF(left, top, left + l * scale, top + w * scale)

        c.drawRect(room, paint(Color.parseColor("#F1E3CB")))
        val gridPaint = paint(Color.parseColor("#22000000"), Paint.Style.STROKE, 1.5f)
        var gx = 0.5f
        while (gx < l) { c.drawLine(left + gx * scale, top, left + gx * scale, room.bottom, gridPaint); gx += 0.5f }
        var gy = 0.5f
        while (gy < w) { c.drawLine(left, top + gy * scale, room.right, top + gy * scale, gridPaint); gy += 0.5f }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        items.drawOrder().forEach { f ->
            val b = f.bounds
            val r = RectF(left + b.left.toFloat() * scale, top + b.top.toFloat() * scale, left + b.right.toFloat() * scale, top + b.bottom.toFloat() * scale)
            val fill = f.colorArgb.toInt()
            val round = f.shape() == FurnitureShape.ROUND
            val fp = paint(fill)
            val sp = paint(shade(fill, 0.6f), Paint.Style.STROKE, 3f)
            if (round) { c.drawOval(r, fp); c.drawOval(r, sp) }
            else { val rad = min(r.width(), r.height()) * 0.1f; c.drawRoundRect(r, rad, rad, fp); c.drawRoundRect(r, rad, rad, sp) }
            if (r.width() > 90f && r.height() > 36f) {
                val lum = (0.299 * Color.red(fill) + 0.587 * Color.green(fill) + 0.114 * Color.blue(fill)) / 255.0
                textPaint.color = if (lum > 0.55) Color.argb(200, 0, 0, 0) else Color.WHITE
                textPaint.textSize = min(26f, r.height() * 0.3f)
                var label = f.name
                while (label.length > 3 && textPaint.measureText(label) > r.width() * 0.92f) label = label.dropLast(2)
                c.drawText(if (label.length < f.name.length) "$label…" else label, r.centerX(), r.centerY() + textPaint.textSize / 3f, textPaint)
            }
        }

        c.drawRect(room, paint(Color.parseColor("#222222"), Paint.Style.STROKE, 10f))

        val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444444"); textSize = min(widthPx, heightPx) * 0.035f; textAlign = Paint.Align.CENTER
        }
        c.drawText(unit.format(project.lengthM), room.centerX(), room.top - dim.textSize * 0.9f, dim)
        c.save()
        c.rotate(-90f, room.left - dim.textSize * 0.9f, room.centerY())
        c.drawText(unit.format(project.widthM), room.left - dim.textSize * 0.9f, room.centerY(), dim)
        c.restore()
        return bmp
    }

    fun writePng(out: OutputStream, project: Project, items: List<PlacedFurniture>, unit: MeasureUnit) {
        val bmp = renderPlan(project, items, unit, 1800, 1400)
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
    }

    /** A4 summary: title, room details, floor plan, and a furniture table (multiple pages if needed). */
    fun writePdf(out: OutputStream, project: Project, layoutName: String, items: List<PlacedFurniture>, unit: MeasureUnit) {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.parseColor("#0A6B36") }
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.parseColor("#222222") }
        val boldP = Paint(textP).apply { typeface = Typeface.DEFAULT_BOLD }
        val mutedP = Paint(textP).apply { color = Color.parseColor("#666666") }
        val linePaint = paint(Color.parseColor("#DDDDDD"), Paint.Style.STROKE, 1f)

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
        var canvas = page.canvas
        var y = margin + 22f
        canvas.drawText("RoomDraft", margin, y, titleP)
        y += 22f
        canvas.drawText(project.name, margin, y, boldP.apply { textSize = 15f })
        boldP.textSize = 11f
        y += 18f
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        listOf(
            "Room type: ${project.roomType.label}",
            "Room size: ${unit.formatPair(project.lengthM, project.widthM)}   Area: ${areaLabel(project.areaM2, unit)}",
            "Layout: $layoutName   Items: ${items.size}",
            "Created: ${fmt.format(Date(project.createdAt))}   Exported: ${fmt.format(Date())}"
        ).forEach { canvas.drawText(it, margin, y, textP); y += 15f }
        y += 8f

        val planW = pageW - 2 * margin
        val planH = planW * 0.72f
        val bmp = renderPlan(project, items, unit, 1400, (1400 * 0.72f).toInt())
        canvas.drawBitmap(bmp, null, RectF(margin, y, margin + planW, y + planH), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawRect(RectF(margin, y, margin + planW, y + planH), linePaint)
        bmp.recycle()
        y += planH + 24f

        fun header(cv: Canvas, yy: Float): Float {
            cv.drawText("Furniture", margin, yy, boldP)
            val h = yy + 16f
            cv.drawText("Item", margin, h, boldP)
            cv.drawText("Size (W × D)", margin + 200f, h, boldP)
            cv.drawText("Position (x, y)", margin + 330f, h, boldP)
            cv.drawText("Rot.", margin + 470f, h, boldP)
            cv.drawLine(margin, h + 4f, pageW - margin, h + 4f, linePaint)
            return h + 18f
        }
        y = header(canvas, y)
        if (items.isEmpty()) canvas.drawText("No furniture in this layout.", margin, y, mutedP)

        items.forEach { f ->
            if (y > pageH - margin) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
                canvas = page.canvas
                y = header(canvas, margin + 14f)
            }
            canvas.drawText(f.name.take(34), margin, y, textP)
            canvas.drawText(unit.formatPair(f.widthM, f.depthM), margin + 200f, y, textP)
            canvas.drawText("${unit.format(f.centerX)}, ${unit.format(f.centerY)}", margin + 330f, y, textP)
            canvas.drawText("${f.rotationDeg}°", margin + 470f, y, textP)
            y += 15f
        }
        doc.finishPage(page)
        try {
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
