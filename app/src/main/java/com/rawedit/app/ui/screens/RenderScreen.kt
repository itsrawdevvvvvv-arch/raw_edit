package com.rawedit.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.rawedit.app.data.Prefs
import com.rawedit.app.data.VideoItem
import com.rawedit.app.data.VideoStore
import com.rawedit.app.net.ServerApi
import com.rawedit.app.net.ServerConfig
import com.rawedit.app.net.ServerException
import com.rawedit.app.render.RenderEngine
import com.rawedit.app.render.StageHost
import com.rawedit.app.render.WebStage
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Renders the video.
 *
 * If a render server is configured in About > Server, the phone uploads the job and
 * downloads the MP4. If none is set, it falls back to drawing the frames on the phone,
 * so the app still works before the server is deployed.
 */
@Composable
fun RenderScreen(
    vm: CreateViewModel,
    onFinished: () -> Unit,
    onExitToCode: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { Prefs.get(context) }
    val serverUrl = remember { ServerConfig.normalize(prefs.serverUrl) }
    val useServer = remember { !ServerConfig.isPlaceholder(serverUrl) }

    var progress by remember { mutableFloatStateOf(0.02f) }
    var stageText by remember { mutableStateOf(if (useServer) "Checking the server" else "Reading code") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var jobId by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var userCancelled by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf<StageHost?>(null) }
    var stage by remember { mutableStateOf<WebStage?>(null) }

    val renderWidth = if (vm.fastMode || vm.lowEndDevice) 1280 else 1920
    val renderHeight = if (renderWidth == 1920) 1080 else 720
    val fps = if (vm.lowEndDevice) 24 else 30

    fun finishVideo(file: File, stamp: Long, existingThumb: File? = null) {
        val thumbFile = existingThumb
            ?: File(File(context.filesDir, "thumbs").apply { mkdirs() }, "thumb_$stamp.png")
        if (!thumbFile.exists()) Thumb.fromVideo(file.absolutePath, thumbFile)
        VideoStore.get(context).add(
            VideoItem(
                id = stamp.toString(),
                title = "Video " + (VideoStore.get(context).count() + 1),
                style = vm.style?.label ?: "Motion Graphics",
                durationMs = vm.audioDurationMs,
                filePath = file.absolutePath,
                thumbPath = if (thumbFile.exists()) thumbFile.absolutePath else ""
            )
        )
        vm.lastVideoPath = file.absolutePath
        vm.lastThumbPath = thumbFile.absolutePath
        vm.lastDurationMs = vm.audioDurationMs
        progress = 1f
        stageText = "Done"
        onFinished()
    }

    // ---- server path -------------------------------------------------------

    suspend fun renderOnServer(audioUri: Uri) {
        val api = ServerApi(serverUrl)
        var tempAudio: File? = null
        var outFile: File? = null
        try {
            stageText = "Waking up the server"
            if (!api.health()) {
                throw ServerException(
                    "The render server is not answering. Check the address in About > Server, or clear it to render on the phone."
                )
            }
            progress = 0.06f
            ensureActive()

            stageText = "Reading your voice file"
            tempAudio = File(context.cacheDir, "upload_voice").apply {
                context.contentResolver.openInputStream(audioUri)?.use { input ->
                    outputStream().use { output -> input.copyTo(output) }
                } ?: throw ServerException("Could not read the voice file. Pick it again.")
            }
            progress = 0.10f
            ensureActive()

            stageText = "Uploading"
            val id = api.upload(prefs.deviceId, tempAudio, vm.audioName, vm.code, vm.layersJson)
            jobId = id
            progress = 0.16f
            RenderLog.log("job " + id + " uploaded")

            var lastPercent = -1
            var done = false
            val deadline = System.currentTimeMillis() + 40 * 60 * 1000
            while (System.currentTimeMillis() < deadline) {
                ensureActive()
                val s = api.status(id)
                when (s.status) {
                    "queued" -> stageText = "Waiting in line"
                    "rendering" -> stageText = "Drawing frames"
                    "encoding" -> stageText = "Adding your voice"
                    "done" -> done = true
                    "error" -> throw ServerException("The server could not render this: " + s.message)
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

            stageText = "Downloading the video"
            val stamp = System.currentTimeMillis()
            outFile = File(File(context.filesDir, "renders").apply { mkdirs() }, "raw_edit_$stamp.mp4")
            val ok = api.download(id, outFile) { p -> progress = 0.92f + (p / 100f) * 0.08f }
            if (!ok) throw ServerException("The download did not finish. Try again.")
            finishVideo(outFile, stamp)
        } finally {
            tempAudio?.delete()
        }
    }

    // ---- on-phone path -----------------------------------------------------

    suspend fun renderOnPhone(s: WebStage, audioUri: Uri) {
        val engine = RenderEngine(
            context = context,
            stage = s,
            audioUri = audioUri,
            durationMs = vm.audioDurationMs,
            preferredWidth = renderWidth,
            preferredHeight = renderHeight,
            fps = fps,
            stageWidth = renderWidth,
            stageHeight = renderHeight
        )
        val result = engine.render(
            code = vm.code,
            layersJson = vm.layersJson,
            onStage = { stageText = it },
            onProgress = { p -> progress = p },
            onPreview = { bmp -> preview = bmp }
        )
        finishVideo(result.video, System.currentTimeMillis(), result.thumb)
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

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            job?.cancel()
        }
    }

    LaunchedEffect(attempt) {
        job = coroutineContext[Job]
        errorMessage = null
        preview = null
        progress = 0.02f
        stageText = if (useServer) "Checking the server" else "Reading code"
        startedAt = System.currentTimeMillis()
        userCancelled = false
        RenderLog.start(context)

        val audioUri = vm.audioUri
        if (audioUri == null) {
            RenderLog.log("stopped: no audio file selected")
            errorMessage = "No voice file. Go back and add one."
            return@LaunchedEffect
        }
        if (vm.code.isBlank()) {
            RenderLog.log("stopped: no code")
            errorMessage = "No code pasted. Go back and paste the HTML."
            return@LaunchedEffect
        }

        try {
            if (useServer) {
                renderOnServer(audioUri)
            } else {
                RenderLog.log("no server set, rendering on the phone")
                var waited = 0
                while (host == null && waited < 6000) {
                    delay(50)
                    waited += 50
                }
                val s = stage ?: throw IllegalStateException("The preview did not start.")
                renderOnPhone(s, audioUri)
            }
        } catch (c: CancellationException) {
            if (userCancelled) onCancel()
        } catch (t: Throwable) {
            RenderLog.log("render failed", t)
            errorMessage = t.message ?: "Something went wrong while rendering."
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
            Spacer(Modifier.height(18.dp))
            MonoLabel(
                when {
                    errorMessage != null -> "RENDER STOPPED"
                    useServer -> "RENDERING ON THE SERVER"
                    else -> "RENDERING ON THIS PHONE"
                }
            )
            Spacer(Modifier.height(14.dp))

            if (!useServer) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            StageHost(ctx, renderWidth, renderHeight).also { h ->
                                val ws = WebStage(ctx, h.web)
                                ws.prepare()
                                h.lockInput()
                                host = h
                                stage = ws
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    val p = preview
                    if (p != null) {
                        Image(
                            bitmap = p.asImageBitmap(),
                            contentDescription = "Live preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            } else {
                Spacer(Modifier.height(24.dp))
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Ink.Danger,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
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
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = (progress * 100).toInt().toString() + "%" +
                        estimateText(progress, startedAt),
                    color = Ink.Muted,
                    fontFamily = Mono,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                ShimmerBar(progress = progress, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (useServer) {
                        "The free server draws the frames. Keep the app open."
                    } else {
                        "No server set, so this phone draws the frames. Keep the app open."
                    },
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
                                ServerApi(serverUrl).cancel(id)
                            }
                        }
                        onCancel()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private suspend fun ensureActive() {
    if (!currentCoroutineContext().isActive) throw CancellationException("cancelled")
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
