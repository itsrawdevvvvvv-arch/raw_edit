package com.rawedit.app.render

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns the picked voice file into an AAC track inside a small MP4.
 *
 * Three routes, tried in order:
 *  1. MediaExtractor, choosing the cheapest path for the track type:
 *     - already AAC  -> remux the samples, no re-encode
 *     - raw PCM (WAV) -> feed straight to the AAC encoder, no decoder
 *     - anything else (MP3, M4A, OGG...) -> decode then encode
 *  2. A hand-written RIFF/WAVE parser, for WAV files the extractor refuses.
 *
 * The old version only had route 1's third branch, which is why WAV files failed
 * with "couldn't read the voice file".
 */
class AudioTranscoder(private val context: Context) {

    fun transcode(uri: Uri, outFile: File): Boolean {
        if (runCatching { fromExtractor(uri, outFile) }.getOrDefault(false)) {
            if (outFile.exists() && outFile.length() > 0) return true
        }
        outFile.delete()
        if (runCatching { fromWave(uri, outFile) }.getOrDefault(false)) {
            if (outFile.exists() && outFile.length() > 0) return true
        }
        outFile.delete()
        return false
    }

    // ---------------------------------------------------------------- extractor

    private fun fromExtractor(uri: Uri, outFile: File): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var index = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    index = i
                    format = f
                    break
                }
            }
            if (index < 0 || format == null) return false
            extractor.selectTrack(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            return when (mime) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> copyAac(extractor, format, outFile)
                MediaFormat.MIMETYPE_AUDIO_RAW -> encodePcmFromExtractor(extractor, format, outFile)
                else -> decodeThenEncode(extractor, format, outFile)
            }
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Already AAC: no decode, no encode, just a new container. */
    private fun copyAac(extractor: MediaExtractor, format: MediaFormat, outFile: File): Boolean {
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val track = muxer.addTrack(format)
            muxer.start()
            val buffer = ByteBuffer.allocateDirect(512 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, 0)
                muxer.writeSampleData(track, buffer, info)
                extractor.advance()
            }
            return true
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    /** WAV arrives as audio/raw: hand the PCM straight to the encoder. */
    private fun encodePcmFromExtractor(
        extractor: MediaExtractor,
        format: MediaFormat,
        outFile: File
    ): Boolean {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val sink = AacSink(outFile, sampleRate, channels)
        try {
            val buffer = ByteBuffer.allocateDirect(512 * 1024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                sink.write(buffer, size, extractor.sampleTime)
                extractor.advance()
            }
            sink.finish()
            return true
        } finally {
            sink.release()
        }
    }

    /** MP3 and friends: decode to PCM, then encode to AAC. */
    private fun decodeThenEncode(
        extractor: MediaExtractor,
        format: MediaFormat,
        outFile: File
    ): Boolean {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
        val decoder = MediaCodec.createDecoderByType(mime)
        var sink: AacSink? = null
        try {
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var guard = 0

            while (!decoderDone && guard < 2_000_000) {
                guard++
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buf = decoder.getInputBuffer(inIndex)
                        if (buf == null) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, 0)
                        } else {
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        if (sink == null) {
                            val outFormat = decoder.outputFormat
                            sink = AacSink(
                                outFile,
                                outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            )
                        }
                        decoder.getOutputBuffer(outIndex)?.let { pcm ->
                            pcm.position(info.offset)
                            pcm.limit(info.offset + info.size)
                            sink?.write(pcm, info.size, info.presentationTimeUs)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        decoderDone = true
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && sink == null) {
                    val outFormat = decoder.outputFormat
                    sink = AacSink(
                        outFile,
                        outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    )
                }
            }
            val s = sink ?: return false
            s.finish()
            return true
        } finally {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { sink?.release() }
        }
    }

    // -------------------------------------------------------------------- WAV

    /** Last resort: parse RIFF/WAVE by hand and encode the PCM ourselves. */
    private fun fromWave(uri: Uri, outFile: File): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        if (bytes.size < 44) return false
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return false
        }
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
        ) {
            return false
        }

        var pos = 12
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var audioFormat = 1
        var dataStart = -1
        var dataLen = 0

        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = le32(bytes, pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > bytes.size + 1) {
                if (id != "data") break
            }
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return false
                    audioFormat = le16(bytes, body)
                    channels = le16(bytes, body + 2)
                    sampleRate = le32(bytes, body + 4)
                    bits = le16(bytes, body + 14)
                    if (audioFormat == 0xFFFE && body + 26 <= bytes.size) {
                        audioFormat = le16(bytes, body + 24)
                    }
                }

                "data" -> {
                    dataStart = body
                    dataLen = minOf(size, bytes.size - body)
                    break
                }
            }
            pos = body + size + (size % 2)
        }

        if (channels <= 0 || sampleRate <= 0 || bits <= 0 || dataStart < 0 || dataLen <= 0) {
            return false
        }

        val sink = AacSink(outFile, sampleRate, channels)
        try {
            val bytesPerSample = bits / 8
            val frameBytes = bytesPerSample * channels
            if (frameBytes <= 0) return false
            val chunk = 8192
            var offset = dataStart
            val end = dataStart + dataLen
            var framesDone = 0L
            val out = ByteBuffer.allocate(chunk)
            while (offset < end) {
                out.clear()
                var written = 0
                while (written < chunk && offset + frameBytes <= end) {
                    for (c in 0 until channels) {
                        val sample = readSample(bytes, offset + c * bytesPerSample, bits, audioFormat)
                        val clamped = sample.coerceIn(-32768, 32767).toShort()
                        out.put((clamped.toInt() and 0xFF).toByte())
                        out.put(((clamped.toInt() shr 8) and 0xFF).toByte())
                        written += 2
                    }
                    offset += frameBytes
                    framesDone++
                }
                if (written == 0) break
                val ptsUs = (framesDone * 1_000_000L) / (sampleRate.toLong() * channels)
                sink.write(out, written, ptsUs)
            }
            sink.finish()
            return true
        } finally {
            sink.release()
        }
    }

    private fun readSample(bytes: ByteArray, offset: Int, bits: Int, format: Int): Int {
        if (offset < 0 || offset >= bytes.size) return 0
        return when {
            format == 3 && bits == 32 -> {
                if (offset + 4 > bytes.size) 0 else {
                    val f = ByteBuffer.wrap(bytes, offset, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).float
                    (f * 32767f).toInt()
                }
            }

            bits == 8 -> ((bytes[offset].toInt() and 0xFF) - 128) shl 8
            bits == 16 -> {
                if (offset + 2 > bytes.size) 0
                else le16(bytes, offset).let { if (it > 32767) it - 65536 else it }
            }

            bits == 24 -> {
                if (offset + 3 > bytes.size) 0 else {
                    val b0 = bytes[offset].toInt() and 0xFF
                    val b1 = bytes[offset + 1].toInt() and 0xFF
                    val b2 = bytes[offset + 2].toInt() and 0xFF
                    var v = (b2 shl 16) or (b1 shl 8) or b0
                    if (v and 0x800000 != 0) v = v or 0xFF000000.toInt()
                    v shr 8
                }
            }

            bits == 32 -> {
                if (offset + 4 > bytes.size) 0 else {
                    val v = le32(bytes, offset)
                    v shr 16
                }
            }

            else -> 0
        }
    }

    private fun le16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)

    // ------------------------------------------------------------------- sink

    /** Owns one AAC encoder plus the muxer, and feeds them sample by sample. */
    private inner class AacSink(
        private val outFile: File,
        sampleRate: Int,
        channels: Int
    ) {
        private val encoder: MediaCodec
        private val muxer: MediaMuxer = MediaMuxer(
            outFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        private var track = -1
        private var started = false
        private val info = MediaCodec.BufferInfo()

        init {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate.coerceAtLeast(8000),
                channels.coerceIn(1, 2)
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
        }

        fun write(src: ByteBuffer?, size: Int, ptsUs: Long) {
            if (size <= 0) {
                drain()
                return
            }
            var attempts = 0
            var index = encoder.dequeueInputBuffer(TIMEOUT_US)
            while (index < 0 && attempts < 40) {
                drain()
                attempts++
                index = encoder.dequeueInputBuffer(TIMEOUT_US)
            }
            if (index < 0) return
            val dst = encoder.getInputBuffer(index) ?: return
            dst.clear()
            if (src != null) {
                val copy = src.duplicate()
                copy.position(0)
                copy.limit(size)
                dst.put(copy)
            }
            encoder.queueInputBuffer(index, 0, size, ptsUs, 0)
            drain()
        }

        fun finish() {
            var attempts = 0
            var index = encoder.dequeueInputBuffer(TIMEOUT_US * 5)
            while (index < 0 && attempts < 20) {
                drain()
                attempts++
                index = encoder.dequeueInputBuffer(TIMEOUT_US * 5)
            }
            if (index >= 0) {
                encoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            var guard = 0
            while (guard < 400) {
                guard++
                val outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!started) {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        started = true
                    }
                    continue
                }
                if (outIndex < 0) continue
                val buf = encoder.getOutputBuffer(outIndex)
                if (buf != null && info.size > 0 && started &&
                    (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                ) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(track, buf, info)
                }
                encoder.releaseOutputBuffer(outIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }

        private fun drain() {
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(info, 0)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!started) {
                            track = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            started = true
                        }
                    }

                    outIndex >= 0 -> {
                        val buf = encoder.getOutputBuffer(outIndex)
                        if (buf != null && info.size > 0 && started &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                    }

                    else -> return
                }
            }
        }

        fun release() {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { if (started) muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
    }
}
