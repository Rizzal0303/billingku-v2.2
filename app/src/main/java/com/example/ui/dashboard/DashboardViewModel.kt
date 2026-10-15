package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.repository.BillingRepository
import com.example.util.PeriodUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardUiState(
    val totalRevenueMonth: Long = 0,
    val activeCustomerCount: Int = 0,
    val totalCustomerCount: Int = 0,
    val paidCustomerCount: Int = 0,
    val unpaidCustomerCount: Int = 0, // Pelanggan belum bayar bulan ini
    val totalUnpaidBillsCount: Int = 0, // Total nota tagihan di kasir (termasuk tunggakan lampau)
    val pastArrearsBillsCount: Int = 0, // Total nota tunggakan bulan lalu
    val totalArrearsAmount: Long = 0, // Total piutang seluruh tunggakan (sinkron dengan Kasir)
    val multiMonthArrearsCount: Int = 0, // Jumlah pelanggan yang menunggak > 1 bulan
    val isolatedCustomerCount: Int = 0, // Jumlah pelanggan terisolir
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val dueCustomers: List<CustomerEntity> = emptyList(),
    val customerArrearsMap: Map<Long, List<String>> = emptyMap(), // customerId -> unpaidPeriods
    val customerDueAmountMap: Map<Long, Long> = emptyMap(), // customerId -> totalDueAmount
    val currentPeriod: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

class DashboardViewModel(
    private val repository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun refreshFromMysql(onComplete: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val sync = repository.mysqlSyncManager
            if (sync == null || sync.getApiUrl().isBlank()) {
                _uiState.update { it.copy(isRefreshing = false) }
                onComplete(false, "Atur URL MySQL di Pengaturan untuk sinkronisasi otomatis multi-perangkat")
                return@launch
            }
            val result = sync.performFullAutoSync(force = true)
            _uiState.update { it.copy(isRefreshing = false) }
            result.fold(
                onSuccess = { msg -> onComplete(true, "✓ $msg") },
                onFailure = { err -> onComplete(false, err.localizedMessage ?: err.message ?: "Gagal sinkronisasi") }
            )
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.checkAndGenerateMonthlyBills()
        }
        loadDashboardData()
    }

    private fun loadDashboardData() {
        val currentPeriod = repository.getCurrentMonthPeriod()
        _uiState.update { it.copy(currentPeriod = currentPeriod, isLoading = true) }

        viewModelScope.launch(Dispatchers.Default) {
            combine(
                repository.getTotalRevenueByMonth(currentPeriod),
                repository.allCustomers,
                repository.allTransactions,
                repository.allBills
            ) { revenue, customers, transactions, bills ->
                val transactionsByCustomer = transactions.groupBy { it.customerId }
                val billsByCustomer = bills.groupBy { it.customerId }

                // Pelanggan yang ditagih: ACTIVE dan ISOLATED (sama persis dengan Kasir)
                val billableCustomers = customers.filter {
                    it.status.equals("ACTIVE", ignoreCase = true) || it.status.equals("ISOLATED", ignoreCase = true)
                }
                val activeCustomers = customers.filter { it.status.equals("ACTIVE", ignoreCase = true) }
                val isolatedCustomers = customers.filter { it.status.equals("ISOLATED", ignoreCase = true) }

                val dueCustomersList = mutableListOf<CustomerEntity>()
                val arrearsMap = mutableMapOf<Long, List<String>>()
                val dueAmountMap = mutableMapOf<Long, Long>()

                var totalArrearsSum = 0L
                var totalBillsCount = 0
                var pastBillsCount = 0
                var multiMonthCount = 0
                val currentMonthUnpaidCustomerIds = mutableSetOf<Long>()
                val currentMonthPaidCustomerIds = mutableSetOf<Long>()

                for (cust in billableCustomers) {
                    val custTransactions = transactionsByCustomer[cust.id] ?: emptyList()
                    val custBills = billsByCustomer[cust.id] ?: emptyList()
                    val paidPeriods = (
                        custTransactions.map { it.periodMonth } +
                        custBills.filter { it.status.equals("PAID", ignoreCase = true) }.map { it.periodMonth }
                    ).toSet()

                    // Dapatkan periode yang seharusnya dibayar dari saat daftar sampai bulan sekarang (maksimal 6 bulan lampau)
                    // Logika dinamis ini sama persis dengan yang ada di CashierViewModel
                    val eligiblePeriods = PeriodUtils.getEligiblePeriodsForCustomer(cust.createdAt, maxPastMonths = 6)
                    val unpaidPeriods = eligiblePeriods.filter { !paidPeriods.contains(it) }

                    if (unpaidPeriods.contains(currentPeriod)) {
                        currentMonthUnpaidCustomerIds.add(cust.id)
                    } else if (eligiblePeriods.contains(currentPeriod) || paidPeriods.contains(currentPeriod)) {
                        currentMonthPaidCustomerIds.add(cust.id)
                    }

                    if (unpaidPeriods.isNotEmpty()) {
                        dueCustomersList.add(cust)
                        arrearsMap[cust.id] = unpaidPeriods
                        val custTotalDue = cust.monthlyFee * unpaidPeriods.size
                        dueAmountMap[cust.id] = custTotalDue
                        totalArrearsSum += custTotalDue
                        totalBillsCount += unpaidPeriods.size

                        val pastCount = unpaidPeriods.count { PeriodUtils.isPastPeriod(it) }
                        pastBillsCount += pastCount

                        if (unpaidPeriods.size > 1) {
                            multiMonthCount++
                        }
                    }
                }

                // Urutkan prioritas penagihan sama persis dengan Kasir:
                // 1. Tagihan terisolir paling atas
                // 2. Yang menunggak > 1 bulan / nunggak bulan lalu
                // 3. Jumlah tunggakan terbanyak
                // 4. Tanggal jatuh tempo terdekat
                dueCustomersList.sortWith(
                    compareByDescending<CustomerEntity> { it.status.equals("ISOLATED", ignoreCase = true) }
                        .thenByDescending { (arrearsMap[it.id]?.size ?: 0) > 1 }
                        .thenByDescending { arrearsMap[it.id]?.size ?: 0 }
                        .thenBy { it.dueDay }
                )

                DashboardUiState(
                    totalRevenueMonth = revenue,
                    activeCustomerCount = activeCustomers.size,
                    totalCustomerCount = customers.size,
                    paidCustomerCount = currentMonthPaidCustomerIds.size,
                    unpaidCustomerCount = currentMonthUnpaidCustomerIds.size,
                    totalUnpaidBillsCount = totalBillsCount,
                    pastArrearsBillsCount = pastBillsCount,
                    totalArrearsAmount = totalArrearsSum,
                    multiMonthArrearsCount = multiMonthCount,
                    isolatedCustomerCount = isolatedCustomers.size,
                    recentTransactions = transactions.take(6),
                    dueCustomers = dueCustomersList,
                    customerArrearsMap = arrearsMap,
                    customerDueAmountMap = dueAmountMap,
                    currentPeriod = currentPeriod,
                    isLoading = false
                )
            }.flowOn(Dispatchers.Default).collect { newState ->
                _uiState.value = newState
            }
        }
    }
}

class DashboardViewModelFactory(private val repository: BillingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
