package com.example.ui.settings.tabs

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TransactionEntity
import com.example.printer.EscPosHelper
import com.example.ui.settings.FinancialReportData
import com.example.ui.settings.SettingsUiState
import com.example.ui.settings.SettingsViewModel
import com.example.ui.settings.reports.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onPreviewReceipt: (EscPosHelper.ReceiptData) -> Unit
) {
    val context = LocalContext.current
    val report = uiState.reportData
    val periodFilter = uiState.periodFilter

    var searchQuery by remember { mutableStateOf("") }
    var selectedMethodFilter by remember { mutableStateOf("ALL") } // "ALL", "TUNAI", "TRANSFER", "QRIS"
    var isPrintingReport by remember { mutableStateOf(false) }

    // Filter transactions list for the table/cards
    val displayTransactions = remember(report.transactions, searchQuery, selectedMethodFilter) {
        report.transactions.filter { trx ->
            val matchesSearch = searchQuery.isBlank() ||
                trx.customerName.contains(searchQuery, ignoreCase = true) ||
                trx.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                trx.customerCode.contains(searchQuery, ignoreCase = true) ||
                trx.cashierName.contains(searchQuery, ignoreCase = true)

            val matchesMethod = when (selectedMethodFilter) {
                "ALL" -> true
                else -> trx.paymentMethod.equals(selectedMethodFilter, ignoreCase = true)
            }

            matchesSearch && matchesMethod
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
    ) {
        // 1. Modern Period Selector & Navigator
        item {
            PeriodSelectorCard(
                filter = periodFilter,
                onSelectType = { viewModel.setPeriodFilterType(it) },
                onPrev = { viewModel.navigatePeriodOffset(-1) },
                onNext = { viewModel.navigatePeriodOffset(1) },
                onResetToCurrent = { viewModel.resetPeriodToCurrent() }
            )
        }

        // 2. Hero Gradient Revenue Card (Material 3 Fintech Card)
        item {
            HeroFinancialCard(
                report = report,
                periodTitle = periodFilter.getDisplayRangeTitle(),
                periodType = periodFilter.type
            )
        }

        // 3. Quick Action Buttons (Print Thermal & WhatsApp Share)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Print Thermal Button
                Button(
                    onClick = {
                        isPrintingReport = true
                        viewModel.printCurrentPeriodReport(printedBy = "Admin") { success, msg ->
                            isPrintingReport = false
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_print_thermal_report"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                    elevation = ButtonDefaults.buttonElevation(2.dp),
                    enabled = !isPrintingReport
                ) {
                    if (isPrintingReport) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cetak Rekap (58mm)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                // WhatsApp Share Button
                Button(
                    onClick = {
                        val shareText = ReportExportHelper.generateWhatsAppText(
                            businessName = uiState.businessName,
                            periodTitle = periodFilter.getDisplayRangeTitle(),
                            periodType = periodFilter.type,
                            reportData = report,
                            undepositedCash = report.undepositedCashTotal
                        )
                        ReportExportHelper.shareReportViaWhatsApp(context, shareText)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_share_wa_report"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                    elevation = ButtonDefaults.buttonElevation(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kirim WhatsApp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }

        // 4. Modern 3D Bar Chart (Tren Omzet Interaktif)
        item {
            Modern3DBarChart(
                points = report.chartPoints,
                periodType = periodFilter.type,
                selectedPoint = uiState.selectedChartPoint,
                onSelectPoint = { viewModel.setSelectedChartPoint(it) }
            )
        }

        // 5. Payment Method Donut Arc Chart
        item {
            PaymentMethodDonutChart(
                cashAmount = report.cashTotal,
                transferAmount = report.transferTotal,
                qrisAmount = report.qrisTotal,
                totalAmount = report.totalRevenue
            )
        }

        // 6. Cashier / Collector Breakdown Card
        item {
            CashierBreakdownCard(
                staffMap = report.staffBreakdown,
                totalRevenue = report.totalRevenue
            )
        }

        // 7. Transactions Header & Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Transaksi (${displayTransactions.size})",
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        fontSize = 16.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate100
                    ) {
                        Text(
                            text = "${report.transactionCount} Total di Periode",
                            color = Slate600,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_search_trx_input"),
                    placeholder = { Text("Cari pelanggan, invoice, atau staf...", fontSize = 12.sp, color = Slate400) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = BluePrimary,
                        unfocusedBorderColor = Slate200
                    )
                )

                // Filter Method Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val methods = listOf("ALL" to "Semua", "TUNAI" to "Tunai", "TRANSFER" to "Transfer", "QRIS" to "QRIS")
                    items(methods) { (key, label) ->
                        val isSelected = selectedMethodFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMethodFilter = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BluePrimary,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Slate700
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) BluePrimary else Slate200
                            )
                        )
                    }
                }
            }
        }

        // 8. Transactions Items List
        if (displayTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate100)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Slate100,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Slate400, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tidak Ada Transaksi",
                            fontWeight = FontWeight.Bold,
                            color = Slate800,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Tidak ada transaksi yang cocok untuk filter dan rentang periode ini.",
                            color = Slate500,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(displayTransactions, key = { it.id }) { trx ->
                ModernTransactionReportCard(
                    trx = trx,
                    uiState = uiState,
                    onPreviewReceipt = onPreviewReceipt
                )
            }
        }
    }
}

// ---------------- SUB-COMPONENTS ----------------

@Composable
private fun PeriodSelectorCard(
    filter: PeriodFilterState,
    onSelectType: (ReportPeriodType) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetToCurrent: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Mode Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate100)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val types = listOf(
                    ReportPeriodType.WEEKLY,
                    ReportPeriodType.MONTHLY,
                    ReportPeriodType.YEARLY,
                    ReportPeriodType.ALL_TIME
                )
                types.forEach { type ->
                    val isSelected = filter.type == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { onSelectType(type) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) BluePrimary else Slate600,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigator Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev button
                IconButton(
                    onClick = onPrev,
                    enabled = filter.type != ReportPeriodType.ALL_TIME,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (filter.type != ReportPeriodType.ALL_TIME) Slate100 else Slate50)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Sebelumnya",
                        tint = if (filter.type != ReportPeriodType.ALL_TIME) Slate700 else Slate300,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Range Title & Pill
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = filter.getDisplayRangeTitle(),
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!filter.isCurrentPeriod() && filter.type != ReportPeriodType.ALL_TIME) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BluePrimaryLight,
                            modifier = Modifier.clickable { onResetToCurrent() }
                        ) {
                            Text(
                                text = "Kembali ke Saat Ini ↺",
                                color = BluePrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (filter.type != ReportPeriodType.ALL_TIME) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(SuccessGreen, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Periode Berjalan",
                                color = SuccessGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Next button
                IconButton(
                    onClick = onNext,
                    enabled = filter.type != ReportPeriodType.ALL_TIME,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (filter.type != ReportPeriodType.ALL_TIME) Slate100 else Slate50)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Berikutnya",
                        tint = if (filter.type != ReportPeriodType.ALL_TIME) Slate700 else Slate300,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroFinancialCard(
    report: FinancialReportData,
    periodTitle: String,
    periodType: ReportPeriodType
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
        border = BorderStroke(1.dp, Slate100)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BluePrimaryLight.copy(alpha = 0.45f),
                            Color.White
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "TOTAL OMZET PEMASUKAN",
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = EscPosHelper.formatRupiah(report.totalRevenue),
                            fontWeight = FontWeight.Black,
                            color = Slate950,
                            fontSize = 28.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SuccessGreenLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${report.transactionCount} Trx",
                                color = SuccessGreenDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Breakdown pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModernPill(title = "Tunai", amount = report.cashTotal, color = SuccessGreen, modifier = Modifier.weight(1f))
                    ModernPill(title = "Transfer", amount = report.transferTotal, color = BluePrimary, modifier = Modifier.weight(1f))
                    ModernPill(title = "QRIS", amount = report.qrisTotal, color = Color(0xFF8B5CF6), modifier = Modifier.weight(1f))
                }

                // If there's undeposited cash, show warning badge
                if (report.undepositedCashTotal > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WarningAmberLight,
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmberDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Uang Tunai Belum Disetor ke Owner",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = "Sebesar ${EscPosHelper.formatRupiah(report.undepositedCashTotal)} di laci kasir",
                                    fontSize = 10.sp,
                                    color = Slate600
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernPill(title: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = EscPosHelper.formatRupiah(amount),
                fontWeight = FontWeight.ExtraBold,
                color = Slate900,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CashierBreakdownCard(staffMap: Map<String, Long>, totalRevenue: Long) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rekapitulasi per Kasir / Staf",
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    fontSize = 15.sp
                )
                Text(
                    text = "${staffMap.size} Petugas",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (staffMap.isEmpty()) {
                Text("Belum ada transaksi staf pada periode ini", color = Slate400, fontSize = 12.sp)
            } else {
                staffMap.forEach { (cashier, amount) ->
                    val percentage = if (totalRevenue > 0) ((amount.toDouble() / totalRevenue) * 100).toInt() else 0

                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = BluePrimaryLight,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = cashier.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = BluePrimary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = cashier, fontWeight = FontWeight.SemiBold, color = Slate800, fontSize = 13.sp)
                                    Text(text = "$percentage% dari total omzet", color = Slate400, fontSize = 10.sp)
                                }
                            }

                            Text(
                                text = EscPosHelper.formatRupiah(amount),
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (totalRevenue > 0) (amount.toFloat() / totalRevenue.toFloat()) else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BluePrimary,
                            trackColor = Slate100
                        )
                    }
                    HorizontalDivider(color = Slate100, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ModernTransactionReportCard(
    trx: TransactionEntity,
    uiState: SettingsUiState,
    onPreviewReceipt: (EscPosHelper.ReceiptData) -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("id", "ID"))
    val dateFormatted = sdf.format(Date(trx.paidAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onPreviewReceipt(
                    EscPosHelper.ReceiptData(
                        businessName = uiState.businessName,
                        businessAddress = uiState.businessAddress,
                        businessPhone = uiState.businessPhone,
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
                        date = trx.paidAt,
                        template = uiState.receiptTemplate,
                        footerMessage = uiState.footerMessage
                    )
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trx.customerName,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (trx.paymentMethod.uppercase()) {
                            "TUNAI" -> SuccessGreenLight
                            "TRANSFER" -> BluePrimaryLight
                            else -> Color(0xFFF3E8FF)
                        }
                    ) {
                        Text(
                            text = trx.paymentMethod,
                            color = when (trx.paymentMethod.uppercase()) {
                                "TUNAI" -> SuccessGreenDark
                                "TRANSFER" -> BluePrimary
                                else -> Color(0xFF7C3AED)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${trx.invoiceNumber} • ${trx.periodMonth}",
                    color = Slate500,
                    fontSize = 11.sp
                )
                Text(
                    text = "$dateFormatted • Kasir: ${trx.cashierName}",
                    color = Slate400,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = EscPosHelper.formatRupiah(trx.totalAmount),
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate950,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BluePrimaryLight,
                    modifier = Modifier.padding(1.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Receipt, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Struk", color = BluePrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
