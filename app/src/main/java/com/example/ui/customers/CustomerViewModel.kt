package com.example.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.PackagePlanEntity
import com.example.data.repository.BillingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CustomerUiState(
    val customers: List<CustomerEntity> = emptyList(),
    val filteredCustomers: List<CustomerEntity> = emptyList(),
    val availablePackages: List<PackagePlanEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // "ALL", "ACTIVE", "ISOLATED", "INACTIVE"
    val isLoading: Boolean = false,
    val isFormOpen: Boolean = false,
    val editingCustomer: CustomerEntity? = null,
    val isRefreshing: Boolean = false
)

class CustomerViewModel(
    private val repository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerUiState())
    val uiState: StateFlow<CustomerUiState> = _uiState.asStateFlow()

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
                onSuccess = { msg -> onComplete(true, "✓ Data pelanggan berhasil disinkronkan!") },
                onFailure = { err -> onComplete(false, err.localizedMessage ?: err.message ?: "Gagal sinkronisasi") }
            )
        }
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.allCustomers,
                repository.activePlans
            ) { customers, packages ->
                Pair(customers, packages)
            }.flowOn(Dispatchers.Default).collect { (customers, packages) ->
                _uiState.update { current ->
                    val filtered = applyFilter(customers, current.searchQuery, current.selectedFilter)
                    current.copy(
                        customers = customers,
                        filteredCustomers = filtered,
                        availablePackages = packages,
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
            val filtered = applyFilter(current.customers, "", current.selectedFilter)
            _uiState.update { it.copy(filteredCustomers = filtered) }
        } else {
            searchDebounceJob = viewModelScope.launch(Dispatchers.Default) {
                kotlinx.coroutines.delay(180)
                val current = _uiState.value
                val filtered = applyFilter(current.customers, query, current.selectedFilter)
                _uiState.update { it.copy(filteredCustomers = filtered) }
            }
        }
    }

    fun onFilterSelected(filter: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.customers, current.searchQuery, filter)
            current.copy(selectedFilter = filter, filteredCustomers = filtered)
        }
    }

    private fun applyFilter(
        list: List<CustomerEntity>,
        query: String,
        filter: String
    ): List<CustomerEntity> {
        return list.filter { item ->
            val matchesFilter = when (filter) {
                "ACTIVE" -> item.status == "ACTIVE"
                "ISOLATED" -> item.status == "ISOLATED"
                "INACTIVE" -> item.status == "INACTIVE"
                else -> true
            }
            val matchesQuery = query.isBlank() ||
                    item.name.contains(query, ignoreCase = true) ||
                    item.customerCode.contains(query, ignoreCase = true) ||
                    item.phone.contains(query) ||
                    item.address.contains(query, ignoreCase = true) ||
                    item.odpLocation.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(isFormOpen = true, editingCustomer = null) }
    }

    fun openEditDialog(customer: CustomerEntity) {
        _uiState.update { it.copy(isFormOpen = true, editingCustomer = customer) }
    }

    fun closeDialog() {
        _uiState.update { it.copy(isFormOpen = false, editingCustomer = null) }
    }

    fun saveCustomer(
        code: String,
        name: String,
        phone: String,
        address: String,
        odp: String,
        packageId: Long,
        dueDay: Int,
        ipOrPppoe: String,
        status: String
    ) {
        viewModelScope.launch {
            val selectedPlan = _uiState.value.availablePackages.firstOrNull { it.id == packageId }
            val planName = selectedPlan?.name ?: "Paket WiFi"
            val fee = selectedPlan?.price ?: 150000L

            val editing = _uiState.value.editingCustomer
            if (editing != null) {
                // Update
                val updated = editing.copy(
                    customerCode = code,
                    name = name,
                    phone = phone,
                    address = address,
                    odpLocation = odp,
                    packageId = packageId,
                    packageName = planName,
                    monthlyFee = fee,
                    dueDay = dueDay,
                    ipOrPppoe = ipOrPppoe,
                    status = status
                )
                repository.updateCustomer(updated)
            } else {
                // Create
                val newCode = if (code.isNotBlank()) code.trim() else generateCustomerCode()
                val newCustomer = CustomerEntity(
                    customerCode = newCode,
                    name = name,
                    phone = phone,
                    address = address,
                    odpLocation = odp,
                    packageId = packageId,
                    packageName = planName,
                    monthlyFee = fee,
                    dueDay = dueDay,
                    ipOrPppoe = ipOrPppoe,
                    status = status
                )
                repository.insertCustomer(newCustomer)
            }
            closeDialog()
        }
    }

    private fun generateCustomerCode(): String {
        val existingCodes = _uiState.value.customers.map { it.customerCode.trim() }.toSet()
        repeat(100) {
            val randomNum = kotlin.random.Random.nextInt(100000, 1000000)
            val candidate = "NET-$randomNum"
            if (!existingCodes.contains(candidate)) {
                return candidate
            }
        }
        val fallbackNum = kotlin.random.Random.nextInt(100000, 1000000)
        return "NET-$fallbackNum"
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
        }
    }

    fun toggleCustomerStatus(customer: CustomerEntity) {
        viewModelScope.launch {
            val newStatus = if (customer.status == "ACTIVE") "ISOLATED" else "ACTIVE"
            repository.updateCustomer(customer.copy(status = newStatus))
        }
    }
}

class CustomerViewModelFactory(private val repository: BillingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CustomerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CustomerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
