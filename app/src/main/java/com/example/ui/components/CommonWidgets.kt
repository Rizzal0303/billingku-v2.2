package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.printer.EscPosHelper
import com.example.ui.theme.*
import com.example.util.PdfInvoiceGenerator
import com.example.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = BluePrimary,
    backgroundColor: Color = Color.White,
    tag: String
) {
    Card(
        modifier = modifier.testTag(tag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Slate900,
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(iconTint)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, dotColor, label) = when (status.uppercase()) {
        "ACTIVE", "LUNAS", "PAID" -> Quadruple(SuccessGreenLight, SuccessGreenDark, SuccessGreen, "Lunas / Aktif")
        "ISOLATED", "ISOLIR" -> Quadruple(DangerRedLight, DangerRedDark, DangerRed, "Terisolir")
        "UNPAID", "BELUM LUNAS" -> Quadruple(WarningAmberLight, WarningAmberDark, WarningAmber, "Belum Lunas")
        "INACTIVE" -> Quadruple(Slate100, Slate700, Slate400, "Nonaktif")
        else -> Quadruple(Slate100, Slate700, Slate400, status)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(0.8.dp, textColor.copy(alpha = 0.25f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            // Dual-ring glowing dot
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Dialog Bukti Pembayaran & Struk Modern & Elegan
 * Digunakan setelah pembayaran kasir berhasil maupun saat melihat/mencetak struk di riwayat.
 */
@Composable
fun ReceiptPreviewDialog(
    receiptData: EscPosHelper.ReceiptData,
    isNewlyPaid: Boolean = false,
    customerPhone: String? = null,
    onDismiss: () -> Unit,
    onPrintAgain: () -> Unit,
    onDone: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val receiptText = EscPosHelper.generatePlainTextReceipt(receiptData)
    var selectedTab by remember { mutableStateOf(0) } // 0: Struk Digital, 1: Format Thermal
    val coroutineScope = rememberCoroutineScope()
    var isPrintingLocally by remember { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    val dateFormatted = remember(receiptData.date) { sdf.format(Date(receiptData.date)) }

    // Avatar initials
    val initials = remember(receiptData.customerName) {
        receiptData.customerName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "P" }
    }

    // Helper text for WhatsApp
    fun buildWaReceiptText(): String {
        val sb = StringBuilder()
        sb.appendLine("*BUKTI PEMBAYARAN INTERNET WIFI (LUNAS)*")
        sb.appendLine("=============================")
        sb.appendLine("🏪 *${receiptData.businessName}*")
        if (receiptData.businessAddress.isNotBlank()) sb.appendLine("📍 ${receiptData.businessAddress}")
        if (receiptData.businessPhone.isNotBlank()) sb.appendLine("📞 Kontak/WA: ${receiptData.businessPhone}")
        sb.appendLine("-----------------------------")
        sb.appendLine("No. Faktur  : ${receiptData.invoiceNo}")
        sb.appendLine("Waktu Bayar : $dateFormatted WIB")
        sb.appendLine("Pelanggan   : *${receiptData.customerName}* (${receiptData.customerCode})")
        sb.appendLine("Periode     : ${receiptData.period}")
        sb.appendLine("Paket WiFi  : ${receiptData.packageName}")
        if (receiptData.odpLocation.isNotBlank()) sb.appendLine("ODP/Port    : ${receiptData.odpLocation}")
        sb.appendLine("-----------------------------")
        sb.appendLine("Biaya Paket : ${EscPosHelper.formatRupiah(receiptData.monthlyFee)}")
        if (receiptData.adminFee > 0) sb.appendLine("Biaya Admin : ${EscPosHelper.formatRupiah(receiptData.adminFee)}")
        if (receiptData.discount > 0) sb.appendLine("Potongan/Diskon: -${EscPosHelper.formatRupiah(receiptData.discount)}")
        sb.appendLine("-----------------------------")
        sb.appendLine("*TOTAL BAYAR: ${EscPosHelper.formatRupiah(receiptData.totalAmount)}*")
        sb.appendLine("Metode      : ${receiptData.paymentMethod}")
        sb.appendLine("Status      : *LUNAS [VERIFIED]*")
        sb.appendLine("=============================")
        sb.appendLine(receiptData.footerMessage)
        return sb.toString().trim()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 440.dp)
                .wrapContentHeight()
                .padding(vertical = 12.dp)
                .testTag("receipt_preview_dialog"),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(16.dp),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // =========================================================================
                // 1. HEADER (Celebration if newly paid, or Elegant Receipt Title)
                // =========================================================================
                if (isNewlyPaid) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Glowing double ring
                            Surface(
                                shape = CircleShape,
                                color = SuccessGreenLight,
                                modifier = Modifier.size(54.dp),
                                border = BorderStroke(2.dp, SuccessGreen.copy(alpha = 0.3f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreenDark,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Pembayaran Berhasil!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Slate900,
                                fontSize = 19.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Struk otomatis dikirim ke printer Bluetooth",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SuccessGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Printer Thermal 58mm: Siap / Terkirim",
                                        color = SuccessGreenDark,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Slate400)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BluePrimaryLight,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = BluePrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Bukti Pembayaran",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "Faktur Sah Layanan Internet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = Slate500)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // =========================================================================
                // 2. SEGMENTED TAB SWITCHER (Struk Digital vs Format Thermal 58mm)
                // =========================================================================
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = 0 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedTab == 0) Color.White else Color.Transparent,
                            shadowElevation = if (selectedTab == 0) 2.dp else 0.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = if (selectedTab == 0) BluePrimary else Slate500,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Struk Digital",
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (selectedTab == 0) BluePrimary else Slate600
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = 1 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedTab == 1) Color.White else Color.Transparent,
                            shadowElevation = if (selectedTab == 1) 2.dp else 0.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = null,
                                        tint = if (selectedTab == 1) BluePrimary else Slate500,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Kertas Thermal",
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (selectedTab == 1) BluePrimary else Slate600
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // =========================================================================
                // 3. MAIN CONTENT: DIGITAL TICKET OR THERMAL PAPER VIEW
                // =========================================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally(
                                    initialOffsetX = { it / 3 },
                                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(280))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { -it / 3 },
                                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                                    ) + fadeOut(animationSpec = tween(250))
                                )
                            } else {
                                (slideInHorizontally(
                                    initialOffsetX = { -it / 3 },
                                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(280))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { it / 3 },
                                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                                    ) + fadeOut(animationSpec = tween(250))
                                )
                            }
                        },
                        label = "ReceiptTabAnim"
                    ) { currentTab ->
                        if (currentTab == 0) {
                            // --- ELEGANT DIGITAL TICKET RECEIPT ---
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate50),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Business Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BluePrimary,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Wifi,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = receiptData.businessName,
                                            fontWeight = FontWeight.Black,
                                            color = Slate900,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        if (receiptData.businessAddress.isNotBlank()) {
                                            Text(
                                                text = receiptData.businessAddress,
                                                color = Slate500,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                        if (receiptData.businessPhone.isNotBlank()) {
                                            Text(
                                                text = "WA: ${receiptData.businessPhone}",
                                                color = Slate500,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Invoice Pill & Timestamp
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(receiptData.invoiceNo))
                                            Toast.makeText(context, "No. Invoice disalin!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = receiptData.invoiceNo,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate800
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Salin",
                                                tint = Slate400,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = dateFormatted,
                                        fontSize = 11.sp,
                                        color = Slate500,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Ticket Perforation Dashed Line
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                ) {
                                    drawLine(
                                        color = Slate300,
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, 0f),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                                        strokeWidth = 2f
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Customer & Package Box
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Slate200)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(listOf(BluePrimary, ElectricIndigo))
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = initials,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = receiptData.customerName,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 14.sp,
                                                        color = Slate900
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Slate100
                                                    ) {
                                                        Text(
                                                            text = receiptData.customerCode,
                                                            color = Slate700,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Paket: ${receiptData.packageName} • Periode: ${receiptData.period}",
                                                    fontSize = 11.sp,
                                                    color = Slate500
                                                )
                                                if (receiptData.odpLocation.isNotBlank()) {
                                                    Text(
                                                        text = "ODP/Port: ${receiptData.odpLocation}",
                                                        fontSize = 10.sp,
                                                        color = Slate400
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Ledger Breakdown
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ReceiptRowItem(
                                        label = "Biaya Paket Bulanan",
                                        value = EscPosHelper.formatRupiah(receiptData.monthlyFee)
                                    )
                                    if (receiptData.adminFee > 0) {
                                        ReceiptRowItem(
                                            label = "Biaya Admin Kasir",
                                            value = EscPosHelper.formatRupiah(receiptData.adminFee)
                                        )
                                    }
                                    if (receiptData.discount > 0) {
                                        ReceiptRowItem(
                                            label = "Potongan / Diskon",
                                            value = "-${EscPosHelper.formatRupiah(receiptData.discount)}",
                                            valueColor = SuccessGreenDark
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                ) {
                                    drawLine(
                                        color = Slate300,
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, 0f),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                                        strokeWidth = 2f
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Total Highlight
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "TOTAL BAYAR",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate500,
                                            letterSpacing = 1.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SuccessGreenLight
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = SuccessGreenDark,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "LUNAS",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = SuccessGreenDark
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = EscPosHelper.formatRupiah(receiptData.totalAmount),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BluePrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Method & Cashier Badges
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Slate200)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            val icon = when (receiptData.paymentMethod.uppercase()) {
                                                "TRANSFER" -> Icons.Default.AccountBalance
                                                "QRIS" -> Icons.Default.QrCodeScanner
                                                else -> Icons.Default.Payments
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = BluePrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = receiptData.paymentMethod,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate800
                                            )
                                        }
                                    }


                                }

                                if (receiptData.footerMessage.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = receiptData.footerMessage,
                                        fontSize = 10.sp,
                                        color = Slate400,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        // --- AUTHENTIC THERMAL PAPER 58MM PREVIEW ---
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFBFBFB))
                                .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = receiptText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }

                Spacer(modifier = Modifier.height(18.dp))

                // =========================================================================
                // 4. ACTION BUTTONS: KIRIM WA, CETAK ULANG, BAGIKAN PDF, SELESAI
                // =========================================================================
                // Indikator Loading Cetak Struk (Anti Double-Click)
                AnimatedVisibility(visible = isPrintingLocally) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BluePrimaryLight,
                        border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = BluePrimary,
                                strokeWidth = 2.5.dp
                            )
                            Column {
                                Text(
                                    text = "Sedang Mencetak Struk ke Printer Thermal...",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimaryDark
                                )
                                Text(
                                    text = "Mohon tunggu sejenak, tombol terkunci untuk mencegah struk dobel.",
                                    fontSize = 10.5.sp,
                                    color = Slate600
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tombol Kirim WhatsApp
                    Button(
                        onClick = {
                            val waText = buildWaReceiptText()
                            WhatsAppHelper.sendWhatsAppMessage(context, customerPhone, waText)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Icon(imageVector = Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim WA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Tombol Cetak Struk (Anti Double-Click & Loading State)
                    Button(
                        onClick = {
                            if (!isPrintingLocally) {
                                isPrintingLocally = true
                                Toast.makeText(context, "🖨️ Mengirim struk ke printer thermal...", Toast.LENGTH_SHORT).show()
                                onPrintAgain()
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(2500)
                                    isPrintingLocally = false
                                }
                            }
                        },
                        enabled = !isPrintingLocally,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BluePrimary,
                            disabledContainerColor = BluePrimary.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isPrintingLocally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mencetak...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cetak Struk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Baris PDF: Bagikan PDF Faktur Resmi (Opsi B) & Lihat PDF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tombol Bagikan Faktur PDF Resmi
                    Button(
                        onClick = {
                            PdfInvoiceGenerator.shareInvoicePdf(context, receiptData)
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)) // PDF Crimson Red
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bagikan PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Tombol Buka / Pratinjau PDF
                    OutlinedButton(
                        onClick = {
                            PdfInvoiceGenerator.openInvoicePdf(context, receiptData)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate700)
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Buka PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tombol Bagikan Teks via System Chooser
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, buildWaReceiptText())
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Bukti Pembayaran"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate600)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Bagikan Teks", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Tombol Selesai / Transaksi Baru
                    FilledTonalButton(
                        onClick = {
                            onDone?.invoke() ?: onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Slate100,
                            contentColor = Slate800
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(if (isNewlyPaid) "Transaksi Baru" else "Selesai", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRowItem(
    label: String,
    value: String,
    valueColor: Color = Slate800
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = Slate500)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
