package com.example.ui.cashier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.StaffEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.repository.BillingRepository
import com.example.printer.BluetoothPrinterManager
import com.example.printer.EscPosHelper
import com.example.util.PeriodUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Setiap tagihan bulanan yang belum dibayar menjadi 1 kartu tersendiri.
 * Contoh: Jika seorang pelanggan menunggak bulan lalu dan belum bayar bulan ini,
 * akan muncul 2 kartu terpisah di kasir:
 * 1. Kartu tagihan bulan lalu (dengan keterangan "Tunggakan Bulan Lalu")
 * 2. Kartu tagihan bulan ini (dengan keterangan "Tagihan Bulan Ini")
 */
data class BillCardItem(
    val id: String, // e.g. "101_Agustus 2026"
    val customer: CustomerEntity,
    val periodMonth: String, // e.g. "Agustus 2026" atau "September 2026"
    val isPastArrears: Boolean, // true jika bulan lalu / tunggakan lampau
    val isLastMonth: Boolean, // true jika persis bulan lalu
    val isCurrentMonth: Boolean, // true jika bulan ini
    val isOverdue: Boolean,
    val fee: Long
)

data class CashierUiState(
    val allBillCards: List<BillCardItem> = emptyList(),
    val filteredBillCards: List<BillCardItem> = emptyList(),
    val dueFilter: String = "ALL", // "ALL", "ARREARS", "CURRENT_MONTH", "TODAY_OVERDUE", "ISOLATED"
    val searchQuery: String = "",
    val currentPeriod: String = "",
    // Modal Checkout State (Membayar khusus tagihan pada kartu yang dipilih)
    val selectedBillCard: BillCardItem? = null,
    val selectedCustomer: CustomerEntity? = null,
    val selectedPeriodMonth: String = "",
    val isCheckoutModalOpen: Boolean = false,
    val adminFee: Long = 0,
    val discount: Long = 0,
    val paymentMethod: String = "TUNAI",
    val staffList: List<StaffEntity> = emptyList(),
    val selectedStaff: StaffEntity? = null,
    val isStaffRole: Boolean = false,
    val notes: String = "",
    val isProcessing: Boolean = false,
    val isConfirmed: Boolean = false,
    val lastCompletedReceipt: EscPosHelper.ReceiptData? = null,
    val errorMessage: String? = null,
    val autoRestoredFromIsolated: Boolean = false,
    val isRefreshing: Boolean = false
)

class CashierViewModel(
    private val repository: BillingRepository,
    private val printerManager: BluetoothPrinterManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashierUiState())
    val uiState: StateFlow<CashierUiState> = _uiState.asStateFlow()

    fun refreshFromMysql(onComplete: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val sync = repository.mysqlSyncManager
            if (sync == null || sync.getApiUrl().isBlank()) {
                _uiState.update { it.copy(isRefreshing = false) }
                onComplete(false, "Atur URL MySQL di Pengaturan untuk sinkronisasi 2 HP")
                return@launch
            }
            val result = sync.downloadAllFromMysql()
            _uiState.update { it.copy(isRefreshing = false) }
            result.fold(
                onSuccess = { _ -> onComplete(true, "✓ Data tagihan berhasil disinkronkan dari server!") },
                onFailure = { err -> onComplete(false, err.localizedMessage ?: err.message ?: "Gagal sinkronisasi") }
            )
        }
    }

    init {
        val currentPeriod = repository.getCurrentMonthPeriod()
        _uiState.update { it.copy(currentPeriod = currentPeriod) }
        observeData(currentPeriod)
    }

    fun setLoggedInUser(staff: StaffEntity?) {
        if (staff == null) return
        val isStaff = !staff.role.equals("ADMIN", ignoreCase = true)
        _uiState.update { current ->
            current.copy(
                selectedStaff = staff,
                isStaffRole = isStaff
            )
        }
    }

    private fun observeData(currentPeriod: String) {
        viewModelScope.launch {
            combine(
                repository.allCustomers,
                repository.allTransactions,
                repository.allBills
            ) { customers, transactions, bills ->
                val transactionsByCustomer = transactions.groupBy { it.customerId }
                val billsByCustomer = bills.groupBy { it.customerId }
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

                // Pelanggan yang ditagih: ACTIVE dan ISOLATED
                val billableCustomers = customers.filter {
                    it.status.equals("ACTIVE", ignoreCase = true) || it.status.equals("ISOLATED", ignoreCase = true)
                }

                val billCards = mutableListOf<BillCardItem>()

                for (cust in billableCustomers) {
                    val custTransactions = transactionsByCustomer[cust.id] ?: emptyList()
                    val custBills = billsByCustomer[cust.id] ?: emptyList()
                    val paidPeriods = (
                        custTransactions.map { it.periodMonth } +
                        custBills.filter { it.status.equals("PAID", ignoreCase = true) }.map { it.periodMonth }
                    ).toSet()

                    // Dapatkan periode yang seharusnya dibayar dari saat daftar sampai bulan sekarang
                    val eligiblePeriods = PeriodUtils.getEligiblePeriodsForCustomer(cust.createdAt, maxPastMonths = 6)
                    val unpaidPeriods = eligiblePeriods.filter { !paidPeriods.contains(it) }

                    // Untuk setiap periode yang belum dibayar, buat 1 KARTU TERSENDIRI di kasir
                    for (period in unpaidPeriods) {
                        val isPast = PeriodUtils.isPastPeriod(period)
                        val isLast = PeriodUtils.isLastMonth(period)
                        val isCurr = period.equals(currentPeriod, ignoreCase = true)
                        val isOverdue = (cust.dueDay <= currentDay && isCurr) || isPast

                        billCards.add(
                            BillCardItem(
                                id = "${cust.id}_$period",
                                customer = cust,
                                periodMonth = period,
                                isPastArrears = isPast,
                                isLastMonth = isLast,
                                isCurrentMonth = isCurr,
                                isOverdue = isOverdue,
                                fee = cust.monthlyFee
                            )
                        )
                    }
                }

                // Pengurutan kartu tagihan:
                // 1. Tagihan terisolir paling atas
                // 2. Tunggakan bulan lalu duluan (kronologis dari yang paling lama menunggak)
                // 3. Kemudian tagihan bulan ini berdasarkan tanggal jatuh tempo
                billCards.sortWith(
                    compareByDescending<BillCardItem> { it.customer.status.equals("ISOLATED", ignoreCase = true) }
                        .thenByDescending { it.isPastArrears }
                        .thenBy { PeriodUtils.parsePeriod(it.periodMonth).timeInMillis }
                        .thenBy { it.customer.dueDay }
                )

                billCards
            }.flowOn(Dispatchers.Default).collect { billCards ->
                _uiState.update { current ->
                    val filtered = applyFilter(billCards, current.searchQuery, current.dueFilter)
                    current.copy(
                        allBillCards = billCards,
                        filteredBillCards = filtered
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.activeStaff.collect { staffList ->
                _uiState.update { current ->
                    val staffToSelect = current.selectedStaff ?: staffList.firstOrNull()
                    current.copy(
                        staffList = staffList,
                        selectedStaff = staffToSelect
                    )
                }
            }
        }
    }

    private fun applyFilter(
        list: List<BillCardItem>,
        query: String,
        dueFilter: String
    ): List<BillCardItem> {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val queryTrimmed = query.trim()

        return list.filter { item ->
            val cust = item.customer
            val matchesQuery = queryTrimmed.isEmpty() ||
                    cust.name.contains(queryTrimmed, ignoreCase = true) ||
                    cust.customerCode.contains(queryTrimmed, ignoreCase = true) ||
                    cust.phone.contains(queryTrimmed) ||
                    cust.address.contains(queryTrimmed, ignoreCase = true) ||
                    cust.odpLocation.contains(queryTrimmed, ignoreCase = true) ||
                    item.periodMonth.contains(queryTrimmed, ignoreCase = true)

            val matchesFilter = when (dueFilter) {
                "ARREARS" -> item.isPastArrears
                "CURRENT_MONTH" -> item.isCurrentMonth
                "TODAY_OVERDUE" -> (cust.dueDay <= currentDay && item.isCurrentMonth) || item.isPastArrears
                "UPCOMING" -> cust.dueDay > currentDay && item.isCurrentMonth
                "ISOLATED" -> cust.status.equals("ISOLATED", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    private var searchDebounceJob: kotlinx.coroutines.Job? = null

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            val current = _uiState.value
            val filtered = applyFilter(current.allBillCards, "", current.dueFilter)
            _uiState.update { it.copy(filteredBillCards = filtered) }
        } else {
            searchDebounceJob = viewModelScope.launch(Dispatchers.Default) {
                kotlinx.coroutines.delay(180)
                val current = _uiState.value
                val filtered = applyFilter(current.allBillCards, query, current.dueFilter)
                _uiState.update { it.copy(filteredBillCards = filtered) }
            }
        }
    }

    fun setDueFilter(filter: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allBillCards, current.searchQuery, filter)
            current.copy(dueFilter = filter, filteredBillCards = filtered)
        }
    }

    // =========================================================================
    // MODAL CHECKOUT SINGLE-BILL (Membayar khusus periode kartu yang dipilih)
    // =========================================================================

    fun openCheckoutModal(billCard: BillCardItem) {
        viewModelScope.launch {
            val customer = billCard.customer
            val period = billCard.periodMonth

            // Validasi apakah tagihan periode ini sudah lunas secara lokal
            val localTrx = repository.findTransactionForPeriod(customer.id, period)
            if (localTrx != null) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Tagihan '${customer.name}' untuk periode $period SUDAH LUNAS (No. Faktur: ${localTrx.invoiceNumber}). Tidak dapat membayar dobel!"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    selectedBillCard = billCard,
                    selectedCustomer = customer,
                    selectedPeriodMonth = period,
                    isCheckoutModalOpen = true,
                    isConfirmed = false,
                    isProcessing = false,
                    adminFee = 0L,
                    discount = 0L,
                    errorMessage = null,
                    autoRestoredFromIsolated = false
                )
            }
        }
    }

    fun closeCheckoutModal() {
        _uiState.update {
            it.copy(
                isCheckoutModalOpen = false,
                selectedBillCard = null,
                selectedCustomer = null,
                selectedPeriodMonth = "",
                isConfirmed = false,
                isProcessing = false,
                adminFee = 0L,
                discount = 0L
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun selectCustomerById(customerId: Long) {
        viewModelScope.launch {
            // Pilih kartu tagihan pertama dari pelanggan ini (diutamakan yang tunggakan bulan lalu)
            val firstCard = _uiState.value.allBillCards.firstOrNull { it.customer.id == customerId }
            if (firstCard != null) {
                openCheckoutModal(firstCard)
            } else {
                val customer = repository.getCustomerById(customerId)
                if (customer != null) {
                    val currentPeriod = repository.getCurrentMonthPeriod()
                    val fallbackCard = BillCardItem(
                        id = "${customer.id}_$currentPeriod",
                        customer = customer,
                        periodMonth = currentPeriod,
                        isPastArrears = false,
                        isLastMonth = false,
                        isCurrentMonth = true,
                        isOverdue = customer.dueDay <= Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
                        fee = customer.monthlyFee
                    )
                    openCheckoutModal(fallbackCard)
                }
            }
        }
    }

    fun setPaymentMethod(method: String) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun setAdminFee(fee: Long) {
        _uiState.update { it.copy(adminFee = fee) }
    }

    fun setDiscount(disc: Long) {
        _uiState.update { current ->
            val maxFee = current.selectedCustomer?.monthlyFee ?: Long.MAX_VALUE
            val validDisc = disc.coerceIn(0L, maxFee)
            current.copy(discount = validDisc)
        }
    }

    fun setSelectedStaff(staff: StaffEntity) {
        _uiState.update { it.copy(selectedStaff = staff) }
    }

    fun setNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun calculateTotal(): Long {
        val state = _uiState.value
        val fee = state.selectedCustomer?.monthlyFee ?: 0L
        return maxOf(0L, fee + state.adminFee - state.discount)
    }

    fun confirmAndPrintTransaction() {
        val state = _uiState.value
        val customer = state.selectedCustomer ?: return
        val period = state.selectedPeriodMonth
        if (state.isProcessing || period.isBlank()) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            // 1. Cek lokal: Apakah sudah lunas di perangkat ini?
            val localTrx = repository.findTransactionForPeriod(customer.id, period)
            if (localTrx != null) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isCheckoutModalOpen = false,
                        errorMessage = "Tagihan '${customer.name}' periode $period SUDAH LUNAS di perangkat ini (No. Faktur: ${localTrx.invoiceNumber}). Pembayaran dobel dibatalkan!"
                    )
                }
                return@launch
            }

            // 2. Pre-flight check ke server MySQL secara instan: Apakah baru saja dibayar oleh HP lain?
            val sync = repository.mysqlSyncManager
            if (sync != null && sync.getApiUrl().isNotBlank()) {
                val serverCheck = sync.checkPaymentStatusOnServer(customer.id, period)
                if (serverCheck != null && serverCheck.isPaid) {
                    serverCheck.existingTransaction?.let { serverTrx ->
                        repository.insertTransactionSilently(serverTrx)
                    }
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isCheckoutModalOpen = false,
                            errorMessage = "Perhatian: Pelanggan '${customer.name}' periode $period BARU SAJA DIBAYAR di HP/Kasir lain (${serverCheck.cashierName ?: "Kasir"}, No. Faktur: ${serverCheck.invoiceNumber}). Invoice ganda dicegah!"
                        )
                    }
                    return@launch
                }
            }

            // 3. Buat entity transaksi baru khusus untuk periode kartu ini
            val invoiceNo = repository.generateInvoiceNumber()
            val total = calculateTotal()
            val cashier = state.selectedStaff?.name ?: "Kasir Utama"
            val cashierRole = state.selectedStaff?.role ?: "KASIR"

            val transaction = TransactionEntity(
                invoiceNumber = invoiceNo,
                customerId = customer.id,
                customerName = customer.name,
                customerCode = customer.customerCode,
                packageName = customer.packageName,
                monthlyFee = customer.monthlyFee,
                adminFee = state.adminFee,
                discount = state.discount,
                totalAmount = total,
                periodMonth = period,
                paymentMethod = state.paymentMethod,
                cashierName = cashier,
                cashierRole = cashierRole,
                notes = state.notes,
                paidAt = System.currentTimeMillis(),
                isPrinted = false
            )

            val insertResult = repository.insertTransaction(transaction)

            insertResult.fold(
                onSuccess = { trxId ->
                    // Otomatis pulihkan status jika sebelumnya ISOLATED
                    var wasRestored = false
                    if (customer.status.equals("ISOLATED", ignoreCase = true)) {
                        repository.updateCustomer(customer.copy(status = "ACTIVE"))
                        wasRestored = true
                    }

                    // Prepare Receipt Data khusus periode ini
                    val receiptData = EscPosHelper.ReceiptData(
                        businessName = printerManager.getBusinessName(),
                        businessAddress = printerManager.getBusinessAddress(),
                        businessPhone = printerManager.getBusinessPhone(),
                        invoiceNo = invoiceNo,
                        customerName = customer.name,
                        customerCode = customer.customerCode,
                        packageName = customer.packageName,
                        odpLocation = customer.odpLocation,
                        period = period,
                        monthlyFee = customer.monthlyFee,
                        adminFee = state.adminFee,
                        discount = state.discount,
                        totalAmount = total,
                        paymentMethod = state.paymentMethod,
                        cashierName = cashier,
                        template = printerManager.getReceiptTemplate(),
                        footerMessage = if (wasRestored) {
                            "Status internet Anda telah AKTIF kembali. Terima kasih!"
                        } else {
                            printerManager.getFooterMessage()
                        }
                    )

                    // Kirim ke printer Bluetooth Thermal
                    printerManager.printReceipt(receiptData)
                    repository.markTransactionPrinted(trxId)

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isConfirmed = true,
                            lastCompletedReceipt = receiptData,
                            autoRestoredFromIsolated = wasRestored
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isCheckoutModalOpen = false,
                            errorMessage = error.message ?: "Gagal memproses pembayaran: Terjadi kesalahan atau duplikasi."
                        )
                    }
                }
            )
        }
    }

    fun reprintLastReceipt() {
        val receipt = _uiState.value.lastCompletedReceipt ?: return
        viewModelScope.launch {
            printerManager.printReceipt(receipt)
        }
    }
}

class CashierViewModelFactory(
    private val repository: BillingRepository,
    private val printerManager: BluetoothPrinterManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CashierViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CashierViewModel(repository, printerManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
