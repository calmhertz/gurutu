package io.github.calmhertz.gurutu.png

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import io.github.calmhertz.gurutu.Mapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Output {
    const val relativePath = "DCIM/gurutu"
    const val boxAlpha = 0.75f
    const val shutterAlpha = 0.5f

    private val fileFormat = DateTimeFormatter.ofPattern("yyyy_MM_dd-HH_mm_ss")
    private val displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun fileName(now: LocalDateTime): String {
        return fileFormat.format(now) + ".jpg"
    }

    fun displayTime(now: LocalDateTime): String {
        return displayFormat.format(now)
    }

    fun stamp(base: Bitmap, map: Bitmap?, pin: Bitmap?, address: String?, timeText: String, coordsText: String): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val margin = out.width * 0.03f
        val boxHeight = out.height * 0.19f
        val box = RectF(margin, out.height - margin - boxHeight, out.width - margin, out.height - margin)
        val boxPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
            alpha = (boxAlpha * 255).toInt()
        }
        val pad = boxHeight * 0.09f
        val mapSize = boxHeight - pad * 2
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = boxHeight * 0.15f
            typeface = Typeface.MONOSPACE
        }
        val creditPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            alpha = 200
            textSize = boxHeight * 0.07f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }
        val addressPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = boxHeight * 0.12f
            typeface = Typeface.MONOSPACE
        }
        val coordsPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = boxHeight * 0.1275f
            typeface = Typeface.MONOSPACE
        }
        var contentWidth = textPaint.measureText(timeText) + textPaint.measureText("  ") + coordsPaint.measureText(coordsText)
        val cappedWidth = out.width * 0.5f - (pad * 3 + mapSize)
        val maxWidth = maxOf(cappedWidth, boxHeight)
        val addrLines = if (address != null) wrapTwo(addressPaint, address, maxWidth) else emptyList()
        for (line in addrLines) {
            contentWidth = maxOf(contentWidth, addressPaint.measureText(line))
        }
        contentWidth = minOf(contentWidth, maxWidth)
        contentWidth = maxOf(contentWidth, minOf(creditPaint.measureText(Mapshot.credit), maxWidth))
        val boxWidth = pad * 3 + mapSize + contentWidth
        box.left = (out.width - boxWidth) / 2f
        box.right = (out.width + boxWidth) / 2f
        canvas.drawRoundRect(box, margin * 0.5f, margin * 0.5f, boxPaint)
        val mapRect = RectF(box.left + pad, box.top + pad, box.left + pad + mapSize, box.top + pad + mapSize)
        if (map != null) {
            canvas.drawBitmap(map, null, mapRect, null)
        } else {
            val grey = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.GRAY
            }
            canvas.drawRect(mapRect, grey)
            if (pin != null) {
                Mapshot.drawPinned(canvas, pin, mapRect.centerX(), mapRect.centerY(), mapSize * 0.45f)
            } else {
                val dot = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.RED
                }
                canvas.drawCircle(mapRect.centerX(), mapRect.centerY(), mapSize * 0.08f, dot)
            }
        }
        val textX = mapRect.right + pad
        for ((i, line) in addrLines.withIndex()) {
            canvas.drawText(line, textX, box.top + pad * (i + 1) + textPaint.textSize * (i + 1), addressPaint)
        }
        val stampBaseline = box.top + pad * 3 + textPaint.textSize * 3
        val timeWidth = textPaint.measureText(timeText)
        val coordsGap = textPaint.measureText("  ")
        canvas.drawText(timeText, textX, stampBaseline, textPaint)
        canvas.drawText(ellipsize(coordsPaint, coordsText, maxWidth - timeWidth - coordsGap), textX + timeWidth + coordsGap, stampBaseline, coordsPaint)
        canvas.drawText(Mapshot.credit, box.right - pad, box.bottom - pad * 0.4f, creditPaint)
        return out
    }

    private fun wrapTwo(paint: Paint, text: String, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) {
            return listOf(text)
        }
        val words = text.split(' ')
        val first = StringBuilder()
        var i = 0
        while (i < words.size) {
            val candidate = if (first.isEmpty()) words[i] else "$first ${words[i]}"
            if (paint.measureText(candidate) <= maxWidth) {
                if (first.isNotEmpty()) {
                    first.append(' ')
                }
                first.append(words[i])
                i++
            } else {
                break
            }
        }
        if (first.isEmpty()) {
            return listOf(ellipsize(paint, text, maxWidth))
        }
        val rest = words.drop(i).joinToString(" ")
        if (rest.isEmpty()) {
            return listOf(first.toString())
        }
        return listOf(first.toString(), ellipsize(paint, rest, maxWidth))
    }

    private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) {
            return text
        }
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    fun saveBitmap(resolver: ContentResolver, bitmap: Bitmap, now: LocalDateTime): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName(now))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    resolver.delete(uri, null, null)
                    return null
                }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
        return uri
    }

    fun lastImageUri(resolver: ContentResolver): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} IN (?, ?)"
        val args = arrayOf("$relativePath/", relativePath)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                sort
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }

    fun loadThumb(resolver: ContentResolver, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(128, 128), null)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
