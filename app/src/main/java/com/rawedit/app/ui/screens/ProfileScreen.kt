package com.rawedit.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rawedit.app.data.Prefs
import com.rawedit.app.ui.components.MonoLabel
import com.rawedit.app.ui.components.PillButton
import com.rawedit.app.ui.components.RawCard
import com.rawedit.app.ui.components.rememberTopInset
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import java.io.File

@Composable
fun ProfileScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onSaved: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(prefs.name) }
    var photoPath by remember { mutableStateOf(prefs.photoPath) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = prefs.saveProfilePhoto(uri)
        if (saved != null) {
            photoPath = saved
            error = null
        } else {
            error = "That photo could not be read. Try another one."
        }
    }

    val topInset = rememberTopInset()
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
            Text("Edit profile", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        Spacer(Modifier.height(24.dp))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(Ink.Raised)
                    .border(2.dp, Ink.Pill, CircleShape)
                    .clickable { picker.launch(arrayOf("image/*")) },
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
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Tap to change photo",
                color = Ink.Muted,
                fontSize = 12.sp
            )
            if (photoPath != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Remove photo",
                    color = Ink.Danger,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        prefs.removeProfilePhoto()
                        photoPath = null
                    }
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        RawCard(Modifier.fillMaxWidth()) {
            Text("Your name", color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("Shown on the home screen.", color = Ink.Muted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.Raised)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                if (name.isEmpty()) {
                    Text("Your name", color = Ink.Muted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 28) name = it },
                    textStyle = TextStyle(color = Ink.Text, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = Ink.Danger, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))
        PillButton(
            text = "Save",
            enabled = name.trim().isNotEmpty(),
            onClick = {
                prefs.name = name.trim()
                onSaved(prefs.name, photoPath)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        MonoLabel("STORED ONLY ON THIS PHONE")

        Spacer(Modifier.height(40.dp))
    }
}
