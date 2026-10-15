package com.example.ui.history

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.util.WhatsAppHelper
import com.example.ui.components.PullRefreshWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TransactionEntity
import com.example.printer.EscPosHelper
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import com.example.ui.components.AdminPinPromptDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    isAdmin: Boolean = true,
    onVerifyAdminPin: ((String) -> Boolean)? = null,
    onBackClick: (() -> Unit)? = null
) {
    if (onBackClick != null) {
        BackHandler { onBackClick() }
    }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var transactionPendingAuth by remember { mutableStateOf<TransactionEntity?>(null) }

    if (transactionPendingAuth != null) {
        AdminPinPromptDialog(
            actionTitle = "Otorisasi Hapus Faktur",
            actionDescription = "Staff kasir tidak memiliki wewenang untuk membatalkan atau menghapus riwayat transaksi. Masukkan PIN Admin untuk supervisi:",
            onDismiss = { transactionPendingAuth = null },
            onVerify = { pin -> onVerifyAdminPin?.invoke(pin) ?: false },
            onSuccess = {
                val target = transactionPendingAuth
                transactionPendingAuth = null
                if (target != null) {
                    viewModel.confirmDeleteTransaction(target)
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("btn_history_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Dashboard",
                                tint = Slate800
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Riwayat Transaksi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
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
                viewModel.refreshFromMysql { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Cari nama, invoice, ID, paket...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Slate400)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Method Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "Semua",
                    "TUNAI" to "Tunai",
                    "TRANSFER" to "Transfer",
                    "QRIS" to "QRIS"
                ).forEach { (methodKey, label) ->
                    val isSelected = uiState.methodFilter == methodKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onMethodFilterSelected(methodKey) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimaryLight,
                            selectedLabelColor = BluePrimaryDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transactions List
            if (uiState.filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Belum Ada Riwayat Transaksi",
                            fontWeight = FontWeight.Bold,
                            color = Slate700,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Transaksi yang diproses via Kasir POS akan muncul di sini.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 10.dp)
                ) {
                    items(
                        items = uiState.filteredTransactions,
                        key = { it.id },
                        contentType = { "history_card" }
                    ) { trx ->
                        HistoryItemCard(
                            transaction = trx,
                            isAdmin = isAdmin,
                            onClickCard = { viewModel.openInvoiceDetail(trx) },
                            onPrintReceipt = { viewModel.printReceipt(trx) },
                            onDelete = {
                                if (isAdmin) {
                                    viewModel.confirmDeleteTransaction(trx)
                                } else {
                                    transactionPendingAuth = trx
                                }
                            },
                            onContactWhatsApp = {
                                coroutineScope.launch {
                                    val phone = viewModel.getCustomerPhone(trx.customerId)
                                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    val dateStr = sdf.format(Date(trx.paidAt))
                                    val msg = "*BUKTI PEMBAYARAN INTERNET WIFI (LUNAS)*\n" +
                                            "=============================\n" +
                                            "No. Invoice : ${trx.invoiceNumber}\n" +
                                            "Waktu Bayar : $dateStr\n" +
                                            "Pelanggan   : ${trx.customerName} (${trx.customerCode})\n" +
                                            "Periode     : ${trx.periodMonth}\n" +
                                            "Paket WiFi  : ${trx.packageName}\n" +
                                            "Subtotal    : ${EscPosHelper.formatRupiah(trx.monthlyFee)}\n" +
                                            (if (trx.adminFee > 0) "Admin Kasir : ${EscPosHelper.formatRupiah(trx.adminFee)}\n" else "") +
                                            (if (trx.discount > 0) "Diskon      : -${EscPosHelper.formatRupiah(trx.discount)}\n" else "") +
                                            "-----------------------------\n" +
                                            "*TOTAL BAYAR: ${EscPosHelper.formatRupiah(trx.totalAmount)}*\n" +
                                            "Metode      : ${trx.paymentMethod}\n" +
                                            "Petugas     : ${trx.cashierName}\n" +
                                            "Status      : LUNAS [SUCCESS]\n" +
                                            "=============================\n" +
                                            "Terima kasih atas pembayaran Anda. Simpan pesan ini sebagai bukti pembayaran sah."

                                    WhatsAppHelper.sendWhatsAppMessage(context, phone, msg)
                                }
                            }
                        )
                    }
                }
            }
        }
        }
    }

    // Modal Konfirmasi Hapus Transaksi
    uiState.transactionToDelete?.let { trx ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = DangerRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hapus Riwayat Transaksi?")
                }
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus transaksi ${trx.invoiceNumber} atas nama ${trx.customerName} (${EscPosHelper.formatRupiah(trx.totalAmount)})? Status tagihan pelanggan akan kembali menjadi belum lunas."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeDeleteTransaction() },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Hapus Transaksi", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Batal")
                }
            }
        )
    }

    // Modal Struk Preview Cetak Ulang (Elegan & Lengkap)
    uiState.selectedTransactionForReceipt?.let { data ->
        var customerPhone by remember(data.invoiceNo) { mutableStateOf<String?>(null) }
        LaunchedEffect(data.invoiceNo) {
            val trx = uiState.transactions.firstOrNull { it.invoiceNumber == data.invoiceNo }
            if (trx != null) {
                customerPhone = viewModel.getCustomerPhone(trx.customerId)
            }
        }

        ReceiptPreviewDialog(
            receiptData = data,
            isNewlyPaid = false,
            customerPhone = customerPhone,
            onDismiss = { viewModel.dismissReceiptDialog() },
            onPrintAgain = {
                coroutineScope.launch {
                    val trx = uiState.transactions.firstOrNull { it.invoiceNumber == data.invoiceNo }
                    trx?.let { viewModel.printReceipt(it) }
                }
            },
            onDone = {
                viewModel.dismissReceiptDialog()
            }
        )
    }
}

@Composable
fun HistoryItemCard(
    transaction: TransactionEntity,
    isAdmin: Boolean = true,
    onClickCard: () -> Unit,
    onPrintReceipt: () -> Unit,
    onDelete: () -> Unit,
    onContactWhatsApp: () -> Unit
) {
    val dateStr = remember(transaction.paidAt) {
        com.example.util.DateFormatter.formatFullDateTime(transaction.paidAt)
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPrintingThisItem by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickCard() }
            .testTag("history_item_${transaction.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Customer & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuccessGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SuccessGreenDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = transaction.customerName,
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
                                    text = transaction.customerCode,
                                    color = Slate700,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${transaction.invoiceNumber} • $dateStr",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (transaction.paymentMethod) {
                        "TUNAI" -> SuccessGreenLight
                        "TRANSFER" -> BluePrimaryLight
                        else -> Color(0xFFEDE9FE)
                    },
                    border = BorderStroke(
                        1.dp,
                        when (transaction.paymentMethod) {
                            "TUNAI" -> SuccessGreen.copy(alpha = 0.3f)
                            "TRANSFER" -> BluePrimary.copy(alpha = 0.3f)
                            else -> Color(0xFF6D28D9).copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Text(
                        text = transaction.paymentMethod,
                        color = when (transaction.paymentMethod) {
                            "TUNAI" -> SuccessGreenDark
                            "TRANSFER" -> BluePrimaryDark
                            else -> Color(0xFF6D28D9)
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Paket & Nominal Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Paket: ${transaction.packageName}",
                        color = Slate700,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Periode: ${transaction.periodMonth} • Kasir: ${transaction.cashierName}",
                        color = Slate400,
                        fontSize = 11.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = EscPosHelper.formatRupiah(transaction.totalAmount),
                        fontWeight = FontWeight.ExtraBold,
                        color = SuccessGreen,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Lunas",
                        color = SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Slate100)

            // 3 REQUESTED ACTION BUTTONS: CETAK STRUK, HUBUNGI WA, HAPUS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action 1: Cetak Struk (Anti Double-Click & Loading State)
                Button(
                    onClick = {
                        if (!isPrintingThisItem) {
                            isPrintingThisItem = true
                            Toast.makeText(context, "🖨️ Mengirim struk ke printer thermal...", Toast.LENGTH_SHORT).show()
                            onPrintReceipt()
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(2500)
                                isPrintingThisItem = false
                            }
                        }
                    },
                    enabled = !isPrintingThisItem,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary,
                        disabledContainerColor = BluePrimary.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (isPrintingThisItem) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cetak...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cetak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Action 2: Hubungi WA
                FilledTonalButton(
                    onClick = onContactWhatsApp,
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = WhatsAppGreenLight,
                        contentColor = Color(0xFF065F46)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(15.dp), tint = WhatsAppGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kirim WA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Action 3: Hapus
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isAdmin) DangerRedLight else Slate100,
                        contentColor = if (isAdmin) DangerRed else Slate500
                    )
                ) {
                    Icon(
                        imageVector = if (isAdmin) Icons.Default.Delete else Icons.Default.Lock,
                        contentDescription = if (isAdmin) "Hapus" else "Hapus (Perlu PIN Admin)",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
