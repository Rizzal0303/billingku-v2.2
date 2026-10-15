package com.example

import com.example.util.PeriodUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ArrearsBillingTest {

    @Test
    fun testPeriodFormattingAndCandidateGeneration() {
        val current = PeriodUtils.getCurrentPeriod()
        assertNotNull(current)
        assertTrue(current.isNotBlank())

        val candidates = PeriodUtils.getCandidatePeriods(pastMonths = 4, futureMonths = 2)
        assertEquals(7, candidates.size)
        // Ensure current month is in candidates
        assertTrue(candidates.contains(current))
    }

    @Test
    fun testFormatMultiplePeriods() {
        assertEquals("September 2026", PeriodUtils.formatMultiplePeriods(listOf("September 2026")))
        assertEquals(
            "Agustus 2026 & September 2026 (2 Bulan)",
            PeriodUtils.formatMultiplePeriods(listOf("Agustus 2026", "September 2026"))
        )
        assertEquals(
            "Juli 2026 s/d September 2026 (3 Bulan)",
            PeriodUtils.formatMultiplePeriods(listOf("Juli 2026", "Agustus 2026", "September 2026"))
        )
    }

    @Test
    fun testPastAndFuturePeriodDetection() {
        val pastPeriod = PeriodUtils.getPeriodWithOffset(-2)
        val futurePeriod = PeriodUtils.getPeriodWithOffset(2)
        val currentPeriod = PeriodUtils.getCurrentPeriod()

        assertTrue(PeriodUtils.isPastPeriod(pastPeriod))
        assertFalse(PeriodUtils.isFuturePeriod(pastPeriod))

        assertTrue(PeriodUtils.isFuturePeriod(futurePeriod))
        assertFalse(PeriodUtils.isPastPeriod(futurePeriod))

        assertFalse(PeriodUtils.isPastPeriod(currentPeriod))
        assertFalse(PeriodUtils.isFuturePeriod(currentPeriod))

        val lastMonth = PeriodUtils.getPeriodWithOffset(-1)
        assertTrue(PeriodUtils.isLastMonth(lastMonth))
        assertFalse(PeriodUtils.isLastMonth(currentPeriod))
    }

    @Test
    fun testCustomerEligiblePeriodsFromCreatedAt() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -3)
        }
        val eligible = PeriodUtils.getEligiblePeriodsForCustomer(cal.timeInMillis, maxPastMonths = 6)
        // Should include at least 4 periods (month -3, -2, -1, 0)
        assertTrue(eligible.size >= 4)
        assertTrue(eligible.contains(PeriodUtils.getCurrentPeriod()))
    }

    @Test
    fun testNewCustomerRegisteredTodayOnlyGetsCurrentMonthByDefault() {
        // Customer registered today should only be billed starting from current month
        val today = System.currentTimeMillis()
        val eligible = PeriodUtils.getEligiblePeriodsForCustomer(today, maxPastMonths = 6)
        val currentMonth = PeriodUtils.getCurrentPeriod()

        assertEquals(listOf(currentMonth), eligible)
    }

    @Test
    fun testCustomerEligiblePeriodsWhenExplicitlyRequestedPreviousMonth() {
        // When includeAtLeastPreviousMonth is explicitly set to true
        val today = System.currentTimeMillis()
        val eligible = PeriodUtils.getEligiblePeriodsForCustomer(today, maxPastMonths = 6, includeAtLeastPreviousMonth = true)
        val lastMonth = PeriodUtils.getPeriodWithOffset(-1)
        val currentMonth = PeriodUtils.getCurrentPeriod()

        // Must include both last month and current month
        assertTrue("Eligible periods must include last month", eligible.contains(lastMonth))
        assertTrue("Eligible periods must include current month", eligible.contains(currentMonth))
        assertEquals(2, eligible.size)
    }
}
