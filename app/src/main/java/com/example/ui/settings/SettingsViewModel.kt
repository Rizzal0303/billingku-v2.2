package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AnnouncementEntity
import com.example.data.local.entity.PackagePlanEntity
import com.example.data.local.entity.StaffEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.repository.BillingRepository
import com.example.printer.BluetoothPrinterManager
import com.example.printer.EscPosHelper
import com.example.printer.PairedPrinter
import com.example.printer.PrinterStatus
import com.example.ui.settings.reports.DailyChartPoint
import com.example.ui.settings.reports.PeriodFilterState
import com.example.ui.settings.reports.ReportExportHelper
import com.example.ui.settings.reports.ReportPeriodType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FinancialReportData(
    val totalRevenue: Long = 0,
    val transactionCount: Int = 0,
    val cashTotal: Long = 0,
    val transferTotal: Long = 0,
    val qrisTotal: Long = 0,
    val staffBreakdown: Map<String, Long> = emptyMap(),
    val transactions: List<TransactionEntity> = emptyList(),
    val chartPoints: List<DailyChartPoint> = emptyList(),
    val peakPoint: DailyChartPoint? = null,
    val undepositedCashTotal: Long = 0
)

data class SettingsUiState(
    val currentTab: Int = -1, // -1: Hub Overview, 0: Printer, 1: Paket Internet, 2: Staff, 3: Laporan
    // Printer Settings
    val businessName: String = "",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val footerMessage: String = "",
    val receiptTemplate: String = "MODERN",
    val isAutoCutEnabled: Boolean = false,
    val feedLines: Int = 0,
    val pairedPrinters: List<PairedPrinter> = emptyList(),
    val selectedPrinterMac: String? = null,
    val selectedPrinterName: String? = null,
    val printerStatus: PrinterStatus = PrinterStatus.Idle,
    // Master Packages
    val packages: List<PackagePlanEntity> = emptyList(),
    val editingPackage: PackagePlanEntity? = null,
    val isPackageDialogOpen: Boolean = false,
    // Master Staff
    val staffList: List<StaffEntity> = emptyList(),
    val editingStaff: StaffEntity? = null,
    val isStaffDialogOpen: Boolean = false,
    // Financial Reports & Period Filter
    val reportData: FinancialReportData = FinancialReportData(),
    val periodFilter: PeriodFilterState = PeriodFilterState(type = ReportPeriodType.MONTHLY, offset = 0),
    val selectedChartPoint: DailyChartPoint? = null,
    val currentPeriod: String = "",
    val customerCount: Int = 0,
    // MySQL Server REST API Settings
    val mysqlApiUrl: String = "",
    val mysqlPublicUrl: String = "",
    val mysqlLocalUrl: String = "",
    val mysqlEndpointMode: String = "AUTO",
    val activeEndpointSummary: String = "",
    val isSpeedTesting: Boolean = false,
    val speedTestResult: String? = null,
    val mysqlApiKey: String = "billingku_secret_203000",
    val mysqlAutoSync: Boolean = true,
    val mysqlLastSyncTime: String = "",
    val mysqlLastSyncStatus: String = "",
    val isUsingEmbeddedDefaults: Boolean = false,
    val isMysqlTesting: Boolean = false,
    val isMysqlSyncing: Boolean = false,
    val pendingSyncCount: Int = 0,
    // Customer Announcements & Broadcasts (Net-pay Integration)
    val announcements: List<AnnouncementEntity> = emptyList(),
    val activeAnnouncementsCount: Int = 0,
    val isAnnouncementSyncing: Boolean = false,
    val announcementFeedback: String? = null,
    // Local File Backup & Restore
    val isExportingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val lastBackupFileName: String = "",
    val lastBackupTime: String = "",
    val lastBackupSize: String = "",
    val lastBackupRecordCount: String = "",
    val lastExportedFile: java.io.File? = null
)

class SettingsViewModel(
    val repository: BillingRepository,
    private val printerManager: BluetoothPrinterManager,
    private val mysqlSyncManager: com.example.data.sync.MysqlSyncManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeData()
    }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                businessName = printerManager.getBusinessName(),
                businessAddress = printerManager.getBusinessAddress(),
                businessPhone = printerManager.getBusinessPhone(),
                footerMessage = printerManager.getFooterMessage(),
                receiptTemplate = printerManager.getReceiptTemplate(),
                isAutoCutEnabled = printerManager.isAutoCutEnabled(),
                feedLines = printerManager.getFeedLines(),
                selectedPrinterMac = printerManager.getSelectedPrinterAddress(),
                selectedPrinterName = printerManager.getSelectedPrinterName(),
                currentPeriod = repository.getCurrentMonthPeriod(),
                mysqlApiUrl = mysqlSyncManager?.getApiUrl() ?: "",
                mysqlPublicUrl = mysqlSyncManager?.getPublicApiUrl() ?: "",
                mysqlLocalUrl = mysqlSyncManager?.getLocalApiUrl() ?: "",
                mysqlEndpointMode = mysqlSyncManager?.getEndpointMode() ?: "AUTO",
                activeEndpointSummary = mysqlSyncManager?.activeEndpointInfoState?.value?.statusDescription ?: "",
                mysqlApiKey = mysqlSyncManager?.getApiKey() ?: "billingku_secret_203000",
                mysqlAutoSync = mysqlSyncManager?.isAutoSyncEnabled() ?: true,
                mysqlLastSyncTime = mysqlSyncManager?.getLastSyncFormatted() ?: "",
                mysqlLastSyncStatus = mysqlSyncManager?.getLastSyncStatus() ?: "Belum pernah sinkronisasi",
                isUsingEmbeddedDefaults = mysqlSyncManager?.isUsingEmbeddedDefaults() ?: false
            )
        }
        refreshPairedPrinters()
    }

    private var cachedAllTransactions: List<TransactionEntity> = emptyList()

    private fun observeData() {
        mysqlSyncManager?.let { sync ->
            viewModelScope.launch {
                sync.activeEndpointInfoState.collect { info ->
                    _uiState.update { it.copy(activeEndpointSummary = info.statusDescription) }
                }
            }
        }

        viewModelScope.launch {
            repository.totalCustomerCount.collect { count ->
                _uiState.update { it.copy(customerCount = count) }
            }
        }

        viewModelScope.launch {
            repository.pendingSyncCount.collect { count ->
                _uiState.update { it.copy(pendingSyncCount = count) }
            }
        }

        viewModelScope.launch {
            repository.allPlans.collect { list ->
                _uiState.update { it.copy(packages = list) }
            }
        }

        viewModelScope.launch {
            repository.allStaff.collect { list ->
                _uiState.update { it.copy(staffList = list) }
            }
        }

        viewModelScope.launch {
            repository.allTransactions.collect { transactions ->
                cachedAllTransactions = transactions
                recalculateReportData()
            }
        }

        viewModelScope.launch {
            printerManager.printerStatus.collect { status ->
                _uiState.update { it.copy(printerStatus = status) }
            }
        }

        viewModelScope.launch {
            repository.allAnnouncements.collect { list ->
                _uiState.update { it.copy(announcements = list) }
            }
        }

        viewModelScope.launch {
            repository.activeAnnouncements.collect { list ->
                _uiState.update { it.copy(activeAnnouncementsCount = list.size) }
            }
        }
    }

    fun setPeriodFilterType(type: ReportPeriodType) {
        _uiState.update {
            it.copy(
                periodFilter = it.periodFilter.copy(type = type, offset = 0),
                selectedChartPoint = null
            )
        }
        recalculateReportData()
    }

    fun navigatePeriodOffset(delta: Int) {
        _uiState.update {
            val newOffset = it.periodFilter.offset + delta
            it.copy(
                periodFilter = it.periodFilter.copy(offset = newOffset),
                selectedChartPoint = null
            )
        }
        recalculateReportData()
    }

    fun resetPeriodToCurrent() {
        _uiState.update {
            it.copy(
                periodFilter = it.periodFilter.copy(offset = 0),
                selectedChartPoint = null
            )
        }
        recalculateReportData()
    }

    fun setSelectedChartPoint(point: DailyChartPoint?) {
        _uiState.update { it.copy(selectedChartPoint = point) }
    }

    fun printCurrentPeriodReport(printedBy: String = "Admin", onResult: (Boolean, String) -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            val res = ReportExportHelper.printThermalReport(
                printerManager = printerManager,
                businessName = state.businessName,
                periodTitle = state.periodFilter.getDisplayRangeTitle(),
                periodType = state.periodFilter.type,
                reportData = state.reportData,
                printedBy = printedBy
            )
            res.fold(
                onSuccess = { onResult(true, "Laporan berhasil dicetak ke printer") },
                onFailure = { err -> onResult(false, err.localizedMessage ?: "Gagal mencetak laporan") }
            )
        }
    }

    private fun recalculateReportData() {
        val filter = _uiState.value.periodFilter
        val allTrx = cachedAllTransactions

        // Filter transactions based on period
        val filteredTransactions = when (filter.type) {
            ReportPeriodType.ALL_TIME -> allTrx
            ReportPeriodType.WEEKLY -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.WEEK_OF_YEAR, filter.offset)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis

                allTrx.filter { it.paidAt in start..end }
            }
            ReportPeriodType.MONTHLY -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, filter.offset)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis

                allTrx.filter { it.paidAt in start..end }
            }
            ReportPeriodType.YEARLY -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.YEAR, filter.offset)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis

                allTrx.filter { it.paidAt in start..end }
            }
        }

        val total = filteredTransactions.sumOf { it.totalAmount }
        val cash = filteredTransactions.filter { it.paymentMethod == "TUNAI" }.sumOf { it.totalAmount }
        val transfer = filteredTransactions.filter { it.paymentMethod == "TRANSFER" }.sumOf { it.totalAmount }
        val qris = filteredTransactions.filter { it.paymentMethod == "QRIS" }.sumOf { it.totalAmount }
        val undepositedCash = filteredTransactions.filter { it.paymentMethod == "TUNAI" && !it.isDeposited }.sumOf { it.totalAmount }

        val staffMap = filteredTransactions.groupBy { it.cashierName }
            .mapValues { entry -> entry.value.sumOf { it.totalAmount } }

        // Build Chart Points
        val chartPoints = generateChartPoints(filter, filteredTransactions)
        val maxAmount = chartPoints.maxOfOrNull { it.amount } ?: 0L
        val peak = if (maxAmount > 0) chartPoints.firstOrNull { it.amount == maxAmount } else null

        _uiState.update {
            it.copy(
                reportData = FinancialReportData(
                    totalRevenue = total,
                    transactionCount = filteredTransactions.size,
                    cashTotal = cash,
                    transferTotal = transfer,
                    qrisTotal = qris,
                    staffBreakdown = staffMap,
                    transactions = filteredTransactions,
                    chartPoints = chartPoints,
                    peakPoint = peak,
                    undepositedCashTotal = undepositedCash
                )
            )
        }
    }

    private fun generateChartPoints(
        filter: PeriodFilterState,
        transactions: List<TransactionEntity>
    ): List<DailyChartPoint> {
        val points = mutableListOf<DailyChartPoint>()
        val cal = Calendar.getInstance()

        when (filter.type) {
            ReportPeriodType.WEEKLY -> {
                // 7 days: Senin - Minggu
                cal.add(Calendar.WEEK_OF_YEAR, filter.offset)
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

                val dayNames = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
                val dayFormat = SimpleDateFormat("dd/MM", Locale("id", "ID"))

                for (i in 0..6) {
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val startDay = cal.timeInMillis

                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val endDay = cal.timeInMillis

                    val dayTrx = transactions.filter { it.paidAt in startDay..endDay }
                    val dayAmount = dayTrx.sumOf { it.totalAmount }

                    points.add(
                        DailyChartPoint(
                            label = dayNames[i],
                            subLabel = dayFormat.format(cal.time),
                            amount = dayAmount,
                            transactionCount = dayTrx.size,
                            timestamp = startDay
                        )
                    )
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            ReportPeriodType.MONTHLY -> {
                // Group by cycle intervals or daily
                cal.add(Calendar.MONTH, filter.offset)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val dateFormat = SimpleDateFormat("dd MMM", Locale("id", "ID"))

                if (maxDay > 15) {
                    // Segment into 4 quarters: 1-7, 8-14, 15-21, 22-end
                    val segments = listOf(
                        Triple(1, 7, "Mg 1 (1-7)"),
                        Triple(8, 14, "Mg 2 (8-14)"),
                        Triple(15, 21, "Mg 3 (15-21)"),
                        Triple(22, maxDay, "Mg 4 (22-$maxDay)")
                    )

                    for (seg in segments) {
                        cal.set(Calendar.DAY_OF_MONTH, seg.first)
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        val start = cal.timeInMillis

                        cal.set(Calendar.DAY_OF_MONTH, seg.second)
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        val end = cal.timeInMillis

                        val segTrx = transactions.filter { it.paidAt in start..end }
                        val segAmount = segTrx.sumOf { it.totalAmount }

                        points.add(
                            DailyChartPoint(
                                label = "M${segments.indexOf(seg) + 1}",
                                subLabel = seg.third,
                                amount = segAmount,
                                transactionCount = segTrx.size,
                                timestamp = start
                            )
                        )
                    }
                } else {
                    for (d in 1..maxDay) {
                        cal.set(Calendar.DAY_OF_MONTH, d)
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        val start = cal.timeInMillis
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        val end = cal.timeInMillis

                        val dTrx = transactions.filter { it.paidAt in start..end }
                        points.add(
                            DailyChartPoint(
                                label = "$d",
                                subLabel = dateFormat.format(cal.time),
                                amount = dTrx.sumOf { it.totalAmount },
                                transactionCount = dTrx.size,
                                timestamp = start
                            )
                        )
                    }
                }
            }
            ReportPeriodType.YEARLY -> {
                // 12 months: Jan - Des
                cal.add(Calendar.YEAR, filter.offset)
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
                val yearStr = cal.get(Calendar.YEAR).toString()

                for (m in 0..11) {
                    cal.set(Calendar.MONTH, m)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    val start = cal.timeInMillis

                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    val end = cal.timeInMillis

                    val mTrx = transactions.filter { it.paidAt in start..end }
                    points.add(
                        DailyChartPoint(
                            label = monthNames[m],
                            subLabel = "$yearStr",
                            amount = mTrx.sumOf { it.totalAmount },
                            transactionCount = mTrx.size,
                            timestamp = start
                        )
                    )
                }
            }
            ReportPeriodType.ALL_TIME -> {
                // Group by year or recent months
                val sorted = transactions.sortedBy { it.paidAt }
                if (sorted.isEmpty()) return emptyList()

                val firstYear = Calendar.getInstance().apply { timeInMillis = sorted.first().paidAt }.get(Calendar.YEAR)
                val lastYear = Calendar.getInstance().apply { timeInMillis = sorted.last().paidAt }.get(Calendar.YEAR)

                for (y in firstYear..lastYear) {
                    cal.set(Calendar.YEAR, y)
                    cal.set(Calendar.DAY_OF_YEAR, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    val start = cal.timeInMillis

                    cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR))
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    val end = cal.timeInMillis

                    val yTrx = transactions.filter { it.paidAt in start..end }
                    points.add(
                        DailyChartPoint(
                            label = "$y",
                            subLabel = "Tahun $y",
                            amount = yTrx.sumOf { it.totalAmount },
                            transactionCount = yTrx.size,
                            timestamp = start
                        )
                    )
                }
            }
        }

        // Mark highest amount as peak
        val maxVal = points.maxOfOrNull { it.amount } ?: 0L
        return points.map { pt ->
            pt.copy(isPeak = maxVal > 0 && pt.amount == maxVal)
        }
    }

    fun setTab(tab: Int) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun refreshPairedPrinters() {
        val paired = printerManager.getPairedPrinters()
        _uiState.update { it.copy(pairedPrinters = paired) }
    }

    fun savePrinterConfig(
        name: String,
        address: String,
        phone: String,
        footer: String,
        template: String
    ) {
        printerManager.setBusinessName(name)
        printerManager.setBusinessAddress(address)
        printerManager.setBusinessPhone(phone)
        printerManager.setFooterMessage(footer)
        printerManager.setReceiptTemplate(template)
        _uiState.update {
            it.copy(
                businessName = name,
                businessAddress = address,
                businessPhone = phone,
                footerMessage = footer,
                receiptTemplate = template
            )
        }
    }

    fun selectPrinter(printer: PairedPrinter) {
        printerManager.setSelectedPrinter(printer.name, printer.address)
        _uiState.update {
            it.copy(
                selectedPrinterMac = printer.address,
                selectedPrinterName = printer.name
            )
        }
    }

    fun setAutoCut(enabled: Boolean) {
        printerManager.setAutoCutEnabled(enabled)
        _uiState.update { it.copy(isAutoCutEnabled = enabled) }
    }

    fun setFeedLines(lines: Int) {
        val clamped = lines.coerceIn(0, 5)
        printerManager.setFeedLines(clamped)
        _uiState.update { it.copy(feedLines = clamped) }
    }

    fun printTestReceipt() {
        val testData = EscPosHelper.ReceiptData(
            businessName = _uiState.value.businessName,
            businessAddress = _uiState.value.businessAddress,
            businessPhone = _uiState.value.businessPhone,
            invoiceNo = "TEST-PRINT-58MM",
            customerName = "PELANGGAN UJI COBA",
            customerCode = "WIFI-TEST",
            packageName = "Paket Family 20 Mbps",
            odpLocation = "ODP-TEST Port 1",
            period = _uiState.value.currentPeriod,
            monthlyFee = 175000,
            adminFee = 2500,
            discount = 0,
            totalAmount = 177500,
            paymentMethod = "TUNAI",
            cashierName = "Admin Billingku",
            template = _uiState.value.receiptTemplate,
            footerMessage = "UJI COBA CETAK STRUK THERMAL 58MM BERHASIL!"
        )

        viewModelScope.launch {
            printerManager.printReceipt(testData)
        }
    }

    // Master Packages CRUD
    fun openAddPackageDialog() {
        _uiState.update { it.copy(isPackageDialogOpen = true, editingPackage = null) }
    }

    fun openEditPackageDialog(plan: PackagePlanEntity) {
        _uiState.update { it.copy(isPackageDialogOpen = true, editingPackage = plan) }
    }

    fun closePackageDialog() {
        _uiState.update { it.copy(isPackageDialogOpen = false, editingPackage = null) }
    }

    fun savePackage(name: String, speed: Int, price: Long, desc: String) {
        viewModelScope.launch {
            val editing = _uiState.value.editingPackage
            if (editing != null) {
                repository.updatePlan(editing.copy(name = name, speedMbps = speed, price = price, description = desc))
            } else {
                repository.insertPlan(PackagePlanEntity(name = name, speedMbps = speed, price = price, description = desc))
            }
            closePackageDialog()
        }
    }

    fun deletePackage(plan: PackagePlanEntity) {
        viewModelScope.launch {
            repository.deletePlan(plan)
        }
    }

    // Master Staff CRUD
    fun openAddStaffDialog() {
        _uiState.update { it.copy(isStaffDialogOpen = true, editingStaff = null) }
    }

    fun openEditStaffDialog(staff: StaffEntity) {
        _uiState.update { it.copy(isStaffDialogOpen = true, editingStaff = staff) }
    }

    fun closeStaffDialog() {
        _uiState.update { it.copy(isStaffDialogOpen = false, editingStaff = null) }
    }

    fun saveStaff(username: String, name: String, role: String, phone: String, pin: String) {
        viewModelScope.launch {
            val editing = _uiState.value.editingStaff
            if (editing != null) {
                repository.updateStaff(editing.copy(username = username, name = name, role = role, phone = phone, pin = pin))
            } else {
                repository.insertStaff(StaffEntity(username = username, name = name, role = role, phone = phone, pin = pin))
            }
            closeStaffDialog()
        }
    }

    fun deleteStaff(staff: StaffEntity) {
        viewModelScope.launch {
            repository.deleteStaff(staff)
        }
    }

    fun updateStaffPin(staffId: Long, newPin: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val staff = repository.getStaffById(staffId) ?: return@launch
            repository.updateStaff(staff.copy(pin = newPin))
            onComplete()
        }
    }

    fun updateStaffProfile(
        staff: StaffEntity,
        name: String,
        username: String,
        phone: String,
        pin: String,
        onComplete: (StaffEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val finalPin = if (pin.isNotBlank()) pin.trim() else staff.pin
            val updated = staff.copy(
                name = name.trim(),
                username = username.trim(),
                phone = phone.trim(),
                pin = finalPin
            )
            repository.updateStaff(updated)
            onComplete(updated)
        }
    }

    // MySQL Server Settings & Sync
    fun saveMysqlSettings(url: String, key: String, autoSync: Boolean) {
        val sync = mysqlSyncManager
        sync?.apply {
            setApiUrl(url)
            setApiKey(key)
            setAutoSyncEnabled(autoSync)
        }
        val activeUrl = sync?.getApiUrl() ?: url.trim()
        val isEmbedded = sync?.isUsingEmbeddedDefaults() ?: false
        _uiState.update {
            it.copy(
                mysqlApiUrl = activeUrl,
                mysqlPublicUrl = activeUrl,
                mysqlLocalUrl = "",
                mysqlApiKey = key,
                mysqlAutoSync = autoSync,
                isUsingEmbeddedDefaults = isEmbedded
            )
        }
        if (activeUrl.isNotBlank() && sync != null) {
            viewModelScope.launch {
                sync.performFullAutoSync()
                _uiState.update {
                    it.copy(
                        mysqlLastSyncTime = sync.getLastSyncFormatted(),
                        mysqlLastSyncStatus = sync.getLastSyncStatus(),
                        activeEndpointSummary = "Server: $activeUrl",
                        isUsingEmbeddedDefaults = sync.isUsingEmbeddedDefaults()
                    )
                }
            }
        }
    }

    fun saveMysqlDualSettings(
        publicUrl: String,
        localUrl: String,
        mode: String,
        key: String,
        autoSync: Boolean
    ) {
        // Forwarding call to unified saveMysqlSettings
        val primaryUrl = publicUrl.ifBlank { localUrl }
        saveMysqlSettings(primaryUrl, key, autoSync)
    }

    fun resetMysqlToEmbeddedDefaults(onFinished: () -> Unit = {}) {
        val sync = mysqlSyncManager ?: return
        sync.resetToEmbeddedDefaults()
        _uiState.update {
            it.copy(
                mysqlApiUrl = sync.getApiUrl(),
                mysqlPublicUrl = sync.getPublicApiUrl(),
                mysqlLocalUrl = "",
                mysqlEndpointMode = "PUBLIC",
                mysqlApiKey = sync.getApiKey(),
                isUsingEmbeddedDefaults = true,
                activeEndpointSummary = "Bawaan Server Cloud Ditanam diaktifkan"
            )
        }
        onFinished()
    }

    fun runDualSpeedTest(
        publicUrl: String,
        localUrl: String,
        key: String,
        onResult: (com.example.data.sync.MysqlSyncManager.DualEndpointTestResult) -> Unit
    ) {
        val sync = mysqlSyncManager ?: return
        _uiState.update { it.copy(isSpeedTesting = true) }
        viewModelScope.launch {
            val res = sync.testDualEndpoints(localUrlInput = localUrl, publicUrlInput = publicUrl, keyInput = key)
            _uiState.update {
                it.copy(
                    isSpeedTesting = false,
                    speedTestResult = res.summary,
                    activeEndpointSummary = res.summary,
                    mysqlApiUrl = res.fastestUrl
                )
            }
            onResult(res)
        }
    }

    fun testMysqlConnection(url: String, key: String, onResult: (Boolean, String) -> Unit) {
        val sync = mysqlSyncManager ?: run {
            onResult(false, "Modul MySQL Sync belum siap")
            return
        }
        _uiState.update { it.copy(isMysqlTesting = true) }
        viewModelScope.launch {
            val res = sync.testConnection(url, key)
            _uiState.update { it.copy(isMysqlTesting = false) }
            res.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { err -> onResult(false, err.localizedMessage ?: err.message ?: "Koneksi gagal") }
            )
        }
    }

    fun uploadAllToMysql(onResult: (Boolean, String) -> Unit) {
        val sync = mysqlSyncManager ?: run {
            onResult(false, "Modul MySQL Sync belum siap")
            return
        }
        _uiState.update { it.copy(isMysqlSyncing = true) }
        viewModelScope.launch {
            val res = sync.uploadAllToMysql()
            _uiState.update {
                it.copy(
                    isMysqlSyncing = false,
                    mysqlLastSyncTime = sync.getLastSyncFormatted(),
                    mysqlLastSyncStatus = sync.getLastSyncStatus()
                )
            }
            res.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { err -> onResult(false, err.localizedMessage ?: err.message ?: "Upload gagal") }
            )
        }
    }

    fun downloadAllFromMysql(onResult: (Boolean, String) -> Unit) {
        val sync = mysqlSyncManager ?: run {
            onResult(false, "Modul MySQL Sync belum siap")
            return
        }
        _uiState.update { it.copy(isMysqlSyncing = true) }
        viewModelScope.launch {
            val res = sync.downloadAllFromMysql()
            _uiState.update {
                it.copy(
                    isMysqlSyncing = false,
                    mysqlLastSyncTime = sync.getLastSyncFormatted(),
                    mysqlLastSyncStatus = sync.getLastSyncStatus()
                )
            }
            res.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { err -> onResult(false, err.localizedMessage ?: err.message ?: "Download gagal") }
            )
        }
    }

    fun triggerFullAutoSync(onResult: (Boolean, String) -> Unit) {
        val sync = mysqlSyncManager ?: run {
            onResult(false, "Modul MySQL Sync belum siap")
            return
        }
        _uiState.update { it.copy(isMysqlSyncing = true) }
        viewModelScope.launch {
            val res = sync.performFullAutoSync()
            _uiState.update {
                it.copy(
                    isMysqlSyncing = false,
                    mysqlLastSyncTime = sync.getLastSyncFormatted(),
                    mysqlLastSyncStatus = sync.getLastSyncStatus()
                )
            }
            res.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { err -> onResult(false, err.localizedMessage ?: err.message ?: "Sinkronisasi gagal") }
            )
        }
    }

    // ------------------------------------------------------------------------
    // ANNOUNCEMENTS & BROADCASTS (NET-PAY)
    // ------------------------------------------------------------------------
    fun saveAnnouncement(
        id: Long = 0L,
        title: String,
        category: String,
        message: String,
        targetScope: String = "SEMUA",
        status: String = "ACTIVE",
        estimatedEnd: String = "",
        createdBy: String = "Admin",
        onFinished: (AnnouncementEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val announcement = AnnouncementEntity(
                id = id,
                title = title.trim(),
                category = category,
                message = message.trim(),
                targetScope = targetScope.trim().ifBlank { "SEMUA" },
                status = status,
                estimatedEnd = estimatedEnd.trim(),
                createdBy = createdBy,
                createdAt = if (id != 0L) {
                    val existing = repository.getAnnouncementById(id)
                    existing?.createdAt ?: System.currentTimeMillis()
                } else {
                    System.currentTimeMillis()
                },
                updatedAt = System.currentTimeMillis()
            )

            if (id == 0L) {
                val newId = repository.insertAnnouncement(announcement)
                onFinished(announcement.copy(id = newId))
            } else {
                repository.updateAnnouncement(announcement)
                onFinished(announcement)
            }
        }
    }

    fun toggleAnnouncementStatus(id: Long, currentStatus: String) {
        viewModelScope.launch {
            val nextStatus = if (currentStatus == "ACTIVE") "RESOLVED" else "ACTIVE"
            repository.toggleAnnouncementStatus(id, nextStatus)
        }
    }

    fun deleteAnnouncement(announcement: AnnouncementEntity, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAnnouncement(announcement)
            onFinished()
        }
    }

    fun pushAnnouncementToServer(announcement: AnnouncementEntity, onResult: (Boolean, String) -> Unit) {
        val sync = mysqlSyncManager
        if (sync == null || sync.getApiUrl().isBlank()) {
            onResult(false, "URL Server MySQL belum dikonfigurasi di Pengaturan")
            return
        }

        _uiState.update { it.copy(isAnnouncementSyncing = true) }
        viewModelScope.launch {
            val res = sync.pushAnnouncement(announcement)
            _uiState.update { it.copy(isAnnouncementSyncing = false) }
            res.fold(
                onSuccess = { onResult(true, "✓ Pengumuman berhasil disinkronkan ke server Net-pay!") },
                onFailure = { err -> onResult(false, err.localizedMessage ?: err.message ?: "Gagal sinkronisasi") }
            )
        }
    }

    fun getNetPayAnnouncementApiUrl(): String {
        return mysqlSyncManager?.getNetPayAnnouncementApiUrl() ?: "https://your-domain.com/billingku.php?action=get_announcements"
    }

    // -------------------------------------------------------------
    // LOCAL BACKUP & RESTORE METHODS
    // -------------------------------------------------------------
    fun exportLocalBackup(
        context: android.content.Context,
        onResult: (Boolean, String, java.io.File?) -> Unit
    ) {
        if (_uiState.value.isExportingBackup) return
        _uiState.update { it.copy(isExportingBackup = true) }

        viewModelScope.launch {
            val appSettings = com.example.util.AppSettingsBackup(
                businessName = printerManager.getBusinessName(),
                businessAddress = printerManager.getBusinessAddress(),
                businessPhone = printerManager.getBusinessPhone(),
                footerMessage = printerManager.getFooterMessage(),
                receiptTemplate = printerManager.getReceiptTemplate(),
                feedLines = printerManager.getFeedLines(),
                mysqlPublicUrl = mysqlSyncManager?.getPublicApiUrl() ?: "",
                mysqlApiKey = mysqlSyncManager?.getApiKey() ?: ""
            )

            val res = com.example.util.BackupRestoreHelper.exportDatabaseToJson(
                context = context,
                repository = repository,
                businessName = _uiState.value.businessName.ifBlank { "Billingku RT/RW Net" },
                appSettings = appSettings
            )

            res.fold(
                onSuccess = { file ->
                    val sizeKb = String.format(Locale.US, "%.1f KB", file.length() / 1024.0)
                    val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date())
                    _uiState.update {
                        it.copy(
                            isExportingBackup = false,
                            lastExportedFile = file,
                            lastBackupFileName = file.name,
                            lastBackupTime = timeStr,
                            lastBackupSize = sizeKb
                        )
                    }
                    onResult(true, "✓ File cadangan berhasil dibuat: ${file.name} ($sizeKb)", file)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isExportingBackup = false) }
                    onResult(false, "Gagal mengekspor cadangan: ${err.localizedMessage ?: err.message}", null)
                }
            )
        }
    }

    fun shareLastBackup(context: android.content.Context) {
        val file = _uiState.value.lastExportedFile
        if (file != null && file.exists()) {
            com.example.util.BackupRestoreHelper.shareBackupFile(context, file)
        } else {
            exportLocalBackup(context) { success, msg, newFile ->
                if (success && newFile != null) {
                    com.example.util.BackupRestoreHelper.shareBackupFile(context, newFile)
                }
            }
        }
    }

    fun restoreLocalBackup(
        payload: com.example.util.BackupPayload,
        cleanReplace: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        if (_uiState.value.isRestoringBackup) return
        _uiState.update { it.copy(isRestoringBackup = true) }

        viewModelScope.launch {
            // Restore Settings if present in payload
            payload.settings?.let { s ->
                if (s.businessName.isNotBlank()) printerManager.setBusinessName(s.businessName)
                if (s.businessAddress.isNotBlank()) printerManager.setBusinessAddress(s.businessAddress)
                if (s.businessPhone.isNotBlank()) printerManager.setBusinessPhone(s.businessPhone)
                if (s.footerMessage.isNotBlank()) printerManager.setFooterMessage(s.footerMessage)
                if (s.receiptTemplate.isNotBlank()) printerManager.setReceiptTemplate(s.receiptTemplate)
                if (s.feedLines in 0..5) printerManager.setFeedLines(s.feedLines)

                if (s.mysqlPublicUrl.isNotBlank()) {
                    mysqlSyncManager?.setPublicApiUrl(s.mysqlPublicUrl)
                }
                if (s.mysqlApiKey.isNotBlank()) {
                    mysqlSyncManager?.setApiKey(s.mysqlApiKey)
                }

                _uiState.update {
                    it.copy(
                        businessName = printerManager.getBusinessName(),
                        businessAddress = printerManager.getBusinessAddress(),
                        businessPhone = printerManager.getBusinessPhone(),
                        footerMessage = printerManager.getFooterMessage(),
                        receiptTemplate = printerManager.getReceiptTemplate(),
                        feedLines = printerManager.getFeedLines(),
                        mysqlPublicUrl = mysqlSyncManager?.getPublicApiUrl() ?: it.mysqlPublicUrl,
                        mysqlApiKey = mysqlSyncManager?.getApiKey() ?: it.mysqlApiKey
                    )
                }
            }

            val result = com.example.util.BackupRestoreHelper.restoreDatabase(
                repository = repository,
                payload = payload,
                cleanReplace = cleanReplace
            )

            _uiState.update { it.copy(isRestoringBackup = false) }
            val extraNote = if (payload.settings != null) "\n(Pengaturan nama usaha, struk, dan server API juga berhasil dipulihkan)" else ""
            onResult(result.isSuccess, "${result.message}$extraNote")
        }
    }
}

class SettingsViewModelFactory(
    private val repository: BillingRepository,
    private val printerManager: BluetoothPrinterManager,
    private val mysqlSyncManager: com.example.data.sync.MysqlSyncManager? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository, printerManager, mysqlSyncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
