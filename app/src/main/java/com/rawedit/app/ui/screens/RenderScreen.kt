package com.rawedit.app.ui.screens

import android.graphics.Bitmap
import android.view.View
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
import com.rawedit.app.data.VideoItem
import com.rawedit.app.data.VideoStore
import com.rawedit.app.render.RenderEngine
import com.rawedit.app.render.StageHost
import com.rawedit.app.render.WebStage
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.components.ShimmerBar
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import com.rawedit.app.viewmodel.CreateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

@Composable
fun RenderScreen(
    vm: CreateViewModel,
    onFinished: () -> Unit,
    onFailed: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var host by remember { mutableStateOf<StageHost?>(null) }
    var stage by remember { mutableStateOf<WebStage?>(null) }
    var progress by remember { mutableFloatStateOf(0.02f) }
    var status by remember { mutableStateOf("Reading code") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            job?.cancel()
        }
    }

    LaunchedEffect(host) {
        val h = host ?: return@LaunchedEffect
        val s = stage ?: return@LaunchedEffect
        job = coroutineContext[Job]
        val uri = vm.audioUri
        if (uri == null) {
            error = "No voice file. Go back and add one."
            return@LaunchedEffect
        }
        val outW = if (vm.fastMode) 1280 else 1920
        val outH = if (vm.fastMode) 720 else 1080
        val engine = RenderEngine(
            context = context,
            stage = s,
            audioUri = uri,
            durationMs = vm.audioDurationMs,
            outWidth = outW,
            outHeight = outH
        )
        try {
            val result = engine.render(
                code = vm.code,
                layersJson = vm.layersJson,
                onProgress = { p, text ->
                    progress = p
                    status = text
                },
                onPreview = { bmp -> preview = bmp }
            )
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
            status = "Done"
            onFinished()
        } catch (c: CancellationException) {
            onCancel()
        } catch (t: Throwable) {
            error = t.message ?: "Something went wrong while rendering."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        MonoLabel("RENDERING ON THIS PHONE")
        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
        ) {
            // The WebView stays visible and laid out at full stage size; it is the live preview.
            AndroidView(
                factory = { ctx ->
                    StageHost(ctx).also { h ->
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
                    contentDescription = "Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Text(
            text = status,
            color = Ink.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = (progress * 100).toInt().toString() + "%",
            color = Ink.Muted,
            fontFamily = Mono,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        ShimmerBar(progress = progress, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Keep the app open. A 1 minute video usually takes a few minutes on a phone.",
            color = Ink.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        if (error != null) {
            Text(
                text = error!!,
                color = Ink.Danger,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            PillButton(
                text = "Go back",
                onClick = { onFailed(error!!) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            PillButton(
                text = "Cancel",
                filled = false,
                icon = Icons.Rounded.Close,
                onClick = {
                    job?.cancel()
                    onCancel()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}
