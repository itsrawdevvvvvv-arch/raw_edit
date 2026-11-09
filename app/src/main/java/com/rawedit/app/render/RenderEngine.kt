package com.rawedit.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Runs the whole render on the phone: drives the WebView frame by frame,
 * encodes H.264, encodes the voiceover to AAC and muxes both into one MP4.
 */
class RenderEngine(
    private val context: Context,
    private val stage: WebStage,
    private val audioUri: Uri,
    private val durationMs: Long,
    private val outWidth: Int,
    private val outHeight: Int,
    private val fps: Int = 30
) {

    data class Result(val video: File, val thumb: File)

    suspend fun render(
        code: String,
        layersJson: String?,
        onProgress: (Float, String) -> Unit,
        onPreview: (Bitmap) -> Unit
    ): Result = withContext(Dispatchers.Main) {
        val workDir = File(context.filesDir, "renders/tmp").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val tempAudio = File(workDir, "audio_$stamp.mp4")
        val tempVideo = File(workDir, "video_$stamp.mp4")
        val outFile = File(File(context.filesDir, "renders").apply { mkdirs() }, "raw_edit_$stamp.mp4")
        val thumbFile = File(File(context.filesDir, "thumbs").apply { mkdirs() }, "thumb_$stamp.png")

        var encoder: VideoEncoder? = null
        try {
            onProgress(0.01f, "Reading code")
            if (!stage.load(code)) {
                throw IllegalStateException("The code didn't start. Ask Claude to try again.")
            }
            if (!layersJson.isNullOrBlank()) {
                stage.writeLayers(layersJson)
            }
            checkActive()

            onProgress(0.05f, "Adding voice")
            val audioOk = withContext(Dispatchers.IO) {
                AudioTranscoder(context).transcode(audioUri, tempAudio)
            }
            if (!audioOk) {
                throw IllegalStateException("The voice file could not be read. Try another MP3.")
            }
            checkActive()

            val capture = Bitmap.createBitmap(StageHost.STAGE_W, StageHost.STAGE_H, Bitmap.Config.ARGB_8888)
            val scaled = if (outWidth == StageHost.STAGE_W && outHeight == StageHost.STAGE_H) {
                null
            } else {
                Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val dst = Rect(0, 0, outWidth, outHeight)

            val seconds = durationMs / 1000.0
            val totalFrames = max(1, (seconds * fps).toInt())

            val ve = VideoEncoder(outWidth, outHeight, fps, bitrateFor(), tempVideo)
            encoder = ve
            ve.start()

            val previewStep = max(1, totalFrames / 24)
            var lastPreviewAt = 0L

            for (i in 0 until totalFrames) {
                checkActive()
                val t = i.toDouble() / fps
                stage.seek(t)
                stage.drawTo(capture)

                val frame = if (scaled == null) {
                    capture
                } else {
                    Canvas(scaled).drawBitmap(capture, null, dst, paint)
                    scaled
                }
                ve.encode(frame, (i * 1_000_000L) / fps)

                if (i % previewStep == 0) {
                    val now = System.currentTimeMillis()
                    if (now - lastPreviewAt > 260) {
                        lastPreviewAt = now
                        onPreview(Bitmap.createScaledBitmap(frame, 384, 216, true))
                    }
                }

                if (i % 5 == 0) {
                    val p = 0.10f + 0.82f * (i.toFloat() / totalFrames)
                    onProgress(p, frameStatus(i, totalFrames))
                }
            }

            ve.finish()
            encoder = null
            capture.recycle()
            scaled?.recycle()

            checkActive()
            onProgress(0.94f, "Adding voice")

            // grab a poster frame
            try {
                stage.seek(min(1.0, seconds / 2.0))
                val poster = Bitmap.createBitmap(StageHost.STAGE_W, StageHost.STAGE_H, Bitmap.Config.ARGB_8888)
                stage.drawTo(poster)
                val small = Bitmap.createScaledBitmap(poster, 640, 360, true)
                FileOutputStream(thumbFile).use { out ->
                    small.compress(Bitmap.CompressFormat.PNG, 92, out)
                }
                small.recycle()
                poster.recycle()
            } catch (t: Throwable) {
                // a missing thumbnail is not fatal
            }

            onProgress(0.96f, "Finishing video")
            val combined = withContext(Dispatchers.IO) {
                Mp4Combine.combine(tempVideo, tempAudio, outFile)
            }
            if (!combined || !outFile.exists() || outFile.length() < 1024) {
                throw IllegalStateException("The video could not be finished. Try a shorter voiceover.")
            }

            onProgress(1f, "Done")
            Result(outFile, thumbFile)
        } catch (c: CancellationException) {
            try {
                encoder?.release()
            } catch (t: Throwable) {
                // ignore
            }
            outFile.delete()
            throw c
        } catch (t: Throwable) {
            try {
                encoder?.release()
            } catch (x: Throwable) {
                // ignore
            }
            outFile.delete()
            throw t
        } finally {
            tempAudio.delete()
            tempVideo.delete()
        }
    }

    private fun frameStatus(i: Int, total: Int): String {
        val pct = (i * 100) / total
        return when {
            pct < 15 -> "Reading code"
            pct < 55 -> "Drawing frames"
            pct < 90 -> "Drawing frames"
            else -> "Adding voice"
        }
    }

    private fun bitrateFor(): Int = if (outWidth >= 1920) 3_200_000 else 1_600_000

    private suspend fun checkActive() {
        if (!currentCoroutineContext().isActive) throw CancellationException("cancelled")
    }
}
