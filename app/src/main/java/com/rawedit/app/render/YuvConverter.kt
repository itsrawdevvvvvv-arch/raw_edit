package com.rawedit.app.render

import android.media.Image

/** ARGB -> YUV 4:2:0 conversion for the H.264 encoder. */
object YuvConverter {

    fun fillImage(image: Image, argb: IntArray, w: Int, h: Int) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        val uRow = uPlane.rowStride
        val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride
        val vPix = vPlane.pixelStride

        for (j in 0 until h) {
            val rowBase = j * w
            val yBase = j * yRow
            for (i in 0 until w) {
                val c = argb[rowBase + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                var y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                if (y < 0) y = 0 else if (y > 255) y = 255
                yBuf.put(yBase + i * yPix, y.toByte())
            }
        }

        for (j in 0 until h / 2) {
            val uBase = j * uRow
            val vBase = j * vRow
            for (i in 0 until w / 2) {
                val c0 = argb[(2 * j) * w + 2 * i]
                val c1 = argb[(2 * j) * w + 2 * i + 1]
                val c2 = argb[(2 * j + 1) * w + 2 * i]
                val c3 = argb[(2 * j + 1) * w + 2 * i + 1]

                val r = (((c0 shr 16) and 0xFF) + ((c1 shr 16) and 0xFF) +
                    ((c2 shr 16) and 0xFF) + ((c3 shr 16) and 0xFF)) / 4
                val g = (((c0 shr 8) and 0xFF) + ((c1 shr 8) and 0xFF) +
                    ((c2 shr 8) and 0xFF) + ((c3 shr 8) and 0xFF)) / 4
                val b = ((c0 and 0xFF) + (c1 and 0xFF) + (c2 and 0xFF) + (c3 and 0xFF)) / 4

                var u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                var v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                if (u < 0) u = 0 else if (u > 255) u = 255
                if (v < 0) v = 0 else if (v > 255) v = 255

                uBuf.put(uBase + i * uPix, u.toByte())
                vBuf.put(vBase + i * vPix, v.toByte())
            }
        }
    }

    /** NV12 (planar = false) or I420 (planar = true) into a flat byte array. */
    fun toByteArray(argb: IntArray, w: Int, h: Int, out: ByteArray, planar: Boolean) {
        var yIndex = 0
        for (j in 0 until h) {
            val rowBase = j * w
            for (i in 0 until w) {
                val c = argb[rowBase + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                var y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                if (y < 0) y = 0 else if (y > 255) y = 255
                out[yIndex++] = y.toByte()
            }
        }

        val chromaSize = w * h / 4
        var uvIndex = w * h
        for (j in 0 until h / 2) {
            for (i in 0 until w / 2) {
                val c0 = argb[(2 * j) * w + 2 * i]
                val c1 = argb[(2 * j) * w + 2 * i + 1]
                val c2 = argb[(2 * j + 1) * w + 2 * i]
                val c3 = argb[(2 * j + 1) * w + 2 * i + 1]

                val r = (((c0 shr 16) and 0xFF) + ((c1 shr 16) and 0xFF) +
                    ((c2 shr 16) and 0xFF) + ((c3 shr 16) and 0xFF)) / 4
                val g = (((c0 shr 8) and 0xFF) + ((c1 shr 8) and 0xFF) +
                    ((c2 shr 8) and 0xFF) + ((c3 shr 8) and 0xFF)) / 4
                val b = ((c0 and 0xFF) + (c1 and 0xFF) + (c2 and 0xFF) + (c3 and 0xFF)) / 4

                var u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                var v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                if (u < 0) u = 0 else if (u > 255) u = 255
                if (v < 0) v = 0 else if (v > 255) v = 255

                if (planar) {
                    val idx = j * (w / 2) + i
                    out[w * h + idx] = u.toByte()
                    out[w * h + chromaSize + idx] = v.toByte()
                } else {
                    out[uvIndex++] = u.toByte()
                    out[uvIndex++] = v.toByte()
                }
            }
        }
    }
}
