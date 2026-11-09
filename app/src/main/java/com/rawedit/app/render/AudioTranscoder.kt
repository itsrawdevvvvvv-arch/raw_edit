package com.rawedit.app.render

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.IOException

/** Decodes the picked MP3 (or any audio) and re-encodes it as AAC inside an MP4. */
class AudioTranscoder(private val context: Context) {

    private lateinit var extractor: MediaExtractor
    private lateinit var decoder: MediaCodec
    private lateinit var encoder: MediaCodec
    private lateinit var muxer: MediaMuxer

    private var muxTrack = -1
    private var muxStarted = false
    private var inputDone = false
    private var decoderDone = false
    private var encoderDone = false

    fun transcode(uri: Uri, outFile: File): Boolean {
        var ok = false
        try {
            val format = openInput(uri) ?: return false
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
            encFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            encFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            encFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val info = MediaCodec.BufferInfo()
            var guard = 0
            while (!encoderDone && guard < 4_000_000) {
                guard++
                feedDecoder()
                pumpDecoder(info)
                drainEncoder(info)
                if (decoderDone && encoderDone) break
            }
            ok = outFile.exists() && outFile.length() > 0
        } catch (t: Throwable) {
            ok = false
        } finally {
            release()
        }
        return ok
    }

    private fun openInput(uri: Uri): MediaFormat? {
        extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(f, null, null, 0)
                decoder.start()
                return f
            }
        }
        throw IOException("No audio track in this file")
    }

    private fun feedDecoder() {
        if (inputDone) return
        val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
        if (inIndex < 0) return
        val buf = decoder.getInputBuffer(inIndex) ?: return
        val size = extractor.readSampleData(buf, 0)
        if (size < 0) {
            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun pumpDecoder(info: MediaCodec.BufferInfo) {
        if (decoderDone) return
        val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
        if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) return
        if (outIndex < 0) return

        if (info.size > 0) {
            val pcm = decoder.getOutputBuffer(outIndex)
            if (pcm != null) {
                pcm.position(info.offset)
                pcm.limit(info.offset + info.size)
                pushPcm(pcm, info.presentationTimeUs, info.size)
            }
        }
        decoder.releaseOutputBuffer(outIndex, false)

        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            decoderDone = true
            pushPcm(null, info.presentationTimeUs, 0)
            val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US * 5)
            if (inIndex >= 0) {
                encoder.queueInputBuffer(
                    inIndex, 0, 0, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
        }
    }

    private fun pushPcm(src: java.nio.ByteBuffer?, ptsUs: Long, size: Int) {
        var attempts = 0
        var inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
        while (inIndex < 0 && attempts < 20) {
            drainEncoder(MediaCodec.BufferInfo())
            attempts++
            inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
        }
        if (inIndex < 0) return
        val dst = encoder.getInputBuffer(inIndex) ?: return
        dst.clear()
        if (src != null) {
            dst.put(src)
            encoder.queueInputBuffer(inIndex, 0, size, ptsUs, 0)
        } else {
            encoder.queueInputBuffer(inIndex, 0, 0, ptsUs, 0)
        }
    }

    private fun drainEncoder(info: MediaCodec.BufferInfo) {
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(info, 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxStarted) {
                        muxTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxStarted = true
                    }
                }

                outIndex >= 0 -> {
                    val buf = encoder.getOutputBuffer(outIndex)
                    if (buf != null && info.size > 0 && muxStarted &&
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(muxTrack, buf, info)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true
                        return
                    }
                }

                else -> return
            }
        }
    }

    private fun release() {
        try {
            decoder.stop()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            decoder.release()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            encoder.stop()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            encoder.release()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            if (muxStarted) muxer.stop()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            muxer.release()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            extractor.release()
        } catch (t: Throwable) {
            // ignore
        }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
    }
}
