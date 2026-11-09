package com.rawedit.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.rememberTopInset
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.util.Fmt
import com.rawedit.app.util.MediaStoreSaver
import java.io.File

@Composable
fun ResultScreen(
    videoPath: String,
    title: String,
    style: String,
    durationMs: Long,
    onBack: () -> Unit,
    onMakeAnother: () -> Unit
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var savedUri by remember { mutableStateOf<String?>(null) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val f = File(videoPath)
            if (f.exists()) {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(f)))
                prepare()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val topInset = rememberTopInset()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink.Raised)
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Ink.Text,
                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                MonoLabel(style.uppercase() + "  |  " + Fmt.duration(durationMs))
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = "Save to phone",
                icon = Icons.Rounded.SaveAlt,
                onClick = {
                    val name = "raw_edit_" + System.currentTimeMillis() + ".mp4"
                    val uri = MediaStoreSaver.saveVideo(context, File(videoPath), name)
                    message = if (uri != null) "Saved to Movies/raw_edit" else "Could not save. Check storage space."
                    savedUri = uri?.toString()
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = "Share",
                icon = Icons.Rounded.Share,
                filled = false,
                onClick = {
                    try {
                        val uri: Uri = if (savedUri != null) {
                            Uri.parse(savedUri)
                        } else {
                            FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                File(videoPath)
                            )
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share video"))
                    } catch (t: Throwable) {
                        message = "Nothing found to share this video."
                    }
                },
                modifier = Modifier.weight(1f)
            )
            PillButton(
                text = "Make another",
                icon = Icons.Rounded.Refresh,
                filled = false,
                onClick = onMakeAnother,
                modifier = Modifier.weight(1f)
            )
        }

        if (message != null) {
            Spacer(Modifier.height(14.dp))
            Text(message!!, color = Ink.Muted, fontSize = 12.sp)
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "Saved videos live in the Videos tab too.",
            color = Ink.Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(30.dp))
    }
}
