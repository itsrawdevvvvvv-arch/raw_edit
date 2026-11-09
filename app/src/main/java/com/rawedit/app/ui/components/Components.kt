package com.rawedit.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.ui.theme.Mono
import com.rawedit.app.util.rememberMotionEnabled

@Composable
fun RawCard(
    modifier: Modifier = Modifier,
    color: Color = Ink.Card,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(color)
            .padding(padding),
        content = content
    )
}

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = Ink.Muted) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = Mono,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    icon: ImageVector? = null
) {
    val motion = rememberMotionEnabled()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "pillScale"
    )
    val bg = when {
        !enabled -> Ink.Pill.copy(alpha = 0.45f)
        filled -> Color.White
        else -> Ink.Pill
    }
    val fg = when {
        !enabled -> Ink.Muted
        filled -> Ink.Background
        else -> Ink.Text
    }
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun StepProgress(step: Int, total: Int = 4, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..total) {
            val active = i <= step
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.White else Ink.Pill)
            )
        }
    }
}

@Composable
fun PillNav(current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple("Home", Icons.Rounded.Home, 0),
        Triple("Create", Icons.Rounded.Add, 1),
        Triple("Videos", Icons.Rounded.VideoLibrary, 2)
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Ink.Raised)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (label, icon, index) ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 100.dp else 56.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
                label = "navWidth"
            )
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (active) Ink.Pill else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) Ink.Text else Ink.Muted,
                        modifier = Modifier.size(20.dp)
                    )
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = Ink.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerBar(progress: Float, modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shift"
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(Ink.Pill)
    ) {
        val w = size.width
        val p = progress.coerceIn(0f, 1f)
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, 0f),
            size = Size(w * p, size.height)
        )
        if (motion && p > 0.01f && p < 0.99f) {
            val bandW = w * 0.18f
            val x = (w + bandW) * shift - bandW
            drawRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(x.coerceAtMost(w * p - 2f), 0f),
                size = Size(bandW.coerceAtMost(w * p).coerceAtLeast(0f), size.height)
            )
        }
    }
}

@Composable
fun LogoMark(size: Dp = 34.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = this.size.minDimension * 0.09f
        val inset = stroke / 2f
        val radius = this.size.minDimension * 0.24f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(inset, inset),
            size = Size(w - stroke, h - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            style = Stroke(width = stroke)
        )
        val path = Path().apply {
            moveTo(w * 0.40f, h * 0.31f)
            lineTo(w * 0.72f, h * 0.50f)
            lineTo(w * 0.40f, h * 0.69f)
            close()
        }
        drawPath(path = path, color = Color.White)
    }
}

@Composable
fun DotGrid(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 26.dp.toPx()
        val dot = 1.2.dp.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawCircle(Color.White.copy(alpha = 0.05f), radius = dot, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(96.dp)) {
            drawRoundRect(
                color = Ink.Pill,
                size = Size(size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
                style = Stroke(width = 2f)
            )
            drawCircle(Ink.Pill, radius = size.minDimension * 0.10f, center = Offset(size.width * 0.5f, size.height * 0.5f))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            color = Ink.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = Ink.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
