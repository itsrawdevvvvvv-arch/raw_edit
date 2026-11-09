package com.rawedit.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rawedit.app.data.Prefs
import com.rawedit.app.data.VideoItem
import com.rawedit.app.data.VideoStore
import com.rawedit.app.net.ServerApi
import com.rawedit.app.net.ServerConfig
import com.rawedit.app.net.ServerException
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.components.ShimmerBar
import com.rawedit.app.ui.components.rememberTopInset
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import com.rawedit.app.util.RenderLog
import com.rawedit.app.util.Thumb
import com.rawedit.app.viewmodel.CreateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RenderScreen(
    vm: CreateViewModel,
    onFinished: () -> Unit,
    onExitToCode: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var progress by remember { mutableFloatStateOf(0.02f) }
    var stageText by remember { mutableStateOf("Checking the server") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var jobId by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var userCancelled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(attempt) {
        job = coroutineContext[Job]
        errorMessage = null
        progress = 0.02f
        stageText = "Checking the server"
        startedAt = System.currentTimeMillis()
        userCancelled = false
        RenderLog.start(context)

        val prefs = Prefs.get(context)
        val base = ServerConfig.normalize(prefs.serverUrl)
        val api = ServerApi(base)
        val audioUri = vm.audioUri
        if (audioUri == null) {
            errorMessage = "No voice file. Go back and add one."
            return@LaunchedEffect
        }
        if (ServerConfig.isPlaceholder(base)) {
            errorMessage = "No render server set. Open About > Server and paste your server address."
            return@LaunchedEffect
        }

        var tempAudio: File? = null
        var outFile: File? = null
        try {
            // 1. is the server awake?
            stageText = "Waking up the server"
            if (!api.health()) {
                throw ServerException(
                    "The render server is not answering. Check the address in About > Server and your internet."
                )
            }
            progress = 0.06f
            checkActive()

            // 2. copy the voice file out of the picker so the upload is a plain stream
            stageText = "Reading your voice file"
            tempAudio = File(context.cacheDir, "upload_voice").apply {
                context.contentResolver.openInputStream(audioUri)?.use { input ->
                    outputStream().use { output -> input.copyTo(output) }
                } ?: throw ServerException("Could not read the voice file. Pick it again.")
            }
            progress = 0.10f
            checkActive()

            // 3. upload
            stageText = "Uploading"
            val id = api.upload(
                deviceId = prefs.deviceId,
                audioFile = tempAudio,
                audioName = vm.audioName,
                code = vm.code,
                layersJson = vm.layersJson
            )
            jobId = id
            progress = 0.16f
            RenderLog.log("job " + id + " uploaded")

            // 4. poll
            var lastPercent = -1
            var done = false
            val deadline = System.currentTimeMillis() + 40 * 60 * 1000
            while (System.currentTimeMillis() < deadline) {
                checkActive()
                val s = api.status(id)
                when (s.status) {
                    "queued" -> stageText = "Waiting in line"
                    "rendering" -> stageText = "Drawing frames"
                    "encoding" -> stageText = "Adding your voice"
                    "done" -> {
                        done = true
                    }

                    "error" -> throw ServerException(
                        "The server could not render this: " + s.message
                    )

                    "cancelled" -> throw ServerException("The job was cancelled.")
                }
                if (done) break
                val mapped = 0.16f + (s.percent.coerceIn(0, 100) / 100f) * 0.74f
                if (mapped > progress) progress = mapped
                if (s.percent != lastPercent) {
                    lastPercent = s.percent
                    RenderLog.log("poll " + s.status + " " + s.percent + "%")
                }
                delay(2000)
            }
            if (!done) throw ServerException("The render took too long and was stopped.")
            progress = 0.92f

            // 5. download
            stageText = "Downloading the video"
            val stamp = System.currentTimeMillis()
            outFile = File(File(context.filesDir, "renders").apply { mkdirs() }, "raw_edit_$stamp.mp4")
            val ok = api.download(id, outFile) { p ->
                progress = 0.92f + (p / 100f) * 0.08f
            }
            if (!ok) throw ServerException("The download did not finish. Try again.")

            val thumbFile = File(File(context.filesDir, "thumbs").apply { mkdirs() }, "thumb_$stamp.png")
            Thumb.fromVideo(outFile.absolutePath, thumbFile)

            VideoStore.get(context).add(
                VideoItem(
                    id = stamp.toString(),
                    title = "Video " + (VideoStore.get(context).count() + 1),
                    style = vm.style?.label ?: "Motion Graphics",
                    durationMs = vm.audioDurationMs,
                    filePath = outFile.absolutePath,
                    thumbPath = if (thumbFile.exists()) thumbFile.absolutePath else ""
                )
            )
            vm.lastVideoPath = outFile.absolutePath
            vm.lastThumbPath = thumbFile.absolutePath
            vm.lastDurationMs = vm.audioDurationMs

            progress = 1f
            stageText = "Done"
            RenderLog.log("downloaded " + outFile.length() + " bytes")
            onFinished()
        } catch (c: CancellationException) {
            if (userCancelled) onCancel()
        } catch (t: Throwable) {
            RenderLog.log("render failed", t)
            outFile?.delete()
            errorMessage = t.message ?: "Something went wrong while rendering."
        } finally {
            tempAudio?.delete()
        }
    }

    fun shareLog() {
        try {
            val file = RenderLog.file(context)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "raw_edit render log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share render log"))
        } catch (t: Throwable) {
            errorMessage = (errorMessage ?: "") + "\nCould not share the log."
        }
    }

    suspend fun checkActive() {
        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
            throw CancellationException("cancelled")
        }
    }

    val topInset = rememberTopInset()
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            MonoLabel(if (errorMessage == null) "RENDERING ON THE SERVER" else "RENDER STOPPED")
            Spacer(Modifier.height(40.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Ink.Danger,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Nothing was saved.",
                    color = Ink.Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = "Retry",
                    onClick = { attempt += 1 },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = "Share log",
                    filled = false,
                    onClick = { shareLog() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = "Back to code",
                    filled = false,
                    onClick = { onExitToCode() },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = stageText,
                    color = Ink.Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = (progress * 100).toInt().toString() + "%" +
                        estimateText(progress, startedAt),
                    color = Ink.Muted,
                    fontFamily = Mono,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(18.dp))
                ShimmerBar(progress = progress, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "The free server draws the frames. Keep the app open; " +
                        "a 30 second video usually takes a few minutes.",
                    color = Ink.Muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = "Cancel",
                    filled = false,
                    icon = Icons.Rounded.Close,
                    onClick = {
                        userCancelled = true
                        job?.cancel()
                        jobId?.let { id ->
                            kotlinx.coroutines.MainScope().launch {
                                ServerApi(
                                    ServerConfig.normalize(Prefs.get(context).serverUrl)
                                ).cancel(id)
                            }
                        }
                        onCancel()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

private fun estimateText(progress: Float, startedAt: Long): String {
    if (startedAt <= 0L || progress <= 0.05f || progress >= 1f) return ""
    val elapsed = System.currentTimeMillis() - startedAt
    val totalMs = elapsed.toFloat() / progress
    val left = ((totalMs - elapsed).toLong() / 1000L).coerceAtLeast(0L)
    if (left < 5) return ""
    val mins = left / 60
    val secs = left % 60
    return "  -  about " + (if (mins > 0) mins.toString() + "m " else "") + secs + "s left"
}
