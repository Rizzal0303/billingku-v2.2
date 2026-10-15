package com.example

import com.example.printer.EscPosHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosHelperTest {

    @Test
    fun testTwoColumnAlignmentWidth() {
        val line = EscPosHelper.formatTwoColumns("Pelanggan", "Ahmad", 32)
        assertEquals(32, line.length)
        assertTrue(line.startsWith("Pelanggan"))
        assertTrue(line.endsWith("Ahmad"))
    }

    @Test
    fun testPlainTextReceiptGeneration() {
        val receiptData = EscPosHelper.ReceiptData(
            invoiceNo = "INV-TEST-001",
            customerName = "Budi Santoso",
            customerCode = "WIFI-101",
            packageName = "Paket Family 20 Mbps",
            period = "September 2026",
            monthlyFee = 175000,
            adminFee = 2500,
            discount = 0,
            totalAmount = 177500,
            paymentMethod = "TUNAI"
        )
        val text = EscPosHelper.generatePlainTextReceipt(receiptData)
        assertTrue(text.contains("BILLINGKU"))
        assertTrue(text.contains("INV-TEST-001"))
        assertTrue(text.contains("Budi Santoso"))
        assertTrue(text.contains("177.500"))
    }
}
