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
import androidx.compose.material3.Icon
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
import com.rawedit.app.data.VideoItem
import com.rawedit.app.data.VideoStore
import com.rawedit.app.render.RenderEngine
import com.rawedit.app.render.StageHost
import com.rawedit.app.render.WebStage
import com.rawedit.app.service.RenderService
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.components.ShimmerBar
import com.rawedit.app.ui.components.rememberTopInset
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import com.rawedit.app.util.RenderLog
import com.rawedit.app.viewmodel.CreateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

@Composable
fun RenderScreen(
    vm: CreateViewModel,
    onFinished: () -> Unit,
    onExitToCode: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var host by remember { mutableStateOf<StageHost?>(null) }
    var stage by remember { mutableStateOf<WebStage?>(null) }
    var progress by remember { mutableFloatStateOf(0.02f) }
    var stageText by remember { mutableStateOf("Reading code") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var userCancelled by remember { mutableStateOf(false) }
    var selfTest by remember { mutableStateOf(false) }
    var selfTestPassed by remember { mutableStateOf<String?>(null) }

    val renderWidth = if (vm.fastMode || vm.lowEndDevice) 1280 else 1920
    val renderHeight = if (renderWidth == 1920) 1080 else 720
    // The stage view is created once, so the self test uses the same size.
    val testWidth = renderWidth
    val testHeight = renderHeight

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            job?.cancel()
            RenderService.stop(context)
        }
    }

    LaunchedEffect(attempt, host) {
        val s = stage ?: return@LaunchedEffect
        if (host == null) return@LaunchedEffect

        val isTest = selfTest
        val code = if (isTest) {
            runCatching {
                context.assets.open("selftest.html").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        } else {
            vm.code
        }
        val durationMs = if (isTest) 3000L else vm.audioDurationMs
        val uri = vm.audioUri
        if (!isTest && uri == null) {
            errorMessage = "No voice file. Go back and add one."
            return@LaunchedEffect
        }

        job = coroutineContext[Job]
        errorMessage = null
        selfTestPassed = null
        progress = 0.02f
        stageText = "Reading code"
        preview = null
        startedAt = System.currentTimeMillis()
        userCancelled = false
        RenderLog.start(context)
        RenderService.start(context)

        val fps = if (vm.lowEndDevice) 24 else 30
        val engine = RenderEngine(
            context = context,
            stage = s,
            audioUri = uri ?: Uri.EMPTY,
            durationMs = durationMs,
            preferredWidth = if (isTest) testWidth else renderWidth,
            preferredHeight = if (isTest) testHeight else renderHeight,
            fps = fps,
            stageWidth = if (isTest) testWidth else renderWidth,
            stageHeight = if (isTest) testHeight else renderHeight
        )

        var lastNotifAt = 0L
        try {
            val result = engine.render(
                code = code,
                layersJson = if (isTest) null else vm.layersJson,
                onStage = { stageText = it },
                onProgress = { p ->
                    progress = p
                    val now = System.currentTimeMillis()
                    if (now - lastNotifAt > 1000) {
                        lastNotifAt = now
                        RenderService.update(context, (p * 100).toInt(), stageText)
                    }
                },
                onPreview = { bmp -> preview = bmp },
                selfTest = isTest
            )
            RenderService.stop(context)
            if (isTest) {
                val seconds = (result.elapsedMs / 1000.0)
                selfTestPassed = String.format(
                    "Self test passed: %d frames at %dx%d in %.1f s",
                    result.frames, result.width, result.height, seconds
                )
                result.video.delete()
                selfTest = false
                progress = 1f
                stageText = "Self test passed"
            } else {
                vm.lastVideoPath = result.video.absolutePath
                vm.lastThumbPath = result.thumb.absolutePath
                vm.lastDurationMs = vm.audioDurationMs
                VideoStore.get(context).add(
                    VideoItem(
                        id = System.currentTimeMillis().toString(),
                        title = "Video " + (VideoStore.get(context).count() + 1),
                        style = vm.style?.label ?: "Motion Graphics",
                        durationMs = vm.audioDurationMs,
                        filePath = result.video.absolutePath,
                        thumbPath = result.thumb.absolutePath
                    )
                )
                progress = 1f
                stageText = "Done"
                onFinished()
            }
        } catch (c: CancellationException) {
            RenderService.stop(context)
            if (userCancelled) onCancel()
        } catch (t: Throwable) {
            RenderService.stop(context)
            selfTest = false
            errorMessage = t.message ?: "Something went wrong while rendering."
        }
    }

    fun shareLog() {
        try {
            val file = RenderLog.file(context)
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
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

    val topInset = rememberTopInset()
    Box(
        modifier = Modifier
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
            Spacer(Modifier.height(20.dp))
            MonoLabel(
                when {
                    selfTestPassed != null -> "SELF TEST PASSED"
                    errorMessage != null -> "RENDER STOPPED"
                    else -> "RENDERING ON THIS PHONE"
                }
            )
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        StageHost(
                            ctx,
                            if (selfTest) testWidth else renderWidth,
                            if (selfTest) testHeight else renderHeight
                        ).also { h ->
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

            when {
                selfTestPassed != null -> {
                    Text(
                        text = selfTestPassed!!,
                        color = Ink.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The phone can capture and encode. If your own code still " +
                            "fails, the problem is in the code, not the app.",
                        color = Ink.Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    PillButton(
                        text = "Back to code",
                        filled = false,
                        onClick = { onExitToCode() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        color = Ink.Danger,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Nothing was saved.",
                        color = Ink.Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    PillButton(
                        text = "Retry",
                        onClick = {
                            selfTest = false
                            attempt += 1
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    PillButton(
                        text = "Run self test",
                        filled = false,
                        onClick = {
                            selfTest = true
                            attempt += 1
                        },
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
                        onClick = {
                            RenderService.stop(context)
                            onExitToCode()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    Text(
                        text = stageText,
                        color = Ink.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
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
                        text = "Keep the app open. Progress also shows in the notification.",
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
                            RenderService.stop(context)
                            job?.cancel()
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun estimateText(progress: Float, startedAt: Long): String {
    if (startedAt <= 0L || progress <= 0.03f || progress >= 1f) return ""
    val elapsed = System.currentTimeMillis() - startedAt
    val totalMs = elapsed.toFloat() / progress
    val left = ((totalMs - elapsed).toLong() / 1000L).coerceAtLeast(0L)
    if (left < 5) return ""
    val mins = left / 60
    val secs = left % 60
    return "  -  about " + (if (mins > 0) mins.toString() + "m " else "") + secs + "s left"
}
