package com.rawedit.app.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rawedit.app.data.Layer
import com.rawedit.app.render.StageHost
import com.rawedit.app.render.WebStage
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.rememberTopInset
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import com.rawedit.app.util.Fmt
import com.rawedit.app.viewmodel.CreateViewModel
import kotlinx.coroutines.delay
import org.json.JSONArray

@Composable
fun EditorScreen(vm: CreateViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var host by remember { mutableStateOf<StageHost?>(null) }
    var stage by remember { mutableStateOf<WebStage?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(0f) }
    var showLayers by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val duration = remember(vm.audioDurationMs) {
        if (vm.audioDurationMs > 0) vm.audioDurationMs / 1000f else 10f
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.size > 10 * 1024 * 1024) return@rememberLauncherForActivityResult
            val mime = context.contentResolver.getType(uri) ?: "image/png"
            val data = "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            val maxZ = vm.layers.maxOfOrNull { it.z } ?: 0
            val layer = Layer(
                id = "user_" + System.currentTimeMillis(),
                name = "Image " + ((vm.layers.count { it.type == "image" }) + 1),
                type = "image",
                x = 560f,
                y = 340f,
                w = 800f,
                h = 400f,
                opacity = 1f,
                z = maxZ + 1,
                start = 0f,
                end = duration,
                src = data
            )
            vm.setLayers(vm.layers + layer)
            selectedId = layer.id
            showLayers = true
        } catch (t: Throwable) {
            loadError = "That image could not be added."
        }
    }

    // Load the code once the stage exists.
    LaunchedEffect(host) {
        val h = host ?: return@LaunchedEffect
        val s = stage ?: return@LaunchedEffect
        loading = true
        val ok = s.load(vm.code)
        if (!ok) {
            loadError = "The code didn't start. Ask Claude to try again."
            loading = false
            return@LaunchedEffect
        }
        s.fitStage()
        val raw = s.readLayers()
        val list = parseLayers(raw)
        if (list.isNotEmpty()) {
            vm.setLayers(list, recordHistory = false)
        }
        val d = s.probe().duration
        if (d > 0) time = 0f
        s.seek(0.0)
        loading = false
    }

    // Push layer edits into the page.
    LaunchedEffect(vm.layersJson) {
        val s = stage ?: return@LaunchedEffect
        val json = vm.layersJson ?: return@LaunchedEffect
        // debounce: a drag emits many updates, and each one is a JS round trip
        delay(60)
        s.writeLayers(json)
        s.seek(time.toDouble())
        vm.saveDraft()
    }

    // Playback loop.
    LaunchedEffect(playing) {
        while (playing) {
            val next = time + 0.033f
            time = if (next >= duration) 0f else next
            delay(33)
        }
    }

    LaunchedEffect(time, playing) {
        if (!playing) {
            val s = stage ?: return@LaunchedEffect
            s.seek(time.toDouble())
        }
    }

    val selected = vm.layers.firstOrNull { it.id == selectedId }

    val topInset = rememberTopInset()
    Column(Modifier.fillMaxSize().padding(top = topInset).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.ArrowBack, "Back") {
                playing = false
                onBack()
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Edit video", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                MonoLabel("OPTIONAL - SKIP IF YOU LIKE IT")
            }
            RoundIcon(Icons.Rounded.Undo, "Undo", enabled = vm.canUndo()) { vm.undo() }
            Spacer(Modifier.width(6.dp))
            RoundIcon(Icons.Rounded.Redo, "Redo", enabled = vm.canRedo()) { vm.redo() }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black)
        ) {
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
                modifier = Modifier.fillMaxSize().onSizeChanged { previewSize = it }
            )

            if (!loading && loadError == null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val size = previewSize
                                if (size.width == 0) return@awaitEachGesture
                                val s = minOf(
                                    size.width.toFloat() / StageHost.STAGE_W,
                                    size.height.toFloat() / StageHost.STAGE_H
                                )
                                val offX = (size.width - StageHost.STAGE_W * s) / 2f
                                val offY = (size.height - StageHost.STAGE_H * s) / 2f
                                val px = (down.position.x - offX) / s
                                val py = (down.position.y - offY) / s

                                val hit = vm.layers
                                    .filter {
                                        !it.hidden && px >= it.x && px <= it.x + it.w &&
                                            py >= it.y && py <= it.y + it.h
                                    }
                                    .maxByOrNull { it.z }
                                if (hit != null) {
                                    selectedId = hit.id
                                    vm.beginEdit()
                                }
                                val target = hit
                                if (target != null) {
                                    do {
                                        val event = awaitPointerEvent()
                                        val pan = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        if (pan.x != 0f || pan.y != 0f || zoom != 1f) {
                                            vm.updateLayer(target.id) { layer ->
                                                val newW = (layer.w * zoom).coerceIn(60f, 1920f)
                                                val newH = (layer.h * zoom).coerceIn(60f, 1080f)
                                                layer.copy(
                                                    x = layer.x + pan.x / s,
                                                    y = layer.y + pan.y / s,
                                                    w = newW,
                                                    h = newH
                                                )
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        }
                )
            }

            if (loading) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Text("Loading preview", color = Ink.Muted, fontSize = 12.sp)
                }
            }
            if (loadError != null) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Text(
                        loadError!!,
                        color = Ink.Danger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(
                icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                label = if (playing) "Pause" else "Play"
            ) { playing = !playing }
            Spacer(Modifier.width(12.dp))
            Text(
                text = Fmt.duration((time * 1000).toLong()) + " / " + Fmt.duration((duration * 1000).toLong()),
                color = Ink.Text,
                fontFamily = Mono,
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Rounded.AddPhotoAlternate, "Add image") {
                imagePicker.launch(arrayOf("image/*"))
            }
            Spacer(Modifier.width(6.dp))
            RoundIcon(Icons.Rounded.Layers, "Layers") { showLayers = !showLayers }
        }

        Slider(
            value = time.coerceIn(0f, duration),
            onValueChange = {
                playing = false
                time = it
            },
            valueRange = 0f..maxOf(duration, 0.1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Ink.Pill
            )
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (selected != null) {
                Spacer(Modifier.height(6.dp))
                Text(selected.name, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                MonoLabel(selected.type.uppercase() + "  |  Z " + selected.z)
                Spacer(Modifier.height(8.dp))

                Text("Opacity", color = Ink.Muted, fontSize = 11.sp)
                Slider(
                    value = selected.opacity,
                    onValueChange = { v ->
                        vm.updateLayer(selected.id) { it.copy(opacity = v) }
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Ink.Pill
                    )
                )

                Text(
                    "Size  " + selected.w.toInt() + " x " + selected.h.toInt(),
                    color = Ink.Muted,
                    fontSize = 11.sp
                )
                Slider(
                    value = (selected.w / 1920f).coerceIn(0.03f, 1f),
                    onValueChange = { v ->
                        val newW = v * 1920f
                        val ratio = if (selected.w > 0f) selected.h / selected.w else 1f
                        vm.updateLayer(selected.id) { it.copy(w = newW, h = newW * ratio) }
                    },
                    valueRange = 0.03f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Ink.Pill
                    )
                )

                Text(
                    "Time  " + String.format("%.1f", selected.start) + "s to " +
                        String.format("%.1f", selected.end) + "s",
                    color = Ink.Muted,
                    fontSize = 11.sp
                )
                val lo = minOf(selected.start, selected.end).coerceIn(0f, duration)
                val hi = maxOf(selected.start, selected.end).coerceIn(0f, duration)
                RangeSlider(
                    value = lo..maxOf(hi, lo + 0.1f),
                    onValueChange = { range ->
                        vm.updateLayer(selected.id) {
                            it.copy(start = range.start, end = range.endInclusive)
                        }
                    },
                    valueRange = 0f..maxOf(duration, 0.1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Ink.Pill
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Up") {
                        vm.updateLayer(selected.id) { it.copy(z = it.z + 1) }
                    }
                    SmallButton("Down") {
                        vm.updateLayer(selected.id) { it.copy(z = it.z - 1) }
                    }
                    SmallButton(if (selected.hidden) "Show" else "Hide") {
                        vm.updateLayer(selected.id) { it.copy(hidden = !it.hidden) }
                    }
                    SmallButton("Delete", danger = true) {
                        vm.setLayers(vm.layers.filterNot { it.id == selected.id })
                        selectedId = null
                    }
                }
            }

            if (showLayers) {
                Spacer(Modifier.height(14.dp))
                Text("Layers", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                if (vm.layers.isEmpty()) {
                    Text(
                        "This code has no layers yet.",
                        color = Ink.Muted,
                        fontSize = 12.sp
                    )
                }
                vm.layers.sortedByDescending { it.z }.forEach { layer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (layer.id == selectedId) Ink.Raised else Ink.Card)
                            .clickable { selectedId = layer.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = layer.name,
                            color = if (layer.hidden) Ink.Muted else Ink.Text,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (layer.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Hide",
                            tint = Ink.Muted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    vm.updateLayer(layer.id) { it.copy(hidden = !it.hidden) }
                                }
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = Ink.Muted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    vm.setLayers(vm.layers.filterNot { it.id == layer.id })
                                }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            PillButton(
                text = "Done, back to code",
                onClick = {
                    playing = false
                    onBack()
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Ink.Raised)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Ink.Text else Ink.Muted,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SmallButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Ink.Pill)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = if (danger) Ink.Danger else Ink.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun parseLayers(raw: String): List<Layer> {
    return try {
        val text = if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        } else {
            raw
        }
        val arr = JSONArray(text)
        val out = mutableListOf<Layer>()
        for (i in 0 until arr.length()) {
            out.add(Layer.fromJson(arr.getJSONObject(i)))
        }
        out
    } catch (t: Throwable) {
        emptyList()
    }
}
