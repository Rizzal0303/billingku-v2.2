package com.example

import com.example.data.local.entity.TransactionEntity
import com.example.printer.EscPosHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    @Test
    fun testTransactionWhatsAppFormat() {
        val trx = TransactionEntity(
            invoiceNumber = "INV-202609-0099",
            customerId = 1L,
            customerName = "Bambang Sudiro",
            customerCode = "WIFI-088",
            packageName = "Paket Ultra 35 Mbps",
            monthlyFee = 250000,
            adminFee = 2500,
            discount = 0,
            totalAmount = 252500,
            periodMonth = "September 2026",
            paymentMethod = "TRANSFER",
            cashierName = "Siti Rahma"
        )

        val receiptData = EscPosHelper.ReceiptData(
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
            cashierName = trx.cashierName
        )

        val plainReceipt = EscPosHelper.generatePlainTextReceipt(receiptData)
        assertTrue(plainReceipt.contains("INV-202609-0099"))
        assertTrue(plainReceipt.contains("Bambang Sudiro"))
        assertTrue(plainReceipt.contains("252.500"))
        assertEquals("TRANSFER", trx.paymentMethod)
    }
}
