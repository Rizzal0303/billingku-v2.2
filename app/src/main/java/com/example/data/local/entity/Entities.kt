package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["customerCode"]),
        Index(value = ["status"]),
        Index(value = ["packageId"])
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerCode: String, // e.g. "NET-001"
    val name: String,
    val phone: String, // WhatsApp
    val address: String,
    val odpLocation: String = "", // e.g. "ODP-JBG-02 Port 4"
    val packageId: Long,
    val packageName: String,
    val monthlyFee: Long,
    val dueDay: Int = 10, // Tanggal jatuh tempo (1-28)
    val ipOrPppoe: String = "",
    val status: String = "ACTIVE", // "ACTIVE", "ISOLATED", "INACTIVE"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "package_plans")
data class PackagePlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val speedMbps: Int,
    val price: Long,
    val description: String = "",
    val isActive: Boolean = true
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["periodMonth"]),
        Index(value = ["paidAt"]),
        Index(value = ["isDeposited"]),
        Index(value = ["invoiceNumber"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val invoiceNumber: String, // e.g. "INV-202609-001"
    val customerId: Long,
    val customerName: String,
    val customerCode: String,
    val packageName: String,
    val monthlyFee: Long,
    val adminFee: Long = 0,
    val discount: Long = 0,
    val totalAmount: Long,
    val periodMonth: String, // e.g. "September 2026"
    val paymentMethod: String = "TUNAI", // "TUNAI", "TRANSFER", "QRIS"
    val cashierName: String = "Kasir Utama",
    val cashierRole: String = "KASIR",
    val notes: String = "",
    val paidAt: Long = System.currentTimeMillis(),
    val isPrinted: Boolean = false,
    val isDeposited: Boolean = false,
    val settlementBatchId: Long? = null,
    val settlementBatchNumber: String? = null,
    val depositedAt: Long? = null
)

@Entity(
    tableName = "settlement_batches",
    indices = [
        Index(value = ["periodMonth"]),
        Index(value = ["createdAt"])
    ]
)
data class SettlementBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val batchNumber: String, // e.g. "STR-202609-001"
    val periodMonth: String, // e.g. "September 2026"
    val cashierName: String,
    val recipientName: String = "Owner / Admin",
    val totalAmount: Long,
    val totalCashAmount: Long = 0,
    val totalNonCashAmount: Long = 0,
    val customerCount: Int,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "staff_members")
data class StaffEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val name: String,
    val role: String, // "ADMIN", "KASIR", "TEKNISI"
    val phone: String = "",
    val pin: String = "1234",
    val isActive: Boolean = true
)

@Entity(tableName = "pending_sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String, // "CUSTOMER", "PACKAGE", "TRANSACTION", "SETTLEMENT", "STAFF", "ANNOUNCEMENT"
    val entityId: Long,
    val action: String, // "UPSERT", "DELETE"
    val payloadJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)

@Entity(
    tableName = "announcements",
    indices = [
        Index(value = ["status"]),
        Index(value = ["category"]),
        Index(value = ["createdAt"])
    ]
)
data class AnnouncementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String = "INFO", // "GANGGUAN", "MAINTENANCE", "INFO", "PROMO"
    val message: String,
    val targetScope: String = "SEMUA", // "SEMUA" atau spesifik ODP / Wilayah
    val status: String = "ACTIVE", // "ACTIVE", "RESOLVED", "DRAFT"
    val estimatedEnd: String = "", // e.g. "Hari ini 16:00 WIB"
    val createdBy: String = "Admin",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bills",
    indices = [
        Index(value = ["billCode"], unique = true),
        Index(value = ["customerId"]),
        Index(value = ["customerCode"]),
        Index(value = ["periodMonth"]),
        Index(value = ["status"]),
        Index(value = ["customerId", "periodMonth"], unique = true)
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billCode: String, // e.g. "BILL-NET001-202610"
    val customerId: Long,
    val customerCode: String,
    val customerName: String,
    val packageId: Long = 0,
    val packageName: String = "",
    val periodMonth: String, // e.g. "Oktober 2026"
    val dueDate: Long = 0, // Tanggal jatuh tempo epoch millis
    val amount: Long, // Nominal paket
    val adminFee: Long = 0,
    val discount: Long = 0,
    val totalAmount: Long, // amount + adminFee - discount
    val status: String = "UNPAID", // "UNPAID", "PAID", "CANCELLED"
    val createdAt: Long = System.currentTimeMillis(),
    val paidAt: Long? = null,
    val paymentMethod: String = "", // "TUNAI", "TRANSFER", "QRIS", "MANDIRI"
    val paidVia: String = "", // "CASHIER", "SELF_PAYMENT", "ONLINE"
    val cashierName: String = "",
    val invoiceNumber: String? = null,
    val notes: String = ""
)


