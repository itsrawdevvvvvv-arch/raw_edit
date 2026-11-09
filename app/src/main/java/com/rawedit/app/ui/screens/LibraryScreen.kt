package com.rawedit.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.rawedit.app.data.VideoItem
import com.rawedit.app.ui.components.EmptyState
import com.rawedit.app.ui.components.rememberTopInset
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import com.rawedit.app.util.Fmt
import java.io.File

@Composable
fun LibraryScreen(
    items: List<VideoItem>,
    onCreate: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onDelete: (VideoItem) -> Unit
) {
    val context = LocalContext.current

    val topInset = rememberTopInset()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("My videos", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        MonoLabel(items.size.toString() + " VIDEO" + (if (items.size == 1) "" else "S") + " ON THIS PHONE")
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            EmptyState(
                title = "No videos yet",
                body = "Tap Create and make your first one."
            )
            Spacer(Modifier.height(24.dp))
            PillButton(
                text = "Create a video",
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 130.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Ink.Card)
                            .clickable { onPlay(item) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 108.dp, height = 61.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Ink.Raised),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.thumbPath.isNotEmpty() && File(item.thumbPath).exists()) {
                                AsyncImage(
                                    model = File(item.thumbPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = Ink.Muted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = Ink.Text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = item.style + "  |  " + Fmt.duration(item.durationMs),
                                color = Ink.Muted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = Fmt.date(item.createdAt),
                                color = Ink.Muted,
                                fontFamily = Mono,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Ink.Raised)
                                .clickable {
                                    try {
                                        val uri: Uri = FileProvider.getUriForFile(
                                            context,
                                            context.packageName + ".fileprovider",
                                            File(item.filePath)
                                        )
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Share video"))
                                    } catch (t: Throwable) {
                                        // ignore
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = "Share",
                                tint = Ink.Text,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Ink.Raised)
                                .clickable { onDelete(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                tint = Ink.Danger,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
