package com.rawedit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rawedit.app.R

/** Brand tokens. Dark theme only, no other colours. */
object Ink {
    val Background = Color(0xFF0F0F0F)
    val Hero = Color(0xFF1C1C1C)
    val Card = Color(0xFF171717)
    val Raised = Color(0xFF2A2A2A)
    val Pill = Color(0xFF3A3A3A)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFF9B9B9B)
    val Divider = Color(0xFF333333)
    val Danger = Color(0xFFFF7A7A)
}

val Montserrat = FontFamily(Font(R.font.montserrat_bold, FontWeight.Bold))
val OpenSans = FontFamily(Font(R.font.open_sans_regular, FontWeight.Normal))
val Mono = FontFamily.Monospace

private val RawTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.8).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = OpenSans,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = OpenSans,
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

private val RawColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Ink.Background,
    secondary = Ink.Pill,
    onSecondary = Ink.Text,
    background = Ink.Background,
    onBackground = Ink.Text,
    surface = Ink.Card,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Raised,
    onSurfaceVariant = Ink.Muted,
    outline = Ink.Divider,
    error = Ink.Danger,
    onError = Ink.Background
)

@Composable
fun RawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RawColors,
        typography = RawTypography,
        content = content
    )
}
