package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.MysqlSyncManager
import com.example.data.sync.NetworkMonitor
import com.example.ui.theme.*

@Composable
fun SyncStatusChip(
    networkMonitor: NetworkMonitor,
    mysqlSyncManager: MysqlSyncManager,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val isOnline by networkMonitor.isOnline.collectAsState()
    val isSyncing by mysqlSyncManager.isSyncing.collectAsState()
    val pendingCount by mysqlSyncManager.pendingQueueCount.collectAsState(initial = 0)

    // Color and icon determination
    val (bgColor, textColor, borderColor, icon) = when {
        isSyncing -> {
            Quad(
                Color(0xFFEFF6FF),
                BluePrimary,
                Color(0xFFBFDBFE),
                Icons.Default.Sync
            )
        }
        !isOnline -> {
            if (pendingCount > 0) {
                Quad(
                    Color(0xFFFFFBEB),
                    Color(0xFFB45309),
                    Color(0xFFFDE68A),
                    Icons.Default.CloudOff
                )
            } else {
                Quad(
                    Slate100,
                    Slate600,
                    Slate300,
                    Icons.Default.CloudOff
                )
            }
        }
        mysqlSyncManager.getApiUrl().isBlank() -> {
            Quad(
                Slate100,
                Slate600,
                Slate300,
                Icons.Default.CloudQueue
            )
        }
        pendingCount > 0 -> {
            Quad(
                Color(0xFFFEF3C7),
                Color(0xFFD97706),
                Color(0xFFFCD34D),
                Icons.Default.CloudUpload
            )
        }
        else -> {
            Quad(
                Color(0xFFF0FDF4),
                SuccessGreen,
                Color(0xFFBBF7D0),
                Icons.Default.CloudDone
            )
        }
    }

    // Rotation animation when syncing
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .testTag("chip_sync_status")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier
                    .size(13.dp)
                    .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = when {
                    isSyncing -> "Menyinkronkan..."
                    !isOnline -> if (pendingCount > 0) "Offline ($pendingCount antre)" else "Offline"
                    mysqlSyncManager.getApiUrl().isBlank() -> "MySQL Belum Diatur"
                    pendingCount > 0 -> "$pendingCount Antrean"
                    else -> "Online • Seragam"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

