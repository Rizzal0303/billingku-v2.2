package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.MysqlSyncManager
import com.example.ui.components.AdminPinPromptDialog
import com.example.ui.theme.*
import com.example.util.BackupPayload
import com.example.util.BackupRestoreHelper
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MysqlSettingsTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onShowFeedback: (String) -> Unit = {},
    onVerifyAdminPin: ((String) -> Boolean)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var apiUrl by remember(uiState.mysqlApiUrl) { mutableStateOf(uiState.mysqlApiUrl.ifBlank { uiState.mysqlPublicUrl }) }
    var apiKey by remember(uiState.mysqlApiKey) { mutableStateOf(uiState.mysqlApiKey) }
    var autoSync by remember(uiState.mysqlAutoSync) { mutableStateOf(uiState.mysqlAutoSync) }

    var isConfigUnlocked by remember { mutableStateOf(false) }
    var showAdminPinDialog by remember { mutableStateOf(false) }
    var pendingAdminAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var testDialogMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showUploadConfirmDialog by remember { mutableStateOf(false) }

    // State untuk Cadangan File Lokal (Backup & Restore)
    var showExportSuccessDialog by remember { mutableStateOf<File?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestorePayload by remember { mutableStateOf<BackupPayload?>(null) }
    var restoreModeCleanReplace by remember { mutableStateOf(false) }
    var showAdminPinDialogForRestore by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val parseResult = BackupRestoreHelper.parseBackupJson(jsonString)
                    parseResult.fold(
                        onSuccess = { payload ->
                            pendingRestorePayload = payload
                            restoreModeCleanReplace = false
                            showRestoreDialog = true
                        },
                        onFailure = { err ->
                            Toast.makeText(context, "Format file tidak valid: ${err.localizedMessage ?: err.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membaca file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun runWithAdminPin(action: () -> Unit) {
        if (isConfigUnlocked || onVerifyAdminPin == null) {
            action()
        } else {
            pendingAdminAction = action
            showAdminPinDialog = true
        }
    }

    val isConfigured = uiState.mysqlApiUrl.isNotBlank() || uiState.mysqlPublicUrl.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---------------------------------------------------------------------
        // 1. BANNER STATUS KONEKSI & SINKRONISASI
        // ---------------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, if (isConfigured) Color(0xFF99F6E4) else Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_mysql_status")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isConfigured) Color(0xFFCCFBF1) else Slate100),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (isConfigured) Color(0xFF0F766E) else Slate500,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Server REST API (MySQL)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate900
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isConfigured) SuccessGreen.copy(alpha = 0.12f) else Slate100
                        ) {
                            Text(
                                text = if (isConfigured) "Aktif" else "Belum Diatur",
                                color = if (isConfigured) SuccessGreen else Slate600,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Sinkronisasi Terakhir: ${uiState.mysqlLastSyncTime.ifBlank { "Belum pernah sinkronisasi" }}",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                    if (uiState.mysqlLastSyncStatus.isNotBlank()) {
                        Text(
                            text = uiState.mysqlLastSyncStatus,
                            fontSize = 11.sp,
                            color = BluePrimary,
                            maxLines = 1
                        )
                    }

                    val displayUrl = uiState.mysqlApiUrl.ifBlank { uiState.mysqlPublicUrl }
                    if (displayUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "URL: $displayUrl",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = if (uiState.pendingSyncCount > 0) Color(0xFFD97706) else SuccessGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.pendingSyncCount > 0) {
                                "Room DB: ${uiState.pendingSyncCount} data antre sinkronisasi"
                            } else {
                                "Room DB: Berfungsi offline & semua data seragam"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (uiState.pendingSyncCount > 0) Color(0xFFB45309) else Color(0xFF15803D)
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 2. FORM KONFIGURASI REST API SERVER
        // ---------------------------------------------------------------------
        val isEmbedded = (apiUrl == MysqlSyncManager.DEFAULT_EMBEDDED_PUBLIC_URL || apiUrl.isBlank()) &&
                         (apiKey == MysqlSyncManager.DEFAULT_EMBEDDED_API_KEY)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Bar dengan Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SettingsSuggest,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Konfigurasi REST API Server",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate900
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isEmbedded) Color(0xFFEFF6FF) else Color(0xFFFEF3C7)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isEmbedded) "📌 Bawaan Ditanam" else "✏️ Kustom",
                                color = if (isEmbedded) BluePrimary else Color(0xFFB45309),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "URL server dan API Key telah ditanam ke dalam aplikasi sebagai nilai bawaan. Anda dapat mengubahnya atau mereset kembali dengan otorisasi PIN Admin.",
                    fontSize = 11.sp,
                    color = Slate600
                )

                // Admin PIN Lock & Otorisasi Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isConfigUnlocked) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, if (isConfigUnlocked) Color(0xFF86EFAC) else Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isConfigUnlocked) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isConfigUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isConfigUnlocked) Color(0xFF16A34A) else Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isConfigUnlocked) "Otorisasi Admin Aktif (Mode Edit)" else "Terkunci dengan PIN Admin",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConfigUnlocked) Color(0xFF15803D) else Color(0xFF991B1B)
                                )
                                Text(
                                    text = if (isConfigUnlocked) "Anda bebas mengubah dan menyimpan koneksi" else "Staf kasir tidak dapat mengubah koneksi tanpa izin",
                                    fontSize = 10.sp,
                                    color = if (isConfigUnlocked) Color(0xFF166534) else Color(0xFFB91C1C)
                                )
                            }
                        }
                        if (!isConfigUnlocked) {
                            FilledTonalButton(
                                onClick = {
                                    pendingAdminAction = { isConfigUnlocked = true }
                                    showAdminPinDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_unlock_mysql_config")
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Buka Kunci", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { isConfigUnlocked = false },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_relock_mysql_config")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kunci", fontSize = 11.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                // 1. Input URL REST API Server
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "URL REST API Server (MySQL / Hosting) *",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Slate800
                            )
                        }
                        if (apiUrl == MysqlSyncManager.DEFAULT_EMBEDDED_PUBLIC_URL) {
                            Text("Bawaan", fontSize = 10.sp, color = BluePrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = {
                            if (isConfigUnlocked || onVerifyAdminPin == null) {
                                apiUrl = it
                            } else {
                                runWithAdminPin { apiUrl = it }
                            }
                        },
                        placeholder = { Text(MysqlSyncManager.DEFAULT_EMBEDDED_PUBLIC_URL, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_mysql_api_url"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null, tint = if (isConfigUnlocked) BluePrimary else Slate400, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (!isConfigUnlocked && onVerifyAdminPin != null) {
                                Icon(Icons.Default.Lock, contentDescription = "Terkunci", tint = Slate400, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                    Text(
                        text = "Contoh: https://billing.gonetplus.web.id/billingku.php (Akses online di mana saja via internet)",
                        fontSize = 10.sp,
                        color = Slate500,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                // 4. Input API Secret Key
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = Slate700, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "API Secret Key (Kunci Keamanan) *",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Slate800
                            )
                        }
                        if (apiKey == MysqlSyncManager.DEFAULT_EMBEDDED_API_KEY) {
                            Text("Bawaan", fontSize = 10.sp, color = BluePrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            if (isConfigUnlocked || onVerifyAdminPin == null) {
                                apiKey = it
                            } else {
                                runWithAdminPin { apiKey = it }
                            }
                        },
                        placeholder = { Text(MysqlSyncManager.DEFAULT_EMBEDDED_API_KEY, fontSize = 12.sp) },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_mysql_api_key"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = if (isConfigUnlocked) BluePrimary else Slate400, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isApiKeyVisible) "Sembunyikan API Key" else "Tampilkan API Key",
                                    tint = Slate500,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                    Text(
                        text = "Kunci rahasia untuk memvalidasi otentikasi REST API dengan file billingku.php",
                        fontSize = 10.sp,
                        color = Slate500,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                // 5. Switch Auto-Sync
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate50)
                        .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Sync Real-time & Periodik",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Slate900
                        )
                        Text(
                            text = "Otomatis sinkronkan penambahan & pembaruan data pelanggan, paket, transaksi, dan staf langsung ke server MySQL",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = {
                            runWithAdminPin { autoSync = it }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BluePrimary)
                    )
                }

                // 6. Tombol Aksi: Reset ke Default Bawaan, Uji Latensi, & Simpan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runWithAdminPin { showResetConfirmDialog = true }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_reset_mysql_defaults")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Default", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (apiUrl.isBlank()) {
                                Toast.makeText(context, "Masukkan URL REST API terlebih dahulu", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            viewModel.testMysqlConnection(apiUrl, apiKey) { success, msg ->
                                testDialogMessage = Pair(success, msg)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_test_mysql_connection"),
                        enabled = !uiState.isMysqlTesting
                    ) {
                        if (uiState.isMysqlTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Menguji...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Uji Koneksi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            runWithAdminPin {
                                viewModel.saveMysqlSettings(apiUrl, apiKey, autoSync)
                                isConfigUnlocked = false
                                Toast.makeText(context, "Pengaturan REST API berhasil disimpan & dikunci!", Toast.LENGTH_SHORT).show()
                                onShowFeedback("Pengaturan REST API berhasil disimpan")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("btn_save_mysql_settings")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simpan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // 3. AKSI SINKRONISASI MANUAL (UPLOAD & DOWNLOAD)
        // ---------------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sinkronisasi & Backup Database",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Slate900
                    )
                }

                Text(
                    text = "Gunakan tombol di bawah ini untuk mencadangkan (backup) seluruh data lokal aplikasi ke server MySQL Anda, atau mengunduh ulang semua data jika ganti ponsel.",
                    fontSize = 11.sp,
                    color = Slate600
                )

                HorizontalDivider(color = Slate100)

                // Tombol Sinkronisasi Penuh (Auto-Sync & Reconcile)
                Button(
                    onClick = {
                        if (uiState.mysqlApiUrl.isBlank()) {
                            Toast.makeText(context, "Harap simpan URL API MySQL terlebih dahulu", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.triggerFullAutoSync { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                onShowFeedback(msg)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_auto_sync_full"),
                    enabled = !uiState.isMysqlSyncing
                ) {
                    if (uiState.isMysqlSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sedang Menyelaraskan Data...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sinkronkan Sekarang (Dorong Antrean & Samakan Server)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Tombol Upload (Cadangkan ke MySQL)
                OutlinedButton(
                    onClick = {
                        if (uiState.mysqlApiUrl.isBlank()) {
                            Toast.makeText(context, "Harap simpan URL API MySQL terlebih dahulu", Toast.LENGTH_SHORT).show()
                        } else {
                            showUploadConfirmDialog = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_upload_all_mysql"),
                    enabled = !uiState.isMysqlSyncing
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Seluruh Database Lokal ke MySQL", fontSize = 12.sp)
                }

                // Tombol Download (Pulihkan dari MySQL)
                OutlinedButton(
                    onClick = {
                        if (uiState.mysqlApiUrl.isBlank()) {
                            Toast.makeText(context, "Harap simpan URL API MySQL terlebih dahulu", Toast.LENGTH_SHORT).show()
                        } else {
                            showDownloadConfirmDialog = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E)),
                    border = BorderStroke(1.dp, Color(0xFF0F766E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_download_all_mysql"),
                    enabled = !uiState.isMysqlSyncing
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0F766E))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download & Pulihkan Data dari MySQL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---------------------------------------------------------------------
        // 4. CADANGAN FILE LOKAL (OFFLINE BACKUP & RESTORE)
        // ---------------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_local_backup_restore")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Cadangan File Lokal (.json)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                            Text(
                                text = "Penyimpanan Offline & Portabel",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEFF6FF)
                    ) {
                        Text(
                            text = "📦 JSON",
                            color = BluePrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Ekspor seluruh database (Pelanggan, Paket Layanan, Transaksi, Tagihan, Setoran, Staf & PIN, serta Format Struk & Server API) ke file format .json di HP Anda. File ini dapat disimpan di Google Drive, dikirim via WhatsApp, atau dipulihkan ke HP lain.",
                    fontSize = 11.sp,
                    color = Slate600,
                    lineHeight = 16.sp
                )

                // Info Cadangan Terakhir
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (uiState.lastBackupFileName.isNotBlank()) SuccessGreen else Slate400,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.lastBackupFileName.isNotBlank()) {
                                    "Cadangan Dibuat: ${uiState.lastBackupTime} (${uiState.lastBackupSize})"
                                } else {
                                    "Belum ada file cadangan yang diekspor pada sesi ini"
                                },
                                fontSize = 11.sp,
                                fontWeight = if (uiState.lastBackupFileName.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (uiState.lastBackupFileName.isNotBlank()) Slate800 else Slate500
                            )
                        }
                        if (uiState.lastBackupFileName.isNotBlank()) {
                            Text(
                                text = "File: ${uiState.lastBackupFileName}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = BluePrimary
                            )
                        }
                    }
                }

                // Tombol Aksi Ekspor Utama
                Button(
                    onClick = {
                        viewModel.exportLocalBackup(context) { success, msg, file ->
                            if (success && file != null) {
                                showExportSuccessDialog = file
                                onShowFeedback(msg)
                            } else {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_export_local_backup"),
                    enabled = !uiState.isExportingBackup
                ) {
                    if (uiState.isExportingBackup) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sedang Membuat File Cadangan...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ekspor Cadangan ke File JSON",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Baris Tombol Bagikan & Pulihkan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.shareLastBackup(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_share_local_backup")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan File", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                filePickerLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak dapat membuka pengelola file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E)),
                        border = BorderStroke(1.dp, Color(0xFF0F766E)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_restore_local_backup")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF0F766E)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pulihkan File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialog Berhasil Ekspor Cadangan
    if (showExportSuccessDialog != null) {
        val file = showExportSuccessDialog!!
        val sizeKb = String.format(Locale.US, "%.1f KB", file.length() / 1024.0)
        AlertDialog(
            onDismissRequest = { showExportSuccessDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Cadangan Berhasil Dibuat!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "File database cadangan telah berhasil dibuat dan disimpan:",
                        fontSize = 12.sp,
                        color = Slate700
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("📄 Nama File: ${file.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text("💾 Ukuran File: $sizeKb", fontSize = 11.sp, color = Slate600)
                            Text("📁 Lokasi: Memori Internal / Cache Aplikasi", fontSize = 10.sp, color = Slate500)
                        }
                    }
                    Text(
                        text = "Anda dapat langsung membagikan file ini ke WhatsApp atau Google Drive agar tersimpan aman.",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentFile = showExportSuccessDialog
                        showExportSuccessDialog = null
                        if (currentFile != null) {
                            BackupRestoreHelper.shareBackupFile(context, currentFile)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportSuccessDialog = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Dialog Konfirmasi Pemulihan Cadangan (Restore)
    if (showRestoreDialog && pendingRestorePayload != null) {
        val payload = pendingRestorePayload!!
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                pendingRestorePayload = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Konfirmasi Pemulihan Database",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ditemukan file cadangan '${payload.metadata.appName} v${payload.metadata.version}':",
                        fontSize = 12.sp,
                        color = Slate700
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("📅 Tanggal Dibuat: ${payload.metadata.formattedDate}", fontSize = 11.sp, color = Slate800)
                            Text("📱 Perangkat Asal: ${payload.metadata.deviceModel}", fontSize = 11.sp, color = Slate800)
                            HorizontalDivider(color = Slate200, modifier = Modifier.padding(vertical = 4.dp))
                            Text("• ${payload.customers.size} Pelanggan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BluePrimary)
                            Text("• ${payload.packages.size} Paket Layanan", fontSize = 11.sp, color = Slate700)
                            Text("• ${payload.transactions.size} Riwayat Transaksi", fontSize = 11.sp, color = Slate700)
                            Text("• ${payload.bills.size} Data Tagihan", fontSize = 11.sp, color = Slate700)
                            Text("• ${payload.staffList.size} Akun Staf", fontSize = 11.sp, color = Slate700)
                            if (payload.settings != null) {
                                Text("• ✓ Format Struk, Profil Usaha & Server API", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                            }
                        }
                    }

                    Text(
                        text = "Pilih Metode Pemulihan:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )

                    // Mode 1: Gabung (Merge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!restoreModeCleanReplace) Color(0xFFEFF6FF) else Color.Transparent)
                            .clickable { restoreModeCleanReplace = false }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !restoreModeCleanReplace,
                            onClick = { restoreModeCleanReplace = false }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Gabung / Update (Direkomendasikan)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text("Data cadangan dimasukkan tanpa menghapus data lain yang sudah ada.", fontSize = 10.sp, color = Slate600)
                        }
                    }

                    // Mode 2: Timpa Bersih (Replace)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (restoreModeCleanReplace) Color(0xFFFEF2F2) else Color.Transparent)
                            .clickable { restoreModeCleanReplace = true }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = restoreModeCleanReplace,
                            onClick = { restoreModeCleanReplace = true }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Timpa Bersih (Hapus Data Lama)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                            Text("Kosongkan database lokal lalu isi ulang sepenuhnya dari file cadangan.", fontSize = 10.sp, color = Slate600)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = pendingRestorePayload
                        if (p != null) {
                            showRestoreDialog = false
                            if (onVerifyAdminPin != null) {
                                showAdminPinDialogForRestore = true
                            } else {
                                viewModel.restoreLocalBackup(p, restoreModeCleanReplace) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    onShowFeedback(msg)
                                    pendingRestorePayload = null
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (restoreModeCleanReplace) DangerRed else Color(0xFF0F766E))
                ) {
                    Text("Lanjutkan Pemulihan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    pendingRestorePayload = null
                }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Verifikasi PIN Admin untuk Pemulihan Database
    if (showAdminPinDialogForRestore) {
        AdminPinPromptDialog(
            actionTitle = "Otorisasi Pemulihan Database",
            actionDescription = "Pemulihan database lokal memerlukan hak akses Administrator untuk mencegah data tertimpa tanpa izin. Masukkan PIN Admin:",
            onDismiss = {
                showAdminPinDialogForRestore = false
                pendingRestorePayload = null
            },
            onVerify = { pin -> onVerifyAdminPin?.invoke(pin) ?: false },
            onSuccess = {
                showAdminPinDialogForRestore = false
                val p = pendingRestorePayload
                if (p != null) {
                    viewModel.restoreLocalBackup(p, restoreModeCleanReplace) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onShowFeedback(msg)
                        pendingRestorePayload = null
                    }
                }
            }
        )
    }

    // Dialog Hasil Tes Koneksi
    if (testDialogMessage != null) {
        val (isSuccess, message) = testDialogMessage!!
        AlertDialog(
            onDismissRequest = { testDialogMessage = null },
            icon = {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isSuccess) SuccessGreen else DangerRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (isSuccess) "Koneksi MySQL Sukses" else "Koneksi MySQL Gagal",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(text = message, fontSize = 13.sp, color = Slate700)
            },
            confirmButton = {
                Button(
                    onClick = { testDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSuccess) SuccessGreen else BluePrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }



    // Dialog Konfirmasi Upload
    if (showUploadConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUploadConfirmDialog = false },
            title = { Text("Konfirmasi Upload ke MySQL", fontWeight = FontWeight.Bold) },
            text = {
                Text("Semua data pelanggan, paket, transaksi kasir, setoran, dan staf di aplikasi ini akan dikirim dan disimpan ke server MySQL Anda. Lanjutkan?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUploadConfirmDialog = false
                        viewModel.uploadAllToMysql { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onShowFeedback(msg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Ya, Mulai Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Konfirmasi Download
    if (showDownloadConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadConfirmDialog = false },
            title = { Text("Konfirmasi Download dari MySQL", fontWeight = FontWeight.Bold) },
            text = {
                Text("Data dari server MySQL akan diunduh dan digabungkan ke penyimpanan lokal aplikasi ini. Gunakan fitur ini jika Anda ingin menyinkronkan data dari server ke perangkat ini. Lanjutkan?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDownloadConfirmDialog = false
                        viewModel.downloadAllFromMysql { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onShowFeedback(msg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                ) {
                    Text("Ya, Download Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Otorisasi PIN Admin
    if (showAdminPinDialog) {
        AdminPinPromptDialog(
            actionTitle = "Otorisasi PIN Admin",
            actionDescription = "Pengaturan koneksi REST API MySQL & API Key diproteksi. Masukkan PIN Admin untuk melanjutkan:",
            onDismiss = {
                showAdminPinDialog = false
                pendingAdminAction = null
            },
            onVerify = { pin -> onVerifyAdminPin?.invoke(pin) ?: false },
            onSuccess = {
                showAdminPinDialog = false
                isConfigUnlocked = true
                val act = pendingAdminAction
                pendingAdminAction = null
                act?.invoke()
            }
        )
    }

    // Dialog Konfirmasi Reset ke Default Bawaan
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Reset ke Bawaan Ditanam?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Konfigurasi akan dikembalikan ke nilai server bawaan aplikasi:",
                        fontSize = 12.sp,
                        color = Slate700
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("• URL Server: ${MysqlSyncManager.DEFAULT_EMBEDDED_PUBLIC_URL}", fontSize = 11.sp, color = Slate800, fontWeight = FontWeight.SemiBold)
                            Text("• API Key: ${MysqlSyncManager.DEFAULT_EMBEDDED_API_KEY}", fontSize = 10.sp, color = Slate600)
                        }
                    }
                    Text(
                        text = "Setelah di-reset, Anda tetap dapat mengubah nilai URL dan API Key ini kapan saja dengan otorisasi PIN Admin.",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetMysqlToEmbeddedDefaults {
                            apiUrl = MysqlSyncManager.DEFAULT_EMBEDDED_PUBLIC_URL
                            apiKey = MysqlSyncManager.DEFAULT_EMBEDDED_API_KEY
                            Toast.makeText(context, "Konfigurasi bawaan ditanam berhasil diterapkan!", Toast.LENGTH_SHORT).show()
                            onShowFeedback("Konfigurasi bawaan ditanam berhasil diterapkan")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Ya, Terapkan Bawaan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
