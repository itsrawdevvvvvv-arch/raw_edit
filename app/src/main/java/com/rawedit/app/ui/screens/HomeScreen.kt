package com.rawedit.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rawedit.app.BuildConfig
import com.rawedit.app.ui.components.DotGrid
import com.rawedit.app.ui.components.LogoMark
import com.rawedit.app.ui.components.NavBottomSpacer
import com.rawedit.app.ui.components.rememberTopInset
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.components.RawCard
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import com.rawedit.app.util.rememberMotionEnabled
import java.io.File

@Composable
fun HomeScreen(
    name: String,
    photoPath: String?,
    videoCount: Int,
    onCreate: () -> Unit,
    onLibrary: () -> Unit,
    onAbout: () -> Unit,
    onProfile: () -> Unit
) {
    val topInset = rememberTopInset()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        // Hero
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(Ink.Hero)
        ) {
            DotGrid(Modifier.matchParentSize())
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(30.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "raw_edit",
                        color = Ink.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.weight(1f))
                    MonoLabel("v" + BuildConfig.VERSION_NAME)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Hello again,\n" + name + "!",
                    color = Ink.Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 34.sp
                )
                Spacer(Modifier.height(18.dp))

                // Studio card
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Ink.Raised)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Ink.Pill),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoPath != null && File(photoPath).exists()) {
                            AsyncImage(
                                model = File(photoPath),
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Ink.Muted,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = name,
                            color = Ink.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "raw_edit studio",
                            color = Ink.Muted,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Ink.Pill)
                            .clickable { onProfile() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Edit", color = Ink.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Stats
        RawCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink.Raised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Ink.Text,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (videoCount == 0) "No videos yet" else "$videoCount video" + (if (videoCount == 1) "" else "s"),
                        color = Ink.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Total videos made",
                        color = Ink.Muted,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Ink.Pill)
                        .clickable { onLibrary() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("Open", color = Ink.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Steps
        RawCard(Modifier.fillMaxWidth()) {
            Text(
                text = "Make a video in 3 easy steps",
                color = Ink.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            StepLine("1", "Add voice", "Pick your MP3 voiceover")
            StepLine("2", "Pick a style", "Motion, documentary or type")
            StepLine("3", "Get video", "Paste the code, tap create")
            Spacer(Modifier.height(18.dp))
            PillButton(
                text = "Create a video",
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))

        // About
        RawCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAbout() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "raw_edit",
                        color = Ink.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Version " + BuildConfig.VERSION_NAME,
                        color = Ink.Muted,
                        fontFamily = Mono,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("About app", color = Ink.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Ink.Muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        NavBottomSpacer()
    }
}

@Composable
private fun StepLine(number: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Ink.Raised),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Ink.Text,
                fontFamily = Mono,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Ink.Muted, fontSize = 12.sp)
        }
    }
}

private val unusedBrush = Brush.linearGradient(listOf(Color.Black, Color.White))
