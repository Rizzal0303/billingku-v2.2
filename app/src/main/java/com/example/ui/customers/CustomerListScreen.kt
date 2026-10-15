package com.example.ui.customers

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.util.WhatsAppHelper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CustomerEntity
import com.example.printer.EscPosHelper
import com.example.ui.components.AdminPinPromptDialog
import com.example.ui.components.PullRefreshWrapper
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    viewModel: CustomerViewModel,
    isAdmin: Boolean = true,
    onVerifyAdminPin: ((String) -> Boolean)? = null,
    onNavigateToCashier: (Long) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    if (onBackClick != null) {
        BackHandler { onBackClick() }
    }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerPendingAuthDelete by remember { mutableStateOf<CustomerEntity?>(null) }

    if (customerPendingAuthDelete != null) {
        AdminPinPromptDialog(
            actionTitle = "Otorisasi Hapus Pelanggan",
            actionDescription = "Staff kasir tidak memiliki izin menghapus data pelanggan. Masukkan PIN Admin untuk verifikasi supervisi:",
            onDismiss = { customerPendingAuthDelete = null },
            onVerify = { pin -> onVerifyAdminPin?.invoke(pin) ?: false },
            onSuccess = {
                val target = customerPendingAuthDelete
                customerPendingAuthDelete = null
                if (target != null) {
                    customerToDelete = target
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
                            modifier = Modifier.testTag("btn_customer_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Slate800
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Kelola Pelanggan WiFi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            text = "Total ${uiState.customers.size} Pelanggan Terdaftar",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate50)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = BluePrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Tambah Pelanggan", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_add_customer")
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
                // Search Input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Cari nama, ID, WA, atau alamat...", fontSize = 13.sp) },
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
                    .testTag("customer_search_field"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "Semua",
                    "ACTIVE" to "Aktif",
                    "ISOLATED" to "Isolir",
                    "INACTIVE" to "Nonaktif"
                ).forEach { (key, label) ->
                    val isSelected = uiState.selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onFilterSelected(key) },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimaryLight,
                            selectedLabelColor = BluePrimaryDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Customer List
            if (uiState.filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tidak Ada Data Pelanggan",
                            fontWeight = FontWeight.Bold,
                            color = Slate700,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Coba ubah kata kunci pencarian atau tambah baru.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(
                        items = uiState.filteredCustomers,
                        key = { it.id },
                        contentType = { "customer_card" }
                    ) { customer ->
                        CustomerCardItem(
                            customer = customer,
                            isAdmin = isAdmin,
                            onEditClick = { viewModel.openEditDialog(customer) },
                            onDeleteClick = {
                                if (isAdmin) {
                                    customerToDelete = customer
                                } else {
                                    customerPendingAuthDelete = customer
                                }
                            },
                            onToggleStatus = { viewModel.toggleCustomerStatus(customer) },
                            onChatWa = {
                                val msg = "Halo Bpk/Ibu *${customer.name}* (ID: ${customer.customerCode}),\nKami dari layanan WiFi Billingku. Ada yang bisa kami bantu terkait koneksi internet Anda?"
                                WhatsAppHelper.sendWhatsAppMessage(context, customer.phone, msg)
                            }
                        )
                    }
                }
            }
        }
        }
    }

    // Add / Edit Dialog
    if (uiState.isFormOpen) {
        AddEditCustomerDialog(
            customer = uiState.editingCustomer,
            availablePackages = uiState.availablePackages,
            onDismiss = { viewModel.closeDialog() },
            onSave = { code, name, phone, address, odp, packageId, dueDay, ip, status ->
                viewModel.saveCustomer(code, name, phone, address, odp, packageId, dueDay, ip, status)
            }
        )
    }

    // Delete Confirmation Dialog
    customerToDelete?.let { customer ->
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text("Hapus Pelanggan?") },
            text = { Text("Apakah Anda yakin ingin menghapus data pelanggan ${customer.name} (${customer.customerCode})? Data tidak dapat dikembalikan.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer)
                        customerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun CustomerCardItem(
    customer: CustomerEntity,
    isAdmin: Boolean = true,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onChatWa: () -> Unit
) {
    val context = LocalContext.current
    val avatarGradient = remember(customer.name) {
        val colorPairs = listOf(
            listOf(BluePrimary, ElectricIndigo),
            listOf(SuccessGreen, Color(0xFF059669)),
            listOf(WarningAmber, Color(0xFFD97706)),
            listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
            listOf(CyanAccentDark, Color(0xFF0369A1))
        )
        val idx = (customer.name.hashCode() and 0x7FFFFFFF) % colorPairs.size
        colorPairs[idx]
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_card_${customer.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(avatarGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customer.name.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = customer.name,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Slate100
                            ) {
                                Text(
                                    text = customer.customerCode,
                                    color = Slate700,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (customer.phone.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "•",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = customer.phone,
                                    color = Slate500,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (customer.address.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = customer.address,
                                color = Slate600,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                StatusBadge(status = customer.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BluePrimaryLight
                        ) {
                            Text(
                                text = customer.packageName,
                                color = BluePrimaryDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Jatuh Tempo Tgl ${customer.dueDay}",
                            color = Slate600,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (customer.odpLocation.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "ODP: ${customer.odpLocation}",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = EscPosHelper.formatRupiah(customer.monthlyFee),
                        color = BluePrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "/ bulan",
                        color = Slate400,
                        fontSize = 10.sp
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate100)

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = onChatWa,
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = WhatsAppGreenLight)
                    ) {
                        Icon(imageVector = Icons.Default.Chat, contentDescription = "WA", tint = WhatsAppGreen, modifier = Modifier.size(19.dp))
                    }
                    FilledTonalIconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Slate100)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Slate700, modifier = Modifier.size(18.dp))
                    }
                    FilledTonalIconButton(
                        onClick = onToggleStatus,
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (customer.status == "ACTIVE") DangerRedLight else SuccessGreenLight
                        )
                    ) {
                        Icon(
                            imageVector = if (customer.status == "ACTIVE") Icons.Default.Block else Icons.Default.Check,
                            contentDescription = "Isolir / Aktifkan",
                            tint = if (customer.status == "ACTIVE") DangerRed else SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isAdmin) DangerRedLight else Slate100
                        )
                    ) {
                        Icon(
                            imageVector = if (isAdmin) Icons.Default.Delete else Icons.Default.Lock,
                            contentDescription = if (isAdmin) "Hapus" else "Hapus (Perlu PIN Admin)",
                            tint = if (isAdmin) DangerRed else Slate500,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCustomerDialog(
    customer: CustomerEntity?,
    availablePackages: List<com.example.data.local.entity.PackagePlanEntity>,
    onDismiss: () -> Unit,
    onSave: (code: String, name: String, phone: String, address: String, odp: String, packageId: Long, dueDay: Int, ip: String, status: String) -> Unit
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf(customer?.customerCode ?: "") }
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var address by remember { mutableStateOf(customer?.address ?: "") }
    var odp by remember { mutableStateOf(customer?.odpLocation ?: "") }
    var ip by remember { mutableStateOf(customer?.ipOrPppoe ?: "") }
    var dueDayStr by remember { mutableStateOf(customer?.dueDay?.toString() ?: "10") }
    var status by remember { mutableStateOf(customer?.status ?: "ACTIVE") }

    val defaultPackageId = customer?.packageId ?: availablePackages.firstOrNull()?.id ?: 1L
    var selectedPackageId by remember { mutableLongStateOf(defaultPackageId) }
    var packageDropdownExpanded by remember { mutableStateOf(false) }

    val selectedPlan = availablePackages.firstOrNull { it.id == selectedPackageId }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSaving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("modal_add_edit_customer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header modal identik dengan kasir
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (customer != null) "Edit Data Pelanggan" else "Pendaftaran Pelanggan Baru",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = if (customer != null) "ID: ${customer.customerCode} • Status: ${customer.status}" else "Entri Baru Sistem Billing RT/RW Net",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Slate600)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Banner Ringkasan Pelanggan (Identik dengan Pelanggan Banner Kasir)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BluePrimaryLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name.ifBlank { "Nama Pelanggan" },
                            fontWeight = FontWeight.Bold,
                            color = BluePrimaryDark,
                            fontSize = 16.sp
                        )
                        Surface(shape = RoundedCornerShape(6.dp), color = Color.White) {
                            Text(
                                text = code.ifBlank { "AUTO-ID" },
                                color = BluePrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${selectedPlan?.name ?: "Pilih Paket"} (${selectedPlan?.speedMbps ?: 0} Mbps) • ${EscPosHelper.formatRupiah(selectedPlan?.price ?: 0L)} / bln",
                        color = Slate700,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Jatuh Tempo: Tgl ${dueDayStr.ifBlank { "10" }} tiap bulan",
                            color = Slate600,
                            fontSize = 11.sp
                        )
                        if (odp.isNotBlank()) {
                            Text(
                                text = "ODP: $odp",
                                color = Slate600,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Data Identitas & Kontak
            Text(
                text = "Identitas & Kontak Pelanggan",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Slate800
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama Lengkap Pelanggan *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(20.dp)) },
                placeholder = { Text("Contoh: Bpk. Bambang Sutrisno") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Kode Pelanggan") },
                    placeholder = { Text("Auto: NET-xxxxxx") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Slate500, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("No. WhatsApp *") },
                    placeholder = { Text("0812xxxx") },
                    leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Alamat Pemasangan *") },
                placeholder = { Text("Jl. Merpati No. 12 RT 03 / RW 05") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = Slate500, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Pilihan Paket Internet
            Text(
                text = "Pilih Paket Internet Langganan",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Slate800
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = packageDropdownExpanded,
                onExpandedChange = { packageDropdownExpanded = !packageDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedPlan?.let { "${it.name} (${it.speedMbps} Mbps) • ${EscPosHelper.formatRupiah(it.price)}/bln" } ?: "Pilih Paket Internet",
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(20.dp)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = packageDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Slate50,
                        unfocusedContainerColor = Slate50
                    )
                )

                ExposedDropdownMenu(
                    expanded = packageDropdownExpanded,
                    onDismissRequest = { packageDropdownExpanded = false }
                ) {
                    availablePackages.forEach { plan ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${plan.name} (${plan.speedMbps} Mbps)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${EscPosHelper.formatRupiah(plan.price)}/bulan",
                                        fontSize = 11.sp,
                                        color = BluePrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            },
                            onClick = {
                                selectedPackageId = plan.id
                                packageDropdownExpanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 3: Data Teknis & Jatuh Tempo
            Text(
                text = "Pengaturan Teknis & Jatuh Tempo",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Slate800
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dueDayStr,
                    onValueChange = { dueDayStr = it },
                    label = { Text("Jatuh Tempo (Tgl)") },
                    placeholder = { Text("1-28") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Slate500, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = odp,
                    onValueChange = { odp = it },
                    label = { Text("Box ODP / Port") },
                    placeholder = { Text("ODP-01 Port 3") },
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null, tint = Slate500, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("Akun PPPoE / IP Static (Opsional)") },
                placeholder = { Text("user_bambang@net / 192.168.10.25") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = Slate500, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Status Pelanggan (Chips Style seperti Metode Bayar Kasir)
            Text(
                text = "Status Pelanggan",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Slate800
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("ACTIVE", "AKTIF", SuccessGreen),
                    Triple("ISOLATED", "ISOLIR", WarningAmber),
                    Triple("INACTIVE", "NONAKTIF", DangerRed)
                ).forEach { (statKey, statLabel, statColor) ->
                    val isSelected = status == statKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) statColor else Slate100)
                            .clickable { status = statKey }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statLabel,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Slate700,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Slate200)

            // Total Tagihan Bulanan (Identik dengan TOTAL PEMBAYARAN di Kasir)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TAGIHAN BULANAN",
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Iuran per ${dueDayStr.ifBlank { "10" }} tiap bulan",
                        color = Slate500,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = EscPosHelper.formatRupiah(selectedPlan?.price ?: 0L),
                    fontWeight = FontWeight.ExtraBold,
                    color = BluePrimary,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (customer == null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BluePrimaryLight.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Tagihan pertama dimulai sejak ${com.example.util.PeriodUtils.getCurrentPeriod()} (tanpa tagihan bulan lampau).",
                            fontSize = 11.sp,
                            color = BluePrimaryDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tombol Simpan / Tambah Pelanggan (Material 3 Button)
            Button(
                onClick = {
                    if (isSaving) return@Button
                    if (name.isBlank()) {
                        Toast.makeText(context, "Nama pelanggan wajib diisi!", Toast.LENGTH_SHORT).show()
                    } else if (phone.isBlank()) {
                        Toast.makeText(context, "Nomor WhatsApp wajib diisi!", Toast.LENGTH_SHORT).show()
                    } else {
                        isSaving = true
                        val parsedDueDay = dueDayStr.toIntOrNull() ?: 10
                        onSave(code, name, phone, address, odp, selectedPackageId, parsedDueDay, ip, status)
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_save_customer"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BluePrimary,
                    contentColor = Color.White,
                    disabledContainerColor = Slate300,
                    disabledContentColor = Slate500
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (customer != null) Icons.Default.Check else Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (customer != null) "Simpan Perubahan" else "Tambah Pelanggan",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_cancel_customer"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Slate600
                )
            ) {
                Text("Batal", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

