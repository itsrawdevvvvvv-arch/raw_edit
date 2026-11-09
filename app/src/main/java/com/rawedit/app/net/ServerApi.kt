package com.rawedit.app.net

import com.rawedit.app.util.RenderLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** A failure we can explain to the user. */
class ServerException(message: String) : IOException(message)

/** Talks to raw_edit_server: upload, poll, download. No third party libraries. */
class ServerApi(private val baseUrl: String) {

    data class Status(
        val status: String,
        val percent: Int,
        val message: String,
        val queuePosition: Int,
        val etaSeconds: Int?
    )

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = open("/health", "GET")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun upload(
        deviceId: String,
        audioFile: File,
        audioName: String,
        code: String,
        layersJson: String?
    ): String = withContext(Dispatchers.IO) {
        val boundary = "----rawedit" + System.currentTimeMillis()
        val conn = open("/jobs", "POST")
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 180000
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.setRequestProperty("X-Device-Id", deviceId)
        conn.setChunkedStreamingMode(0)

        try {
            conn.outputStream.use { out ->
                writeFilePart(out, boundary, "audio", audioName.ifEmpty { "voice.mp3" }, "audio/mpeg", audioFile)
                writeTextPart(out, boundary, "code", "code.html", "text/html", code)
                if (!layersJson.isNullOrBlank()) {
                    writeTextPart(out, boundary, "layers", "layers.json", "application/json", layersJson)
                }
                out.write(("--$boundary--\r\n").toByteArray())
                out.flush()
            }

            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            RenderLog.log("upload http=" + status + " body=" + body.take(200))

            if (status == 202) {
                val id = JSONObject(body).optString("jobId", "")
                if (id.isNotEmpty()) return@withContext id
                throw ServerException("The server did not return a job id.")
            }
            throw ServerException(
                when (status) {
                    429 -> "You have made 5 videos this hour. Try again a bit later."
                    503 -> "The server is busy right now. Try again in a minute."
                    413 -> "That voice file is too long for the free server (20 minutes max)."
                    400 -> "The server could not read the files. Check the code and the voice file."
                    else -> "The server answered $status."
                }
            )
        } catch (s: ServerException) {
            throw s
        } catch (t: Throwable) {
            RenderLog.log("upload failed", t)
            throw ServerException(
                "Could not reach the render server. Check your internet, then the address in About > Server."
            )
        } finally {
            try {
                conn.disconnect()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }

    suspend fun status(jobId: String): Status = withContext(Dispatchers.IO) {
        val conn = open("/jobs/$jobId", "GET")
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 404) throw ServerException("The job disappeared on the server.")
            if (code !in 200..299) throw ServerException("The server answered $code.")
            val o = JSONObject(body)
            Status(
                status = o.optString("status", "queued"),
                percent = o.optInt("percent", 0),
                message = o.optString("message", ""),
                queuePosition = o.optInt("queuePosition", 0),
                etaSeconds = if (o.isNull("etaSeconds")) null else o.optInt("etaSeconds")
            )
        } finally {
            try {
                conn.disconnect()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }

    suspend fun download(jobId: String, dest: File, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val conn = open("/jobs/$jobId/video", "GET")
            conn.connectTimeout = 20000
            conn.readTimeout = 120000
            try {
                val code = conn.responseCode
                if (code != 200) throw ServerException("The server has no video for this job.")
                val total = conn.contentLengthLong
                dest.parentFile?.mkdirs()
                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(256 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } > 0) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) onProgress(((done * 100) / total).toInt())
                        }
                    }
                }
                dest.exists() && dest.length() > 1024
            } finally {
                try {
                    conn.disconnect()
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }

    suspend fun cancel(jobId: String) = withContext(Dispatchers.IO) {
        try {
            val conn = open("/jobs/$jobId", "DELETE")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.responseCode
            conn.disconnect()
        } catch (t: Throwable) {
            // best effort
        }
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.instanceFollowRedirects = true
        return conn
    }

    private fun writeTextPart(
        out: OutputStream,
        boundary: String,
        field: String,
        fileName: String,
        mime: String,
        text: String
    ) {
        out.write(
            ("--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"$field\"; filename=\"$fileName\"\r\n" +
                "Content-Type: $mime\r\n\r\n").toByteArray()
        )
        out.write(text.toByteArray())
        out.write("\r\n".toByteArray())
    }

    private fun writeFilePart(
        out: OutputStream,
        boundary: String,
        field: String,
        fileName: String,
        mime: String,
        file: File
    ) {
        out.write(
            ("--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"$field\"; filename=\"$fileName\"\r\n" +
                "Content-Type: $mime\r\n\r\n").toByteArray()
        )
        file.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } > 0) out.write(buf, 0, read)
        }
        out.write("\r\n".toByteArray())
    }
}
