package com.rawedit.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.rawedit.app.data.Style
import com.rawedit.app.render.CodeValidator
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.components.RawCard
import com.rawedit.app.ui.components.StepProgress
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import com.rawedit.app.util.AudioMeta
import com.rawedit.app.util.Fmt
import com.rawedit.app.util.rememberMotionEnabled
import com.rawedit.app.viewmodel.CreateViewModel
import kotlinx.coroutines.delay

@Composable
fun CreateScreen(
    vm: CreateViewModel,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onStartRender: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var step by remember { mutableStateOf(1) }
    var copied by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (t: Throwable) {
            // some pickers do not grant persistable permission
        }
        val info = AudioMeta.read(context, uri)
        if (info == null) {
            pickError = "That file has no readable audio. Try another one."
        } else if (info.durationMs <= 0L) {
            pickError = "That file has no readable audio. Try another one."
        } else {
            pickError = null
            vm.setAudio(uri, info.displayName, info.durationMs)
        }
    }

    LaunchedEffect(Unit) { vm.loadDraft() }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Ink.Raised)
                    .clickable {
                        if (step > 1) step -= 1 else onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Ink.Text,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            MonoLabel("STEP " + step + " OF 4")
        }
        Spacer(Modifier.height(16.dp))
        StepProgress(step = step)
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (step) {
                1 -> StepAudio(
                    vm = vm,
                    pickError = pickError,
                    onPick = { picker.launch(arrayOf("audio/*")) }
                )

                2 -> StepStyle(vm = vm)
                3 -> StepPrompt(
                    vm = vm,
                    copied = copied,
                    onCopy = {
                        clipboard.setText(AnnotatedString(vm.prompt()))
                        copied = true
                    },
                    onOpenClaude = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai"))
                            )
                        } catch (t: Throwable) {
                            error = "No browser found to open Claude."
                        }
                    }
                )

                else -> StepCode(
                    vm = vm,
                    error = error,
                    onError = { error = it },
                    onOpenEditor = onOpenEditor,
                    onPaste = {
                        val text = clipboard.getText()?.text
                        if (text.isNullOrBlank()) {
                            error = "Nothing to paste. Copy the code from Claude first."
                        } else {
                            vm.code = text
                            error = null
                        }
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // footer
        Column {
            val nextEnabled = when (step) {
                1 -> vm.canLeaveAudioStep()
                2 -> vm.canLeaveStyleStep()
                3 -> true
                else -> CodeValidator.validate(vm.code) is CodeValidator.Result.Ok
            }
            PillButton(
                text = if (step < 4) "Next" else "Create video",
                onClick = {
                    when (step) {
                        1 -> step = 2
                        2 -> step = 3
                        3 -> step = 4
                        else -> {
                            val check = CodeValidator.validate(vm.code)
                            if (check is CodeValidator.Result.Ok) {
                                vm.code = CodeValidator.normalize(vm.code)
                                vm.saveDraft()
                                onStartRender()
                            } else if (check is CodeValidator.Result.Bad) {
                                error = check.message
                            }
                        }
                    }
                },
                enabled = nextEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (step) {
                    1 -> "Next unlocks once a voice file is added."
                    2 -> "Next unlocks once a style is picked."
                    3 -> "Copy the prompt, paste it in Claude, then tap I have the code."
                    else -> "Paste the code Claude returned."
                },
                color = Ink.Muted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun StepAudio(vm: CreateViewModel, pickError: String?, onPick: () -> Unit) {
    val motion = rememberMotionEnabled()
    val float by androidx.compose.animation.core.rememberInfiniteTransition(label = "float")
        .animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(2000),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "floatY"
        )

    Text("Add your voice", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 26.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Pick the MP3 voiceover you already made.",
        color = Ink.Muted,
        fontSize = 13.sp
    )
    Spacer(Modifier.height(18.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Ink.Card)
            .clickable { onPick() }
    ) {
        Canvas(Modifier.matchParentSize().padding(1.dp)) {
            drawRoundRect(
                color = Ink.Pill,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
                )
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.UploadFile,
                contentDescription = null,
                tint = Ink.Text,
                modifier = Modifier
                    .size(38.dp)
                    .offset(y = if (motion) float.dp else 0.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (vm.audioUri == null) "Tap to choose your MP3" else vm.audioName,
                color = Ink.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            MonoLabel(
                if (vm.audioUri == null) "MP3 or any audio" else Fmt.duration(vm.audioDurationMs)
            )
        }
    }

    if (pickError != null) {
        Spacer(Modifier.height(10.dp))
        Text(pickError, color = Ink.Danger, fontSize = 12.sp)
    }

    Spacer(Modifier.height(18.dp))
    RawCard(Modifier.fillMaxWidth()) {
        Text("Script or topic (optional)", color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Claude cannot hear the audio. Paste the words you speak to make the video match.",
            color = Ink.Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ink.Raised)
                .padding(14.dp)
        ) {
            if (vm.script.isEmpty()) {
                Text("Type or paste your script here", color = Ink.Muted, fontSize = 13.sp)
            }
            BasicTextField(
                value = vm.script,
                onValueChange = {
                    vm.script = it
                    vm.saveDraft()
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Ink.Text,
                    fontSize = 13.sp,
                    fontFamily = Mono
                ),
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = VisualTransformation.None
            )
        }
    }
}

@Composable
private fun StepStyle(vm: CreateViewModel) {
    Text("Pick a style", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 26.sp)
    Spacer(Modifier.height(6.dp))
    Text("Each style changes how the video looks.", color = Ink.Muted, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))

    val entries = listOf(
        Triple(Style.MOTION, Icons.Rounded.Movie, "Bold shapes and kinetic text"),
        Triple(Style.DOCUMENTARY, Icons.Rounded.Description, "Cinematic scenes, slow moves"),
        Triple(Style.TYPOGRAPHY, Icons.Rounded.TextFields, "Big words timed to the voice")
    )

    entries.forEach { (style, icon, blurb) ->
        StyleCard(
            style = style,
            icon = icon,
            blurb = blurb,
            selected = vm.style == style,
            onClick = {
                vm.style = style
                vm.saveDraft()
            }
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StyleCard(
    style: Style,
    icon: ImageVector,
    blurb: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val motion = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (selected && motion) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "styleScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(26.dp))
            .background(if (selected) Ink.Raised else Ink.Card)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Ink.Divider,
                shape = RoundedCornerShape(26.dp)
            )
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.Pill),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Ink.Text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(style.label, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(blurb, color = Ink.Muted, fontSize = 12.sp)
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StepPrompt(
    vm: CreateViewModel,
    copied: Boolean,
    onCopy: () -> Unit,
    onOpenClaude: () -> Unit
) {
    Text("Copy your prompt", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 26.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Paste this into Claude. Claude replies with one HTML code.",
        color = Ink.Muted,
        fontSize = 13.sp
    )
    Spacer(Modifier.height(16.dp))

    val prompt = remember(vm.audioName, vm.audioDurationMs, vm.style, vm.script) { vm.prompt() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Ink.Card)
            .padding(16.dp)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = prompt,
                color = Ink.Text,
                fontFamily = Mono,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
            text = if (copied) "Copied!" else "Copy prompt",
            icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            onClick = onCopy,
            modifier = Modifier.weight(1f)
        )
        PillButton(
            text = "Open Claude",
            icon = Icons.Rounded.OpenInNew,
            filled = false,
            onClick = onOpenClaude,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Claude cannot hear your MP3, so the script box on step 1 makes it much more accurate.",
        color = Ink.Muted,
        fontSize = 11.sp
    )
}

@Composable
private fun StepCode(
    vm: CreateViewModel,
    error: String?,
    onError: (String?) -> Unit,
    onOpenEditor: () -> Unit,
    onPaste: () -> Unit
) {
    Text("Have a code? Click me", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 26.sp)
    Spacer(Modifier.height(6.dp))
    Text(
        "Paste the HTML code Claude gave you. It stays on your phone.",
        color = Ink.Muted,
        fontSize = 13.sp
    )
    Spacer(Modifier.height(16.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Ink.Card)
            .border(1.dp, if (error != null) Ink.Danger else Ink.Divider, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        if (vm.code.isEmpty()) {
            Text("Paste here", color = Ink.Muted, fontSize = 13.sp, fontFamily = Mono)
        }
        BasicTextField(
            value = vm.code,
            onValueChange = {
                vm.code = it
                onError(null)
                vm.saveDraft()
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Ink.Text,
                fontSize = 11.sp,
                fontFamily = Mono
            ),
            modifier = Modifier.fillMaxSize()
        )
    }

    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(error, color = Ink.Danger, fontSize = 12.sp)
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
            text = "Paste",
            filled = false,
            icon = Icons.Rounded.ContentCopy,
            onClick = onPaste,
            modifier = Modifier.weight(1f)
        )
        PillButton(
            text = "Edit video",
            filled = false,
            icon = Icons.Rounded.MusicNote,
            onClick = {
                val check = CodeValidator.validate(vm.code)
                if (check is CodeValidator.Result.Ok) {
                    vm.code = CodeValidator.normalize(vm.code)
                    vm.saveDraft()
                    onOpenEditor()
                } else if (check is CodeValidator.Result.Bad) {
                    onError(check.message)
                }
            },
            enabled = CodeValidator.validate(vm.code) is CodeValidator.Result.Ok,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(16.dp))
    RawCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fast mode", color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Renders at 720p. About twice as quick.", color = Ink.Muted, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 30.dp)
                    .clip(CircleShape)
                    .background(if (vm.fastMode) Color.White else Ink.Pill)
                    .clickable {
                        vm.fastMode = !vm.fastMode
                        vm.saveDraft()
                    },
                contentAlignment = if (vm.fastMode) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (vm.fastMode) Ink.Background else Ink.Muted)
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Rendering happens on this phone. Keep the app open until it finishes.",
        color = Ink.Muted,
        fontSize = 11.sp
    )
}
