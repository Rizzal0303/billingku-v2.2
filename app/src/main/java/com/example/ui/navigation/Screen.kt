package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Cashier : Screen("cashier", "Kasir POS", Icons.Default.PointOfSale)
    object Settlement : Screen("settlement", "Setoran", Icons.Default.AccountBalanceWallet)
    object History : Screen("history", "Riwayat", Icons.Default.Assessment)
    object Customers : Screen("customers", "Pelanggan", Icons.Default.People)
    object Settings : Screen("settings", "Pengaturan", Icons.Default.Settings)
}
