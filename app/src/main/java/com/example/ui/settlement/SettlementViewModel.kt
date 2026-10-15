package com.example.ui.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.SettlementBatchEntity
import com.example.data.local.entity.StaffEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.repository.BillingRepository
import com.example.printer.BluetoothPrinterManager
import com.example.printer.EscPosHelper
import com.example.printer.PrinterStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SettlementUiState(
    val currentTab: Int = 0, // 0 = Pencocokan & Setoran, 1 = Riwayat Setoran
    val periodMonth: String = "",
    val calendarMonthOffset: Int = 0,
    val searchQuery: String = "",
    val statusFilter: String = "ALL", // "ALL", "UNDEPOSITED", "DEPOSITED", "CASH_ONLY"
    val allTransactionsInMonth: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val selectedTransactionIds: Set<Long> = emptySet(),
    // Summary
    val totalRevenueInMonth: Long = 0,
    val totalPaidCustomersCount: Int = 0,
    val undepositedTotal: Long = 0,
    val undepositedCashTotal: Long = 0,
    val undepositedNonCashTotal: Long = 0,
    val undepositedCount: Int = 0,
    val depositedTotal: Long = 0,
    val depositedCount: Int = 0,
    // Cash Drawer Physical Money Reconciliation Tool
    val isReconciliationExpanded: Boolean = true,
    val physicalCashInput: String = "",
    val denomination100k: Int = 0,
    val denomination50k: Int = 0,
    val denomination20k: Int = 0,
    val denomination10k: Int = 0,
    val denomination5k: Int = 0,
    val denomination2k: Int = 0,
    val denomination1k: Int = 0,
    // Deposit Confirmation Dialog
    val isDepositDialogOpen: Boolean = false,
    val depositRecipientName: String = "Owner / Admin",
    val depositNotes: String = "",
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Staff Members from Database
    val staffList: List<StaffEntity> = emptyList(),
    // Success / Completed Batch Dialog
    val lastCompletedBatch: SettlementBatchEntity? = null,
    val lastCompletedBatchItems: List<TransactionEntity> = emptyList(),
    // Batch History
    val settlementBatches: List<SettlementBatchEntity> = emptyList(),
    val selectedBatchForDetail: SettlementBatchEntity? = null,
    val selectedBatchItems: List<TransactionEntity> = emptyList(),
    val isLoadingBatchDetails: Boolean = false,
    // Printer State
    val printerStatus: PrinterStatus = PrinterStatus.Idle,
    val isRefreshing: Boolean = false
) {
    val totalPhysicalCashCalculated: Long
        get() {
            val fromDenominations = (denomination100k * 100000L) +
                    (denomination50k * 50000L) +
                    (denomination20k * 20000L) +
                    (denomination10k * 10000L) +
                    (denomination5k * 5000L) +
                    (denomination2k * 2000L) +
                    (denomination1k * 1000L)
            val manual = physicalCashInput.toLongOrNull() ?: 0L
            return maxOf(fromDenominations, manual)
        }

    val cashReconciliationDifference: Long
        get() = totalPhysicalCashCalculated - undepositedCashTotal

    val selectedTransactions: List<TransactionEntity>
        get() = allTransactionsInMonth.filter { selectedTransactionIds.contains(it.id) }

    val selectedTotalAmount: Long
        get() = selectedTransactions.sumOf { it.totalAmount }

    val selectedCashAmount: Long
        get() = selectedTransactions.filter { it.paymentMethod.equals("TUNAI", ignoreCase = true) }.sumOf { it.totalAmount }

    val selectedNonCashAmount: Long
        get() = selectedTotalAmount - selectedCashAmount
}

class SettlementViewModel(
    private val repository: BillingRepository,
    private val printerManager: BluetoothPrinterManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettlementUiState())
    val uiState: StateFlow<SettlementUiState> = _uiState.asStateFlow()

    private val calendar = Calendar.getInstance()

    init {
        updatePeriodFromCalendar()
        observePrinterStatus()
        observeSettlementBatches()
        observeStaffMembers()
    }

    private fun observeStaffMembers() {
        viewModelScope.launch {
            repository.allStaff.collect { staffList ->
                val admin = staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) && it.isActive }
                    ?: staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) }
                val defaultRecipient = if (admin != null) {
                    "${admin.name} (Owner)"
                } else {
                    "Owner / Admin"
                }

                _uiState.update { current ->
                    current.copy(
                        staffList = staffList,
                        depositRecipientName = if (current.depositRecipientName == "Owner / Admin" || current.depositRecipientName == "Budi Hartono (Owner)") {
                            defaultRecipient
                        } else {
                            current.depositRecipientName
                        }
                    )
                }
            }
        }
    }

    fun refreshFromMysql(onComplete: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val sync = repository.mysqlSyncManager
            if (sync == null || sync.getApiUrl().isBlank()) {
                _uiState.update { it.copy(isRefreshing = false) }
                onComplete(false, "Atur URL MySQL di Pengaturan untuk sinkronisasi 2 HP")
                return@launch
            }
            val result = sync.performFullAutoSync(force = true)
            _uiState.update { it.copy(isRefreshing = false) }
            result.fold(
                onSuccess = { msg -> onComplete(true, "✓ Data setoran & transaksi berhasil disinkronkan!") },
                onFailure = { err -> onComplete(false, err.localizedMessage ?: err.message ?: "Gagal sinkronisasi") }
            )
        }
    }

    private fun observePrinterStatus() {
        viewModelScope.launch {
            printerManager.printerStatus.collect { status ->
                _uiState.update { it.copy(printerStatus = status) }
            }
        }
    }

    private fun observeSettlementBatches() {
        viewModelScope.launch {
            repository.allSettlementBatches.collect { batches ->
                _uiState.update { it.copy(settlementBatches = batches) }
            }
        }
    }

    private fun updatePeriodFromCalendar() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        val period = sdf.format(calendar.time)
        _uiState.update {
            it.copy(
                periodMonth = period,
                selectedTransactionIds = emptySet()
            )
        }
        observeTransactionsForPeriod(period)
    }

    fun nextMonth() {
        calendar.add(Calendar.MONTH, 1)
        val offset = _uiState.value.calendarMonthOffset + 1
        _uiState.update { it.copy(calendarMonthOffset = offset) }
        updatePeriodFromCalendar()
    }

    fun previousMonth() {
        calendar.add(Calendar.MONTH, -1)
        val offset = _uiState.value.calendarMonthOffset - 1
        _uiState.update { it.copy(calendarMonthOffset = offset) }
        updatePeriodFromCalendar()
    }

    fun resetToCurrentMonth() {
        calendar.time = Date()
        _uiState.update { it.copy(calendarMonthOffset = 0) }
        updatePeriodFromCalendar()
    }

    private fun observeTransactionsForPeriod(period: String) {
        viewModelScope.launch {
            repository.getTransactionsByMonth(period).collect { list ->
                _uiState.update { state ->
                    val undeposited = list.filter { !it.isDeposited }
                    val deposited = list.filter { it.isDeposited }

                    val undepositedTotal = undeposited.sumOf { it.totalAmount }
                    val undepositedCash = undeposited.filter { it.paymentMethod.equals("TUNAI", ignoreCase = true) }.sumOf { it.totalAmount }
                    val undepositedNonCash = undepositedTotal - undepositedCash

                    val depositedTotal = deposited.sumOf { it.totalAmount }
                    val totalRevenue = list.sumOf { it.totalAmount }

                    val filtered = applyFilterAndSearch(
                        list = list,
                        statusFilter = state.statusFilter,
                        searchQuery = state.searchQuery
                    )

                    state.copy(
                        allTransactionsInMonth = list,
                        filteredTransactions = filtered,
                        totalRevenueInMonth = totalRevenue,
                        totalPaidCustomersCount = list.size,
                        undepositedTotal = undepositedTotal,
                        undepositedCashTotal = undepositedCash,
                        undepositedNonCashTotal = undepositedNonCash,
                        undepositedCount = undeposited.size,
                        depositedTotal = depositedTotal,
                        depositedCount = deposited.size
                    )
                }
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(currentTab = index) }
    }

    fun setStatusFilter(filter: String) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.allTransactionsInMonth, filter, state.searchQuery)
            state.copy(statusFilter = filter, filteredTransactions = filtered)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.allTransactionsInMonth, state.statusFilter, query)
            state.copy(searchQuery = query, filteredTransactions = filtered)
        }
    }

    private fun applyFilterAndSearch(
        list: List<TransactionEntity>,
        statusFilter: String,
        searchQuery: String
    ): List<TransactionEntity> {
        var result = list
        result = when (statusFilter) {
            "UNDEPOSITED" -> result.filter { !it.isDeposited }
            "DEPOSITED" -> result.filter { it.isDeposited }
            "CASH_ONLY" -> result.filter { it.paymentMethod.equals("TUNAI", ignoreCase = true) }
            else -> result
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            result = result.filter {
                it.customerName.lowercase(Locale.getDefault()).contains(q) ||
                        it.customerCode.lowercase(Locale.getDefault()).contains(q) ||
                        it.invoiceNumber.lowercase(Locale.getDefault()).contains(q) ||
                        it.packageName.lowercase(Locale.getDefault()).contains(q)
            }
        }

        return result
    }

    fun toggleTransactionSelection(transaction: TransactionEntity) {
        // Enforce constraint: Already deposited for this month cannot be re-deposited!
        if (transaction.isDeposited) {
            _uiState.update {
                it.copy(
                    errorMessage = "Pelanggan ${transaction.customerName} sudah disetorkan untuk bulan ${transaction.periodMonth} dan tidak dapat disetorkan ulang kecuali beda bulan selanjutnya."
                )
            }
            return
        }

        _uiState.update { state ->
            val current = state.selectedTransactionIds.toMutableSet()
            if (current.contains(transaction.id)) {
                current.remove(transaction.id)
            } else {
                current.add(transaction.id)
            }
            state.copy(selectedTransactionIds = current, errorMessage = null)
        }
    }

    fun selectAllUndeposited() {
        val undepositedIds = _uiState.value.allTransactionsInMonth
            .filter { !it.isDeposited }
            .map { it.id }
            .toSet()

        _uiState.update { it.copy(selectedTransactionIds = undepositedIds) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTransactionIds = emptySet()) }
    }

    // Physical Cash Drawer Tools
    fun toggleReconciliationExpanded() {
        _uiState.update { it.copy(isReconciliationExpanded = !it.isReconciliationExpanded) }
    }

    fun onPhysicalCashInputChanged(input: String) {
        val filtered = input.filter { it.isDigit() }
        _uiState.update { it.copy(physicalCashInput = filtered) }
    }

    fun updateDenomination(denom: Int, delta: Int) {
        _uiState.update { state ->
            when (denom) {
                100000 -> state.copy(denomination100k = maxOf(0, state.denomination100k + delta))
                50000 -> state.copy(denomination50k = maxOf(0, state.denomination50k + delta))
                20000 -> state.copy(denomination20k = maxOf(0, state.denomination20k + delta))
                10000 -> state.copy(denomination10k = maxOf(0, state.denomination10k + delta))
                5000 -> state.copy(denomination5k = maxOf(0, state.denomination5k + delta))
                2000 -> state.copy(denomination2k = maxOf(0, state.denomination2k + delta))
                1000 -> state.copy(denomination1k = maxOf(0, state.denomination1k + delta))
                else -> state
            }
        }
    }

    fun resetDenominations() {
        _uiState.update {
            it.copy(
                denomination100k = 0,
                denomination50k = 0,
                denomination20k = 0,
                denomination10k = 0,
                denomination5k = 0,
                denomination2k = 0,
                denomination1k = 0,
                physicalCashInput = ""
            )
        }
    }

    fun autoFillPhysicalCashMatching() {
        _uiState.update { state ->
            val cashTotal = state.undepositedCashTotal
            state.copy(
                physicalCashInput = if (cashTotal > 0) cashTotal.toString() else "0",
                denomination100k = 0,
                denomination50k = 0,
                denomination20k = 0,
                denomination10k = 0,
                denomination5k = 0,
                denomination2k = 0,
                denomination1k = 0
            )
        }
    }

    // Process Settlement
    fun openDepositDialog(customRecipient: String? = null) {
        if (_uiState.value.selectedTransactionIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Pilih minimal satu data transaksi pelanggan yang akan disetorkan.") }
            return
        }

        val resolvedRecipient = customRecipient ?: run {
            val admin = _uiState.value.staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) && it.isActive }
                ?: _uiState.value.staffList.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) }
            if (admin != null) {
                "${admin.name} (Owner)"
            } else if (_uiState.value.depositRecipientName.isNotBlank() && !_uiState.value.depositRecipientName.contains("Budi Hartono")) {
                _uiState.value.depositRecipientName
            } else {
                "Owner / Admin"
            }
        }

        _uiState.update {
            it.copy(
                isDepositDialogOpen = true,
                depositRecipientName = resolvedRecipient,
                depositNotes = "",
                errorMessage = null
            )
        }
    }

    fun closeDepositDialog() {
        _uiState.update { it.copy(isDepositDialogOpen = false) }
    }

    fun setDepositRecipientName(name: String) {
        _uiState.update { it.copy(depositRecipientName = name) }
    }

    fun setDepositNotes(notes: String) {
        _uiState.update { it.copy(depositNotes = notes) }
    }

    fun processSettlement(cashierName: String) {
        val state = _uiState.value
        val selectedItems = state.selectedTransactions
        if (selectedItems.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tidak ada transaksi yang dipilih.") }
            return
        }

        // Validate strictly: None are already deposited
        val already = selectedItems.filter { it.isDeposited }
        if (already.isNotEmpty()) {
            val names = already.joinToString { it.customerName }
            _uiState.update {
                it.copy(
                    errorMessage = "Transaksi untuk pelanggan ($names) sudah pernah disetorkan untuk bulan ${state.periodMonth} dan tidak dapat disetorkan ulang!",
                    isDepositDialogOpen = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val result = repository.createSettlementBatch(
                periodMonth = state.periodMonth,
                cashierName = cashierName,
                recipientName = state.depositRecipientName,
                selectedTransactions = selectedItems,
                notes = state.depositNotes
            )

            result.onSuccess { batch ->
                _uiState.update { current ->
                    current.copy(
                        isProcessing = false,
                        isDepositDialogOpen = false,
                        selectedTransactionIds = emptySet(),
                        lastCompletedBatch = batch,
                        lastCompletedBatchItems = selectedItems,
                        successMessage = "Setoran berhasil diproses & dikunci! No. Batch: ${batch.batchNumber}"
                    )
                }
                resetDenominations()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = err.message ?: "Terjadi kesalahan saat memproses setoran."
                    )
                }
            }
        }
    }

    fun dismissSuccessDialog() {
        _uiState.update { it.copy(lastCompletedBatch = null, lastCompletedBatchItems = emptyList()) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Detail & Printing
    fun openBatchDetails(batch: SettlementBatchEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedBatchForDetail = batch, isLoadingBatchDetails = true) }
            val items = repository.getTransactionsByBatchIdSync(batch.id)
            _uiState.update { it.copy(selectedBatchItems = items, isLoadingBatchDetails = false) }
        }
    }

    fun closeBatchDetails() {
        _uiState.update { it.copy(selectedBatchForDetail = null, selectedBatchItems = emptyList()) }
    }

    fun printSettlementReceipt(batch: SettlementBatchEntity, items: List<TransactionEntity>) {
        viewModelScope.launch {
            val receiptData = EscPosHelper.SettlementReceiptData(
                businessName = printerManager.getBusinessName(),
                businessAddress = printerManager.getBusinessAddress(),
                businessPhone = printerManager.getBusinessPhone(),
                batchNumber = batch.batchNumber,
                periodMonth = batch.periodMonth,
                cashierName = batch.cashierName,
                recipientName = batch.recipientName,
                date = batch.createdAt,
                totalAmount = batch.totalAmount,
                totalCashAmount = batch.totalCashAmount,
                totalNonCashAmount = batch.totalNonCashAmount,
                customerCount = batch.customerCount,
                items = items,
                notes = batch.notes
            )
            printerManager.printSettlementReceipt(receiptData)
        }
    }

    fun getWhatsAppShareText(batch: SettlementBatchEntity, items: List<TransactionEntity>): String {
        val receiptData = EscPosHelper.SettlementReceiptData(
            businessName = printerManager.getBusinessName(),
            businessAddress = printerManager.getBusinessAddress(),
            businessPhone = printerManager.getBusinessPhone(),
            batchNumber = batch.batchNumber,
            periodMonth = batch.periodMonth,
            cashierName = batch.cashierName,
            recipientName = batch.recipientName,
            date = batch.createdAt,
            totalAmount = batch.totalAmount,
            totalCashAmount = batch.totalCashAmount,
            totalNonCashAmount = batch.totalNonCashAmount,
            customerCount = batch.customerCount,
            items = items,
            notes = batch.notes
        )
        return EscPosHelper.generateWhatsAppSettlementText(receiptData)
    }
}

class SettlementViewModelFactory(
    private val repository: BillingRepository,
    private val printerManager: BluetoothPrinterManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettlementViewModel::class.java)) {
            return SettlementViewModel(repository, printerManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
