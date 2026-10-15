package com.example.ui.settlement

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.SettlementBatchEntity
import com.example.data.local.entity.StaffEntity
import com.example.data.local.entity.TransactionEntity
import com.example.printer.EscPosHelper
import com.example.ui.components.PullRefreshWrapper
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    viewModel: SettlementViewModel,
    currentStaff: StaffEntity?,
    isAdmin: Boolean = true,
    onBackClick: (() -> Unit)? = null
) {
    if (!isAdmin) {
        androidx.activity.compose.BackHandler {
            onBackClick?.invoke()
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text("Akses Dibatasi", fontWeight = FontWeight.Bold, color = Slate900) },
                    navigationIcon = {
                        if (onBackClick != null) {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate50)
                )
            },
            containerColor = Slate50
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DangerRedLight,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Akses Khusus Admin / Owner",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Slate900,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Menu Setoran Kas dan Rekonsiliasi hanya dapat diakses oleh akun Administrator/Owner.",
                            color = Slate500,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { onBackClick?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Kembali ke Dashboard", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Setoran Kas & Rekonsiliasi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Slate900
                            )
                        }
                        Text(
                            text = "Pencocokan Uang & Pelanggan Lunas • Petugas: ${currentStaff?.name ?: "Kasir"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.resetToCurrentMonth()
                        },
                        modifier = Modifier.testTag("btn_reset_current_month")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = "Bulan Ini",
                            tint = BluePrimary
                        )
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Period Selector Header (Month navigator)
                PeriodNavigatorHeader(
                    periodMonth = uiState.periodMonth,
                    isCurrentMonth = uiState.calendarMonthOffset == 0,
                    onPreviousMonth = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.previousMonth()
                    },
                    onNextMonth = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.nextMonth()
                    },
                    onResetMonth = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.resetToCurrentMonth()
                    }
                )

                // 2. Modern Segmented Pill Selector (iOS / Material 3 Style)
                Surface(
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(14.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isTab0 = uiState.currentTab == 0
                        val isTab1 = uiState.currentTab == 1

                        // Tab 0: Pencocokan & Setoran
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (isTab0) Color.White else Color.Transparent,
                            shadowElevation = if (isTab0) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setTab(0)
                                }
                                .testTag("tab_settlement_reconcile")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PriceCheck,
                                    contentDescription = null,
                                    tint = if (isTab0) BluePrimary else Slate500,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pencocokan & Setoran",
                                    fontWeight = if (isTab0) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isTab0) BluePrimary else Slate600
                                )
                                if (uiState.undepositedCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = DangerRed
                                    ) {
                                        Text(
                                            text = "${uiState.undepositedCount}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Tab 1: Riwayat Setoran
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (isTab1) Color.White else Color.Transparent,
                            shadowElevation = if (isTab1) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setTab(1)
                                }
                                .testTag("tab_settlement_history")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HistoryEdu,
                                    contentDescription = null,
                                    tint = if (isTab1) BluePrimary else Slate500,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Riwayat Setoran",
                                    fontWeight = if (isTab1) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isTab1) BluePrimary else Slate600
                                )
                                if (uiState.settlementBatches.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isTab1) BluePrimaryLight else Slate200
                                    ) {
                                        Text(
                                            text = "${uiState.settlementBatches.size}",
                                            color = if (isTab1) BluePrimary else Slate700,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Tab Contents with smooth AnimatedContent transition
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = uiState.currentTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth / 4 },
                                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(280))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                            } else {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(280))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> fullWidth / 4 },
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                            }
                        },
                        label = "SettlementTabAnimation",
                        modifier = Modifier.fillMaxSize()
                    ) { currentTab ->
                        when (currentTab) {
                            0 -> ReconcileAndDepositTabContent(
                                uiState = uiState,
                                viewModel = viewModel,
                                currentStaff = currentStaff
                            )
                            1 -> SettlementHistoryTabContent(
                                uiState = uiState,
                                viewModel = viewModel,
                                onShareWhatsApp = { text ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, text)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Kirim Bukti Setoran ke WhatsApp"))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Confirmation Dialog for Processing Deposit
    if (uiState.isDepositDialogOpen) {
        DepositConfirmationDialog(
            uiState = uiState,
            currentStaff = currentStaff,
            onDismiss = { viewModel.closeDepositDialog() },
            onConfirm = {
                viewModel.processSettlement(cashierName = currentStaff?.name ?: "Kasir")
            },
            onRecipientChanged = { viewModel.setDepositRecipientName(it) },
            onNotesChanged = { viewModel.setDepositNotes(it) }
        )
    }

    // Modal: Success Result Dialog (Batch created & locked)
    uiState.lastCompletedBatch?.let { batch ->
        DepositSuccessDialog(
            batch = batch,
            items = uiState.lastCompletedBatchItems,
            onDismiss = { viewModel.dismissSuccessDialog() },
            onPrint = { viewModel.printSettlementReceipt(batch, uiState.lastCompletedBatchItems) },
            onShareWhatsApp = {
                val text = viewModel.getWhatsAppShareText(batch, uiState.lastCompletedBatchItems)
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Bagikan Bukti Setoran"))
            }
        )
    }

    // Modal: Batch Details from History
    uiState.selectedBatchForDetail?.let { batch ->
        BatchDetailDialog(
            batch = batch,
            items = uiState.selectedBatchItems,
            isLoading = uiState.isLoadingBatchDetails,
            onDismiss = { viewModel.closeBatchDetails() },
            onPrint = { viewModel.printSettlementReceipt(batch, uiState.selectedBatchItems) },
            onShareWhatsApp = {
                val text = viewModel.getWhatsAppShareText(batch, uiState.selectedBatchItems)
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Bagikan Bukti Setoran"))
            }
        )
    }

    // Snackbar / Error Banner
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed) },
            title = { Text("Pemberitahuan Setoran", fontWeight = FontWeight.Bold) },
            text = { Text(error, color = Slate700, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissError() },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate800)
                ) {
                    Text("Mengerti")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

@Composable
private fun PeriodNavigatorHeader(
    periodMonth: String,
    isCurrentMonth: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onResetMonth: () -> Unit
) {
    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = Slate100,
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.testTag("btn_prev_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Bulan Sebelumnya",
                        tint = Slate700,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BluePrimary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = periodMonth,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BluePrimary
                        )
                    }
                }

                if (isCurrentMonth) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Bulan Berjalan",
                                color = SuccessGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = onResetMonth,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Ke Bulan Ini", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = Slate100,
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.testTag("btn_next_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Bulan Selanjutnya",
                        tint = Slate700,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReconcileAndDepositTabContent(
    uiState: SettlementUiState,
    viewModel: SettlementViewModel,
    currentStaff: StaffEntity?
) {
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Metric Summary Overview Card
            item {
                SettlementSummaryCard(uiState = uiState)
            }

            // 2. Physical Cash Drawer Reconciliation Tool (Interactive Cash Tally)
            item {
                CashDrawerReconciliationCard(
                    uiState = uiState,
                    onToggleExpanded = { viewModel.toggleReconciliationExpanded() },
                    onCashInputChanged = { viewModel.onPhysicalCashInputChanged(it) },
                    onDenominationChange = { denom, delta ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateDenomination(denom, delta)
                    },
                    onAutoMatch = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.autoFillPhysicalCashMatching()
                    },
                    onResetDenominations = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.resetDenominations()
                    }
                )
            }

            // 3. Search Bar and Status Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Cari nama, ID, invoice, atau paket...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
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
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setoran_search_input")
                    )

                    // Filter Chips & Selection Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                FilterChip(
                                    selected = uiState.statusFilter == "ALL",
                                    onClick = { viewModel.setStatusFilter("ALL") },
                                    label = { Text("Semua (${uiState.allTransactionsInMonth.size})", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = uiState.statusFilter == "UNDEPOSITED",
                                    onClick = { viewModel.setStatusFilter("UNDEPOSITED") },
                                    label = { Text("Belum Setor (${uiState.undepositedCount})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = WarningAmber.copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFFB45309)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = uiState.statusFilter == "DEPOSITED",
                                    onClick = { viewModel.setStatusFilter("DEPOSITED") },
                                    label = { Text("Sudah Setor (${uiState.depositedCount})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SuccessGreen.copy(alpha = 0.2f),
                                        selectedLabelColor = SuccessGreen
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = uiState.statusFilter == "CASH_ONLY",
                                    onClick = { viewModel.setStatusFilter("CASH_ONLY") },
                                    label = { Text("Tunai", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        if (uiState.undepositedCount > 0) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (uiState.selectedTransactionIds.size == uiState.undepositedCount) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAllUndeposited()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = if (uiState.selectedTransactionIds.size == uiState.undepositedCount && uiState.undepositedCount > 0) "Batal Semua" else "Pilih Semua",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                            }
                        }
                    }
                }
            }

            // 4. Transaction Items List
            if (uiState.filteredTransactions.isEmpty()) {
                item {
                    EmptySettlementView(periodMonth = uiState.periodMonth)
                }
            } else {
                items(
                    items = uiState.filteredTransactions,
                    key = { it.id },
                    contentType = { "settlement_trx" }
                ) { trx ->
                    SettlementTransactionCard(
                        transaction = trx,
                        isSelected = uiState.selectedTransactionIds.contains(trx.id),
                        onToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleTransactionSelection(trx)
                        }
                    )
                }
            }
        }

        // Modern Floating Capsule Bottom Bar for Processing Selected Items
        if (uiState.selectedTransactionIds.isNotEmpty()) {
            Surface(
                color = Color.White,
                shadowElevation = 14.dp,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = BluePrimary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${uiState.selectedTransactionIds.size}",
                                    color = BluePrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pelanggan Terpilih",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = EscPosHelper.formatRupiah(uiState.selectedTotalAmount),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = BluePrimary
                        )
                        Text(
                            text = "Tunai: ${EscPosHelper.formatRupiah(uiState.selectedCashAmount)} • Non-Tunai: ${EscPosHelper.formatRupiah(uiState.selectedNonCashAmount)}",
                            fontSize = 10.sp,
                            color = Slate500,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openDepositDialog()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("btn_process_deposit_now")
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Setorkan Sekarang", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementSummaryCard(uiState: SettlementUiState) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Modern Gradient Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0F172A), // Slate 900
                                Color(0xFF1E293B), // Slate 800
                                Color(0xFF1D4ED8)  // Blue Primary Dark
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "TOTAL PEMBAYARAN BULAN INI",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${uiState.totalPaidCustomersCount} Lunas",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = EscPosHelper.formatRupiah(uiState.totalRevenueInMonth),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Visual Progress Bar: Ratio of Deposited vs Remaining in Cashier
                    val percentDeposited = if (uiState.totalRevenueInMonth > 0) {
                        ((uiState.depositedTotal.toDouble() / uiState.totalRevenueInMonth.toDouble()) * 100).toInt().coerceIn(0, 100)
                    } else 0

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$percentDeposited% Telah Disetorkan ke Owner",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                text = "${100 - percentDeposited}% di Kasir",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        LinearProgressIndicator(
                            progress = { percentDeposited / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = SuccessGreen,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Two Modern Metric Tiles
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pillar 1: Belum Disetorkan (Fisik di Kasir)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(WarningAmber.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(WarningAmber)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "BELUM SETOR",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color(0xFF92400E),
                                    letterSpacing = 0.4.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = EscPosHelper.formatRupiah(uiState.undepositedTotal),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF78350F)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Tunai: ${EscPosHelper.formatRupiah(uiState.undepositedCashTotal)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB45309)
                            )
                            Text(
                                text = "${uiState.undepositedCount} Pelanggan siap setor",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }

                    // Pillar 2: Sudah Disetorkan
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(SuccessGreen)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SUDAH DISETOR",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color(0xFF166534),
                                    letterSpacing = 0.4.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = EscPosHelper.formatRupiah(uiState.depositedTotal),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF14532D)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${uiState.depositedCount} Pelanggan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF15803D)
                            )
                            Text(
                                text = "Terkunci & terarsip ✓",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashDrawerReconciliationCard(
    uiState: SettlementUiState,
    onToggleExpanded: () -> Unit,
    onCashInputChanged: (String) -> Unit,
    onDenominationChange: (Int, Int) -> Unit,
    onAutoMatch: () -> Unit,
    onResetDenominations: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Cocokkan Uang Fisik Kasir (Laci Kas)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate900
                        )
                        Text(
                            text = "Hitung uang tunai di tangan vs data sistem",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }

                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (uiState.isReconciliationExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Slate600
                    )
                }
            }

            AnimatedVisibility(visible = uiState.isReconciliationExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Slate100)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Reconciliation Status Result Box
                    val diff = uiState.cashReconciliationDifference
                    val isMatch = diff == 0L
                    val isSurplus = diff > 0L

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isMatch -> Color(0xFFF0FDF4)
                            isSurplus -> Color(0xFFEFF6FF)
                            else -> Color(0xFFFEF2F2)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isMatch -> Color(0xFFBBF7D0)
                                isSurplus -> Color(0xFFBFDBFE)
                                else -> Color(0xFFFECACA)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when {
                                            isMatch -> Icons.Default.CheckCircle
                                            isSurplus -> Icons.Default.Info
                                            else -> Icons.Default.ErrorOutline
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            isMatch -> Color(0xFF166534)
                                            isSurplus -> Color(0xFF1E40AF)
                                            else -> DangerRed
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            isMatch -> "UANG FISIK COCOK & SEIMBANG"
                                            isSurplus -> "LEBIH FISIK (+${EscPosHelper.formatRupiah(diff)})"
                                            else -> "KURANG FISIK (-${EscPosHelper.formatRupiah(-diff)})"
                                        },
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        color = when {
                                            isMatch -> Color(0xFF166534)
                                            isSurplus -> Color(0xFF1E40AF)
                                            else -> DangerRed
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Sistem (Tunai Belum Setor): ${EscPosHelper.formatRupiah(uiState.undepositedCashTotal)}",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Uang Dihitung:",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                                Text(
                                    text = EscPosHelper.formatRupiah(uiState.totalPhysicalCashCalculated),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Slate900
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Manual Input and Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.physicalCashInput,
                            onValueChange = onCashInputChanged,
                            label = { Text("Input Total Fisik (Rp)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalButton(
                            onClick = onAutoMatch,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Samakan Sistem", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onResetDenominations,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Reset", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Hitung cepat per lembar uang kertas:",
                        fontSize = 11.sp,
                        color = Slate600,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Denomination Counters Grid (Row 1: 100k, 50k, 20k, 10k)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModernDenominationBadge(
                            label = "100rb",
                            tagColor = Color(0xFFE11D48),
                            count = uiState.denomination100k,
                            onAdd = { onDenominationChange(100000, 1) },
                            onSub = { onDenominationChange(100000, -1) },
                            modifier = Modifier.weight(1f)
                        )
                        ModernDenominationBadge(
                            label = "50rb",
                            tagColor = Color(0xFF2563EB),
                            count = uiState.denomination50k,
                            onAdd = { onDenominationChange(50000, 1) },
                            onSub = { onDenominationChange(50000, -1) },
                            modifier = Modifier.weight(1f)
                        )
                        ModernDenominationBadge(
                            label = "20rb",
                            tagColor = Color(0xFF16A34A),
                            count = uiState.denomination20k,
                            onAdd = { onDenominationChange(20000, 1) },
                            onSub = { onDenominationChange(20000, -1) },
                            modifier = Modifier.weight(1f)
                        )
                        ModernDenominationBadge(
                            label = "10rb",
                            tagColor = Color(0xFF9333EA),
                            count = uiState.denomination10k,
                            onAdd = { onDenominationChange(10000, 1) },
                            onSub = { onDenominationChange(10000, -1) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Denomination Counters Grid (Row 2: 5k, 2k, 1k)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModernDenominationBadge(
                            label = "5rb",
                            tagColor = Color(0xFFD97706),
                            count = uiState.denomination5k,
                            onAdd = { onDenominationChange(5000, 1) },
                            onSub = { onDenominationChange(5000, -1) },
                            modifier = Modifier.weight(1f)
                        )
                        ModernDenominationBadge(
                            label = "2rb",
                            tagColor = Color(0xFF64748B),
                            count = uiState.denomination2k,
                            onAdd = { onDenominationChange(2000, 1) },
                            onSub = { onDenominationChange(2000, -1) },
                            modifier = Modifier.weight(1f)
                        )
                        ModernDenominationBadge(
                            label = "1rb",
                            tagColor = Color(0xFF0D9488),
                            count = uiState.denomination1k,
                            onAdd = { onDenominationChange(1000, 1) },
                            onSub = { onDenominationChange(1000, -1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernDenominationBadge(
    label: String,
    tagColor: Color,
    count: Int,
    onAdd: () -> Unit,
    onSub: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (count > 0) tagColor.copy(alpha = 0.06f) else Slate50,
        border = BorderStroke(1.dp, if (count > 0) tagColor.copy(alpha = 0.35f) else Slate200),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = tagColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = tagColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${count}x",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                color = if (count > 0) tagColor else Slate400
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (count > 0) Slate200 else Slate100,
                    modifier = Modifier.size(22.dp)
                ) {
                    IconButton(onClick = onSub, modifier = Modifier.size(22.dp), enabled = count > 0) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = CircleShape,
                    color = tagColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(22.dp)
                ) {
                    IconButton(onClick = onAdd, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = tagColor, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementTransactionCard(
    transaction: TransactionEntity,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val isDeposited = transaction.isDeposited
    val paidDateStr = remember(transaction.paidAt) {
        com.example.util.DateFormatter.formatShortDateTime(transaction.paidAt)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFFF8FAFC)
                isDeposited -> Color(0xFFFAFAFA)
                else -> Color.White
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = when {
                isSelected -> BluePrimary
                isDeposited -> Color(0xFFE2E8F0)
                else -> Slate200
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDeposited) { onToggle() }
            .testTag("setoran_item_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox or Lock Icon
            if (isDeposited) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Sudah disetorkan (Terkunci)",
                        tint = SuccessGreen,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = BluePrimary,
                        uncheckedColor = Slate400
                    ),
                    modifier = Modifier.testTag("checkbox_item_${transaction.id}")
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = transaction.customerName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isDeposited) Slate700 else Slate900,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Slate100
                            ) {
                                Text(
                                    text = transaction.customerCode,
                                    fontSize = 10.sp,
                                    color = Slate600,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = transaction.packageName,
                            fontSize = 11.sp,
                            color = Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = EscPosHelper.formatRupiah(transaction.totalAmount),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = if (isDeposited) Slate600 else Slate900
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Payment Method Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (transaction.paymentMethod.uppercase()) {
                                "TUNAI" -> Color(0xFFDCFCE7)
                                "QRIS" -> Color(0xFFF3E8FF)
                                else -> Color(0xFFDBEAFE)
                            }
                        ) {
                            Text(
                                text = transaction.paymentMethod.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (transaction.paymentMethod.uppercase()) {
                                    "TUNAI" -> Color(0xFF15803D)
                                    "QRIS" -> Color(0xFF7E22CE)
                                    else -> Color(0xFF1D4ED8)
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Metadata & Status Note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bayar: $paidDateStr • Kasir: ${transaction.cashierName.take(12)}",
                        fontSize = 10.sp,
                        color = Slate400
                    )

                    if (isDeposited) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SuccessGreen.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Sudah Disetor (${transaction.settlementBatchNumber ?: "STR"})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = WarningAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "⏳ Siap Disetor",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // If already deposited, display explicit notice that it cannot be deposited again
                if (isDeposited) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🔒 Terkunci untuk ${transaction.periodMonth}. Tidak dapat disetorkan ulang kecuali tagihan bulan depan.",
                        fontSize = 9.sp,
                        color = Slate400,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySettlementView(periodMonth: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Slate100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Belum Ada Pembayaran di Periode Ini",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Slate800
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tidak ditemukan transaksi pelanggan lunas untuk $periodMonth. Pelanggan yang membayar di Kasir POS akan otomatis muncul di sini.",
                fontSize = 11.sp,
                color = Slate500,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettlementHistoryTabContent(
    uiState: SettlementUiState,
    viewModel: SettlementViewModel,
    onShareWhatsApp: (String) -> Unit
) {
    if (uiState.settlementBatches.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = Slate300,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Belum Ada Riwayat Setoran",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Slate700
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Setoran kasir yang telah dikunci dan diserahkan ke bendahara/owner akan dicatat di sini.",
                    fontSize = 12.sp,
                    color = Slate400,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = uiState.settlementBatches,
                key = { it.id },
                contentType = { "batch_card" }
            ) { batch ->
                SettlementBatchCard(
                    batch = batch,
                    onViewDetails = { viewModel.openBatchDetails(batch) },
                    onPrint = { viewModel.openBatchDetails(batch) },
                    onShareWA = {
                        val text = viewModel.getWhatsAppShareText(batch, emptyList())
                        onShareWhatsApp(text)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettlementBatchCard(
    batch: SettlementBatchEntity,
    onViewDetails: () -> Unit,
    onPrint: () -> Unit,
    onShareWA: () -> Unit
) {
    val dateStr = remember(batch.createdAt) {
        com.example.util.DateFormatter.formatFullDateTime(batch.createdAt)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("batch_card_${batch.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEEF2FF)
                    ) {
                        Text(
                            text = batch.batchNumber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = BluePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = batch.periodMonth,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SuccessGreen.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "✓ Disetorkan",
                        color = SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Total Uang Disetorkan:",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                    Text(
                        text = EscPosHelper.formatRupiah(batch.totalAmount),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Slate900
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tunai: ${EscPosHelper.formatRupiah(batch.totalCashAmount)} • Non-Tunai: ${EscPosHelper.formatRupiah(batch.totalNonCashAmount)}",
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${batch.customerCount} Pelanggan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Slate800
                    )
                    Text(
                        text = dateStr,
                        fontSize = 10.sp,
                        color = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Penyetor: ${batch.cashierName} → ${batch.recipientName}",
                    fontSize = 11.sp,
                    color = Slate600
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = onViewDetails,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Rincian & Cetak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- DIALOGS ----------------

@Composable
private fun DepositConfirmationDialog(
    uiState: SettlementUiState,
    currentStaff: StaffEntity?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRecipientChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Konfirmasi Setoran Kas",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Slate900
                        )
                        Text(
                            text = "Kunci & cocokkan setoran periode ${uiState.periodMonth}",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Summary of Selected
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Jumlah Pelanggan:", fontSize = 12.sp, color = Slate600)
                            Text("${uiState.selectedTransactions.size} Orang", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Uang Tunai (Cash Fisik):", fontSize = 12.sp, color = Slate600)
                            Text(EscPosHelper.formatRupiah(uiState.selectedCashAmount), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF15803D))
                        }
                        if (uiState.selectedNonCashAmount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Non-Tunai (Transfer/QR):", fontSize = 12.sp, color = Slate600)
                                Text(EscPosHelper.formatRupiah(uiState.selectedNonCashAmount), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E40AF))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Slate200)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TOTAL NOMINAL SETORAN:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text(EscPosHelper.formatRupiah(uiState.selectedTotalAmount), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = BluePrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Recipient Selector Chips
                Text("Penerima Setoran:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                Spacer(modifier = Modifier.height(4.dp))

                val adminStaff = remember(uiState.staffList, currentStaff) {
                    uiState.staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) && it.isActive }
                        ?: uiState.staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) }
                        ?: if (currentStaff?.role.equals("ADMIN", ignoreCase = true)) currentStaff else null
                }
                val ownerPreset = if (adminStaff != null) "${adminStaff.name} (Owner)" else "Owner / Admin"
                val recipientPresets = remember(ownerPreset) {
                    listOf(ownerPreset, "Bendahara Kantor", "Kas Besar Bisnis")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    recipientPresets.forEach { preset ->
                        val isSelected = uiState.depositRecipientName == preset
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) BluePrimary.copy(alpha = 0.1f) else Slate100,
                            border = BorderStroke(1.dp, if (isSelected) BluePrimary else Color.Transparent),
                            modifier = Modifier.clickable { onRecipientChanged(preset) }
                        ) {
                            Text(
                                text = preset.substringBefore(" ("),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) BluePrimary else Slate700,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Fields
                OutlinedTextField(
                    value = uiState.depositRecipientName,
                    onValueChange = onRecipientChanged,
                    label = { Text("Nama Penerima (Owner/Bendahara)", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.depositNotes,
                    onValueChange = onNotesChanged,
                    label = { Text("Catatan / Keterangan (Opsional)", fontSize = 12.sp) },
                    placeholder = { Text("cth: Uang tunai fisik pas, diserahkan sore hari", fontSize = 11.sp) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Warning / Info Note
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Setelah disetorkan, data pelanggan ini akan terkunci untuk bulan ${uiState.periodMonth} dan tidak dapat disetorkan ulang.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = !uiState.isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        if (uiState.isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Menyimpan...")
                        } else {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kunci & Setorkan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepositSuccessDialog(
    batch: SettlementBatchEntity,
    items: List<TransactionEntity>,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPrinting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Setoran Berhasil Disimpan!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Slate900
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No. Batch: ${batch.batchNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BluePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total ${batch.customerCount} pelanggan lunas telah dikunci untuk ${batch.periodMonth} dengan total nominal ${EscPosHelper.formatRupiah(batch.totalAmount)}.",
                    fontSize = 12.sp,
                    color = Slate500,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (!isPrinting) {
                            isPrinting = true
                            Toast.makeText(context, "🖨️ Mencetak bukti setoran ke printer...", Toast.LENGTH_SHORT).show()
                            onPrint()
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(2500)
                                isPrinting = false
                            }
                        }
                    },
                    enabled = !isPrinting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary,
                        disabledContainerColor = BluePrimary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mencetak Bukti Setoran...", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cetak Bukti Setoran (58mm)", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onShareWhatsApp,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kirim Rekap ke WhatsApp Owner", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("Selesai & Tutup", color = Slate600)
                }
            }
        }
    }
}

@Composable
private fun BatchDetailDialog(
    batch: SettlementBatchEntity,
    items: List<TransactionEntity>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPrintingDetail by remember { mutableStateOf(false) }
    val dateStr = remember(batch.createdAt) {
        com.example.util.DateFormatter.formatFullDateTime(batch.createdAt)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Rincian Batch Setoran",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Slate900
                        )
                        Text(
                            text = "${batch.batchNumber} • ${batch.periodMonth}",
                            fontSize = 12.sp,
                            color = BluePrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Slate500)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Detail Specs
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Waktu Setor:", fontSize = 11.sp, color = Slate500)
                            Text(dateStr, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate800)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Penyetor:", fontSize = 11.sp, color = Slate500)
                            Text(batch.cashierName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Penerima:", fontSize = 11.sp, color = Slate500)
                            Text(batch.recipientName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Nominal:", fontSize = 11.sp, color = Slate500)
                            Text(EscPosHelper.formatRupiah(batch.totalAmount), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = BluePrimary)
                        }
                        if (batch.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Catatan: ${batch.notes}", fontSize = 11.sp, color = Slate600)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Daftar Pelanggan yang Disetorkan (${items.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate800
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BluePrimary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items) { itm ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(itm.customerName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${itm.customerCode} • ${itm.packageName}", fontSize = 10.sp, color = Slate500)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(EscPosHelper.formatRupiah(itm.totalAmount), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900)
                                        Text(itm.paymentMethod, fontSize = 10.sp, color = Slate500)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Print & WhatsApp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShareWhatsApp,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share WA", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (!isPrintingDetail) {
                                isPrintingDetail = true
                                Toast.makeText(context, "🖨️ Mencetak bukti setoran ke printer...", Toast.LENGTH_SHORT).show()
                                onPrint()
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(2500)
                                    isPrintingDetail = false
                                }
                            }
                        },
                        enabled = !isPrintingDetail,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BluePrimary,
                            disabledContainerColor = BluePrimary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        if (isPrintingDetail) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mencetak...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cetak Struk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
