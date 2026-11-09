package com.rawedit.app.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

/** Reads name, size and duration of the picked voiceover. */
object AudioMeta {

    data class Info(
        val displayName: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val mime: String?
    )

    fun read(context: Context, uri: Uri): Info? {
        var name = "voiceover.mp3"
        var size = 0L
        var mime: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (t: Throwable) {
            // fall through with defaults
        }
        mime = try {
            context.contentResolver.getType(uri)
        } catch (t: Throwable) {
            null
        }

        val durationMs = try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            raw?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        }

        if (durationMs <= 0L) return null
        return Info(name, durationMs, size, mime)
    }
}
