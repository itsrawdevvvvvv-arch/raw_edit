package com.rawedit.app.render

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.rawedit.app.util.RenderLog
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins a video-only MP4 and an audio-only MP4 into one file, without re-encoding.
 *
 * The merge loop must advance the extractor after every sample it writes. The old
 * version read a sample, wrote it and read it again without advancing, so the loop
 * never ended: the render sat at 96% and kept writing the same sample to disk.
 */
object Mp4Combine {

    private class Sample {
        var size: Int = 0
        var pts: Long = -1L
        var flags: Int = 0
    }

    fun combine(videoFile: File, audioFile: File, outFile: File): Boolean {
        val audioUsable = audioFile.exists() && audioFile.length() > 0L
        if (!audioUsable) {
            RenderLog.log("combine: no audio file, copying video only")
            return copyVideo(videoFile, outFile)
        }

        var muxer: MediaMuxer? = null
        var vEx: MediaExtractor? = null
        var aEx: MediaExtractor? = null
        try {
            vEx = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            aEx = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            val vTrackIn = firstTrack(vEx, "video/")
            val aTrackIn = firstTrack(aEx, "audio/")
            if (vTrackIn < 0) {
                RenderLog.log("combine: no video track")
                return false
            }
            if (aTrackIn < 0) {
                RenderLog.log("combine: no audio track, copying video only")
                return copyVideo(videoFile, outFile)
            }
            vEx.selectTrack(vTrackIn)
            aEx.selectTrack(aTrackIn)

            val vFormat = vEx.getTrackFormat(vTrackIn)
            val m = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val vTrackOut = m.addTrack(vFormat)
            val aTrackOut = m.addTrack(aEx.getTrackFormat(aTrackIn))
            m.start()

            val vBuf = ByteBuffer.allocateDirect(1 shl 22)
            val aBuf = ByteBuffer.allocateDirect(1 shl 20)
            val info = MediaCodec.BufferInfo()
            val v = Sample()
            val a = Sample()

            read(vEx, vBuf, v)
            read(aEx, aBuf, a)

            var written = 0L
            var guard = 0L
            val limit = 4_000_000L

            while ((v.pts >= 0L || a.pts >= 0L) && guard < limit) {
                guard++
                val useVideo = v.pts >= 0L && (a.pts < 0L || v.pts <= a.pts)
                if (useVideo) {
                    info.set(0, v.size, v.pts, v.flags)
                    vBuf.position(0)
                    m.writeSampleData(vTrackOut, vBuf, info)
                    vEx.advance()
                    read(vEx, vBuf, v)
                } else {
                    info.set(0, a.size, a.pts, a.flags)
                    aBuf.position(0)
                    m.writeSampleData(aTrackOut, aBuf, info)
                    aEx.advance()
                    read(aEx, aBuf, a)
                }
                written++
            }

            RenderLog.log(
                "combine: wrote " + written + " samples, out=" + outFile.length() + " bytes"
            )
            if (guard >= limit) {
                RenderLog.log("combine: hit the sample guard, output may be truncated")
                return false
            }
            return true
        } catch (t: Throwable) {
            RenderLog.log("combine failed", t)
            return false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { vEx?.release() }
            runCatching { aEx?.release() }
        }
    }

    /** Video only: used by the self test and as a fallback when audio is unusable. */
    fun copyVideo(videoFile: File, outFile: File): Boolean {
        var muxer: MediaMuxer? = null
        var ex: MediaExtractor? = null
        try {
            ex = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            val trackIn = firstTrack(ex, "video/")
            if (trackIn < 0) return false
            ex.selectTrack(trackIn)
            val m = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val trackOut = m.addTrack(ex.getTrackFormat(trackIn))
            m.start()
            val buf = ByteBuffer.allocateDirect(1 shl 22)
            val info = MediaCodec.BufferInfo()
            var guard = 0L
            while (guard < 4_000_000L) {
                guard++
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                info.set(0, size, ex.sampleTime, mapFlags(ex.sampleFlags))
                buf.position(0)
                m.writeSampleData(trackOut, buf, info)
                ex.advance()
            }
            return true
        } catch (t: Throwable) {
            RenderLog.log("copyVideo failed", t)
            return false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { ex?.release() }
        }
    }

    /** Reads the current sample into [buf] and records its size, time and flags. */
    private fun read(ex: MediaExtractor, buf: ByteBuffer, out: Sample) {
        buf.clear()
        val size = ex.readSampleData(buf, 0)
        if (size < 0) {
            out.size = 0
            out.pts = -1L
            out.flags = 0
            return
        }
        out.size = size
        out.pts = ex.sampleTime
        out.flags = mapFlags(ex.sampleFlags)
    }

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun mapFlags(flags: Int): Int {
        var out = 0
        if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) out = out or MediaCodec.BUFFER_FLAG_KEY_FRAME
        return out
    }
}
