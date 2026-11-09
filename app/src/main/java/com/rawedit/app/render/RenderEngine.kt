package com.rawedit.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import com.rawedit.app.util.RenderLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** A render failure that carries a message we can safely show to the user. */
class RenderException(message: String) : Exception(message)

/**
 * Runs the whole render on the phone: drives the WebView frame by frame,
 * encodes H.264, encodes the voiceover to AAC and muxes both into one MP4.
 *
 * Every stage reports real progress, and a failed stage never disappears silently.
 */
class RenderEngine(
    private val context: Context,
    private val stage: WebStage,
    private val audioUri: Uri,
    private val durationMs: Long,
    private val preferredWidth: Int,
    private val preferredHeight: Int,
    private val fps: Int,
    private val stageWidth: Int = StageHost.STAGE_W,
    private val stageHeight: Int = StageHost.STAGE_H
) {

    data class Result(
        val video: File,
        val thumb: File,
        val width: Int,
        val height: Int,
        val frames: Int,
        val elapsedMs: Long
    )

    suspend fun render(
        code: String,
        layersJson: String?,
        onStage: (String) -> Unit,
        onProgress: (Float) -> Unit,
        onPreview: (Bitmap) -> Unit,
        selfTest: Boolean = false
    ): Result = withContext(Dispatchers.Main) {
        val startedAt = SystemClock.uptimeMillis()
        val workDir = File(context.filesDir, "renders/tmp").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val tempAudio = File(workDir, "audio_$stamp.mp4")
        val tempVideo = File(workDir, "video_$stamp.mp4")
        val outFile = File(File(context.filesDir, "renders").apply { mkdirs() }, "raw_edit_$stamp.mp4")
        val thumbFile = File(File(context.filesDir, "thumbs").apply { mkdirs() }, "thumb_$stamp.png")

        var encoder: VideoEncoder? = null
        try {
            // ---- stage 1: read the code ----
            onStage("Reading code")
            onProgress(0.02f)
            RenderLog.log("render start, selfTest=$selfTest, stage=" + stageWidth + "x" + stageHeight + ", fps=$fps")
            val loaded = stage.load(code)
            RenderLog.log("load returned $loaded")
            if (!loaded) {
                val probe = stage.probe()
                throw RenderException(
                    when {
                        !probe.hasRaw -> "Couldn't read the code: window.RAW was not found."
                        !probe.hasSeek -> "Couldn't read the code: seek(t) is missing."
                        else -> "The code loaded but never became ready. Ask Claude to try again."
                    }
                )
            }
            if (!layersJson.isNullOrBlank()) stage.writeLayers(layersJson)
            // Warm the page up. The first seek builds the DOM, so it can take a few
            // tries; a slow first call is not a broken page. The frame loop and the
            // blank-frame watchdog decide whether the page really works.
            // Warm the page up: the first seek builds the DOM. Never block here - a
            // slow warm-up is not a broken page, and the frame loop is the real test.
            onStage("Warming up")
            for (tryIndex in 0 until 3) {
                if (stage.seek(0.0)) break
            }
            checkActive()
            onProgress(0.05f)

            // ---- stage 2: voice ----
            onStage("Preparing voice")
            val audioOk = if (selfTest) {
                RenderLog.log("self test: skipping audio")
                false
            } else {
                withContext(Dispatchers.IO) {
                    AudioTranscoder(context).transcode(audioUri, tempAudio)
                }
            }
            RenderLog.log("audio transcoded=$audioOk")
            if (!audioOk && !selfTest) {
                throw RenderException(
                    "Couldn't read the voice file. Pick the audio again, or convert it to MP3."
                )
            }
            checkActive()
            onProgress(0.09f)

            // ---- stage 3: frames, with a resolution fallback ----
            val attempts = buildList {
                add(preferredWidth to preferredHeight)
                if (preferredWidth > 1280) add(1280 to 720)
                add(854 to 480)
            }.distinct()

            var lastError: Throwable? = null
            for ((requestedW, requestedH) in attempts) {
                val w = requestedW - (requestedW % 2)
                val h = requestedH - (requestedH % 2)
                RenderLog.log("trying encoder at " + w + "x" + h)
                try {
                    onStage(if (w == requestedW) "Preparing frames" else "Retrying at ${h}p")
                    val (frames, fpsUsed) = encodeFrames(w, h, tempVideo, onStage, onProgress, onPreview)
                    encoder = null
                    checkActive()

                    // ---- stage 4: poster frame ----
                    onStage("Saving poster")
                    onProgress(0.94f)
                    writeThumb(thumbFile)

                    // ---- stage 5: mux ----
                    onStage("Muxing audio")
                    onProgress(0.96f)
                    val combined = withContext(Dispatchers.IO) {
                        if (selfTest || !tempAudio.exists() || tempAudio.length() == 0L) {
                            Mp4Combine.copyVideo(tempVideo, outFile)
                        } else {
                            Mp4Combine.combine(tempVideo, tempAudio, outFile)
                        }
                    }
                    RenderLog.log("mux combined=$combined out=" + outFile.length())
                    if (!combined || !outFile.exists() || outFile.length() < 1024) {
                        throw RenderException("The video could not be finished. Try a shorter voiceover.")
                    }
                    onStage("Done")
                    onProgress(1f)
                    return@withContext Result(
                        video = outFile,
                        thumb = thumbFile,
                        width = w,
                        height = h,
                        frames = frames,
                        elapsedMs = SystemClock.uptimeMillis() - startedAt
                    )
                } catch (c: CancellationException) {
                    throw c
                } catch (oom: OutOfMemoryError) {
                    RenderLog.log("encoder OOM at " + w + "x" + h)
                    lastError = oom
                    encoder?.release()
                    encoder = null
                } catch (t: Throwable) {
                    RenderLog.log("encoder failed at " + w + "x" + h, t)
                    lastError = t
                    encoder?.release()
                    encoder = null
                }
            }
            throw RenderException(
                "This phone's encoder couldn't make the video. " +
                    (lastError?.message ?: "Try Fast mode.")
            )
        } catch (c: CancellationException) {
            encoder?.release()
            outFile.delete()
            throw c
        } catch (t: Throwable) {
            RenderLog.log("render failed", t)
            encoder?.release()
            outFile.delete()
            throw t
        } finally {
            tempAudio.delete()
            tempVideo.delete()
        }
    }

    private suspend fun encodeFrames(
        width: Int,
        height: Int,
        tempVideo: File,
        onStage: (String) -> Unit,
        onProgress: (Float) -> Unit,
        onPreview: (Bitmap) -> Unit
    ): Pair<Int, Int> {
        val seconds = durationMs / 1000.0
        val totalFrames = max(1, (seconds * fps).toInt())

        val capture = Bitmap.createBitmap(stageWidth, stageHeight, Bitmap.Config.ARGB_8888)
        val scaled = if (width == stageWidth && height == stageHeight) {
            null
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val dst = Rect(0, 0, width, height)

        val ve = VideoEncoder(width, height, fps, bitrateFor(width), tempVideo)
        ve.start()
        try {
            val previewStep = max(1, totalFrames / 20)
            var sampled = 0
            var blank = 0
            var lastProgressAt = 0L
            var lastPreviewAt = 0L
            var lastFrameAt = SystemClock.uptimeMillis()

            var failedSeeks = 0
            for (i in 0 until totalFrames) {
                checkActive()
                if (stage.seek(i.toDouble() / fps)) {
                    failedSeeks = 0
                } else {
                    failedSeeks++
                    if (failedSeeks > 25) {
                        throw RenderException(
                            "The code stopped responding at frame " + i + ". " +
                                "seek(t) is throwing an error."
                        )
                    }
                }
                stage.drawTo(capture)

                val frame = if (scaled == null) {
                    capture
                } else {
                    Canvas(scaled).drawBitmap(capture, null, dst, paint)
                    scaled
                }
                if (i < 3 && isBlank(capture)) {
                    RenderLog.log("frame " + i + " captured fully black")
                    if (i == 2) {
                        throw RenderException(
                            "This phone's WebView returned a black frame, so the video " +
                                "cannot be drawn here. Tap Share log and send it over."
                        )
                    }
                }
                if (i % 15 == 0) {
                    sampled++
                    if (isBlank(capture)) blank++
                }
                ve.encode(frame, (i * 1_000_000L) / fps)

                val now = SystemClock.uptimeMillis()
                if (i % previewStep == 0 && now - lastPreviewAt > 400) {
                    lastPreviewAt = now
                    onPreview(Bitmap.createScaledBitmap(frame, 384, 216, true))
                }
                // at most 4 UI updates a second, so the main thread stays light
                if (now - lastProgressAt > 250) {
                    lastProgressAt = now
                    val ratio = i.toFloat() / totalFrames
                    onStage("Rendering frame " + (i + 1) + " of " + totalFrames)
                    onProgress(0.10f + 0.82f * ratio)
                }
                lastFrameAt = now
            }
            onStage("Encoding")
            ve.finish()
            RenderLog.log("frames encoded=" + totalFrames + " at " + width + "x" + height)
            if (sampled > 4 && blank * 100 / sampled > 90) {
                throw RenderException(
                    "Every frame came out black. This phone's WebView couldn't draw the code. " +
                        "Try a smaller Fast mode or a different code."
                )
            }
            return totalFrames to fps
        } finally {
            ve.release()
            capture.recycle()
            scaled?.recycle()
        }
    }

    private suspend fun writeThumb(thumbFile: File) {
        try {
            val seconds = durationMs / 1000.0
            stage.seek(minOf(1.0, seconds / 2.0))
            val poster = Bitmap.createBitmap(StageHost.STAGE_W, StageHost.STAGE_H, Bitmap.Config.ARGB_8888)
            stage.drawTo(poster)
            val small = Bitmap.createScaledBitmap(poster, 640, 360, true)
            FileOutputStream(thumbFile).use { out ->
                small.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            small.recycle()
            poster.recycle()
        } catch (t: Throwable) {
            // a missing poster is not fatal
        }
    }

    private fun isBlank(bitmap: Bitmap): Boolean {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            var dark = 0
            var total = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    total++
                    if ((bitmap.getPixel(x, y) and 0x00FFFFFF) == 0) dark++
                    x += 32
                }
                y += 32
            }
            total > 0 && dark == total
        } catch (t: Throwable) {
            false
        }
    }

    private fun bitrateFor(width: Int): Int = when {
        width >= 1920 -> 3_200_000
        width >= 1280 -> 1_900_000
        else -> 1_100_000
    }

    private suspend fun checkActive() {
        if (!currentCoroutineContext().isActive) throw CancellationException("cancelled")
    }
}
