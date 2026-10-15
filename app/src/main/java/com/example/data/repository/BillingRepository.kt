package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AnnouncementEntity
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.PackagePlanEntity
import com.example.data.local.entity.SettlementBatchEntity
import com.example.data.local.entity.StaffEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillingRepository(private val db: AppDatabase) {
    // Bills / Tagihan Terpusat (Room + MySQL)
    val allBills: Flow<List<BillEntity>> = db.billDao().getAllBills()
    val unpaidBills: Flow<List<BillEntity>> = db.billDao().getUnpaidBills()
    val unpaidBillCount: Flow<Int> = db.billDao().getUnpaidBillCount()
    val totalUnpaidAmount: Flow<Long> = db.billDao().getTotalUnpaidAmount()

    // Customers
    val allCustomers: Flow<List<CustomerEntity>> = db.customerDao().getAllCustomers()
    val activeCustomerCount: Flow<Int> = db.customerDao().getActiveCustomerCount()
    val totalCustomerCount: Flow<Int> = db.customerDao().getTotalCustomerCount()
    val pendingSyncCount: Flow<Int> = db.syncQueueDao().getPendingCount()

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = db.customerDao().searchCustomers(query)
    suspend fun getCustomerById(id: Long): CustomerEntity? = db.customerDao().getCustomerById(id)

    var mysqlSyncManager: com.example.data.sync.MysqlSyncManager? = null

    private suspend fun syncDirectPush(
        entityType: String,
        entityId: Long,
        action: String,
        pushCall: suspend (com.example.data.sync.MysqlSyncManager) -> Result<Boolean>
    ) {
        val sync = mysqlSyncManager ?: return
        sync.enqueuePending(entityType, entityId, action)
        if (sync.getApiUrl().isNotBlank()) {
            try {
                val res = pushCall(sync)
                if (res.isSuccess) {
                    db.syncQueueDao().deleteByEntity(entityType, entityId)
                }
            } catch (_: Exception) {
                // Biarkan di antrean pending sync untuk di-retry otomatis
            }
        }
    }

    suspend fun insertCustomer(customer: CustomerEntity): Long {
        val toSave = if (customer.id == 0L) customer.copy(id = com.example.util.IdGenerator.generateUniqueId()) else customer
        db.customerDao().insertCustomer(toSave)
        val saved = toSave
        syncDirectPush("CUSTOMER", saved.id, "UPSERT") { it.pushCustomer(saved) }
        return saved.id
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        db.customerDao().updateCustomer(customer)
        // Perbarui nominal tagihan berjalan yang belum dibayar (UNPAID) untuk pelanggan ini
        db.billDao().updateUnpaidBillsForCustomer(
            customerId = customer.id,
            newAmount = customer.monthlyFee,
            packageName = customer.packageName
        )
        syncDirectPush("CUSTOMER", customer.id, "UPSERT") { it.pushCustomer(customer) }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        db.customerDao().deleteCustomer(customer)
        syncDirectPush("CUSTOMER", customer.id, "DELETE") { it.deleteCustomer(customer.id) }
    }

    // Packages
    val activePlans: Flow<List<PackagePlanEntity>> = db.packagePlanDao().getAllActivePlans()
    val allPlans: Flow<List<PackagePlanEntity>> = db.packagePlanDao().getAllPlans()

    suspend fun insertPlan(plan: PackagePlanEntity): Long {
        val toSave = if (plan.id == 0L) plan.copy(id = com.example.util.IdGenerator.generateUniqueId()) else plan
        db.packagePlanDao().insertPlan(toSave)
        val saved = toSave
        syncDirectPush("PACKAGE", saved.id, "UPSERT") { it.pushPackagePlan(saved) }
        return saved.id
    }

    suspend fun updatePlan(plan: PackagePlanEntity) {
        db.packagePlanDao().updatePlan(plan)
        syncDirectPush("PACKAGE", plan.id, "UPSERT") { it.pushPackagePlan(plan) }
    }

    suspend fun deletePlan(plan: PackagePlanEntity) {
        db.packagePlanDao().deletePlan(plan)
        syncDirectPush("PACKAGE", plan.id, "DELETE") { it.deletePackagePlan(plan.id) }
    }

    // Transactions
    val allTransactions: Flow<List<TransactionEntity>> = db.transactionDao().getAllTransactions()
    fun getTransactionsByMonth(month: String): Flow<List<TransactionEntity>> = db.transactionDao().getTransactionsByMonth(month)
    fun getTotalRevenueByMonth(month: String): Flow<Long> = db.transactionDao().getTotalRevenueByMonth(month)
    val totalRevenueAllTime: Flow<Long> = db.transactionDao().getTotalRevenueAllTime()

    suspend fun insertTransactionSilently(transaction: TransactionEntity) {
        db.transactionDao().insertTransaction(transaction)
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Result<Long> {
        // 1. Cek lokal terlebih dahulu
        val existing = db.transactionDao().findTransactionForPeriod(transaction.customerId, transaction.periodMonth)
        if (existing != null) {
            return Result.failure(
                com.example.util.DuplicatePaymentException(
                    existing.invoiceNumber,
                    "Tagihan pelanggan '${transaction.customerName}' untuk periode ${transaction.periodMonth} sudah lunas (No. Faktur: ${existing.invoiceNumber})."
                )
            )
        }

        val toSave = if (transaction.id == 0L) transaction.copy(id = com.example.util.IdGenerator.generateUniqueId()) else transaction
        db.transactionDao().insertTransaction(toSave)
        val saved = toSave

        val sync = mysqlSyncManager
        if (sync != null && sync.getApiUrl().isNotBlank()) {
            sync.enqueuePending("TRANSACTION", saved.id, "UPSERT")
            try {
                val res = sync.pushTransaction(saved)
                if (res.isSuccess) {
                    db.syncQueueDao().deleteByEntity("TRANSACTION", saved.id)
                } else {
                    val ex = res.exceptionOrNull()
                    if (ex is com.example.util.DuplicatePaymentException) {
                        // Rollback lokal: Hapus transaksi ganda agar tidak tersimpan di HP ini
                        db.transactionDao().deleteTransactionById(saved.id)
                        db.syncQueueDao().deleteByEntity("TRANSACTION", saved.id)
                        try { sync.reconcileAndMergeFromMysql() } catch (_: Exception) {}
                        return Result.failure(ex)
                    }
                }
            } catch (e: Exception) {
                if (e is com.example.util.DuplicatePaymentException) {
                    db.transactionDao().deleteTransactionById(saved.id)
                    db.syncQueueDao().deleteByEntity("TRANSACTION", saved.id)
                    try { sync.reconcileAndMergeFromMysql() } catch (_: Exception) {}
                    return Result.failure(e)
                }
            }
        }

        // Sinkronkan status tagihan (bills) di database lokal & server
        try {
            val existingBill = db.billDao().getBillByCustomerAndPeriod(saved.customerId, saved.periodMonth)
            if (existingBill != null) {
                val updatedBill = existingBill.copy(
                    status = "PAID",
                    paidAt = saved.paidAt,
                    paymentMethod = saved.paymentMethod,
                    paidVia = if (saved.paymentMethod.equals("MANDIRI", ignoreCase = true)) "SELF_PAYMENT" else "CASHIER",
                    cashierName = saved.cashierName,
                    invoiceNumber = saved.invoiceNumber,
                    notes = saved.notes
                )
                db.billDao().updateBill(updatedBill)
            } else {
                val periodCode = try {
                    com.example.util.PeriodUtils.parsePeriod(saved.periodMonth).let {
                        SimpleDateFormat("yyyyMM", Locale.US).format(it.time)
                    }
                } catch (_: Exception) { "202610" }
                val newBill = BillEntity(
                    id = com.example.util.IdGenerator.generateUniqueId(),
                    billCode = "BILL-${saved.customerCode}-$periodCode",
                    customerId = saved.customerId,
                    customerCode = saved.customerCode,
                    customerName = saved.customerName,
                    packageName = saved.packageName,
                    periodMonth = saved.periodMonth,
                    amount = saved.monthlyFee,
                    adminFee = saved.adminFee,
                    discount = saved.discount,
                    totalAmount = saved.totalAmount,
                    status = "PAID",
                    paidAt = saved.paidAt,
                    paymentMethod = saved.paymentMethod,
                    paidVia = if (saved.paymentMethod.equals("MANDIRI", ignoreCase = true)) "SELF_PAYMENT" else "CASHIER",
                    cashierName = saved.cashierName,
                    invoiceNumber = saved.invoiceNumber,
                    notes = saved.notes
                )
                db.billDao().insertBill(newBill)
            }
        } catch (_: Exception) {
            // Abaikan kesalahan non-fatal sinkronisasi tagihan
        }

        return Result.success(saved.id)
    }

    // -------------------------------------------------------------
    // OPERASI BILLS / TAGIHAN RESMI (ROOM INTERNAL)
    // -------------------------------------------------------------
    suspend fun getBillByCustomerAndPeriod(customerId: Long, periodMonth: String): BillEntity? =
        db.billDao().getBillByCustomerAndPeriod(customerId, periodMonth)

    suspend fun getBillById(id: Long): BillEntity? = db.billDao().getBillById(id)
    suspend fun getBillByCode(code: String): BillEntity? = db.billDao().getBillByCode(code)

    suspend fun insertBill(bill: BillEntity): Long {
        val toSave = if (bill.id == 0L) bill.copy(id = com.example.util.IdGenerator.generateUniqueId()) else bill
        db.billDao().insertBill(toSave)
        return toSave.id
    }

    suspend fun updateBill(bill: BillEntity) {
        db.billDao().updateBill(bill)
    }

    suspend fun deleteBill(bill: BillEntity) {
        db.billDao().deleteBill(bill)
    }

    suspend fun markBillAsPaid(
        billId: Long,
        paidAt: Long,
        paymentMethod: String,
        paidVia: String,
        cashierName: String,
        invoiceNumber: String
    ) {
        db.billDao().markBillAsPaid(billId, paidAt, paymentMethod, paidVia, cashierName, invoiceNumber)
    }

    /**
     * Otomatis menerbitkan tagihan awal bulan untuk semua pelanggan aktif
     * Jika tagihan untuk periode tersebut sudah ada di database, tagihan tidak akan diduplikasi.
     */
    suspend fun generateMonthlyBillsForPeriod(
        periodMonth: String,
        defaultAdminFee: Long = 0L
    ): Result<Int> {
        val allCust = db.customerDao().getAllCustomersSync()
        val billableCustomers = allCust.filter {
            it.status.equals("ACTIVE", ignoreCase = true) || it.status.equals("ISOLATED", ignoreCase = true)
        }

        if (billableCustomers.isEmpty()) {
            return Result.success(0)
        }

        val periodCal = com.example.util.PeriodUtils.parsePeriod(periodMonth)
        val periodCode = SimpleDateFormat("yyyyMM", Locale.US).format(periodCal.time)
        var generatedCount = 0

        for (cust in billableCustomers) {
            // Cek apakah tagihan di database sudah ada
            val existing = db.billDao().getBillByCustomerAndPeriod(cust.id, periodMonth)
            if (existing != null) continue

            // Cek apakah transaksi sudah dibayar sebelumnya
            val existingTrx = db.transactionDao().findTransactionForPeriod(cust.id, periodMonth)
            val isPaid = existingTrx != null

            val dueCal = periodCal.clone() as java.util.Calendar
            dueCal.set(java.util.Calendar.DAY_OF_MONTH, cust.dueDay.coerceIn(1, 28))
            dueCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
            dueCal.set(java.util.Calendar.MINUTE, 59)

            val total = cust.monthlyFee + defaultAdminFee
            val billCode = "BILL-${cust.customerCode}-$periodCode"

            val bill = BillEntity(
                id = com.example.util.IdGenerator.generateUniqueId(),
                billCode = billCode,
                customerId = cust.id,
                customerCode = cust.customerCode,
                customerName = cust.name,
                packageId = cust.packageId,
                packageName = cust.packageName,
                periodMonth = periodMonth,
                dueDate = dueCal.timeInMillis,
                amount = cust.monthlyFee,
                adminFee = defaultAdminFee,
                discount = 0L,
                totalAmount = total,
                status = if (isPaid) "PAID" else "UNPAID",
                createdAt = System.currentTimeMillis(),
                paidAt = existingTrx?.paidAt,
                paymentMethod = existingTrx?.paymentMethod ?: "",
                paidVia = if (isPaid) "CASHIER" else "",
                cashierName = existingTrx?.cashierName ?: "",
                invoiceNumber = existingTrx?.invoiceNumber,
                notes = if (isPaid) "Lunas via Kasir" else "Tagihan otomatis awal bulan"
            )

            db.billDao().insertBill(bill)
            generatedCount++
        }

        return Result.success(generatedCount)
    }

    suspend fun checkAndGenerateMonthlyBills(): Result<Int> {
        val currentPeriod = getCurrentMonthPeriod()
        return generateMonthlyBillsForPeriod(currentPeriod)
    }

    suspend fun insertMultipleTransactions(transactions: List<TransactionEntity>): Result<List<Long>> {
        if (transactions.isEmpty()) {
            return Result.failure(IllegalArgumentException("Tidak ada periode tagihan yang dipilih."))
        }
        // 1. Cek duplikasi lokal untuk seluruh periode
        for (trx in transactions) {
            val existing = db.transactionDao().findTransactionForPeriod(trx.customerId, trx.periodMonth)
            if (existing != null) {
                return Result.failure(
                    com.example.util.DuplicatePaymentException(
                        existing.invoiceNumber,
                        "Tagihan pelanggan '${trx.customerName}' periode ${trx.periodMonth} sudah lunas (No. Faktur: ${existing.invoiceNumber}). Pembayaran dobel dibatalkan!"
                    )
                )
            }
        }

        val savedIds = mutableListOf<Long>()
        for (trx in transactions) {
            val result = insertTransaction(trx)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("Gagal memproses transaksi periode ${trx.periodMonth}"))
            }
            savedIds.add(result.getOrThrow())
        }
        return Result.success(savedIds)
    }

    suspend fun getTransactionsByCustomerSync(customerId: Long): List<TransactionEntity> =
        db.transactionDao().getTransactionsByCustomerSync(customerId)

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        db.transactionDao().deleteTransaction(transaction)
        syncDirectPush("TRANSACTION", transaction.id, "DELETE") { it.deleteTransaction(transaction.id) }
    }

    suspend fun deleteTransactionById(id: Long) {
        db.transactionDao().deleteTransactionById(id)
        syncDirectPush("TRANSACTION", id, "DELETE") { it.deleteTransaction(id) }
    }

    suspend fun markTransactionPrinted(id: Long) = db.transactionDao().markAsPrinted(id)
    suspend fun findTransactionForPeriod(customerId: Long, periodMonth: String): TransactionEntity? =
        db.transactionDao().findTransactionForPeriod(customerId, periodMonth)

    // Staff
    val allStaff: Flow<List<StaffEntity>> = db.staffDao().getAllStaff()
    val activeStaff: Flow<List<StaffEntity>> = db.staffDao().getActiveStaff()
    suspend fun getStaffById(id: Long): StaffEntity? = db.staffDao().getStaffById(id)
    suspend fun getStaffByUsername(username: String): StaffEntity? = db.staffDao().getStaffByUsername(username)

    suspend fun authenticate(username: String, pin: String): StaffEntity? {
        val staff = db.staffDao().getStaffByUsernameOrPhone(username.trim()) ?: return null
        if (com.example.util.SecurityUtils.verifyPin(pin, staff.pin)) {
            // Migrasi otomatis PIN plain text lama ke format hash SHA-256 tersensitisasi
            if (!com.example.util.SecurityUtils.isHashed(staff.pin)) {
                val upgraded = staff.copy(pin = com.example.util.SecurityUtils.hashPin(pin))
                db.staffDao().updateStaff(upgraded)
                return upgraded
            }
            return staff
        }
        return null
    }

    suspend fun verifyAdminPin(pin: String): Boolean {
        val admins = db.staffDao().getActiveAdmins()
        if (admins.isEmpty()) {
            return pin.trim() == "1234"
        }
        return admins.any { admin ->
            com.example.util.SecurityUtils.verifyPin(pin, admin.pin)
        }
    }

    suspend fun insertStaff(staff: StaffEntity): Long {
        val securePin = if (!com.example.util.SecurityUtils.isHashed(staff.pin)) {
            com.example.util.SecurityUtils.hashPin(staff.pin)
        } else {
            staff.pin
        }
        val staffToSave = staff.copy(
            id = if (staff.id == 0L) com.example.util.IdGenerator.generateUniqueId() else staff.id,
            pin = securePin
        )
        db.staffDao().insertStaff(staffToSave)
        val saved = staffToSave
        syncDirectPush("STAFF", saved.id, "UPSERT") { it.pushStaff(saved) }
        return saved.id
    }

    suspend fun updateStaff(staff: StaffEntity) {
        val securePin = if (!com.example.util.SecurityUtils.isHashed(staff.pin)) {
            com.example.util.SecurityUtils.hashPin(staff.pin)
        } else {
            staff.pin
        }
        val staffToSave = staff.copy(pin = securePin)
        db.staffDao().updateStaff(staffToSave)
        syncDirectPush("STAFF", staffToSave.id, "UPSERT") { it.pushStaff(staffToSave) }
    }

    suspend fun deleteStaff(staff: StaffEntity) {
        db.staffDao().deleteStaff(staff)
        syncDirectPush("STAFF", staff.id, "DELETE") { it.deleteStaff(staff.id) }
    }

    // Settlement / Setoran
    val allSettlementBatches: Flow<List<SettlementBatchEntity>> = db.settlementDao().getAllSettlementBatches()
    fun getSettlementBatchesByMonth(month: String): Flow<List<SettlementBatchEntity>> =
        db.settlementDao().getSettlementBatchesByMonth(month)
    fun getUndepositedTransactionsByMonth(month: String): Flow<List<TransactionEntity>> =
        db.transactionDao().getUndepositedTransactionsByMonth(month)
    fun getTransactionsByBatchId(batchId: Long): Flow<List<TransactionEntity>> =
        db.transactionDao().getTransactionsByBatchId(batchId)
    suspend fun getTransactionsByBatchIdSync(batchId: Long): List<TransactionEntity> =
        db.transactionDao().getTransactionsByBatchIdSync(batchId)
    suspend fun getSettlementBatchById(id: Long): SettlementBatchEntity? =
        db.settlementDao().getSettlementBatchById(id)

    fun generateSettlementBatchNumber(): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        val rnd = (100..999).random()
        return "STR-${sdf.format(Date())}-$rnd"
    }

    suspend fun createSettlementBatch(
        periodMonth: String,
        cashierName: String,
        recipientName: String,
        selectedTransactions: List<TransactionEntity>,
        notes: String = ""
    ): Result<SettlementBatchEntity> {
        if (selectedTransactions.isEmpty()) {
            return Result.failure(IllegalArgumentException("Pilih minimal satu data transaksi pelanggan untuk disetorkan."))
        }

        // Strict enforcement: Ensure none of the selected transactions are already deposited for this month
        val alreadyDeposited = selectedTransactions.filter { it.isDeposited }
        if (alreadyDeposited.isNotEmpty()) {
            val names = alreadyDeposited.joinToString { it.customerName }
            return Result.failure(
                IllegalStateException("Pelanggan ($names) sudah pernah disetorkan untuk bulan $periodMonth dan tidak dapat disetorkan ulang kecuali beda bulan!")
            )
        }

        val totalAmount = selectedTransactions.sumOf { it.totalAmount }
        val totalCashAmount = selectedTransactions
            .filter { it.paymentMethod.equals("TUNAI", ignoreCase = true) }
            .sumOf { it.totalAmount }
        val totalNonCashAmount = totalAmount - totalCashAmount
        val now = System.currentTimeMillis()
        val batchNumber = generateSettlementBatchNumber()

        val batchEntity = SettlementBatchEntity(
            id = com.example.util.IdGenerator.generateUniqueId(),
            batchNumber = batchNumber,
            periodMonth = periodMonth,
            cashierName = cashierName,
            recipientName = recipientName.ifBlank { "Owner / Bendahara" },
            totalAmount = totalAmount,
            totalCashAmount = totalCashAmount,
            totalNonCashAmount = totalNonCashAmount,
            customerCount = selectedTransactions.size,
            notes = notes,
            createdAt = now
        )

        db.settlementDao().insertSettlementBatch(batchEntity)
        val batchId = batchEntity.id
        val transactionIds = selectedTransactions.map { it.id }

        db.transactionDao().markTransactionsDeposited(
            transactionIds = transactionIds,
            batchId = batchId,
            batchNumber = batchNumber,
            depositedAt = now
        )

        val savedBatch = batchEntity.copy(id = batchId)

        // Offline-first sync queue: Enqueue settlement batch and deposited transactions
        syncDirectPush("SETTLEMENT", batchId, "UPSERT") { it.pushSettlementBatch(savedBatch) }
        val updatedTrxs = db.transactionDao().getTransactionsByBatchIdSync(batchId)
        for (trx in updatedTrxs) {
            syncDirectPush("TRANSACTION", trx.id, "UPSERT") { it.pushTransaction(trx) }
        }

        return Result.success(savedBatch)
    }

    suspend fun ensureDefaultStaffExist() {
        if (db.staffDao().getStaffByUsername("admin") == null) {
            db.staffDao().insertStaff(
                StaffEntity(username = "admin", name = "Budi Hartono (Owner)", role = "ADMIN", phone = "081234567890", pin = "1234")
            )
        }
        if (db.staffDao().getStaffByUsername("kasir1") == null) {
            db.staffDao().insertStaff(
                StaffEntity(username = "kasir1", name = "Siti Rahma", role = "KASIR", phone = "081987654321", pin = "1111")
            )
        }
        if (db.staffDao().getStaffByUsername("teknisi1") == null) {
            db.staffDao().insertStaff(
                StaffEntity(username = "teknisi1", name = "Agus Kurniawan", role = "TEKNISI", phone = "085678901234", pin = "2222")
            )
        }
    }

    fun getCurrentMonthPeriod(): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        return sdf.format(Date())
    }

    fun generateInvoiceNumber(): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        val rnd = (100..999).random()
        return "INV-${sdf.format(Date())}-$rnd"
    }

    // -------------------------------------------------------------
    // ANNOUNCEMENTS & BROADCAST NOTIFICATIONS (NET-PAY)
    // -------------------------------------------------------------
    val allAnnouncements: Flow<List<AnnouncementEntity>> = db.announcementDao().getAllAnnouncements()
    val activeAnnouncements: Flow<List<AnnouncementEntity>> = db.announcementDao().getActiveAnnouncements()

    suspend fun getAnnouncementById(id: Long): AnnouncementEntity? = db.announcementDao().getAnnouncementById(id)

    suspend fun insertAnnouncement(announcement: AnnouncementEntity): Long {
        val toSave = if (announcement.id == 0L) {
            announcement.copy(id = com.example.util.IdGenerator.generateUniqueId())
        } else {
            announcement
        }
        db.announcementDao().insertAnnouncement(toSave)
        syncDirectPush("ANNOUNCEMENT", toSave.id, "UPSERT") { it.pushAnnouncement(toSave) }
        return toSave.id
    }

    suspend fun updateAnnouncement(announcement: AnnouncementEntity) {
        val updated = announcement.copy(updatedAt = System.currentTimeMillis())
        db.announcementDao().updateAnnouncement(updated)
        syncDirectPush("ANNOUNCEMENT", updated.id, "UPSERT") { it.pushAnnouncement(updated) }
    }

    suspend fun deleteAnnouncement(announcement: AnnouncementEntity) {
        db.announcementDao().deleteAnnouncement(announcement)
        syncDirectPush("ANNOUNCEMENT", announcement.id, "DELETE") { it.deleteRemoteAnnouncement(announcement.id) }
    }

    suspend fun toggleAnnouncementStatus(id: Long, newStatus: String) {
        db.announcementDao().updateStatus(id, newStatus)
        val item = db.announcementDao().getAnnouncementById(id)
        if (item != null) {
            syncDirectPush("ANNOUNCEMENT", item.id, "UPSERT") { it.pushAnnouncement(item) }
        }
    }

    // -------------------------------------------------------------
    // BACKUP & RESTORE DATA LOKAL (ROOM DATABASE)
    // -------------------------------------------------------------
    suspend fun getAllCustomersSync(): List<CustomerEntity> = db.customerDao().getAllCustomersSync()
    suspend fun getAllPlansSync(): List<PackagePlanEntity> = db.packagePlanDao().getAllPlansSync()
    suspend fun getAllTransactionsSync(): List<TransactionEntity> = db.transactionDao().getAllTransactionsSync()
    suspend fun getAllBillsSync(): List<BillEntity> = db.billDao().getAllBillsSync()
    suspend fun getAllSettlementBatchesSync(): List<SettlementBatchEntity> = db.settlementDao().getAllSettlementBatchesSync()
    suspend fun getAllAnnouncementsSync(): List<AnnouncementEntity> = db.announcementDao().getAllAnnouncementsSync()
    suspend fun getAllStaffSync(): List<StaffEntity> = db.staffDao().getAllStaffSync()

    suspend fun restoreAllData(
        customers: List<CustomerEntity>,
        plans: List<PackagePlanEntity>,
        transactions: List<TransactionEntity>,
        bills: List<BillEntity>,
        batches: List<SettlementBatchEntity>,
        announcements: List<AnnouncementEntity>,
        staffList: List<StaffEntity>,
        cleanReplace: Boolean
    ) {
        if (cleanReplace) {
            db.customerDao().clearAllCustomers()
            db.packagePlanDao().clearAllPlans()
            db.transactionDao().clearAllTransactions()
            db.billDao().clearAllBills()
            db.settlementDao().clearAllSettlements()
            db.announcementDao().clearAllAnnouncements()
            if (staffList.isNotEmpty()) {
                db.staffDao().clearAllStaff()
            }
        }

        if (customers.isNotEmpty()) db.customerDao().insertCustomers(customers)
        if (plans.isNotEmpty()) db.packagePlanDao().insertPlans(plans)
        if (transactions.isNotEmpty()) db.transactionDao().insertTransactions(transactions)
        if (bills.isNotEmpty()) db.billDao().insertBills(bills)
        if (batches.isNotEmpty()) db.settlementDao().insertSettlementBatches(batches)
        if (announcements.isNotEmpty()) db.announcementDao().insertAnnouncements(announcements)
        if (staffList.isNotEmpty()) {
            db.staffDao().insertStaffList(staffList)
        } else {
            ensureDefaultStaffExist()
        }
    }
}

