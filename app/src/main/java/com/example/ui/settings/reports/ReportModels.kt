package com.example.ui.settings.reports

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriodType(val label: String) {
    WEEKLY("Mingguan"),
    MONTHLY("Bulanan"),
    YEARLY("Tahunan"),
    ALL_TIME("Semua")
}

data class PeriodFilterState(
    val type: ReportPeriodType = ReportPeriodType.MONTHLY,
    val offset: Int = 0 // 0 = current week/month/year, -1 = previous, etc.
) {
    fun getDisplayRangeTitle(): String {
        val cal = Calendar.getInstance()
        when (type) {
            ReportPeriodType.ALL_TIME -> return "Semua Riwayat Transaksi"
            ReportPeriodType.WEEKLY -> {
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = cal.time
                cal.add(Calendar.DAY_OF_WEEK, 6)
                val end = cal.time
                val sdf = SimpleDateFormat("dd MMM", Locale("id", "ID"))
                val sdfYear = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
                return "${sdf.format(start)} – ${sdfYear.format(end)}"
            }
            ReportPeriodType.MONTHLY -> {
                cal.add(Calendar.MONTH, offset)
                val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
                return sdf.format(cal.time)
            }
            ReportPeriodType.YEARLY -> {
                cal.add(Calendar.YEAR, offset)
                val sdf = SimpleDateFormat("yyyy", Locale("id", "ID"))
                return "Tahun ${sdf.format(cal.time)}"
            }
        }
    }

    fun isCurrentPeriod(): Boolean = offset == 0
}

data class DailyChartPoint(
    val label: String,       // e.g. "Sen", "12 Okt", or "Jan"
    val subLabel: String,    // e.g. "12/10" or "2026"
    val amount: Long,
    val transactionCount: Int,
    val isPeak: Boolean = false,
    val timestamp: Long = 0L
)
