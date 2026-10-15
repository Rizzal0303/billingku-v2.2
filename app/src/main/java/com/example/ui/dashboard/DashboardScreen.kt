package com.example.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.StaffEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.sync.MysqlSyncManager
import com.example.data.sync.NetworkMonitor
import com.example.printer.EscPosHelper
import com.example.ui.components.AdminPinPromptDialog
import com.example.ui.components.PullRefreshWrapper
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.components.StatCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.SyncStatusChip
import com.example.ui.theme.*
import com.example.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    currentStaff: StaffEntity? = null,
    onNavigateToCashier: (Long?) -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToPrinterSettings: () -> Unit,
    onNavigateToReports: () -> Unit,
    networkMonitor: NetworkMonitor? = null,
    mysqlSyncManager: MysqlSyncManager? = null,
    onVerifyAdminPin: ((String) -> Boolean)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var previewReceiptData by remember { mutableStateOf<EscPosHelper.ReceiptData?>(null) }
    val isAdmin = currentStaff?.role?.equals("ADMIN", ignoreCase = true) == true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(BluePrimary, Color(0xFF1D4ED8))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Billingku",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Slate900,
                                fontSize = 17.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "WiFi RT/RW Net • Online",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (networkMonitor != null && mysqlSyncManager != null) {
                        SyncStatusChip(
                            networkMonitor = networkMonitor,
                            mysqlSyncManager = mysqlSyncManager,
                            onNavigateToSettings = onNavigateToPrinterSettings,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    // Role Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAdmin) ElectricIndigoLight else SuccessGreenLight,
                        border = BorderStroke(1.dp, if (isAdmin) ElectricIndigo.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("dashboard_user_profile_chip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isAdmin) ElectricIndigo else SuccessGreenDark,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${if (isAdmin) "Admin" else "Kasir"}: ${currentStaff?.name?.split(" ")?.firstOrNull() ?: "Petugas"}",
                                color = if (isAdmin) ElectricIndigo else SuccessGreenDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate50)
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        PullRefreshWrapper(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                viewModel.refreshFromMysql { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // 1. Hero Revenue Banner (Fintech Dark Mesh Look)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_revenue_banner"),
                        shape = RoundedCornerShape(26.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF0A1226),
                                            Color(0xFF0F2B5C),
                                            Color(0xFF1E3A8A),
                                            Color(0xFF2563EB)
                                        )
                                    )
                                )
                                .padding(24.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "BULAN INI",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = uiState.currentPeriod,
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.18f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            tint = Color(0xFF6EE7B7),
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (isAdmin) {
                                    Text(
                                        text = EscPosHelper.formatRupiah(uiState.totalRevenueMonth),
                                        color = Color.White,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp
                                    )
                                } else {
                                    Text(
                                        text = "Rp •••••••••",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Billing Progress
                                val totalCustomers = uiState.activeCustomerCount
                                val paidCustomers = uiState.paidCustomerCount
                                val progress = if (totalCustomers > 0) paidCustomers.toFloat() / totalCustomers.toFloat() else 0f

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = SuccessGreen,
                                    trackColor = Color.White.copy(alpha = 0.22f),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$paidCustomers Lunas dari $totalCustomers Pelanggan",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SuccessGreen.copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = "${(progress * 100).toInt()}% Selesai",
                                            color = Color(0xFF6EE7B7),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 2-Column Analytics Cards
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                title = "Pelanggan Aktif",
                                value = "${uiState.activeCustomerCount}",
                                subtitle = "Total ${uiState.totalCustomerCount} terdaftar",
                                icon = Icons.Default.People,
                                iconTint = BluePrimary,
                                tag = "stat_active_customers"
                            )
                            StatCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigateToCashier(null) },
                                title = "Belum Lunas",
                                value = "${uiState.unpaidCustomerCount} Org",
                                subtitle = "${uiState.totalUnpaidBillsCount} nota di kasir",
                                icon = Icons.Default.PendingActions,
                                iconTint = DangerRed,
                                tag = "stat_unpaid_customers"
                            )
                        }

                        // Banner Total Piutang Tunggakan (Sinkron dengan Kasir)
                        if (uiState.totalArrearsAmount > 0) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = DangerRedLight.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToCashier(null) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = DangerRed
                                        ) {
                                            Box(
                                                modifier = Modifier.size(36.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PriorityHigh,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Total Piutang (${uiState.totalUnpaidBillsCount} Nota Kasir)",
                                                color = DangerRedDark,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = EscPosHelper.formatRupiah(uiState.totalArrearsAmount),
                                                color = DangerRedDark,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.White
                                    ) {
                                        Text(
                                            text = "Buka Kasir »",
                                            color = DangerRedDark,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Jatuh Tempo / Belum Bayar Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tagihan Jatuh Tempo & Tunggakan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = "${uiState.dueCustomers.size} pelanggan (${uiState.totalUnpaidBillsCount} nota) belum lunas",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = onNavigateToCustomers) {
                            Text("Lihat Semua", color = BluePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                if (uiState.dueCustomers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = SuccessGreenLight),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(18.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Semua Tagihan Lunas!",
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Hebat! Tidak ada tagihan tertunda untuk periode ini.",
                                        color = Slate600,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.dueCustomers.take(4),
                        key = { it.id },
                        contentType = { "due_customer" }
                    ) { customer ->
                        val unpaidPeriods = uiState.customerArrearsMap[customer.id] ?: listOf(uiState.currentPeriod)
                        val totalDue = uiState.customerDueAmountMap[customer.id] ?: customer.monthlyFee

                        CustomerDueItem(
                            customer = customer,
                            unpaidPeriods = unpaidPeriods,
                            totalDueAmount = totalDue,
                            onPayClick = { onNavigateToCashier(customer.id) },
                            onSendWaReminder = {
                                val periodText = if (unpaidPeriods.size > 1) {
                                    "${unpaidPeriods.size} bulan (${unpaidPeriods.joinToString(", ")})"
                                } else {
                                    "periode *${unpaidPeriods.firstOrNull() ?: uiState.currentPeriod}*"
                                }
                                val msg = "Halo Bpk/Ibu *${customer.name}*,\n\nKami dari *Billingku WiFi* menginfokan bahwa terdapat tagihan internet $periodText sebesar *${EscPosHelper.formatRupiah(totalDue)}* yang telah jatuh tempo (Tgl ${customer.dueDay}).\n\nMohon lakukan pembayaran agar koneksi internet tetap aktif. Terima kasih!"
                                WhatsAppHelper.sendWhatsAppMessage(context, customer.phone, msg)
                            }
                        )
                    }
                }

                // 5. Transaksi Terbaru
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Riwayat Transaksi Terbaru",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        TextButton(onClick = onNavigateToReports) {
                            Text("Semua Riwayat", color = BluePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                if (uiState.recentTransactions.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Belum ada transaksi di periode ini",
                                color = Slate400,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(20.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(
                        items = uiState.recentTransactions,
                        key = { it.id },
                        contentType = { "recent_transaction" }
                    ) { trx ->
                        TransactionItem(
                            transaction = trx,
                            onClick = {
                                previewReceiptData = EscPosHelper.ReceiptData(
                                    invoiceNo = trx.invoiceNumber,
                                    customerName = trx.customerName,
                                    customerCode = trx.customerCode,
                                    packageName = trx.packageName,
                                    period = trx.periodMonth,
                                    monthlyFee = trx.monthlyFee,
                                    adminFee = trx.adminFee,
                                    discount = trx.discount,
                                    totalAmount = trx.totalAmount,
                                    paymentMethod = trx.paymentMethod,
                                    cashierName = trx.cashierName,
                                    date = trx.paidAt
                                )
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(10.dp)) }
            }
        }
    }

    previewReceiptData?.let { data ->
        ReceiptPreviewDialog(
            receiptData = data,
            onDismiss = { previewReceiptData = null },
            onPrintAgain = {
                previewReceiptData = null
                onNavigateToPrinterSettings()
            }
        )
    }
}

@Composable
fun CustomerDueItem(
    customer: CustomerEntity,
    unpaidPeriods: List<String> = emptyList(),
    totalDueAmount: Long = customer.monthlyFee,
    onPayClick: () -> Unit,
    onSendWaReminder: () -> Unit
) {
    val isMultiMonth = unpaidPeriods.size > 1
    val isIsolated = customer.status.equals("ISOLATED", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_due_item_${customer.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            if (isIsolated) 1.5.dp else 1.dp,
            if (isIsolated) DangerRed.copy(alpha = 0.5f) else BorderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customer.name,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slate100
                    ) {
                        Text(
                            text = customer.customerCode,
                            color = Slate600,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isIsolated) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DangerRed
                        ) {
                            Text(
                                text = "TERISOLIR",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                    }

                    if (isMultiMonth) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DangerRedLight
                        ) {
                            Text(
                                text = "Nunggak ${unpaidPeriods.size} Bln",
                                color = DangerRedDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WarningAmberLight
                        ) {
                            Text(
                                text = "Tgl ${customer.dueDay}",
                                color = WarningAmberDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = customer.packageName,
                        color = Slate600,
                        fontSize = 12.sp
                    )
                }

                if (isMultiMonth) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Periode: ${unpaidPeriods.joinToString(", ")}",
                        color = DangerRedDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (customer.odpLocation.isNotEmpty()) {
                    Text(
                        text = "Lokasi: ${customer.odpLocation}",
                        color = Slate400,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = EscPosHelper.formatRupiah(totalDueAmount),
                    color = if (isMultiMonth || isIsolated) DangerRed else BluePrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // WA reminder button
                FilledTonalIconButton(
                    onClick = onSendWaReminder,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = WhatsAppGreenLight
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Kirim WA",
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Pay POS button
                Button(
                    onClick = onPayClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isIsolated) DangerRed else BluePrimary
                    )
                ) {
                    Text(if (isMultiMonth) "Bayar (${unpaidPeriods.size} Bln)" else "Bayar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val dateStr = remember(transaction.paidAt) {
        com.example.util.DateFormatter.formatShortDateTime(transaction.paidAt)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("trx_item_${transaction.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuccessGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = SuccessGreenDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = transaction.customerName,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${transaction.invoiceNumber} • $dateStr",
                        color = Slate400,
                        fontSize = 11.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BluePrimaryLight
                        ) {
                            Text(
                                text = transaction.paymentMethod,
                                color = BluePrimaryDark,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kasir: ${transaction.cashierName}",
                            color = Slate600,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = EscPosHelper.formatRupiah(transaction.totalAmount),
                    fontWeight = FontWeight.ExtraBold,
                    color = SuccessGreenDark,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = if (transaction.isPrinted) Slate400 else WarningAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (transaction.isPrinted) "Tercetak" else "Lihat",
                        color = Slate500,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
