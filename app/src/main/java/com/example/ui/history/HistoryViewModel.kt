package com.example.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.repository.BillingRepository
import com.example.printer.BluetoothPrinterManager
import com.example.printer.EscPosHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val searchQuery: String = "",
    val methodFilter: String = "ALL", // "ALL", "TUNAI", "TRANSFER", "QRIS"
    val totalRevenue: Long = 0,
    val transactionCount: Int = 0,
    val selectedTransactionForReceipt: EscPosHelper.ReceiptData? = null,
    val transactionToDelete: TransactionEntity? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

class HistoryViewModel(
    private val repository: BillingRepository,
    private val printerManager: BluetoothPrinterManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

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
                onSuccess = { msg -> onComplete(true, "✓ Riwayat transaksi berhasil disinkronkan!") },
                onFailure = { err -> onComplete(false, err.localizedMessage ?: err.message ?: "Gagal sinkronisasi") }
            )
        }
    }

    init {
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            repository.allTransactions.flowOn(Dispatchers.Default).collect { list ->
                _uiState.update { current ->
                    val filtered = applyFilters(list, current.searchQuery, current.methodFilter)
                    current.copy(
                        transactions = list,
                        filteredTransactions = filtered,
                        totalRevenue = list.sumOf { it.totalAmount },
                        transactionCount = list.size,
                        isLoading = false
                    )
                }
            }
        }
    }

    private var searchDebounceJob: kotlinx.coroutines.Job? = null

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            val current = _uiState.value
            val filtered = applyFilters(current.transactions, "", current.methodFilter)
            _uiState.update { it.copy(filteredTransactions = filtered) }
        } else {
            searchDebounceJob = viewModelScope.launch(Dispatchers.Default) {
                kotlinx.coroutines.delay(180)
                val current = _uiState.value
                val filtered = applyFilters(current.transactions, query, current.methodFilter)
                _uiState.update { it.copy(filteredTransactions = filtered) }
            }
        }
    }

    fun onMethodFilterSelected(method: String) {
        _uiState.update { current ->
            val filtered = applyFilters(current.transactions, current.searchQuery, method)
            current.copy(methodFilter = method, filteredTransactions = filtered)
        }
    }

    private fun applyFilters(
        list: List<TransactionEntity>,
        query: String,
        method: String
    ): List<TransactionEntity> {
        return list.filter { trx ->
            val matchesMethod = if (method == "ALL") true else trx.paymentMethod == method
            val matchesQuery = if (query.isBlank()) true else {
                trx.customerName.contains(query, ignoreCase = true) ||
                        trx.customerCode.contains(query, ignoreCase = true) ||
                        trx.invoiceNumber.contains(query, ignoreCase = true) ||
                        trx.packageName.contains(query, ignoreCase = true) ||
                        trx.cashierName.contains(query, ignoreCase = true)
            }
            matchesMethod && matchesQuery
        }
    }

    // Action 1: Hapus Transaksi
    fun confirmDeleteTransaction(transaction: TransactionEntity) {
        _uiState.update { it.copy(transactionToDelete = transaction) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(transactionToDelete = null) }
    }

    fun executeDeleteTransaction() {
        val trx = _uiState.value.transactionToDelete ?: return
        viewModelScope.launch {
            repository.deleteTransaction(trx)
            _uiState.update { it.copy(transactionToDelete = null) }
        }
    }

    private fun buildReceiptData(trx: TransactionEntity): EscPosHelper.ReceiptData {
        return EscPosHelper.ReceiptData(
            businessName = printerManager.getBusinessName(),
            businessAddress = printerManager.getBusinessAddress(),
            businessPhone = printerManager.getBusinessPhone(),
            invoiceNo = trx.invoiceNumber,
            customerName = trx.customerName,
            customerCode = trx.customerCode,
            packageName = trx.packageName,
            period = trx.periodMonth,
            monthlyFee = trx.monthlyFee,
            adminFee = trx.adminFee,
            discount = trx.discount,
            totalAmount = trx.totalAmount,
            paymentMethod = trx.paymentMethod,
            cashierName = trx.cashierName,
            date = trx.paidAt,
            template = printerManager.getReceiptTemplate(),
            footerMessage = printerManager.getFooterMessage()
        )
    }

    // Hanya membuka detail / invoice pelanggan tanpa langsung print
    fun openInvoiceDetail(trx: TransactionEntity) {
        val receiptData = buildReceiptData(trx)
        _uiState.update { it.copy(selectedTransactionForReceipt = receiptData) }
    }

    // Action 2: Cetak Struk (mengirim pekerjaan cetak ke printer Bluetooth thermal)
    fun printReceipt(trx: TransactionEntity) {
        val receiptData = buildReceiptData(trx)
        _uiState.update { it.copy(selectedTransactionForReceipt = receiptData) }

        viewModelScope.launch {
            printerManager.printReceipt(receiptData)
            repository.markTransactionPrinted(trx.id)
        }
    }

    fun dismissReceiptDialog() {
        _uiState.update { it.copy(selectedTransactionForReceipt = null) }
    }

    // Action 3: Dapatkan nomor WhatsApp pelanggan untuk transaksi
    suspend fun getCustomerPhone(customerId: Long): String? {
        val customer = repository.getCustomerById(customerId)
        return customer?.phone
    }
}

class HistoryViewModelFactory(
    private val repository: BillingRepository,
    private val printerManager: BluetoothPrinterManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository, printerManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
