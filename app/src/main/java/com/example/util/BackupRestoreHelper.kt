package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.local.entity.*
import com.example.data.repository.BillingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupMetadata(
    val appName: String,
    val version: String,
    val exportedAt: Long,
    val formattedDate: String,
    val deviceModel: String,
    val totalCustomers: Int,
    val totalPackages: Int,
    val totalTransactions: Int,
    val totalBills: Int,
    val totalSettlements: Int,
    val totalStaff: Int
)

data class AppSettingsBackup(
    val businessName: String = "",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val footerMessage: String = "",
    val receiptTemplate: String = "MODERN",
    val feedLines: Int = 1,
    val mysqlPublicUrl: String = "",
    val mysqlApiKey: String = ""
)

data class BackupPayload(
    val metadata: BackupMetadata,
    val customers: List<CustomerEntity>,
    val packages: List<PackagePlanEntity>,
    val transactions: List<TransactionEntity>,
    val bills: List<BillEntity>,
    val settlements: List<SettlementBatchEntity>,
    val announcements: List<AnnouncementEntity>,
    val staffList: List<StaffEntity>,
    val settings: AppSettingsBackup? = null
)

data class RestoreResult(
    val isSuccess: Boolean,
    val message: String,
    val restoredCustomers: Int = 0,
    val restoredPackages: Int = 0,
    val restoredTransactions: Int = 0,
    val restoredBills: Int = 0,
    val restoredSettlements: Int = 0,
    val restoredStaff: Int = 0
)

object BackupRestoreHelper {

    private const val APP_IDENTIFIER = "Billingku"
    private const val APP_VERSION = "2.1"

    /**
     * Mengekspor seluruh database Room ke file JSON lokal yang tersimpan di memori cache/backups
     */
    suspend fun exportDatabaseToJson(
        context: Context,
        repository: BillingRepository,
        businessName: String = "Billingku RT/RW Net",
        appSettings: AppSettingsBackup? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val customers = repository.getAllCustomersSync()
            val packages = repository.getAllPlansSync()
            val transactions = repository.getAllTransactionsSync()
            val bills = repository.getAllBillsSync()
            val settlements = repository.getAllSettlementBatchesSync()
            val announcements = repository.getAllAnnouncementsSync()
            val staffList = repository.getAllStaffSync()

            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("id", "ID"))
            val formattedDate = dateFormat.format(Date(now))
            val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val fileNameDate = fileDateFormat.format(Date(now))

            val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

            val rootJson = JSONObject().apply {
                put("app", APP_IDENTIFIER)
                put("version", APP_VERSION)
                put("business_name", businessName)
                put("exported_at_millis", now)
                put("exported_at_formatted", formattedDate)
                put("device", deviceModel)

                val summaryObj = JSONObject().apply {
                    put("customers_count", customers.size)
                    put("packages_count", packages.size)
                    put("transactions_count", transactions.size)
                    put("bills_count", bills.size)
                    put("settlements_count", settlements.size)
                    put("staff_count", staffList.size)
                    put("total_revenue", transactions.sumOf { it.totalAmount })
                }
                put("summary", summaryObj)

                // Customers Array
                val custArray = JSONArray()
                customers.forEach { c ->
                    custArray.put(JSONObject().apply {
                        put("id", c.id)
                        put("customerCode", c.customerCode)
                        put("name", c.name)
                        put("phone", c.phone)
                        put("address", c.address)
                        put("odpLocation", c.odpLocation)
                        put("packageId", c.packageId)
                        put("packageName", c.packageName)
                        put("monthlyFee", c.monthlyFee)
                        put("dueDay", c.dueDay)
                        put("ipOrPppoe", c.ipOrPppoe)
                        put("status", c.status)
                        put("createdAt", c.createdAt)
                    })
                }
                put("customers", custArray)

                // Packages Array
                val pkgArray = JSONArray()
                packages.forEach { p ->
                    pkgArray.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("speedMbps", p.speedMbps)
                        put("price", p.price)
                        put("description", p.description)
                        put("isActive", p.isActive)
                    })
                }
                put("packages", pkgArray)

                // Transactions Array
                val trxArray = JSONArray()
                transactions.forEach { t ->
                    trxArray.put(JSONObject().apply {
                        put("id", t.id)
                        put("invoiceNumber", t.invoiceNumber)
                        put("customerId", t.customerId)
                        put("customerName", t.customerName)
                        put("customerCode", t.customerCode)
                        put("packageName", t.packageName)
                        put("monthlyFee", t.monthlyFee)
                        put("adminFee", t.adminFee)
                        put("discount", t.discount)
                        put("totalAmount", t.totalAmount)
                        put("periodMonth", t.periodMonth)
                        put("paymentMethod", t.paymentMethod)
                        put("cashierName", t.cashierName)
                        put("cashierRole", t.cashierRole)
                        put("notes", t.notes)
                        put("paidAt", t.paidAt)
                        put("isPrinted", t.isPrinted)
                        put("isDeposited", t.isDeposited)
                        put("settlementBatchId", t.settlementBatchId ?: JSONObject.NULL)
                        put("settlementBatchNumber", t.settlementBatchNumber ?: JSONObject.NULL)
                        put("depositedAt", t.depositedAt ?: JSONObject.NULL)
                    })
                }
                put("transactions", trxArray)

                // Bills Array
                val billArray = JSONArray()
                bills.forEach { b ->
                    billArray.put(JSONObject().apply {
                        put("id", b.id)
                        put("billCode", b.billCode)
                        put("customerId", b.customerId)
                        put("customerCode", b.customerCode)
                        put("customerName", b.customerName)
                        put("packageId", b.packageId)
                        put("packageName", b.packageName)
                        put("periodMonth", b.periodMonth)
                        put("dueDate", b.dueDate)
                        put("amount", b.amount)
                        put("adminFee", b.adminFee)
                        put("discount", b.discount)
                        put("totalAmount", b.totalAmount)
                        put("status", b.status)
                        put("createdAt", b.createdAt)
                        put("paidAt", b.paidAt ?: JSONObject.NULL)
                        put("paymentMethod", b.paymentMethod)
                        put("paidVia", b.paidVia)
                        put("cashierName", b.cashierName)
                        put("invoiceNumber", b.invoiceNumber ?: JSONObject.NULL)
                        put("notes", b.notes)
                    })
                }
                put("bills", billArray)

                // Settlement Batches Array
                val stlArray = JSONArray()
                settlements.forEach { s ->
                    stlArray.put(JSONObject().apply {
                        put("id", s.id)
                        put("batchNumber", s.batchNumber)
                        put("periodMonth", s.periodMonth)
                        put("cashierName", s.cashierName)
                        put("recipientName", s.recipientName)
                        put("totalAmount", s.totalAmount)
                        put("totalCashAmount", s.totalCashAmount)
                        put("totalNonCashAmount", s.totalNonCashAmount)
                        put("customerCount", s.customerCount)
                        put("notes", s.notes)
                        put("createdAt", s.createdAt)
                    })
                }
                put("settlements", stlArray)

                // Announcements Array
                val annArray = JSONArray()
                announcements.forEach { a ->
                    annArray.put(JSONObject().apply {
                        put("id", a.id)
                        put("title", a.title)
                        put("category", a.category)
                        put("message", a.message)
                        put("targetScope", a.targetScope)
                        put("status", a.status)
                        put("estimatedEnd", a.estimatedEnd)
                        put("createdBy", a.createdBy)
                        put("createdAt", a.createdAt)
                        put("updatedAt", a.updatedAt)
                    })
                }
                put("announcements", annArray)

                // Staff Array
                val stfArray = JSONArray()
                staffList.forEach { st ->
                    stfArray.put(JSONObject().apply {
                        put("id", st.id)
                        put("username", st.username)
                        put("name", st.name)
                        put("role", st.role)
                        put("phone", st.phone)
                        put("pin", st.pin)
                        put("isActive", st.isActive)
                    })
                }
                put("staff", stfArray)

                // App Settings (Receipt, Business Info, MySQL Config)
                if (appSettings != null) {
                    val settingsObj = JSONObject().apply {
                        put("business_name", appSettings.businessName)
                        put("business_address", appSettings.businessAddress)
                        put("business_phone", appSettings.businessPhone)
                        put("footer_message", appSettings.footerMessage)
                        put("receipt_template", appSettings.receiptTemplate)
                        put("feed_lines", appSettings.feedLines)
                        put("mysql_public_url", appSettings.mysqlPublicUrl)
                        put("mysql_api_key", appSettings.mysqlApiKey)
                    }
                    put("settings", settingsObj)
                }
            }

            val backupDir = File(context.cacheDir, "backups").apply {
                if (!exists()) mkdirs()
            }
            val backupFile = File(backupDir, "billingku_backup_${fileNameDate}.json")

            FileOutputStream(backupFile).use { fos ->
                fos.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Membagikan file cadangan ke aplikasi lain (WhatsApp, Google Drive, Email, File Manager)
     */
    fun shareBackupFile(context: Context, backupFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, backupFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Cadangan Database Billingku - ${backupFile.name}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Berikut adalah file cadangan database lokal Billingku RT/RW Net (${backupFile.name}). Simpan di tempat yang aman."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan / Simpan File Cadangan")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback teks jika URI gagal
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Gagal melampirkan file: ${e.localizedMessage}")
            }
            context.startActivity(Intent.createChooser(fallback, "Kirim Cadangan"))
        }
    }

    /**
     * Membaca dan memvalidasi struktur JSON cadangan
     */
    fun parseBackupJson(jsonString: String): Result<BackupPayload> {
        return try {
            val root = JSONObject(jsonString)

            val appName = root.optString("app", "Billingku")
            val version = root.optString("version", "2.1")
            val exportedAt = root.optLong("exported_at_millis", System.currentTimeMillis())
            val formattedDate = root.optString("exported_at_formatted", "-")
            val deviceModel = root.optString("device", "Android Device")

            // Parse Customers
            val customers = mutableListOf<CustomerEntity>()
            val custArray = root.optJSONArray("customers")
            if (custArray != null) {
                for (i in 0 until custArray.length()) {
                    val o = custArray.getJSONObject(i)
                    customers.add(
                        CustomerEntity(
                            id = o.optLong("id", 0L),
                            customerCode = o.optString("customerCode", "NET-001"),
                            name = o.optString("name", "Pelanggan"),
                            phone = o.optString("phone", ""),
                            address = o.optString("address", ""),
                            odpLocation = o.optString("odpLocation", ""),
                            packageId = o.optLong("packageId", 1L),
                            packageName = o.optString("packageName", "Paket"),
                            monthlyFee = o.optLong("monthlyFee", 100000L),
                            dueDay = o.optInt("dueDay", 10),
                            ipOrPppoe = o.optString("ipOrPppoe", ""),
                            status = o.optString("status", "ACTIVE"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Parse Packages
            val packages = mutableListOf<PackagePlanEntity>()
            val pkgArray = root.optJSONArray("packages")
            if (pkgArray != null) {
                for (i in 0 until pkgArray.length()) {
                    val o = pkgArray.getJSONObject(i)
                    packages.add(
                        PackagePlanEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", "Paket"),
                            speedMbps = o.optInt("speedMbps", 10),
                            price = o.optLong("price", 100000L),
                            description = o.optString("description", ""),
                            isActive = o.optBoolean("isActive", true)
                        )
                    )
                }
            }

            // Parse Transactions
            val transactions = mutableListOf<TransactionEntity>()
            val trxArray = root.optJSONArray("transactions")
            if (trxArray != null) {
                for (i in 0 until trxArray.length()) {
                    val o = trxArray.getJSONObject(i)
                    transactions.add(
                        TransactionEntity(
                            id = o.optLong("id", 0L),
                            invoiceNumber = o.optString("invoiceNumber", "INV-001"),
                            customerId = o.optLong("customerId", 0L),
                            customerName = o.optString("customerName", "Pelanggan"),
                            customerCode = o.optString("customerCode", "NET-001"),
                            packageName = o.optString("packageName", "Paket"),
                            monthlyFee = o.optLong("monthlyFee", 100000L),
                            adminFee = o.optLong("adminFee", 0L),
                            discount = o.optLong("discount", 0L),
                            totalAmount = o.optLong("totalAmount", 100000L),
                            periodMonth = o.optString("periodMonth", "Oktober 2026"),
                            paymentMethod = o.optString("paymentMethod", "TUNAI"),
                            cashierName = o.optString("cashierName", "Kasir"),
                            cashierRole = o.optString("cashierRole", "KASIR"),
                            notes = o.optString("notes", ""),
                            paidAt = o.optLong("paidAt", System.currentTimeMillis()),
                            isPrinted = o.optBoolean("isPrinted", false),
                            isDeposited = o.optBoolean("isDeposited", false),
                            settlementBatchId = if (o.isNull("settlementBatchId")) null else o.optLong("settlementBatchId"),
                            settlementBatchNumber = if (o.isNull("settlementBatchNumber")) null else o.optString("settlementBatchNumber"),
                            depositedAt = if (o.isNull("depositedAt")) null else o.optLong("depositedAt")
                        )
                    )
                }
            }

            // Parse Bills
            val bills = mutableListOf<BillEntity>()
            val billArray = root.optJSONArray("bills")
            if (billArray != null) {
                for (i in 0 until billArray.length()) {
                    val o = billArray.getJSONObject(i)
                    bills.add(
                        BillEntity(
                            id = o.optLong("id", 0L),
                            billCode = o.optString("billCode", "BILL-001"),
                            customerId = o.optLong("customerId", 0L),
                            customerCode = o.optString("customerCode", "NET-001"),
                            customerName = o.optString("customerName", "Pelanggan"),
                            packageId = o.optLong("packageId", 1L),
                            packageName = o.optString("packageName", "Paket"),
                            periodMonth = o.optString("periodMonth", "Oktober 2026"),
                            dueDate = o.optLong("dueDate", 0L),
                            amount = o.optLong("amount", 100000L),
                            adminFee = o.optLong("adminFee", 0L),
                            discount = o.optLong("discount", 0L),
                            totalAmount = o.optLong("totalAmount", 100000L),
                            status = o.optString("status", "UNPAID"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            paidAt = if (o.isNull("paidAt")) null else o.optLong("paidAt"),
                            paymentMethod = o.optString("paymentMethod", ""),
                            paidVia = o.optString("paidVia", ""),
                            cashierName = o.optString("cashierName", ""),
                            invoiceNumber = if (o.isNull("invoiceNumber")) null else o.optString("invoiceNumber"),
                            notes = o.optString("notes", "")
                        )
                    )
                }
            }

            // Parse Settlements
            val settlements = mutableListOf<SettlementBatchEntity>()
            val stlArray = root.optJSONArray("settlements")
            if (stlArray != null) {
                for (i in 0 until stlArray.length()) {
                    val o = stlArray.getJSONObject(i)
                    settlements.add(
                        SettlementBatchEntity(
                            id = o.optLong("id", 0L),
                            batchNumber = o.optString("batchNumber", "STR-001"),
                            periodMonth = o.optString("periodMonth", "Oktober 2026"),
                            cashierName = o.optString("cashierName", "Kasir"),
                            recipientName = o.optString("recipientName", "Admin"),
                            totalAmount = o.optLong("totalAmount", 0L),
                            totalCashAmount = o.optLong("totalCashAmount", 0L),
                            totalNonCashAmount = o.optLong("totalNonCashAmount", 0L),
                            customerCount = o.optInt("customerCount", 0),
                            notes = o.optString("notes", ""),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Parse Announcements
            val announcements = mutableListOf<AnnouncementEntity>()
            val annArray = root.optJSONArray("announcements")
            if (annArray != null) {
                for (i in 0 until annArray.length()) {
                    val o = annArray.getJSONObject(i)
                    announcements.add(
                        AnnouncementEntity(
                            id = o.optLong("id", 0L),
                            title = o.optString("title", "Pengumuman"),
                            category = o.optString("category", "INFO"),
                            message = o.optString("message", ""),
                            targetScope = o.optString("targetScope", "SEMUA"),
                            status = o.optString("status", "ACTIVE"),
                            estimatedEnd = o.optString("estimatedEnd", ""),
                            createdBy = o.optString("createdBy", "Admin"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Parse Staff
            val staffList = mutableListOf<StaffEntity>()
            val stfArray = root.optJSONArray("staff")
            if (stfArray != null) {
                for (i in 0 until stfArray.length()) {
                    val o = stfArray.getJSONObject(i)
                    staffList.add(
                        StaffEntity(
                            id = o.optLong("id", 0L),
                            username = o.optString("username", "user"),
                            name = o.optString("name", "Staff"),
                            role = o.optString("role", "KASIR"),
                            phone = o.optString("phone", ""),
                            pin = o.optString("pin", "1234"),
                            isActive = o.optBoolean("isActive", true)
                        )
                    )
                }
            }

            val metadata = BackupMetadata(
                appName = appName,
                version = version,
                exportedAt = exportedAt,
                formattedDate = formattedDate,
                deviceModel = deviceModel,
                totalCustomers = customers.size,
                totalPackages = packages.size,
                totalTransactions = transactions.size,
                totalBills = bills.size,
                totalSettlements = settlements.size,
                totalStaff = staffList.size
            )

            // Parse Settings (Optional)
            val settingsObj = root.optJSONObject("settings")
            val settings = if (settingsObj != null) {
                AppSettingsBackup(
                    businessName = settingsObj.optString("business_name", ""),
                    businessAddress = settingsObj.optString("business_address", ""),
                    businessPhone = settingsObj.optString("business_phone", ""),
                    footerMessage = settingsObj.optString("footer_message", ""),
                    receiptTemplate = settingsObj.optString("receipt_template", "MODERN"),
                    feedLines = settingsObj.optInt("feed_lines", 1),
                    mysqlPublicUrl = settingsObj.optString("mysql_public_url", ""),
                    mysqlApiKey = settingsObj.optString("mysql_api_key", "")
                )
            } else null

            Result.success(
                BackupPayload(
                    metadata = metadata,
                    customers = customers,
                    packages = packages,
                    transactions = transactions,
                    bills = bills,
                    settlements = settlements,
                    announcements = announcements,
                    staffList = staffList,
                    settings = settings
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memulihkan data cadangan ke database Room lokal
     * @param cleanReplace jika true, bersihkan tabel lama terlebih dahulu. Jika false, lakukan merge/update.
     */
    suspend fun restoreDatabase(
        repository: BillingRepository,
        payload: BackupPayload,
        cleanReplace: Boolean = false
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            repository.restoreAllData(
                customers = payload.customers,
                plans = payload.packages,
                transactions = payload.transactions,
                bills = payload.bills,
                batches = payload.settlements,
                announcements = payload.announcements,
                staffList = payload.staffList,
                cleanReplace = cleanReplace
            )

            RestoreResult(
                isSuccess = true,
                message = if (cleanReplace) "Database berhasil dipulihkan bersih dari file cadangan." else "Data berhasil digabungkan dengan database lokal.",
                restoredCustomers = payload.customers.size,
                restoredPackages = payload.packages.size,
                restoredTransactions = payload.transactions.size,
                restoredBills = payload.bills.size,
                restoredSettlements = payload.settlements.size,
                restoredStaff = payload.staffList.size
            )
        } catch (e: Exception) {
            RestoreResult(
                isSuccess = false,
                message = "Gagal memulihkan database: ${e.localizedMessage}"
            )
        }
    }
}
