package com.example.ui.settings.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.printer.EscPosHelper
import com.example.ui.theme.*

@Composable
fun Modern3DBarChart(
    modifier: Modifier = Modifier,
    points: List<DailyChartPoint>,
    periodType: ReportPeriodType,
    selectedPoint: DailyChartPoint?,
    onSelectPoint: (DailyChartPoint?) -> Unit
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Belum ada data grafik untuk periode ini",
                color = Slate400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    val maxAmount = remember(points) {
        val maxVal = points.maxOfOrNull { it.amount } ?: 0L
        if (maxVal > 0) maxVal else 100_000L
    }

    val totalAmount = remember(points) { points.sumOf { it.amount } }
    val avgAmount = remember(points) {
        if (points.isNotEmpty()) totalAmount / points.size else 0L
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Title & Peak Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BluePrimaryLight,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Grafik Tren Omzet",
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            fontSize = 15.sp
                        )
                        Text(
                            text = when (periodType) {
                                ReportPeriodType.WEEKLY -> "Statistik Harian (7 Hari)"
                                ReportPeriodType.MONTHLY -> "Statistik Harian per Tanggal"
                                ReportPeriodType.YEARLY -> "Performa Akumulasi 12 Bulan"
                                ReportPeriodType.ALL_TIME -> "Akumulasi Keseluruhan"
                            },
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    }
                }

                // Average badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("Rata-rata:", fontSize = 9.sp, color = Slate400, fontWeight = FontWeight.Medium)
                        Text(
                            text = EscPosHelper.formatRupiah(avgAmount),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Tooltip if a bar is selected
            AnimatedVisibility(visible = selectedPoint != null) {
                selectedPoint?.let { pt ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate900,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${pt.label} (${pt.subLabel})",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    if (pt.isPeak && pt.amount > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = WarningAmber
                                        ) {
                                            Text(
                                                text = "TERTINGGI",
                                                color = Slate950,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${pt.transactionCount} Transaksi Selesai",
                                    color = Slate300,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = EscPosHelper.formatRupiah(pt.amount),
                                fontWeight = FontWeight.ExtraBold,
                                color = CyanAccent,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // Canvas Bar Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                // Background reference line (Average)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp)
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val avgY = if (maxAmount > 0) {
                        canvasHeight * (1f - (avgAmount.toFloat() / maxAmount.toFloat()).coerceIn(0.05f, 0.95f))
                    } else {
                        canvasHeight * 0.8f
                    }

                    // Average dotted line
                    drawLine(
                        color = Slate300,
                        start = Offset(0f, avgY),
                        end = Offset(canvasWidth, avgY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // Interactive Bars Row
                Row(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    points.forEach { point ->
                        val isSelected = selectedPoint?.label == point.label
                        val isPeak = point.isPeak && point.amount > 0
                        val barHeightFraction = if (maxAmount > 0) {
                            (point.amount.toFloat() / maxAmount.toFloat()).coerceIn(0.06f, 1.0f)
                        } else 0.06f

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    if (isSelected) {
                                        onSelectPoint(null)
                                    } else {
                                        onSelectPoint(point)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Peak Crown / Star indicator
                            if (isPeak) {
                                Surface(
                                    shape = CircleShape,
                                    color = WarningAmber,
                                    modifier = Modifier
                                        .size(10.dp)
                                        .padding(bottom = 2.dp)
                                ) {}
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // The Bar Pill
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (points.size > 15) 0.8f else 0.55f)
                                    .fillMaxHeight(barHeightFraction * 0.76f)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .background(
                                        brush = if (isSelected) {
                                            Brush.verticalGradient(
                                                colors = listOf(CyanAccent, BluePrimaryDark)
                                            )
                                        } else if (isPeak) {
                                            Brush.verticalGradient(
                                                colors = listOf(CyanAccentDark, BluePrimary)
                                            )
                                        } else if (point.amount > 0) {
                                            Brush.verticalGradient(
                                                colors = listOf(BluePrimary80, BluePrimary)
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(Slate200, Slate300)
                                            )
                                        }
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // X-axis label
                            Text(
                                text = point.label,
                                fontSize = if (points.size > 15) 9.sp else 11.sp,
                                fontWeight = if (isSelected || isPeak) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) BluePrimary else if (isPeak) Slate900 else Slate500,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer note
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "*Ketuk batang untuk melihat rincian",
                    color = Slate400,
                    fontSize = 10.sp
                )
                Text(
                    text = "Garis putus-putus = Rata-rata periode",
                    color = Slate400,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun PaymentMethodDonutChart(
    modifier: Modifier = Modifier,
    cashAmount: Long,
    transferAmount: Long,
    qrisAmount: Long,
    totalAmount: Long
) {
    val nonZeroTotal = if (totalAmount > 0) totalAmount.toFloat() else 1f
    val cashRatio = if (totalAmount > 0) cashAmount.toFloat() / nonZeroTotal else 0f
    val transferRatio = if (totalAmount > 0) transferAmount.toFloat() / nonZeroTotal else 0f
    val qrisRatio = if (totalAmount > 0) qrisAmount.toFloat() / nonZeroTotal else 0f

    val cashPct = (cashRatio * 100).toInt()
    val transferPct = (transferRatio * 100).toInt()
    val qrisPct = (qrisRatio * 100).toInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Distribusi Metode Pembayaran",
                fontWeight = FontWeight.Bold,
                color = Slate900,
                fontSize = 15.sp
            )
            Text(
                text = "Proporsi saluran kas masuk pada periode aktif",
                color = Slate500,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Canvas
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 22f
                        val sizeMinusStroke = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                        if (totalAmount <= 0) {
                            drawArc(
                                color = Slate200,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = sizeMinusStroke,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        } else {
                            var currentAngle = -90f

                            // Tunai Arc
                            if (cashRatio > 0) {
                                val sweep = cashRatio * 360f
                                drawArc(
                                    color = SuccessGreen,
                                    startAngle = currentAngle,
                                    sweepAngle = (sweep - 4f).coerceAtLeast(1f),
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = sizeMinusStroke,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                currentAngle += sweep
                            }

                            // Transfer Arc
                            if (transferRatio > 0) {
                                val sweep = transferRatio * 360f
                                drawArc(
                                    color = BluePrimary,
                                    startAngle = currentAngle,
                                    sweepAngle = (sweep - 4f).coerceAtLeast(1f),
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = sizeMinusStroke,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                currentAngle += sweep
                            }

                            // QRIS Arc
                            if (qrisRatio > 0) {
                                val sweep = qrisRatio * 360f
                                drawArc(
                                    color = Color(0xFF8B5CF6),
                                    startAngle = currentAngle,
                                    sweepAngle = (sweep - 4f).coerceAtLeast(1f),
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = sizeMinusStroke,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Center label
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (totalAmount > 0) "100%" else "0%",
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Total",
                            color = Slate400,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Legend List
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LegendItem(
                        label = "Tunai",
                        amount = cashAmount,
                        percentage = cashPct,
                        color = SuccessGreen
                    )
                    LegendItem(
                        label = "Transfer Bank",
                        amount = transferAmount,
                        percentage = transferPct,
                        color = BluePrimary
                    )
                    LegendItem(
                        label = "QRIS",
                        amount = qrisAmount,
                        percentage = qrisPct,
                        color = Color(0xFF8B5CF6)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    amount: Long,
    percentage: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700
                )
                Text(
                    text = EscPosHelper.formatRupiah(amount),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.1f)
        ) {
            Text(
                text = "$percentage%",
                color = color,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
