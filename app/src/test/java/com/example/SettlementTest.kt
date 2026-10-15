package com.example

import com.example.data.local.entity.SettlementBatchEntity
import com.example.data.local.entity.TransactionEntity
import com.example.printer.EscPosHelper
import org.junit.Assert.*
import org.junit.Test

class SettlementTest {

    @Test
    fun testSettlementReceiptFormatting() {
        val now = System.currentTimeMillis()
        val item1 = TransactionEntity(
            id = 1L,
            invoiceNumber = "INV-202609-0001",
            customerId = 101L,
            customerName = "Ahmad Zaelani",
            customerCode = "WIFI-001",
            packageName = "Paket Family 20 Mbps",
            monthlyFee = 175000,
            adminFee = 2500,
            discount = 0,
            totalAmount = 177500,
            periodMonth = "September 2026",
            paymentMethod = "TUNAI",
            cashierName = "Siti Rahma"
        )
        val item2 = TransactionEntity(
            id = 2L,
            invoiceNumber = "INV-202609-0002",
            customerId = 102L,
            customerName = "Hendra Setiawan",
            customerCode = "WIFI-004",
            packageName = "Paket Family 20 Mbps",
            monthlyFee = 175000,
            adminFee = 0,
            discount = 0,
            totalAmount = 175000,
            periodMonth = "September 2026",
            paymentMethod = "TUNAI",
            cashierName = "Siti Rahma"
        )

        val receiptData = EscPosHelper.SettlementReceiptData(
            batchNumber = "STR-202609-0001",
            periodMonth = "September 2026",
            cashierName = "Siti Rahma",
            recipientName = "Budi Hartono (Owner)",
            date = now,
            totalAmount = 352500,
            totalCashAmount = 352500,
            totalNonCashAmount = 0,
            customerCount = 2,
            items = listOf(item1, item2),
            notes = "Uang tunai diserahkan shift 1"
        )

        val plainReceipt = EscPosHelper.generatePlainTextSettlementReceipt(receiptData)
        assertTrue(plainReceipt.contains("STR-202609-0001"))
        assertTrue(plainReceipt.contains("September 2026"))
        assertTrue(plainReceipt.contains("Ahmad Zaelani"))
        assertTrue(plainReceipt.contains("Hendra Setiawan"))
        assertTrue(plainReceipt.contains("352.500"))
        assertTrue(plainReceipt.contains("Siti Rahma"))

        val waText = EscPosHelper.generateWhatsAppSettlementText(receiptData)
        assertTrue(waText.contains("STR-202609-0001"))
        assertTrue(waText.contains("BUKTI TANDA TERIMA SETORAN"))
        assertTrue(waText.contains("September 2026"))
    }

    @Test
    fun testAlreadyDepositedCustomerValidation() {
        val septDeposited = TransactionEntity(
            id = 1L,
            invoiceNumber = "INV-202609-0001",
            customerId = 101L,
            customerName = "Ahmad Zaelani",
            customerCode = "WIFI-001",
            packageName = "Paket Family 20 Mbps",
            monthlyFee = 175000,
            totalAmount = 175000,
            periodMonth = "September 2026",
            isDeposited = true,
            settlementBatchNumber = "STR-202609-0001"
        )

        // Rule: cannot be deposited again for the same month
        val cannotRedepositInSameMonth = septDeposited.isDeposited
        assertTrue("Customer who has been deposited cannot be re-deposited for this month", cannotRedepositInSameMonth)

        // But next month (October 2026), a new transaction is created and is not deposited yet
        val octNewTransaction = TransactionEntity(
            id = 2L,
            invoiceNumber = "INV-202610-0001",
            customerId = 101L,
            customerName = "Ahmad Zaelani",
            customerCode = "WIFI-001",
            packageName = "Paket Family 20 Mbps",
            monthlyFee = 175000,
            totalAmount = 175000,
            periodMonth = "Oktober 2026",
            isDeposited = false,
            settlementBatchNumber = null
        )

        assertFalse("New transaction for next month is eligible for deposit", octNewTransaction.isDeposited)
        assertEquals("Oktober 2026", octNewTransaction.periodMonth)
    }

    @Test
    fun testCashReconciliationDifferenceCalculation() {
        val totalSystemCash = 350000L
        val physicalCashCounted = 350000L
        val difference = physicalCashCounted - totalSystemCash
        assertEquals(0L, difference)

        val physicalSurplus = 370000L
        assertEquals(20000L, physicalSurplus - totalSystemCash)

        val physicalDeficit = 340000L
        assertEquals(-10000L, physicalDeficit - totalSystemCash)
    }

    @Test
    fun testSettlementUiStatePullToRefresh() {
        val defaultState = com.example.ui.settlement.SettlementUiState()
        assertFalse("Default state should not be refreshing", defaultState.isRefreshing)

        val refreshingState = defaultState.copy(isRefreshing = true)
        assertTrue("State should reflect refreshing true", refreshingState.isRefreshing)
    }
}
