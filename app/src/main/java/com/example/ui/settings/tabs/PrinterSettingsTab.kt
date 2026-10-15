package com.example.ui.settings.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.StaffEntity
import com.example.printer.PrinterStatus
import com.example.printer.EscPosHelper
import com.example.ui.settings.SettingsUiState
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.*

@Composable
fun PrinterSettingsTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onShowFeedback: (String) -> Unit
) {
    var businessName by remember(uiState.businessName) { mutableStateOf(uiState.businessName) }
    var businessAddress by remember(uiState.businessAddress) { mutableStateOf(uiState.businessAddress) }
    var businessPhone by remember(uiState.businessPhone) { mutableStateOf(uiState.businessPhone) }
    var footerMessage by remember(uiState.footerMessage) { mutableStateOf(uiState.footerMessage) }
    var template by remember(uiState.receiptTemplate) { mutableStateOf(uiState.receiptTemplate) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
    ) {
        // Section A: Bluetooth Hardware
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(BluePrimaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Printer Thermal Bluetooth (58mm)",
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "ESC/POS Mobile Printer",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.refreshPairedPrinters() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = BluePrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when (uiState.printerStatus) {
                            is PrinterStatus.Connected, is PrinterStatus.Success -> SuccessGreenLight
                            is PrinterStatus.Error -> DangerRedLight
                            else -> Slate100
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (uiState.printerStatus) {
                                    is PrinterStatus.Connected, is PrinterStatus.Success -> Icons.Default.CheckCircle
                                    is PrinterStatus.Error -> Icons.Default.Error
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = when (uiState.printerStatus) {
                                    is PrinterStatus.Connected, is PrinterStatus.Success -> SuccessGreen
                                    is PrinterStatus.Error -> DangerRed
                                    else -> Slate600
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (val s = uiState.printerStatus) {
                                    is PrinterStatus.Connected -> "Tersambung ke ${s.deviceName}"
                                    is PrinterStatus.Connecting -> "Menghubungkan ke printer..."
                                    is PrinterStatus.Printing -> "Sedang mengirim data struk..."
                                    is PrinterStatus.Success -> s.message
                                    is PrinterStatus.Error -> s.errorMessage
                                    else -> if (uiState.selectedPrinterName != null) "Dipilih: ${uiState.selectedPrinterName}" else "Belum ada printer Bluetooth dipilih"
                                },
                                color = when (uiState.printerStatus) {
                                    is PrinterStatus.Connected, is PrinterStatus.Success -> Color(0xFF065F46)
                                    is PrinterStatus.Error -> DangerRed
                                    else -> Slate700
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Device selection
                    if (uiState.pairedPrinters.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Slate50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Tidak Ada Perangkat Bluetooth Terpasang (Paired)",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate800,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Silakan hidupkan Bluetooth dan pasangkan printer thermal di pengaturan HP Android Anda. Mode Virtual Preview tetap aktif otomatis jika tanpa printer.",
                                    color = Slate500,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Pilih Perangkat Printer:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Slate700
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.pairedPrinters.forEach { printer ->
                            val isSelected = uiState.selectedPrinterMac == printer.address
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) BluePrimaryLight else Slate50,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, BluePrimary) else androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectPrinter(printer) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.selectPrinter(printer) },
                                            colors = RadioButtonDefaults.colors(selectedColor = BluePrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(text = printer.name, fontWeight = FontWeight.Bold, color = Slate900, fontSize = 13.sp)
                                            Text(text = "MAC: ${printer.address}", color = Slate400, fontSize = 11.sp)
                                        }
                                    }
                                    if (isSelected) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = BluePrimary) {
                                            Text(
                                                text = "Aktif",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Auto Cut & Pilihan Gap Baris Kertas
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Pilihan Gap Baris Kertas (Presisi 2mm per baris, tanpa auto-cut)
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
                                    .clip(CircleShape)
                                    .background(SuccessGreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Jarak Sobekan Kertas (Gap 2mm)",
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = when (uiState.feedLines) {
                                        0 -> "0 mm (Nol Gap - Berhenti di teks terakhir)"
                                        1 -> "2 mm (Gap Standar Sobek Rapat) ⭐"
                                        2 -> "4 mm (Pas di Gigi Sobek Printer)"
                                        3 -> "6 mm (Sedikit Longgar)"
                                        4 -> "8 mm (Standar Mesin Kasir)"
                                        else -> "${uiState.feedLines * 2} mm (${uiState.feedLines} Baris)"
                                    },
                                    color = if (uiState.feedLines == 1) SuccessGreen else Slate600,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Stepper (- / +)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Slate100)
                                .padding(4.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.setFeedLines(uiState.feedLines - 1) },
                                enabled = uiState.feedLines > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text(
                                    text = "−",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (uiState.feedLines > 0) BluePrimary else Slate400
                                )
                            }
                            Text(
                                text = if (uiState.feedLines == 0) "0 mm" else "${uiState.feedLines * 2} mm",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Slate900,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { viewModel.setFeedLines(uiState.feedLines + 1) },
                                enabled = uiState.feedLines < 5,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text(
                                    text = "+",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (uiState.feedLines < 5) BluePrimary else Slate400
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Select Chips (0 mm, 2 mm, 4 mm, 6 mm, 8 mm)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            0 to "0 mm",
                            1 to "2 mm ⭐",
                            2 to "4 mm",
                            3 to "6 mm",
                            4 to "8 mm"
                        ).forEach { (lines, label) ->
                            val isSelected = uiState.feedLines == lines
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setFeedLines(lines) },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BluePrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = Slate100,
                                    labelColor = Slate700
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (uiState.feedLines <= 1) SuccessGreenLight.copy(alpha = 0.6f) else Slate50,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.feedLines <= 1) SuccessGreen.copy(alpha = 0.5f) else Slate200
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = when (uiState.feedLines) {
                                    0 -> "🌟 Mode 0 mm (Nol Gap): Kertas termal langsung berhenti tepat di bawah teks terakhir tanpa ruang sisa."
                                    1 -> "⭐ Mode 2 mm (Rekomendasi Standar): Celah 2 mm memberi jarak aman pada gigi sobekan printer agar teks struk tidak terpotong saat ditarik, sekaligus sangat hemat kertas!"
                                    else -> "📏 Mode ${uiState.feedLines * 2} mm (${uiState.feedLines} Baris): Memajukan ${uiState.feedLines * 2} mm celah kosong tambahan di akhir struk."
                                },
                                color = Slate800,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Section B: Template Selection
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CyanAccentLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = CyanAccentDark, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Format Desain Struk",
                                fontWeight = FontWeight.Bold,
                                color = Slate900,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Pilih gaya cetak sesuai selera dan efisiensi kertas",
                                color = Slate400,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val availableTemplates = listOf(
                        Triple("MODERN", "Modern", "Standar & rapi"),
                        Triple("COMPACT", "Ringkas", "Hemat kertas"),
                        Triple("MINIMALIST", "Minimalis", "Simpel & bersih"),
                        Triple("DETAILED", "Lengkap", "ODP & Port detail"),
                        Triple("INVOICE", "Faktur", "Format nota resmi")
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(availableTemplates) { (key, title, subtitle) ->
                            val isSelected = template == key
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) BluePrimary else Slate50,
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                modifier = Modifier
                                    .width(112.dp)
                                    .clickable { template = key }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Slate800,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = subtitle,
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else Slate400,
                                        fontSize = 10.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section C: Business Identity
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEDE9FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFF6D28D9), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Identitas Usaha pada Struk",
                                fontWeight = FontWeight.Bold,
                                color = Slate900,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Dicetak di bagian kepala & kaki struk pembayaran",
                                color = Slate400,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Nama Usaha / RT-RW Net") },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = businessAddress,
                        onValueChange = { businessAddress = it },
                        label = { Text("Alamat Usaha / Lokasi Server") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = businessPhone,
                        onValueChange = { businessPhone = it },
                        label = { Text("Nomor WhatsApp / Layanan Pengaduan") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = footerMessage,
                        onValueChange = { footerMessage = it },
                        label = { Text("Pesan Penutup Struk (Footer)") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.printTestReceipt() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tes Cetak", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.savePrinterConfig(
                                    businessName,
                                    businessAddress,
                                    businessPhone,
                                    footerMessage,
                                    template
                                )
                                onShowFeedback("✓ Pengaturan Printer & Struk Berhasil Disimpan!")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section D: Live Paper Simulation
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Simulasi Kertas 58mm ($template)",
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFAFAFA))
                            .border(1.dp, Slate200, RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        val sampleData = EscPosHelper.ReceiptData(
                            businessName = businessName.ifEmpty { "Billingku RT/RW Net" },
                            businessAddress = businessAddress.ifEmpty { "Jl. Melati No. 45" },
                            businessPhone = businessPhone.ifEmpty { "081234567890" },
                            invoiceNo = "INV-SAMPLE-01",
                            customerName = "Bpk. Ahmad Zaelani",
                            customerCode = "WIFI-001",
                            packageName = "Paket Family 20 Mbps",
                            odpLocation = "ODP-01 Port 2",
                            period = uiState.currentPeriod.ifEmpty { "September 2026" },
                            monthlyFee = 175000,
                            adminFee = 2500,
                            discount = 0,
                            totalAmount = 177500,
                            paymentMethod = "TUNAI",
                            template = template,
                            footerMessage = footerMessage.ifEmpty { "Terima kasih atas pembayaran Anda." }
                        )
                        val previewText = remember(sampleData, uiState.isAutoCutEnabled, uiState.feedLines) {
                            try {
                                EscPosHelper.generatePlainTextReceipt(
                                    data = sampleData,
                                    autoCut = uiState.isAutoCutEnabled,
                                    feedLines = uiState.feedLines
                                )
                            } catch (e: Exception) {
                                "Pratinjau struk: ${e.localizedMessage ?: "Format tidak dapat ditampilkan"}"
                            }
                        }
                        Text(
                            text = previewText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Slate800
                        )
                    }
                }
            }
        }
    }
}
