package com.rawedit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.BuildConfig
import com.rawedit.app.ui.components.LogoMark
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.RawCard
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono

@Composable
fun AboutScreen(
    name: String,
    cacheMessage: String?,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Ink.Raised)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Ink.Text,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("About app", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(46.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("raw_edit", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                MonoLabel("VERSION " + BuildConfig.VERSION_NAME)
            }
        }

        Spacer(Modifier.height(20.dp))

        RawCard(Modifier.fillMaxWidth()) {
            Text("How it works", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            HowLine("1", "Add your MP3 voiceover and pick a style.")
            HowLine("2", "Copy the prompt the app writes and paste it into Claude.")
            HowLine("3", "Paste Claude's HTML code back into the app.")
            HowLine("4", "Tap Create video. The phone draws every frame and makes the MP4.")
            Spacer(Modifier.height(8.dp))
            Text(
                "No account, no server, no ads. Everything stays on this phone.",
                color = Ink.Muted,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        RawCard(Modifier.fillMaxWidth()) {
            Text("Settings", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            SettingRow("Name", name) { onProfile() }
            SettingRow("Profile photo", "Change") { onProfile() }
            SettingRow("Clear cache", "Free space") { onClearCache() }
            if (cacheMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(cacheMessage, color = Ink.Muted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Rendering runs on the phone with the built-in encoder. Long voiceovers take longer.",
            color = Ink.Muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun HowLine(number: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            text = number,
            color = Ink.Muted,
            fontFamily = Mono,
            fontSize = 12.sp,
            modifier = Modifier.width(22.dp)
        )
        Text(text, color = Ink.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Ink.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ink.Muted, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Ink.Muted,
            modifier = Modifier.size(18.dp)
        )
    }
}
