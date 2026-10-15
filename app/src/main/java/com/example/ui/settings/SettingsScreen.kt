package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.StaffEntity
import com.example.printer.PrinterStatus
import com.example.printer.EscPosHelper
import com.example.ui.components.AdminPinPromptDialog
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.settings.dialogs.EditAdminProfileDialog
import com.example.ui.settings.tabs.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isAdmin: Boolean = true,
    currentStaff: StaffEntity? = null,
    onLogout: (() -> Unit)? = null,
    onPinUpdated: ((String) -> Unit)? = null,
    onStaffProfileUpdated: ((StaffEntity) -> Unit)? = null,
    onVerifyAdminPin: ((String) -> Boolean)? = null,
    onNavigateToCustomers: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var previewReceiptData by remember { mutableStateOf<EscPosHelper.ReceiptData?>(null) }
    var saveFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var staffForPinChange by remember { mutableStateOf<StaffEntity?>(null) }
    var staffToEditProfile by remember { mutableStateOf<StaffEntity?>(null) }
    var showAdminPinDialogForEditAdmin by remember { mutableStateOf(false) }
    var isElevatedByPin by remember { mutableStateOf(false) }
    var showAdminPinDialogForTab by remember { mutableStateOf<Int?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val tabTitles = listOf(
        "Printer & Format Struk",
        "Paket WiFi & Layanan",
        "Kelola Staf & Kasir",
        "Laporan & Rekap Keuangan",
        "Database MySQL & Cadangan",
        "Pusat Pengumuman Pelanggan"
    )

    if (showAdminPinDialogForTab != null) {
        val tabIdx = showAdminPinDialogForTab ?: 1
        AdminPinPromptDialog(
            actionTitle = "Otorisasi Menu ${tabTitles.getOrElse(tabIdx) { "" }}",
            actionDescription = "Menu ini memerlukan hak akses Administrator untuk menjaga privasi data & keuangan bisnis. Masukkan PIN Admin:",
            onDismiss = { showAdminPinDialogForTab = null },
            onVerify = { pin -> onVerifyAdminPin?.invoke(pin) ?: false },
            onSuccess = {
                val targetTab = showAdminPinDialogForTab
                isElevatedByPin = true
                showAdminPinDialogForTab = null
                if (targetTab != null) {
                    viewModel.setTab(targetTab)
                }
            }
        )
    }

    if (showAdminPinDialogForEditAdmin) {
        AdminPinPromptDialog(
            actionTitle = "Otorisasi Edit Profil Admin",
            actionDescription = "Masukkan PIN Admin untuk membuka formulir edit profil administrator:",
            onDismiss = { showAdminPinDialogForEditAdmin = false },
            onVerify = { pin -> onVerifyAdminPin?.invoke(pin) ?: false },
            onSuccess = {
                showAdminPinDialogForEditAdmin = false
                val adminStaff = uiState.staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) } ?: currentStaff
                staffToEditProfile = adminStaff
            }
        )
    }

    if (staffToEditProfile != null) {
        EditAdminProfileDialog(
            staff = staffToEditProfile!!,
            onDismiss = { staffToEditProfile = null },
            onSave = { updatedName, updatedUsername, updatedPhone, updatedPin ->
                val target = staffToEditProfile ?: return@EditAdminProfileDialog
                viewModel.updateStaffProfile(
                    staff = target,
                    name = updatedName,
                    username = updatedUsername,
                    phone = updatedPhone,
                    pin = updatedPin
                ) { updatedStaff ->
                    staffToEditProfile = null
                    saveFeedbackMessage = "✓ Profil ${updatedStaff.name} berhasil diperbarui!"
                    if (currentStaff?.id == updatedStaff.id) {
                        onStaffProfileUpdated?.invoke(updatedStaff)
                    }
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Konfirmasi Keluar", fontWeight = FontWeight.Bold, color = Slate900, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin keluar dari akun ${currentStaff?.name ?: "Petugas"}? Anda perlu memasukkan PIN akun kembali saat login.",
                    color = Slate600,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Keluar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    val hasAdminAccess = isAdmin || isElevatedByPin

    // Keep track of last valid subscreen tab to prevent ArrayIndexOutOfBoundsException when exiting to -1
    var lastActiveTab by remember { mutableIntStateOf(0) }
    if (uiState.currentTab in tabTitles.indices) {
        lastActiveTab = uiState.currentTab
    }
    val inSubScreen = uiState.currentTab in 0..5

    // ---------------- ANIMATED TRANSITION BETWEEN OVERVIEW & SUB-SCREENS ----------------
    AnimatedContent(
        targetState = inSubScreen,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth / 4 },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(280))).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 5 },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                )
            } else {
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 5 },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(280))).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth / 4 },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                )
            }
        },
        label = "SettingsMainSubTransition"
    ) { showingSubScreen ->
        if (showingSubScreen) {
            BackHandler(enabled = true) {
                viewModel.setTab(-1)
            }

            val activeTab = if (uiState.currentTab in tabTitles.indices) uiState.currentTab else lastActiveTab

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = { viewModel.setTab(-1) },
                                modifier = Modifier.testTag("settings_subscreen_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali ke Pengaturan",
                                    tint = Slate800
                                )
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = tabTitles.getOrElse(activeTab) { "Pengaturan" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = "Pengaturan Sistem • Petugas: ${currentStaff?.name ?: "Admin"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.setTab(-1) },
                                modifier = Modifier.testTag("settings_subscreen_close_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup Sub-menu", tint = Slate500)
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
                ) {
                    AnimatedContent(
                        targetState = activeTab,
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
                    label = "SettingsTabTransition",
                    modifier = Modifier.fillMaxSize()
                ) { currentTab ->
                    when (currentTab) {
                        0 -> PrinterSettingsTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            onShowFeedback = { saveFeedbackMessage = it }
                        )
                        1 -> if (hasAdminAccess) {
                            PackagesSettingsTab(viewModel = viewModel, uiState = uiState)
                        } else {
                            AdminOnlyAccessView(
                                tabName = "Paket WiFi & Layanan",
                                onUnlockWithPin = { showAdminPinDialogForTab = 1 }
                            )
                        }
                        2 -> if (hasAdminAccess) {
                            StaffSettingsTab(
                                viewModel = viewModel,
                                uiState = uiState,
                                isAdmin = isAdmin,
                                onOpenChangePin = { staff -> staffForPinChange = staff },
                                onShowFeedback = { saveFeedbackMessage = it }
                            )
                        } else {
                            AdminOnlyAccessView(
                                tabName = "Staff & Kasir",
                                onUnlockWithPin = { showAdminPinDialogForTab = 2 }
                            )
                        }
                        3 -> if (hasAdminAccess) {
                            FinancialReportsTab(
                                viewModel = viewModel,
                                uiState = uiState,
                                onPreviewReceipt = { previewReceiptData = it }
                            )
                        } else {
                            AdminOnlyAccessView(
                                tabName = "Laporan Keuangan",
                                onUnlockWithPin = { showAdminPinDialogForTab = 3 }
                            )
                        }
                        4 -> if (hasAdminAccess) {
                            MysqlSettingsTab(
                                viewModel = viewModel,
                                uiState = uiState,
                                onShowFeedback = { saveFeedbackMessage = it },
                                onVerifyAdminPin = onVerifyAdminPin
                            )
                        } else {
                            AdminOnlyAccessView(
                                tabName = "Database MySQL & Cadangan",
                                onUnlockWithPin = { showAdminPinDialogForTab = 4 }
                            )
                        }
                        5 -> AnnouncementCenterTab(
                            viewModel = viewModel,
                            uiState = uiState,
                            currentStaff = currentStaff,
                            onShowFeedback = { saveFeedbackMessage = it }
                        )
                    }
                }
            }
        }
    } else {
        // Intercept back action in overview to return smoothly to Dashboard
        if (onBackClick != null) {
            BackHandler(enabled = true) {
                onBackClick.invoke()
            }
        }

        // ---------------- NEAT SETTINGS OVERVIEW ----------------
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (onBackClick != null) {
                            IconButton(
                                onClick = { onBackClick.invoke() },
                                modifier = Modifier.testTag("settings_overview_back_btn")
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
                        Text(
                            text = "Pengaturan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier.testTag("settings_overview_logout_btn")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Keluar Akun", tint = DangerRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate50)
                )
            },
            containerColor = Slate50
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
            ) {
                // Header Area
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Role Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isAdmin) Color(0xFFEEF2FF) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (isAdmin) Color(0xFFC7D2FE) else Slate200),
                            modifier = Modifier.testTag("settings_header_role_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isAdmin) Color(0xFF4F46E5) else Slate600,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAdmin) "AKUN ADMINISTRATOR" else "AKUN PETUGAS KASIR",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isAdmin) Color(0xFF4338CA) else Slate700,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large Title
                        Text(
                            text = if (isAdmin) "Profil & Pengaturan Admin" else "Pengaturan & Profil Petugas",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            fontSize = 22.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtitle
                        Text(
                            text = "Kelola akun kasir, data paket, pelanggan, dan preferensi aplikasi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val liveStaff = remember(uiState.staffList, currentStaff) {
                            uiState.staffList.firstOrNull { it.id == currentStaff?.id }
                                ?: uiState.staffList.firstOrNull { it.username.equals(currentStaff?.username, ignoreCase = true) }
                                ?: currentStaff
                        }

                        // Staff Profile Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_profile_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (liveStaff?.role.equals("ADMIN", ignoreCase = true)) Color(0xFF4F46E5) else BluePrimary,
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = (liveStaff?.name ?: "Admin").take(1).uppercase(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = liveStaff?.name ?: "Administrator Utama",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Slate900
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "@${liveStaff?.username ?: "admin"} • ${liveStaff?.role ?: "ADMIN"}",
                                                fontSize = 12.sp,
                                                color = Slate500
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = SuccessGreenLight
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(SuccessGreen, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Aktif",
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 1: MANAJEMEN AKUN & STAF
                item {
                    Column {
                        SettingsSectionHeader(if (isAdmin) "MANAJEMEN AKUN & STAF" else "PROFIL & AKUN SAYA")
                        SettingsGroupCard {
                            if (isAdmin) {
                                SettingsMenuItem(
                                    icon = Icons.Default.ManageAccounts,
                                    iconTint = Color(0xFF4F46E5),
                                    iconBackground = Color(0xFFEEF2FF),
                                    title = "Edit Profil Administrator",
                                    subtitle = "Ubah nama, username, nomor HP & PIN akun Admin",
                                    testTag = "settings_menu_edit_admin_profile",
                                    onClick = {
                                        val adminStaff = currentStaff ?: uiState.staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) }
                                        if (adminStaff != null) {
                                            staffToEditProfile = adminStaff
                                        }
                                    }
                                )

                                HorizontalDivider(
                                    color = Slate100,
                                    modifier = Modifier.padding(start = 68.dp, end = 16.dp)
                                )

                                SettingsMenuItem(
                                    icon = Icons.Default.Badge,
                                    iconTint = Color(0xFF4F46E5),
                                    iconBackground = Color(0xFFEEF2FF),
                                    title = "Kelola Staf & Kasir",
                                    subtitle = "${uiState.staffList.count { !it.role.equals("ADMIN", ignoreCase = true) }} Petugas operasional • Atur akun kasir & teknisi",
                                    testTag = "settings_menu_staff",
                                    onClick = {
                                        viewModel.setTab(2)
                                    }
                                )

                                HorizontalDivider(
                                    color = Slate100,
                                    modifier = Modifier.padding(start = 68.dp, end = 16.dp)
                                )

                                SettingsMenuItem(
                                    icon = Icons.Default.Lock,
                                    iconTint = Color(0xFFD97706),
                                    iconBackground = Color(0xFFFEF3C7),
                                    title = "Keamanan & Ganti PIN",
                                    subtitle = "Ubah PIN otorisasi Administrator",
                                    testTag = "settings_menu_pin",
                                    onClick = {
                                        val adminTarget = currentStaff ?: uiState.staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) }
                                        if (adminTarget != null) {
                                            staffForPinChange = adminTarget
                                        }
                                    }
                                )
                            } else {
                                // Tampilan untuk Staf / Kasir: Hanya opsi edit profil dirinya sendiri
                                SettingsMenuItem(
                                    icon = Icons.Default.Person,
                                    iconTint = Color(0xFF0284C7),
                                    iconBackground = Color(0xFFE0F2FE),
                                    title = "Edit Profil Saya",
                                    subtitle = "Perbarui nama, nomor HP & PIN akun ${currentStaff?.name ?: "Petugas"}",
                                    testTag = "settings_menu_edit_staff_profile",
                                    onClick = {
                                        val staffTarget = currentStaff ?: uiState.staffList.firstOrNull { !it.role.equals("ADMIN", ignoreCase = true) }
                                        if (staffTarget != null) {
                                            staffToEditProfile = staffTarget
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // SECTION 2: PENGATURAN SISTEM
                item {
                    Column {
                        SettingsSectionHeader("PENGATURAN SISTEM")
                        SettingsGroupCard {
                            SettingsMenuItem(
                                icon = Icons.Default.Print,
                                iconTint = Color(0xFF0284C7),
                                iconBackground = Color(0xFFE0F2FE),
                                title = "Printer & Format Struk",
                                subtitle = if (uiState.printerStatus is PrinterStatus.Connected) "Thermal Bluetooth • Terhubung" else "Konfigurasi Bluetooth & template struk",
                                trailingBadge = {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (uiState.printerStatus is PrinterStatus.Connected) SuccessGreen.copy(alpha = 0.12f) else Slate100
                                    ) {
                                        Text(
                                            text = if (uiState.printerStatus is PrinterStatus.Connected) "Aktif" else "Bluetooth",
                                            color = if (uiState.printerStatus is PrinterStatus.Connected) SuccessGreen else Slate600,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                },
                                testTag = "settings_menu_printer",
                                onClick = { viewModel.setTab(0) }
                            )

                            HorizontalDivider(
                                color = Slate100,
                                modifier = Modifier.padding(start = 68.dp, end = 16.dp)
                            )


                            SettingsMenuItem(
                                icon = Icons.Default.Campaign,
                                iconTint = Color(0xFFEA580C),
                                iconBackground = Color(0xFFFFEDD5),
                                title = "Pusat Pengumuman & Broadcast",
                                subtitle = if (uiState.activeAnnouncementsCount > 0)
                                    "${uiState.activeAnnouncementsCount} Pengumuman aktif • Disiarkan ke Net-pay"
                                else
                                    "Kirim info gangguan jaringan & maintenance ke Net-pay",
                                trailingBadge = {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (uiState.activeAnnouncementsCount > 0) DangerRed.copy(alpha = 0.12f) else Color(0xFFFFEDD5)
                                    ) {
                                        Text(
                                            text = if (uiState.activeAnnouncementsCount > 0) "${uiState.activeAnnouncementsCount} Aktif" else "Net-pay",
                                            color = if (uiState.activeAnnouncementsCount > 0) DangerRed else Color(0xFFC2410C),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                },
                                testTag = "settings_menu_announcements",
                                onClick = { viewModel.setTab(5) }
                            )

                            if (isAdmin) {
                                HorizontalDivider(
                                    color = Slate100,
                                    modifier = Modifier.padding(start = 68.dp, end = 16.dp)
                                )

                                SettingsMenuItem(
                                    icon = Icons.Default.Assessment,
                                    iconTint = Color(0xFF16A34A),
                                    iconBackground = Color(0xFFDCFCE7),
                                    title = "Laporan & Rekap Keuangan",
                                    subtitle = "Omzet ${EscPosHelper.formatRupiah(uiState.reportData.totalRevenue)} • Ringkasan kasir & metode",
                                    testTag = "settings_menu_reports",
                                    onClick = {
                                        viewModel.setTab(3)
                                    }
                                )
                            }
                        }
                    }
                }

                // SECTION 3: DATA MASTER & OPERASIONAL
                item {
                    Column {
                        SettingsSectionHeader("DATA MASTER & OPERASIONAL")
                        SettingsGroupCard {
                            SettingsMenuItem(
                                icon = Icons.Default.People,
                                iconTint = Color(0xFF2563EB),
                                iconBackground = Color(0xFFDBEAFE),
                                title = "Data Pelanggan",
                                subtitle = "${uiState.customerCount} Pelanggan terdaftar",
                                testTag = "settings_menu_customers",
                                onClick = { onNavigateToCustomers?.invoke() }
                            )

                            HorizontalDivider(
                                color = Slate100,
                                modifier = Modifier.padding(start = 68.dp, end = 16.dp)
                            )

                            SettingsMenuItem(
                                icon = Icons.Default.Speed,
                                iconTint = Color(0xFF9333EA),
                                iconBackground = Color(0xFFF3E8FF),
                                title = "Paket WiFi & Layanan",
                                subtitle = "${uiState.packages.size} Paket aktif • Atur kecepatan & tarif",
                                testTag = "settings_menu_packages",
                                onClick = {
                                    if (hasAdminAccess) {
                                        viewModel.setTab(1)
                                    } else {
                                        showAdminPinDialogForTab = 1
                                    }
                                }
                            )
                        }
                    }
                }

                // SECTION: DATABASE & CLOUD SYNC (MYSQL REST API)
                item {
                    Column {
                        SettingsSectionHeader("DATABASE & CLOUD SYNC")
                        SettingsGroupCard {
                            SettingsMenuItem(
                                icon = Icons.Default.CloudSync,
                                iconTint = Color(0xFF0F766E),
                                iconBackground = Color(0xFFCCFBF1),
                                title = "Database Cloud & Cadangan Lokal",
                                subtitle = if (uiState.lastBackupFileName.isNotBlank()) {
                                    "Cadangan: ${uiState.lastBackupFileName} • Cloud Sync aktif"
                                } else {
                                    "Sinkronisasi MySQL & Ekspor Cadangan File .json"
                                },
                                trailingBadge = {
                                    val isConfigured = uiState.mysqlApiUrl.isNotBlank() || uiState.mysqlPublicUrl.isNotBlank() || uiState.mysqlLocalUrl.isNotBlank()
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isConfigured) SuccessGreen.copy(alpha = 0.12f) else Color(0xFFEFF6FF)
                                    ) {
                                        Text(
                                            text = if (isConfigured) {
                                                when (uiState.mysqlEndpointMode) {
                                                    "LOCAL" -> "LAN Aktif"
                                                    "PUBLIC" -> "Cloud Aktif"
                                                    else -> "Dual-Endpoint"
                                                }
                                            } else "REST API",
                                            color = if (isConfigured) SuccessGreen else BluePrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                },
                                testTag = "settings_menu_mysql",
                                onClick = {
                                    if (hasAdminAccess) {
                                        viewModel.setTab(4)
                                    } else {
                                        showAdminPinDialogForTab = 4
                                    }
                                }
                            )
                        }
                    }
                }

                // SECTION 4: AKUN & KELUAR
                item {
                    Column {
                        SettingsSectionHeader("AKUN & KELUAR")
                        SettingsGroupCard {
                            SettingsMenuItem(
                                icon = Icons.Default.Logout,
                                iconTint = DangerRed,
                                iconBackground = Color(0xFFFEE2E2),
                                title = "Keluar dari Akun",
                                titleColor = DangerRed,
                                subtitle = "Selesaikan sesi kerja petugas saat ini",
                                testTag = "settings_menu_logout",
                                onClick = { showLogoutDialog = true }
                            )
                        }
                    }
                }

                // Footer Branding
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Billingku RT/RW Net • Versi 1.0.0",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sistem Billing & POS Kasir RT/RW Net Terintegrasi",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }
            }
        }
    }
}

    // Modal Ubah PIN Khusus Admin (Bisa diakses langsung dari Pengaturan)
    staffForPinChange?.let { targetStaff ->
        ChangePinDialog(
            staff = targetStaff,
            onDismiss = { staffForPinChange = null },
            onSave = { newPin ->
                viewModel.updateStaffPin(targetStaff.id, newPin) {
                    saveFeedbackMessage = "✓ PIN akun ${targetStaff.name} (@${targetStaff.username}) berhasil diperbarui!"
                    if (targetStaff.id == currentStaff?.id) {
                        onPinUpdated?.invoke(newPin)
                    }
                }
                staffForPinChange = null
            }
        )
    }

    // Modal Struk Preview
    previewReceiptData?.let { data ->
        ReceiptPreviewDialog(
            receiptData = data,
            onDismiss = { previewReceiptData = null },
            onPrintAgain = {
                viewModel.printTestReceipt()
            }
        )
    }

    // Snackbar / Feedback Modal
    saveFeedbackMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            saveFeedbackMessage = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 70.dp, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Slate900,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = msg, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
