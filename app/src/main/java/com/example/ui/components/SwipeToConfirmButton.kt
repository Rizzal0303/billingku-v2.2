package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SwipeToConfirmButton(
    modifier: Modifier = Modifier,
    text: String = "Geser untuk Bayar & Cetak",
    confirmedText: String = "Pembayaran & Struk Berhasil!",
    isProcessing: Boolean = false,
    isConfirmed: Boolean = false,
    enabled: Boolean = true,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 52.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    val maxDragPx = remember(trackWidthPx, thumbSizePx) {
        maxOf(0f, trackWidthPx - thumbSizePx)
    }

    val dragOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Reset offset when isConfirmed becomes false
    LaunchedEffect(isConfirmed) {
        if (!isConfirmed) {
            dragOffset.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
        } else if (maxDragPx > 0f) {
            dragOffset.snapTo(maxDragPx)
        }
    }

    // Shimmer / arrow translation animation
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val chevronOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevron"
    )

    val progress = if (maxDragPx > 0f) (dragOffset.value / maxDragPx).coerceIn(0f, 1f) else 0f

    val trackColor by animateColorAsState(
        targetValue = when {
            isConfirmed -> SuccessGreen
            progress > 0.6f -> Color(0xFF0284C7)
            else -> Slate100
        },
        label = "trackColor"
    )

    fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            }
        } catch (ignored: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(trackColor)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .testTag("swipe_to_confirm_button"),
        contentAlignment = Alignment.CenterStart
    ) {
        // Gradient fill trail following drag thumb
        if (progress > 0f && !isConfirmed) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (dragOffset.value + thumbSizePx).toDp() })
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(BluePrimary, CyanAccent)
                        )
                    )
            )
        }

        // Center Hint Text
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isConfirmed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = confirmedText,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Memproses & Mencetak...",
                    color = if (progress > 0.4f) Color.White else Slate700,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = text,
                    color = if (progress > 0.35f) Color.White.copy(alpha = 0.95f) else Slate600,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowRight,
                    contentDescription = null,
                    tint = if (progress > 0.35f) Color.White else Slate400,
                    modifier = Modifier
                        .size(18.dp)
                        .offset(x = chevronOffset.dp)
                )
            }
        }

        // Draggable Thumb Button
        if (!isConfirmed) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                    .padding(6.dp)
                    .size(thumbSizeDp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                if (progress > 0.7f) SuccessGreen else BluePrimary,
                                if (progress > 0.7f) Color(0xFF059669) else BluePrimaryDark
                            )
                        )
                    )
                    .pointerInput(enabled, isProcessing, isConfirmed, maxDragPx) {
                        if (!enabled || isProcessing || isConfirmed || maxDragPx <= 0f) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffset.value >= maxDragPx * 0.75f) {
                                    // Snapped to confirm!
                                    triggerVibration()
                                    coroutineScope.launch {
                                        dragOffset.animateTo(maxDragPx, spring(stiffness = Spring.StiffnessHigh))
                                        onConfirmed()
                                    }
                                } else {
                                    // Spring back to 0
                                    coroutineScope.launch {
                                        dragOffset.animateTo(0f, spring(dampingRatio = 0.75f))
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffset.animateTo(0f, spring())
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val newOffset = (dragOffset.value + dragAmount).coerceIn(0f, maxDragPx)
                                coroutineScope.launch {
                                    dragOffset.snapTo(newOffset)
                                    if (newOffset >= maxDragPx * 0.85f && dragOffset.value < maxDragPx * 0.85f) {
                                        triggerVibration()
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = if (progress >= 0.85f) Icons.Default.Check else Icons.Default.Print,
                        contentDescription = "Geser tombol",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
