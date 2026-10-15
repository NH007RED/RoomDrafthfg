package com.nhdeveloper.roomdraft.data.export

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream

object FileIo {
    /** Reads a text file chosen through the Storage Access Framework. Returns null if unreadable or too large. */
    fun readTextLimited(context: Context, uri: Uri, maxBytes: Int = ProjectJson.MAX_FILE_BYTES): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return null
                    out.write(chunk, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Writes through the Storage Access Framework. Returns true on success. */
    fun write(context: Context, uri: Uri, block: (OutputStream) -> Unit): Boolean {
        return try {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
            stream.use { block(it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun safeFileName(name: String): String {
        val cleaned = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").replace(' ', '_')
        return cleaned.ifEmpty { "RoomDraft" }.take(40)
    }
}
