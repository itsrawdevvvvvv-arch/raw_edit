package com.rawedit.app.render

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Joins a video-only MP4 and an audio-only MP4 into one file, without re-encoding. */
object Mp4Combine {

    fun combine(videoFile: File, audioFile: File, outFile: File): Boolean {
        var muxer: MediaMuxer? = null
        var vEx: MediaExtractor? = null
        var aEx: MediaExtractor? = null
        var ok = false
        try {
            vEx = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            aEx = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            val vTrackIn = firstTrack(vEx, "video/")
            val aTrackIn = firstTrack(aEx, "audio/")
            if (vTrackIn < 0) return false
            vEx.selectTrack(vTrackIn)
            if (aTrackIn >= 0) aEx.selectTrack(aTrackIn)

            val vFormat = vEx.getTrackFormat(vTrackIn)
            val m = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val vTrackOut = m.addTrack(vFormat)
            val aTrackOut = if (aTrackIn >= 0) m.addTrack(aEx.getTrackFormat(aTrackIn)) else -1
            m.start()

            val cap = maxOf(1 shl 22, runCatching { vFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(0))
            val vBuf = ByteBuffer.allocateDirect(cap)
            val aBuf = ByteBuffer.allocateDirect(cap)
            val info = MediaCodec.BufferInfo()

            var vPts = readSample(vEx, vBuf)
            var aPts = if (aTrackIn >= 0) readSample(aEx, aBuf) else -1L

            while (vPts >= 0 || aPts >= 0) {
                val useVideo = vPts >= 0 && (aPts < 0 || vPts <= aPts)
                if (useVideo) {
                    info.set(0, vEx.sampleSize, vPts, mapFlags(vEx.sampleFlags))
                    vBuf.position(0)
                    m.writeSampleData(vTrackOut, vBuf, info)
                    vPts = readSample(vEx, vBuf)
                } else {
                    info.set(0, aEx.sampleSize, aPts, mapFlags(aEx.sampleFlags))
                    aBuf.position(0)
                    m.writeSampleData(aTrackOut, aBuf, info)
                    aPts = readSample(aEx, aBuf)
                }
            }
            ok = true
        } catch (t: Throwable) {
            ok = false
        } finally {
            try {
                muxer?.stop()
            } catch (t: Throwable) {
                // ignore
            }
            try {
                muxer?.release()
            } catch (t: Throwable) {
                // ignore
            }
            try {
                vEx?.release()
            } catch (t: Throwable) {
                // ignore
            }
            try {
                aEx?.release()
            } catch (t: Throwable) {
                // ignore
            }
        }
        return ok
    }

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun readSample(ex: MediaExtractor, buf: ByteBuffer): Long {
        buf.clear()
        val size = ex.readSampleData(buf, 0)
        if (size < 0) return -1L
        return ex.sampleTime
    }

    private fun mapFlags(flags: Int): Int {
        var out = 0
        if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) out = out or MediaCodec.BUFFER_FLAG_KEY_FRAME
        if (flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            out = out or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return out
    }
}
