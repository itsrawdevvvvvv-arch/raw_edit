package com.rawedit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rawedit.app.data.Prefs
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
import com.rawedit.app.ui.components.rememberTopInset
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
    val topInset = rememberTopInset()
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var showServer by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(prefs.serverUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset)
            .navigationBarsPadding()
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
            SettingRow(
                "Render server",
                if (com.rawedit.app.net.ServerConfig.isPlaceholder(prefs.serverUrl)) {
                    "Off - this phone"
                } else {
                    prettyHost(prefs.serverUrl)
                }
            ) { showServer = true }
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
        Spacer(Modifier.height(56.dp))
    }

    if (showServer) {
        AlertDialog(
            onDismissRequest = { showServer = false },
            title = { Text("Render server", color = Ink.Text) },
            text = {
                Column {
                    Text(
                        "Leave this empty to draw the frames on this phone. " +
                            "If you run raw_edit_server somewhere, paste its address, " +
                            "for example http://203.0.113.10:7860",
                        color = Ink.Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        singleLine = true,
                        label = { Text("Address") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.serverUrl = com.rawedit.app.net.ServerConfig.normalize(serverUrl)
                    serverUrl = prefs.serverUrl
                    showServer = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showServer = false }) { Text("Cancel") }
            }
        )
    }
}

private fun prettyHost(url: String): String {
    return url.replace("http://", "").replace("https://", "").let {
        if (it.length > 22) it.take(22) + "..." else it
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
