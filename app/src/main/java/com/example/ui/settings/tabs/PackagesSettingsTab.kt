package com.example.ui.settings.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.PackagePlanEntity
import com.example.printer.EscPosHelper
import com.example.ui.components.SwipeToConfirmButton
import com.example.ui.settings.SettingsUiState
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.*

@Composable
fun PackagesSettingsTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daftar Paket Internet (${uiState.packages.size})",
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Pilihan paket langganan bagi pelanggan Anda",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = { viewModel.openAddPackageDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (uiState.packages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Slate300, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada paket internet", fontWeight = FontWeight.SemiBold, color = Slate700)
                            Text("Klik tombol Tambah untuk membuat paket baru", color = Slate400, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(uiState.packages) { plan ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = plan.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BluePrimaryLight
                                    ) {
                                        Text(
                                            text = "${plan.speedMbps} Mbps",
                                            color = BluePrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${EscPosHelper.formatRupiah(plan.price)} / bulan",
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                if (plan.description.isNotEmpty()) {
                                    Text(
                                        text = plan.description,
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = { viewModel.openEditPackageDialog(plan) }) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Slate600)
                                }
                                IconButton(onClick = { viewModel.deletePackage(plan) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus", tint = DangerRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isPackageDialogOpen) {
        PackageDialog(
            plan = uiState.editingPackage,
            onDismiss = { viewModel.closePackageDialog() },
            onSave = { name, speed, price, desc ->
                viewModel.savePackage(name, speed, price, desc)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageDialog(
    plan: PackagePlanEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, speed: Int, price: Long, desc: String) -> Unit
) {
    var name by remember { mutableStateOf(plan?.name ?: "") }
    var speedStr by remember { mutableStateOf(plan?.speedMbps?.toString() ?: "10") }
    var priceStr by remember { mutableStateOf(plan?.price?.toString() ?: "150000") }
    var desc by remember { mutableStateOf(plan?.description ?: "") }

    val speedInt = speedStr.toIntOrNull() ?: 10
    val priceLong = priceStr.toLongOrNull() ?: 150000L

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isConfirmed by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("modal_package_plan_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header identik kasir
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (plan != null) "Edit Paket Internet" else "Tambah Paket Internet Baru",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "Konfigurasi bandwidth & tarif langganan",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Slate600)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Banner Preview Paket (Identik Banner Kasir)
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
                            text = name.ifBlank { "Nama Paket Baru" },
                            fontWeight = FontWeight.Bold,
                            color = BluePrimaryDark,
                            fontSize = 16.sp
                        )
                        Surface(shape = RoundedCornerShape(6.dp), color = Color.White) {
                            Text(
                                text = "$speedInt Mbps",
                                color = BluePrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${EscPosHelper.formatRupiah(priceLong)} / bulan",
                        color = SuccessGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (desc.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = desc,
                            color = Slate600,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nama Paket
            Text("Nama Paket Internet", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama Paket *") },
                placeholder = { Text("Contoh: Paket Home Lite, Paket Bisnis 20M") },
                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Kecepatan Bandwidth & Preset Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kecepatan (Bandwidth)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800)
                // Preset Speed Chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 20, 30, 50).forEach { spd ->
                        FilterChip(
                            selected = speedInt == spd,
                            onClick = { speedStr = spd.toString() },
                            label = { Text("${spd}M", fontSize = 11.sp) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = speedStr,
                onValueChange = { speedStr = it },
                label = { Text("Kecepatan Bandwidth (Mbps) *") },
                placeholder = { Text("10") },
                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = Slate600, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Harga Bulanan & Preset Price Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tarif Iuran Bulanan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800)
                // Preset Price Chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100000L, 150000L, 200000L).forEach { pr ->
                        FilterChip(
                            selected = priceLong == pr,
                            onClick = { priceStr = pr.toString() },
                            label = { Text("${pr / 1000}k", fontSize = 11.sp) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = priceStr,
                onValueChange = { priceStr = it },
                label = { Text("Harga Bulanan (Rp) *") },
                placeholder = { Text("150000") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Deskripsi Tambahan
            Text("Keterangan Tambahan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Slate800)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Keterangan (Opsional)") },
                placeholder = { Text("Contoh: Unlimited FUP, IP Statis, Free Router ONT") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = Slate500, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Slate200)

            // Total Biaya / Bulan (Identik dengan TOTAL PEMBAYARAN kasir)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TARIF BULANAN",
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Speed: $speedInt Mbps",
                        color = Slate500,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = EscPosHelper.formatRupiah(priceLong),
                    fontWeight = FontWeight.ExtraBold,
                    color = BluePrimary,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Geser untuk Simpan Paket (Identik SwipeToConfirm Kasir)
            SwipeToConfirmButton(
                text = if (plan != null) "Geser untuk Simpan Perubahan »" else "Geser untuk Tambah Paket »",
                confirmedText = "✓ Paket Internet Berhasil Disimpan",
                isProcessing = false,
                isConfirmed = isConfirmed,
                onConfirmed = {
                    if (name.isBlank()) {
                        // nama wajib diisi
                    } else {
                        isConfirmed = true
                        onSave(name, speedInt, priceLong, desc)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Tutup", color = Slate500, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

