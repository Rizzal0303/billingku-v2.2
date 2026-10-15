package com.example.ui.settings.tabs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.AnnouncementEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.StaffEntity
import com.example.ui.settings.SettingsUiState
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.*
import com.example.util.DateFormatter
import com.example.util.WhatsAppHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementCenterTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    currentStaff: StaffEntity? = null,
    onShowFeedback: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, GANGGUAN, MAINTENANCE, INFO
    var showCreateEditDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<AnnouncementEntity?>(null) }
    var itemToBroadcast by remember { mutableStateOf<AnnouncementEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<AnnouncementEntity?>(null) }

    val announcements = uiState.announcements
    val activeOutageCount = announcements.count { it.status == "ACTIVE" && it.category == "GANGGUAN" }
    val activeMaintenanceCount = announcements.count { it.status == "ACTIVE" && it.category == "MAINTENANCE" }

    val filteredList = remember(announcements, selectedFilter) {
        when (selectedFilter) {
            "ACTIVE" -> announcements.filter { it.status == "ACTIVE" }
            "GANGGUAN" -> announcements.filter { it.category == "GANGGUAN" }
            "MAINTENANCE" -> announcements.filter { it.category == "MAINTENANCE" }
            "INFO" -> announcements.filter { it.category == "INFO" || it.category == "PROMO" }
            else -> announcements
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------------- 1. STATUS JARINGAN BANNER ----------------
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        activeOutageCount > 0 -> Color(0xFFFEF2F2)
                        activeMaintenanceCount > 0 -> Color(0xFFFFFBEB)
                        else -> Color(0xFFF0FDF4)
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    when {
                        activeOutageCount > 0 -> Color(0xFFFECACA)
                        activeMaintenanceCount > 0 -> Color(0xFFFDE68A)
                        else -> Color(0xFFBBF7D0)
                    }
                ),
                modifier = Modifier.fillMaxWidth().testTag("announcement_status_banner")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            activeOutageCount > 0 -> DangerRed
                            activeMaintenanceCount > 0 -> Color(0xFFD97706)
                            else -> SuccessGreen
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    activeOutageCount > 0 -> Icons.Default.WarningAmber
                                    activeMaintenanceCount > 0 -> Icons.Default.Build
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                activeOutageCount > 0 -> "⚠️ Gangguan Jaringan Aktif ($activeOutageCount Titik)"
                                activeMaintenanceCount > 0 -> "🛠️ Pemeliharaan Jaringan ($activeMaintenanceCount Jadwal)"
                                else -> "✅ Seluruh Jaringan RT/RW Net Beroperasi Normal"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = when {
                                activeOutageCount > 0 -> Color(0xFF991B1B)
                                activeMaintenanceCount > 0 -> Color(0xFF92400E)
                                else -> Color(0xFF166534)
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                activeOutageCount > 0 -> "Pengumuman gangguan sedang ditampilkan di aplikasi pelanggan Net-pay."
                                activeMaintenanceCount > 0 -> "Jadwal maintenance aktif disiarkan ke pelanggan terdampak."
                                else -> "Tidak ada laporan gangguan aktif. Semua pelanggan dapat mengakses internet lancar."
                            },
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                }
            }
        }

        // ---------------- 2. ACTION CONTROLS ----------------
        item {
            Button(
                onClick = {
                    editingItem = null
                    showCreateEditDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_create_announcement")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Buat Pengumuman", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // ---------------- 3. FILTER CHIPS ----------------
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    "ALL" to "Semua (${announcements.size})",
                    "ACTIVE" to "Aktif (${announcements.count { it.status == "ACTIVE" }})",
                    "GANGGUAN" to "Gangguan ($activeOutageCount)",
                    "MAINTENANCE" to "Maintenance ($activeMaintenanceCount)",
                    "INFO" to "Info / Promo"
                )

                items(filters) { (key, label) ->
                    val isSelected = selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimary.copy(alpha = 0.12f),
                            selectedLabelColor = BluePrimary
                        )
                    )
                }
            }
        }

        // ---------------- 4. LIST PENGUMUMAN ----------------
        if (filteredList.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Belum Ada Pengumuman",
                            fontWeight = FontWeight.Bold,
                            color = Slate800,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Klik tombol 'Buat Pengumuman' di atas untuk mengabarkan info gangguan atau jadwal maintenance ke aplikasi pelanggan.",
                            color = Slate500,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.id }) { item ->
                AnnouncementItemCard(
                    item = item,
                    onToggleStatus = {
                        viewModel.toggleAnnouncementStatus(item.id, item.status)
                        onShowFeedback(if (item.status == "ACTIVE") "✓ Pengumuman ditandai SELESAI" else "✓ Pengumuman DIAKTIFKAN kembali")
                    },
                    onBroadcast = { itemToBroadcast = item },
                    onEdit = {
                        editingItem = item
                        showCreateEditDialog = true
                    },
                    onDelete = { itemToDelete = item },
                    onSyncServer = {
                        viewModel.pushAnnouncementToServer(item) { success, msg ->
                            onShowFeedback(msg)
                        }
                    }
                )
            }
        }
    }

    // ---------------- MODAL FORM BUAT / EDIT ----------------
    if (showCreateEditDialog) {
        CreateEditAnnouncementDialog(
            initialItem = editingItem,
            authorName = currentStaff?.name ?: "Admin",
            onDismiss = {
                showCreateEditDialog = false
                editingItem = null
            },
            onSave = { id, title, category, message, targetScope, status, estEnd, author ->
                viewModel.saveAnnouncement(
                    id = id,
                    title = title,
                    category = category,
                    message = message,
                    targetScope = targetScope,
                    status = status,
                    estimatedEnd = estEnd,
                    createdBy = author
                ) { saved ->
                    showCreateEditDialog = false
                    editingItem = null
                    onShowFeedback("✓ Pengumuman '${saved.title}' berhasil disimpan & dipublikasikan!")
                }
            }
        )
    }

    // ---------------- MODAL BROADCAST WHATSAPP ----------------
    itemToBroadcast?.let { item ->
        BroadcastWhatsAppDialog(
            announcement = item,
            repository = viewModel.repository,
            onDismiss = { itemToBroadcast = null },
            onShowFeedback = onShowFeedback
        )
    }

    // ---------------- KONFIRMASI HAPUS ----------------
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text("Hapus Pengumuman?", fontWeight = FontWeight.Bold, color = Slate900)
            },
            text = {
                Text("Pengumuman '${item.title}' akan dihapus dari aplikasi dan server.", color = Slate600)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnnouncement(item) {
                            itemToDelete = null
                            onShowFeedback("✓ Pengumuman telah dihapus")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToDelete = null }, shape = RoundedCornerShape(10.dp)) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }
}

@Composable
private fun AnnouncementItemCard(
    item: AnnouncementEntity,
    onToggleStatus: () -> Unit,
    onBroadcast: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSyncServer: () -> Unit
) {
    val categoryColor = when (item.category) {
        "GANGGUAN" -> DangerRed
        "MAINTENANCE" -> Color(0xFFD97706)
        "PROMO" -> Color(0xFF16A34A)
        else -> BluePrimary
    }

    val categoryBg = when (item.category) {
        "GANGGUAN" -> Color(0xFFFEF2F2)
        "MAINTENANCE" -> Color(0xFFFFFBEB)
        "PROMO" -> Color(0xFFF0FDF4)
        else -> Color(0xFFEFF6FF)
    }

    val categoryLabel = when (item.category) {
        "GANGGUAN" -> "GANGGUAN JARINGAN"
        "MAINTENANCE" -> "MAINTENANCE"
        "PROMO" -> "PROMO"
        else -> "INFORMASI RESMI"
    }

    val isActive = item.status == "ACTIVE"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, if (isActive && item.category == "GANGGUAN") DangerRed.copy(alpha = 0.35f) else Slate200),
        modifier = Modifier.fillMaxWidth().testTag("announcement_item_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Category Pill & Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = categoryBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(categoryColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = categoryLabel,
                            color = categoryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) Color(0xFFDCFCE7) else Slate100
                ) {
                    Text(
                        text = if (isActive) "● AKTIF DI NET-PAY" else "SELESAI",
                        color = if (isActive) Color(0xFF166534) else Slate500,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Slate900
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Message text
            Text(
                text = item.message,
                fontSize = 13.sp,
                color = Slate700,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata info row (Target, Estimasi, Waktu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate100
                ) {
                    Text(
                        text = "🎯 ${if (item.targetScope.isBlank() || item.targetScope.equals("SEMUA", true)) "Semua Pelanggan" else item.targetScope}",
                        fontSize = 11.sp,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                if (item.estimatedEnd.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Text(
                            text = "⏳ Est: ${item.estimatedEnd}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = DateFormatter.formatShortDateTime(item.createdAt),
                    fontSize = 11.sp,
                    color = Slate400
                )
            }

            HorizontalDivider(
                color = Slate100,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Quick broadcast WA
                    FilledTonalButton(
                        onClick = onBroadcast,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFDCFCE7),
                            contentColor = Color(0xFF166534)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Broadcast WA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Toggle Resolved / Active
                    OutlinedButton(
                        onClick = onToggleStatus,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.Check else Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isActive) "Selesaikan" else "Aktifkan", fontSize = 11.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSyncServer, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Sinkronkan", tint = BluePrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Slate600, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = DangerRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEditAnnouncementDialog(
    initialItem: AnnouncementEntity?,
    authorName: String,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var category by remember { mutableStateOf(initialItem?.category ?: "GANGGUAN") }
    var message by remember { mutableStateOf(initialItem?.message ?: "") }
    var targetScope by remember { mutableStateOf(initialItem?.targetScope ?: "SEMUA") }
    var estimatedEnd by remember { mutableStateOf(initialItem?.estimatedEnd ?: "") }
    var status by remember { mutableStateOf(initialItem?.status ?: "ACTIVE") }

    val isEdit = initialItem != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isEdit) "Edit Pengumuman" else "Buat Pengumuman Baru",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Slate900
                            )
                            Text(
                                text = "Akan otomatis tampil di aplikasi pelanggan Net-pay",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Slate500)
                        }
                    }
                }

                // 1. Pilih Kategori
                item {
                    Text("Kategori Pengumuman", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val categories = listOf(
                            "GANGGUAN" to "🚨 Gangguan",
                            "MAINTENANCE" to "🛠️ Maintenance",
                            "INFO" to "📢 Info",
                            "PROMO" to "🎁 Promo"
                        )
                        categories.forEach { (catKey, catTitle) ->
                            val isSel = category == catKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) BluePrimary else Slate100,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        category = catKey
                                        // Auto suggestion template jika kosong
                                        if (message.isBlank()) {
                                            when (catKey) {
                                                "GANGGUAN" -> {
                                                    title = "Kendala Gangguan Jalur Internet"
                                                    message = "Yth. Pelanggan, saat ini sedang terjadi gangguan jaringan pada area distribusi. Tim teknisi sedang melakukan pengecekan dan perbaikan di lokasi."
                                                    estimatedEnd = "1 - 2 Jam"
                                                }
                                                "MAINTENANCE" -> {
                                                    title = "Jadwal Pemeliharaan Jaringan (Maintenance)"
                                                    message = "Diberitahukan bahwa akan diadakan optimalisasi router & server utama. Koneksi internet mungkin terputus sementara waktu."
                                                    estimatedEnd = "01:00 - 03:00 WIB"
                                                }
                                                "INFO" -> {
                                                    title = "Informasi Layanan RT/RW Net"
                                                    message = "Pengingat pembayaran tagihan bulanan internet dapat dilakukan via QRIS atau transfer bank di aplikasi Net-pay."
                                                    estimatedEnd = ""
                                                }
                                                "PROMO" -> {
                                                    title = "Promo Upgrade Kecepatan Internet"
                                                    message = "Dapatkan diskon khusus untuk upgrade paket kecepatan WiFi bulan ini. Hubungi admin untuk detailnya!"
                                                    estimatedEnd = ""
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = catTitle,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color.White else Slate700
                                    )
                                }
                            }
                        }
                    }
                }

                // Preset Buttons
                item {
                    Text("Template Cepat:", fontSize = 11.sp, color = Slate500)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val templates = listOf(
                            "Kabel FO Putus" to ("Kendala Kabel FO Putus" to "Yth. Pelanggan, terjadi kabel FO putus tertabrak truk/pohon. Tim sedang melakukan penyambungan (splicing) darurat."),
                            "Router Pusat Down" to ("Gangguan Server Utama" to "Yth. Pelanggan, terjadi kendala suplai daya pada perangkat pusat. Tim teknisi sedang menyalakan sistem genset cadangan."),
                            "Maintenance Malam" to ("Maintenance Berkala" to "Diberitahukan perbaikan server akan dilakukan malam ini mulai jam 01:00 WIB agar tidak mengganggu aktivitas kerja di siang hari.")
                        )
                        items(templates) { (label, data) ->
                            OutlinedButton(
                                onClick = {
                                    title = data.first
                                    message = data.second
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // 2. Input Judul
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Judul Pengumuman *") },
                        placeholder = { Text("Contoh: Gangguan Kabel FO Jalur Timur") },
                        modifier = Modifier.fillMaxWidth().testTag("input_announcement_title"),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                // 3. Input Pesan Detail
                item {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Isi Pesan / Penjelasan Detail *") },
                        placeholder = { Text("Tuliskan kronologi, penanganan, atau informasi yang jelas untuk pelanggan...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("input_announcement_message"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // 4. Target & Estimasi Selesai
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = targetScope,
                            onValueChange = { targetScope = it },
                            label = { Text("Target Pelanggan") },
                            placeholder = { Text("SEMUA atau ODP-1") },
                            modifier = Modifier.weight(1f).testTag("input_announcement_target"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = estimatedEnd,
                            onValueChange = { estimatedEnd = it },
                            label = { Text("Estimasi Selesai") },
                            placeholder = { Text("16:00 WIB / 2 Jam") },
                            modifier = Modifier.weight(1f).testTag("input_announcement_est_end"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }

                // 5. Status Aktif / Nonaktif
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Publikasikan ke Aplikasi Net-pay", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800)
                            Text("Pelanggan dapat langsung melihat info ini", fontSize = 11.sp, color = Slate500)
                        }
                        Switch(
                            checked = status == "ACTIVE",
                            onCheckedChange = { status = if (it) "ACTIVE" else "RESOLVED" }
                        )
                    }
                }

                // Buttons
                item {
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
                            onClick = {
                                if (title.isBlank()) return@Button
                                onSave(
                                    initialItem?.id ?: 0L,
                                    title,
                                    category,
                                    message,
                                    targetScope,
                                    status,
                                    estimatedEnd,
                                    authorName
                                )
                            },
                            enabled = title.isNotBlank() && message.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.5f).testTag("btn_save_announcement")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan & Siarkan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastWhatsAppDialog(
    announcement: AnnouncementEntity,
    repository: com.example.data.repository.BillingRepository,
    onDismiss: () -> Unit,
    onShowFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val activeCustomers = remember(customers) { customers.filter { it.status == "ACTIVE" } }

    var searchQuery by remember { mutableStateOf("") }
    val filteredCustomers = remember(activeCustomers, searchQuery) {
        if (searchQuery.isBlank()) activeCustomers
        else activeCustomers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery) ||
                    it.odpLocation.contains(searchQuery, ignoreCase = true)
        }
    }

    val broadcastMessage = remember(announcement) {
        val catHeader = when (announcement.category) {
            "GANGGUAN" -> "🚨 PEMBERITAHUAN GANGGUAN JARINGAN"
            "MAINTENANCE" -> "🛠️ PEMBERITAHUAN PEMELIHARAAN (MAINTENANCE)"
            "PROMO" -> "🎁 PROMO RESMI RT/RW NET"
            else -> "📢 INFORMASI RESMI RT/RW NET"
        }

        buildString {
            appendLine("*$catHeader*")
            appendLine("----------------------------------------")
            appendLine("*Judul:* ${announcement.title}")
            appendLine()
            appendLine("Yth. Pelanggan Setia RT/RW Net,")
            appendLine()
            appendLine(announcement.message)
            appendLine()
            if (announcement.estimatedEnd.isNotBlank()) {
                appendLine("⏳ *Estimasi Pemulihan:* ${announcement.estimatedEnd}")
            }
            if (announcement.targetScope.isNotBlank() && !announcement.targetScope.equals("SEMUA", true)) {
                appendLine("📍 *Area Terdampak:* ${announcement.targetScope}")
            }
            appendLine("----------------------------------------")
            appendLine("Mohon maaf atas ketidaknyamanan yang ditimbulkan.")
            appendLine("Hormat kami,")
            appendLine("_Admin & Tim Teknis RT/RW Net_")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Broadcast WhatsApp", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
                            Text("Siarkan pengumuman ke pelanggan", fontSize = 11.sp, color = Slate500)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Slate500)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preview Box & Copy Button
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate50),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Format Teks Pesan WhatsApp:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate700)
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Broadcast RT/RW Net", broadcastMessage))
                                    Toast.makeText(context, "✓ Teks broadcast berhasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                    onShowFeedback("✓ Teks broadcast berhasil disalin")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salin Pesan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = broadcastMessage.take(220) + if (broadcastMessage.length > 220) "..." else "",
                            fontSize = 11.sp,
                            color = Slate600,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search customer
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama, WhatsApp, atau ODP...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Kirim ke Kontak Pelanggan (${filteredCustomers.size} Pelanggan Aktif):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Slate700
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Customer list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCustomers, key = { it.id }) { customer ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                    Text(
                                        text = "${customer.customerCode} • ${customer.phone.ifBlank { "Tidak ada no WA" }}",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                    if (customer.odpLocation.isNotBlank()) {
                                        Text(text = "ODP: ${customer.odpLocation}", fontSize = 10.sp, color = Slate400)
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (customer.phone.isBlank()) {
                                            Toast.makeText(context, "Pelanggan ini tidak memiliki nomor HP", Toast.LENGTH_SHORT).show()
                                        } else {
                                            WhatsAppHelper.sendWhatsAppMessage(context, customer.phone, broadcastMessage)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kirim WA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

