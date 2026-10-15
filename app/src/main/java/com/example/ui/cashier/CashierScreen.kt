package com.example.ui.cashier

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.printer.EscPosHelper
import com.example.ui.components.PullRefreshWrapper
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.components.SwipeToConfirmButton
import com.example.ui.theme.*
import com.example.util.WhatsAppHelper
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(
    viewModel: CashierViewModel,
    preselectedCustomerId: Long? = null,
    isAdmin: Boolean = true,
    onVerifyAdminPin: ((String) -> Boolean)? = null,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showReceiptModal by remember { mutableStateOf(false) }

    val currentDay = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val filteredBillCards = uiState.filteredBillCards

    LaunchedEffect(preselectedCustomerId) {
        preselectedCustomerId?.let { viewModel.selectCustomerById(it) }
    }

    LaunchedEffect(uiState.lastCompletedReceipt, uiState.isConfirmed) {
        if (uiState.lastCompletedReceipt != null && uiState.isConfirmed) {
            viewModel.closeCheckoutModal()
            showReceiptModal = true
        }
    }

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Peringatan Pembayaran",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = uiState.errorMessage ?: "",
                    fontSize = 13.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearErrorMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Kasir & Tagihan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900,
                            fontSize = 17.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Bulan Ini: ${uiState.currentPeriod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_cashier_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Slate800
                        )
                    }
                },
                actions = {},
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(2.dp)) }

                // =============================================================
                // 1. KPI BANNER - TOTAL TAGIHAN & TUNGGAKAN (CLEAN & ELEGANT)
                // =============================================================
                item {
                    val totalArrearsAmount = uiState.allBillCards.sumOf { it.fee }
                    val pastArrearsCount = uiState.allBillCards.count { it.isPastArrears }
                    val currentMonthCount = uiState.allBillCards.count { it.isCurrentMonth }
                    val isolatedCount = uiState.allBillCards.map { it.customer }.distinctBy { it.id }
                        .count { it.status.equals("ISOLATED", ignoreCase = true) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 4.dp, shape = RoundedCornerShape(18.dp), spotColor = BluePrimary.copy(alpha = 0.2f))
                            .testTag("cashier_due_summary_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF0F172A), // Midnight Navy
                                            Color(0xFF1E3A8A), // Royal Navy
                                            Color(0xFF2563EB)  // Blue Accent
                                        )
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "TOTAL PIUTANG TAGIHAN",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (pastArrearsCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = DangerRed
                                            ) {
                                                Text(
                                                    text = "$pastArrearsCount Tunggakan Lalu",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (isolatedCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF7F1D1D)
                                            ) {
                                                Text(
                                                    text = "$isolatedCount Terisolir",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = EscPosHelper.formatRupiah(totalArrearsAmount),
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Total ${uiState.allBillCards.size} nota (${pastArrearsCount} bulan lalu • ${currentMonthCount} bulan ini)",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Nota Terpisah",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 2. SEARCH BAR & FILTER CHIPS (CLEAN & MODERN)
                // =============================================================
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    "Cari nama, ID, WA, ODP, atau bulan...",
                                    fontSize = 13.sp,
                                    color = Slate400
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(19.dp)
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Hapus",
                                            tint = Slate400,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cashier_search_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = BluePrimary,
                                unfocusedBorderColor = Slate200
                            ),
                            singleLine = true
                        )

                        // Filter Chips
                        val pastArrearsCount = uiState.allBillCards.count { it.isPastArrears }
                        val currentMonthCount = uiState.allBillCards.count { it.isCurrentMonth }
                        val isolatedCount = uiState.allBillCards.map { it.customer }.distinctBy { it.id }
                            .count { it.status.equals("ISOLATED", ignoreCase = true) }

                        val filters = remember(uiState.allBillCards.size, pastArrearsCount, currentMonthCount, isolatedCount) {
                            val list = mutableListOf(
                                "ALL" to "Semua (${uiState.allBillCards.size})",
                                "ARREARS" to "Bulan Lalu ($pastArrearsCount)",
                                "CURRENT_MONTH" to "Bulan Ini ($currentMonthCount)",
                                "TODAY_OVERDUE" to "Hari Ini / Lewat"
                            )
                            if (isolatedCount > 0) {
                                list.add("ISOLATED" to "Terisolir ($isolatedCount)")
                            }
                            list
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 1.dp)
                        ) {
                            items(filters) { (filterKey, label) ->
                                val isSelected = uiState.dueFilter == filterKey
                                val isAlertFilter = filterKey == "ARREARS" || filterKey == "ISOLATED"

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when {
                                        isSelected && isAlertFilter -> DangerRed
                                        isSelected -> BluePrimary
                                        else -> Color.White
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isSelected && isAlertFilter -> DangerRed
                                            isSelected -> BluePrimary
                                            isAlertFilter && (pastArrearsCount > 0 || isolatedCount > 0) -> DangerRed.copy(alpha = 0.35f)
                                            else -> Slate200
                                        }
                                    ),
                                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .clickable { viewModel.setDueFilter(filterKey) }
                                        .testTag("filter_chip_$filterKey")
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isSelected -> Color.White
                                            isAlertFilter && filterKey == "ARREARS" && pastArrearsCount > 0 -> DangerRedDark
                                            isAlertFilter && filterKey == "ISOLATED" && isolatedCount > 0 -> DangerRedDark
                                            else -> Slate700
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 3. DAFTAR KARTU TAGIHAN PER PERIODE
                // (Pelanggan nunggak bulan lalu punya kartu bulan lalu, dan kartu bulan ini)
                // =============================================================
                if (filteredBillCards.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = SuccessGreenLight.copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(28.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreenLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Semua Tagihan Beres!",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF065F46),
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tidak ada tagihan atau tunggakan tertunda pada filter ini.",
                                    color = Slate600,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredBillCards,
                        key = { it.id },
                        contentType = { "bill_card" }
                    ) { billCard ->
                        val customer = billCard.customer
                        val isIsolated = customer.status.equals("ISOLATED", ignoreCase = true)
                        val isPast = billCard.isPastArrears
                        val isLast = billCard.isLastMonth
                        val isOverdue = customer.dueDay < currentDay || isPast
                        val isToday = customer.dueDay == currentDay && !isPast

                        // Dynamic Modern Avatar Gradient (Minimal & Elegant)
                        val avatarColors = remember(customer.name) {
                            val palettes = listOf(
                                listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)), // Blue
                                listOf(Color(0xFF4F46E5), Color(0xFF3730A3)), // Indigo
                                listOf(Color(0xFF0D9488), Color(0xFF0F766E)), // Teal
                                listOf(Color(0xFFD97706), Color(0xFFB45309)), // Amber
                                listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)), // Violet
                                listOf(Color(0xFF0284C7), Color(0xFF0369A1))  // Sky
                            )
                            val idx = (customer.name.hashCode() and 0x7FFFFFFF) % palettes.size
                            palettes[idx]
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.openCheckoutModal(billCard)
                                }
                                .testTag("bill_card_${billCard.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPast) Color(0xFFFFFDFC) else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isIsolated -> DangerRed.copy(alpha = 0.5f)
                                    isPast -> Color(0xFFFECDD3) // Soft Rose/Crimson
                                    else -> Color(0xFFE2E8F0)  // Soft Slate
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // 1. Header: Avatar + Nama + ID + Status Badges
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
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(Brush.linearGradient(avatarColors)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = customer.name.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = customer.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Slate900,
                                                    fontSize = 14.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(5.dp),
                                                    color = Slate100
                                                ) {
                                                    Text(
                                                        text = customer.customerCode,
                                                        color = Slate600,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        softWrap = false,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Wifi,
                                                    contentDescription = null,
                                                    tint = Slate400,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = customer.packageName,
                                                    color = Slate500,
                                                    fontSize = 11.5.sp
                                                )
                                                val loc = customer.odpLocation.ifEmpty { customer.address }
                                                if (loc.isNotBlank()) {
                                                    Text(
                                                        text = " • $loc",
                                                        color = Slate400,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Status Badge (Top-Right)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isIsolated) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = DangerRed
                                            ) {
                                                Text(
                                                    text = "ISOLIR",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                isLast || isPast -> Color(0xFFFFE4E6) // Soft Rose
                                                isOverdue -> Color(0xFFFEF2F2)
                                                isToday -> Color(0xFFFEF3C7)
                                                else -> Color(0xFFF1F5F9)
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    isLast -> "Bulan Lalu"
                                                    isPast -> "Tunggakan"
                                                    isOverdue -> "Lewat Tgl ${customer.dueDay}"
                                                    isToday -> "Hari Ini!"
                                                    else -> "Tgl ${customer.dueDay}"
                                                },
                                                color = when {
                                                    isLast || isPast || isOverdue -> DangerRedDark
                                                    isToday -> WarningAmberDark
                                                    else -> Slate600
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Slate100)

                                // 2. Bottom Action Strip: Periode & Nominal Tagihan + Tombol WA + Tombol Bayar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isPast) Icons.Default.WarningAmber else Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = if (isPast) DangerRed else Slate400,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = when {
                                                    isLast -> "Tagihan Bulan Lalu: "
                                                    isPast -> "Tunggakan Lampau: "
                                                    else -> "Tagihan Bulan Ini: "
                                                },
                                                color = if (isPast) DangerRedDark else Slate500,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = billCard.periodMonth,
                                                color = if (isPast) DangerRedDark else Slate700,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = EscPosHelper.formatRupiah(billCard.fee),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isPast || isIsolated) DangerRed else BluePrimary,
                                            fontSize = 17.sp
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Tombol WhatsApp (Clean Modern Icon Button)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFDCFCE7),
                                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clickable {
                                                    val periodLabel = if (isLast) "tunggakan bulan lalu (*${billCard.periodMonth}*)" else if (isPast) "tunggakan lampau (*${billCard.periodMonth}*)" else "periode *${billCard.periodMonth}*"
                                                    val msg = "Halo Bpk/Ibu *${customer.name}* (ID: ${customer.customerCode}),\n" +
                                                            "Kami dari Billingku WiFi menginfokan bahwa terdapat tagihan internet $periodLabel sebesar *${EscPosHelper.formatRupiah(billCard.fee)}* yang telah jatuh tempo (Tgl ${customer.dueDay}).\n" +
                                                            (if (isIsolated) "⚠️ Status layanan Anda saat ini TERISOLIR. " else "") +
                                                            "Mohon lakukan pembayaran agar koneksi internet tetap aktif. Terima kasih!"
                                                    WhatsAppHelper.sendWhatsAppMessage(context, customer.phone, msg)
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Chat,
                                                    contentDescription = "Kirim WA",
                                                    tint = Color(0xFF16A34A),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }

                                        // Tombol Bayar Sekarang khusus periode ini (Sleek & Proportional)
                                        Button(
                                            onClick = { viewModel.openCheckoutModal(billCard) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isPast || isIsolated) DangerRed else BluePrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PointOfSale,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = when {
                                                    isLast -> "Bayar Bulan Lalu"
                                                    isPast -> "Bayar Tunggakan"
                                                    else -> "Bayar Sekarang"
                                                },
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(10.dp)) }
            }
        }
    }

    // =========================================================================
    // MODAL CHECKOUT SINGLE-PERIODE (TANPA PRA-PILIHAN OTOMATIS)
    // =========================================================================
    if (uiState.isCheckoutModalOpen && uiState.selectedCustomer != null && uiState.selectedBillCard != null) {
        val customer = uiState.selectedCustomer!!
        val billCard = uiState.selectedBillCard!!
        val isCustomerIsolated = customer.status.equals("ISOLATED", ignoreCase = true)
        val isPast = billCard.isPastArrears
        val isLast = billCard.isLastMonth
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var discountInputText by remember(billCard.id) {
            mutableStateOf(if (uiState.discount > 0) uiState.discount.toString() else "")
        }

        ModalBottomSheet(
            onDismissRequest = { viewModel.closeCheckoutModal() },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.testTag("modal_checkout_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Konfirmasi Pembayaran Kasir",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900
                        )
                        Text(
                            text = when {
                                isLast -> "Membayar tagihan bulan lalu (${billCard.periodMonth})"
                                isPast -> "Membayar tunggakan lampau (${billCard.periodMonth})"
                                else -> "Membayar tagihan bulan ini (${billCard.periodMonth})"
                            },
                            color = if (isPast) DangerRedDark else Slate500,
                            fontSize = 12.sp,
                            fontWeight = if (isPast) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    IconButton(onClick = { viewModel.closeCheckoutModal() }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Informasi Pelanggan & Periode yang Dibayarkan
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isPast || isCustomerIsolated) DangerRedLight.copy(alpha = 0.5f) else BluePrimaryLight,
                    border = BorderStroke(1.dp, if (isPast || isCustomerIsolated) DangerRed.copy(alpha = 0.3f) else BluePrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = customer.name,
                                fontWeight = FontWeight.Bold,
                                color = if (isPast || isCustomerIsolated) DangerRedDark else BluePrimaryDark,
                                fontSize = 16.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isCustomerIsolated) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = DangerRed) {
                                        Text(
                                            text = "TERISOLIR",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = Color.White) {
                                    Text(
                                        text = customer.customerCode,
                                        color = if (isPast) DangerRed else BluePrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${customer.packageName} • ${customer.phone}",
                            color = Slate700,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Box Periode yang dibayarkan
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = if (isPast) DangerRed else BluePrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            isLast -> "Periode Bulan Lalu:"
                                            isPast -> "Periode Tunggakan:"
                                            else -> "Periode Bulan Ini:"
                                        },
                                        fontSize = 12.sp,
                                        color = if (isPast) DangerRedDark else Slate600,
                                        fontWeight = if (isPast) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                Text(
                                    text = "${billCard.periodMonth} ${if (isLast) "(Bulan Lalu)" else if (isPast) "(Tunggakan)" else "(Bulan Ini)"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isPast) DangerRed else BluePrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metode Pembayaran (TUNAI, TRANSFER, QRIS)
                Text(
                    text = "METODE PEMBAYARAN:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate700,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val methods = listOf(
                        Triple("TUNAI", "Tunai", Icons.Default.Payments),
                        Triple("TRANSFER", "Transfer", Icons.Default.AccountBalance),
                        Triple("QRIS", "QRIS", Icons.Default.QrCodeScanner)
                    )
                    methods.forEach { (key, label, icon) ->
                        val isSelected = uiState.paymentMethod == key
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) BluePrimaryLight else Slate50,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) BluePrimary else BorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setPaymentMethod(key) }
                                .testTag("payment_method_$key")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) BluePrimary else Slate400,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) BluePrimaryDark else Slate600,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Penyesuaian Diskon & Biaya Admin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = discountInputText,
                        onValueChange = { input ->
                            discountInputText = input.filter { it.isDigit() }
                            val discLong = discountInputText.toLongOrNull() ?: 0L
                            viewModel.setDiscount(discLong)
                        },
                        label = { Text("Potongan / Diskon (Rp)", fontSize = 11.sp) },
                        placeholder = { Text("0", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate50,
                            unfocusedContainerColor = Slate50
                        )
                    )

                    OutlinedTextField(
                        value = if (uiState.adminFee > 0) uiState.adminFee.toString() else "",
                        onValueChange = { input ->
                            val feeLong = input.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            viewModel.setAdminFee(feeLong)
                        },
                        label = { Text("Biaya Admin (Rp)", fontSize = 11.sp) },
                        placeholder = { Text("0", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate50,
                            unfocusedContainerColor = Slate50
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Catatan Transaksi
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = { viewModel.setNotes(it) },
                    label = { Text("Catatan Kasir (Opsional)", fontSize = 11.sp) },
                    placeholder = { Text("Cth: Titip tetangga, transfer BCA, dll...", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Slate50,
                        unfocusedContainerColor = Slate50
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Rincian Pembayaran
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Iuran Internet (${billCard.periodMonth})", color = Slate600, fontSize = 13.sp)
                            Text(EscPosHelper.formatRupiah(billCard.fee), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        if (uiState.adminFee > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Biaya Admin", color = Slate600, fontSize = 13.sp)
                                Text("+ ${EscPosHelper.formatRupiah(uiState.adminFee)}", color = Slate700, fontSize = 13.sp)
                            }
                        }

                        if (uiState.discount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Potongan Diskon", color = DangerRed, fontSize = 13.sp)
                                Text("- ${EscPosHelper.formatRupiah(uiState.discount)}", color = DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Slate200)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL DIBAYAR:", fontWeight = FontWeight.ExtraBold, color = Slate900, fontSize = 14.sp)
                            Text(
                                text = EscPosHelper.formatRupiah(viewModel.calculateTotal()),
                                fontWeight = FontWeight.ExtraBold,
                                color = BluePrimary,
                                fontSize = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // SWIPE TO CONFIRM & PRINT BUTTON
                SwipeToConfirmButton(
                    onConfirmed = { viewModel.confirmAndPrintTransaction() },
                    text = "Geser untuk Bayar & Cetak »",
                    isProcessing = uiState.isProcessing,
                    isConfirmed = uiState.isConfirmed,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Modal Struk Selesai
    if (showReceiptModal && uiState.lastCompletedReceipt != null) {
        ReceiptPreviewDialog(
            receiptData = uiState.lastCompletedReceipt!!,
            onDismiss = { showReceiptModal = false },
            onPrintAgain = { viewModel.reprintLastReceipt() }
        )
    }
}
