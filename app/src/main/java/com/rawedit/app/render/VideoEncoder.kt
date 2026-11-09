package com.rawedit.app.render

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Encodes captured frames into a video-only H.264 MP4. */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val outFile: File
) {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    private var frameBytes = width * height * 3 / 2
    private val pixels = IntArray(width * height)

    fun start() {
        colorFormat = pickColorFormat()
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val c = MediaCodec.createEncoderByType(MIME)
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        codec = c
        val m = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = m
    }

    fun encode(bitmap: Bitmap, ptsUs: Long) {
        val c = codec ?: return
        var inIndex = c.dequeueInputBuffer(TIMEOUT_US)
        if (inIndex < 0) {
            drain(false)
            inIndex = c.dequeueInputBuffer(TIMEOUT_US)
        }
        if (inIndex >= 0) {
            writeFrame(c, inIndex, bitmap)
            c.queueInputBuffer(inIndex, 0, frameBytes, ptsUs, 0)
        }
        drain(false)
    }

    private fun writeFrame(c: MediaCodec, index: Int, bitmap: Bitmap) {
        val safe = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        safe.getPixels(pixels, 0, width, 0, 0, width, height)
        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
            val image = c.getInputImage(index)
            if (image != null) {
                YuvConverter.fillImage(image, pixels, width, height)
            }
        } else {
            val buf: ByteBuffer? = c.getInputBuffer(index)
            if (buf != null) {
                buf.clear()
                val planar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                val data = ByteArray(frameBytes)
                YuvConverter.toByteArray(pixels, width, height, data, planar)
                buf.put(data)
            }
        }
        if (safe !== bitmap) safe.recycle()
    }

    fun finish() {
        val c = codec ?: return
        try {
            val inIndex = c.dequeueInputBuffer(TIMEOUT_US * 5)
            if (inIndex >= 0) {
                c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drain(true)
        } catch (t: Throwable) {
            // ignore
        }
        release()
    }

    fun release() {
        try {
            codec?.stop()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            codec?.release()
        } catch (t: Throwable) {
            // ignore
        }
        codec = null
        try {
            if (muxerStarted) muxer?.stop()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            muxer?.release()
        } catch (t: Throwable) {
            // ignore
        }
        muxer = null
        muxerStarted = false
    }

    private fun drain(endOfStream: Boolean) {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIndex = c.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0L)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (endOfStream) return else return
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer?.addTrack(c.outputFormat) ?: -1
                        muxer?.start()
                        muxerStarted = true
                    }
                }

                outIndex >= 0 -> {
                    val buf = c.getOutputBuffer(outIndex)
                    if (buf != null && info.size > 0 && muxerStarted &&
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer?.writeSampleData(trackIndex, buf, info)
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }

                else -> return
            }
        }
    }

    private fun pickColorFormat(): Int {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val info = list.codecInfos.firstOrNull { c ->
                c.isEncoder && c.supportedTypes.any { it.equals(MIME, true) }
            }
            val formats = info?.getCapabilitiesForType(MIME)?.colorFormats ?: IntArray(0)
            when {
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible

                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

                else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            }
        } catch (t: Throwable) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }
    }

    companion object {
        private const val MIME = "video/avc"
        private const val TIMEOUT_US = 10_000L
    }
}
