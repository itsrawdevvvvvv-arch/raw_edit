package com.rawedit.app.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

/** Grabs a poster frame from a finished MP4 for the video library. */
object Thumb {

    fun fromVideo(videoPath: String, out: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
                ?: return false
            val small = Bitmap.createScaledBitmap(frame, 640, 360, true)
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { small.compress(Bitmap.CompressFormat.PNG, 90, it) }
            if (small !== frame) small.recycle()
            frame.recycle()
            true
        } catch (t: Throwable) {
            false
        } finally {
            try {
                retriever.release()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }
}
