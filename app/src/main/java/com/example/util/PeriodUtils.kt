package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PeriodUtils {

    private val localeId = Locale("id", "ID")

    /**
     * Format standar periode bulanan: "September 2026"
     */
    fun getPeriodFormat(): SimpleDateFormat {
        return SimpleDateFormat("MMMM yyyy", localeId)
    }

    /**
     * Mengembalikan nama periode bulan saat ini, misal "September 2026"
     */
    fun getCurrentPeriod(): String {
        return getPeriodFormat().format(Date())
    }

    /**
     * Mengembalikan periode relatif terhadap bulan sekarang.
     * Offset: -1 (bulan lalu), 0 (bulan ini), +1 (bulan depan), dst.
     */
    fun getPeriodWithOffset(offsetMonths: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, offsetMonths)
        return getPeriodFormat().format(cal.time)
    }

    /**
     * Mem-parsing string periode "September 2026" menjadi Calendar.
     * Jika gagal, mengembalikan Calendar saat ini.
     */
    fun parsePeriod(periodStr: String): Calendar {
        val cal = Calendar.getInstance()
        try {
            val date = getPeriodFormat().parse(periodStr)
            if (date != null) {
                cal.time = date
                cal.set(Calendar.DAY_OF_MONTH, 1)
            }
        } catch (_: Exception) {
            // fallback
        }
        return cal
    }

    /**
     * Menghasilkan daftar periode bulan kandidat:
     * Dari [pastMonths] ke belakang (default 5 bulan lalu)
     * sampai [futureMonths] ke depan (default 2 bulan ke depan).
     * Urutan: Kronologis dari terlama ke terbaru.
     */
    fun getCandidatePeriods(pastMonths: Int = 5, futureMonths: Int = 2): List<String> {
        val result = mutableListOf<String>()
        val format = getPeriodFormat()
        for (i in -pastMonths..futureMonths) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, i)
            result.add(format.format(cal.time))
        }
        return result
    }

    /**
     * Menentukan daftar bulan tagihan yang seharusnya dibayar oleh pelanggan
     * mulai dari bulan pendaftaran (createdAt) atau maksimal [maxPastMonths] lalu,
     * sampai bulan sekarang.
     *
     * Secara default [includeAtLeastPreviousMonth] bernilai false, sehingga pelanggan baru
     * yang mendaftar di bulan berjalan hanya akan memiliki tagihan mulai dari bulan pendaftaran,
     * dan TIDAK ditagih untuk bulan-bulan sebelum mereka mendaftar.
     */
    fun getEligiblePeriodsForCustomer(
        createdAt: Long,
        maxPastMonths: Int = 6,
        includeAtLeastPreviousMonth: Boolean = false
    ): List<String> {
        val result = mutableListOf<String>()
        val format = getPeriodFormat()
        val nowCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val createdCal = Calendar.getInstance().apply {
            timeInMillis = createdAt
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Batas mundur maksimal agar tidak terlalu jauh ke masa lalu
        val earliestLimitCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -maxPastMonths)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val effectiveCreatedCal = if (includeAtLeastPreviousMonth) {
            val previousMonthCal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (createdCal.after(previousMonthCal)) {
                previousMonthCal
            } else {
                createdCal
            }
        } else {
            createdCal
        }

        val startCal = if (effectiveCreatedCal.before(earliestLimitCal)) {
            earliestLimitCal
        } else {
            effectiveCreatedCal
        }

        val cursor = startCal.clone() as Calendar
        while (!cursor.after(nowCal)) {
            result.add(format.format(cursor.time))
            cursor.add(Calendar.MONTH, 1)
        }

        if (result.isEmpty()) {
            result.add(getCurrentPeriod())
        }

        return result
    }

    /**
     * Memeriksa apakah suatu periode adalah persis bulan lalu (-1 bulan dari sekarang).
     */
    fun isLastMonth(periodStr: String): Boolean {
        val lastMonth = getPeriodWithOffset(-1)
        return periodStr.equals(lastMonth, ignoreCase = true)
    }

    /**
     * Format representasi ringkas gabungan beberapa periode.
     * Contoh:
     * - ["September 2026"] -> "September 2026"
     * - ["Agustus 2026", "September 2026"] -> "Agustus 2026 & September 2026 (2 Bulan)"
     * - ["Juli 2026", "Agustus 2026", "September 2026"] -> "Juli 2026 s/d September 2026 (3 Bulan)"
     */
    fun formatMultiplePeriods(periods: List<String>): String {
        return when {
            periods.isEmpty() -> getCurrentPeriod()
            periods.size == 1 -> periods.first()
            periods.size == 2 -> "${periods[0]} & ${periods[1]} (2 Bulan)"
            else -> "${periods.first()} s/d ${periods.last()} (${periods.size} Bulan)"
        }
    }

    /**
     * Memeriksa apakah periode tertentu berada sebelum bulan sekarang (periode lampau / tunggakan).
     */
    fun isPastPeriod(periodStr: String): Boolean {
        val currentPeriod = getCurrentPeriod()
        if (periodStr.equals(currentPeriod, ignoreCase = true)) return false

        val periodCal = parsePeriod(periodStr)
        val nowCal = Calendar.getInstance()
        nowCal.set(Calendar.DAY_OF_MONTH, 1)
        nowCal.set(Calendar.HOUR_OF_DAY, 0)
        nowCal.set(Calendar.MINUTE, 0)
        nowCal.set(Calendar.SECOND, 0)
        nowCal.set(Calendar.MILLISECOND, 0)

        return periodCal.before(nowCal)
    }

    /**
     * Memeriksa apakah periode tertentu berada setelah bulan sekarang (bayar di muka).
     */
    fun isFuturePeriod(periodStr: String): Boolean {
        val currentPeriod = getCurrentPeriod()
        if (periodStr.equals(currentPeriod, ignoreCase = true)) return false

        val periodCal = parsePeriod(periodStr)
        val nowCal = Calendar.getInstance()
        nowCal.set(Calendar.DAY_OF_MONTH, 1)
        nowCal.set(Calendar.HOUR_OF_DAY, 23)
        nowCal.set(Calendar.MINUTE, 59)

        return periodCal.after(nowCal)
    }
}
