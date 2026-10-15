package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.StaffEntity
import com.example.ui.theme.*

@Composable
fun UserProfileDialog(
    currentStaff: StaffEntity?,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    if (currentStaff == null) return

    val isAdmin = currentStaff.role.equals("ADMIN", ignoreCase = true)
    val roleColor = if (isAdmin) Color(0xFF6366F1) else SuccessGreen
    val roleLabel = when (currentStaff.role.uppercase()) {
        "ADMIN" -> "👑 ADMINISTRATOR (Owner)"
        "KASIR" -> "💼 STAFF KASIR"
        "TEKNISI" -> "🔧 STAFF TEKNISI"
        else -> currentStaff.role
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp)
                .testTag("user_profile_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profil Petugas Aktif",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Profile Avatar & Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate50)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(roleColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            contentDescription = null,
                            tint = roleColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = currentStaff.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Slate900
                        )
                        Text(
                            text = "@${currentStaff.username} • Telp: ${currentStaff.phone.ifEmpty { "-" }}",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = roleColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = roleLabel,
                                color = roleColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hak Akses (Privileges List)
                Text(
                    text = "Daftar Hak Akses Role Ini:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Slate800
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAFAFA))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isAdmin) {
                        PermissionItem(allowed = true, text = "Akses penuh POS Kasir & Pembayaran")
                        PermissionItem(allowed = true, text = "Cetak Struk Thermal 58mm & Kirim WhatsApp")
                        PermissionItem(allowed = true, text = "Kelola & Hapus Data Pelanggan")
                        PermissionItem(allowed = true, text = "Batalkan & Hapus Riwayat Transaksi (Void)")
                        PermissionItem(allowed = true, text = "Ubah Tarif & Paket Internet WiFi")
                        PermissionItem(allowed = true, text = "Kelola Akun Staff & Ubah PIN Kasir")
                        PermissionItem(allowed = true, text = "Laporan Keuangan Lengkap & Total Omset")
                    } else {
                        PermissionItem(allowed = true, text = "Akses POS Kasir & Terima Pembayaran")
                        PermissionItem(allowed = true, text = "Cetak Struk Thermal 58mm & Kirim WhatsApp")
                        PermissionItem(allowed = true, text = "Cari & Lihat Kontak / Status Pelanggan")
                        PermissionItem(allowed = true, text = "Pengaturan Printer Kasir Bluetooth")
                        PermissionItem(allowed = false, text = "Hapus Riwayat Transaksi (Dibatasi Khusus Admin)")
                        PermissionItem(allowed = false, text = "Hapus Data Pelanggan (Dibatasi Khusus Admin)")
                        PermissionItem(allowed = false, text = "Ubah Tarif / Paket WiFi (Dibatasi Khusus Admin)")
                        PermissionItem(allowed = false, text = "Kelola Akun / PIN Staff (Dibatasi Khusus Admin)")
                        PermissionItem(allowed = false, text = "Laporan Keuangan & Omset (Dibatasi Khusus Admin)")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Logout Button
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("logout_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Keluar (Logout)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(allowed: Boolean, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (allowed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (allowed) SuccessGreen else DangerRed,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (allowed) Slate800 else Slate400,
            lineHeight = 15.sp
        )
    }
}
