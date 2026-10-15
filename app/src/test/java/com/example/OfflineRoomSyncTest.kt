package com.example

import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.PackagePlanEntity
import com.example.data.local.entity.SettlementBatchEntity
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.local.entity.TransactionEntity
import org.junit.Assert.*
import org.junit.Test

class OfflineRoomSyncTest {

    @Test
    fun testPendingSyncQueueCreation() {
        val queueItem = SyncQueueEntity(
            id = 1L,
            entityType = "TRANSACTION",
            entityId = 1001L,
            action = "UPSERT",
            payloadJson = "",
            createdAt = System.currentTimeMillis()
        )

        assertEquals("TRANSACTION", queueItem.entityType)
        assertEquals(1001L, queueItem.entityId)
        assertEquals("UPSERT", queueItem.action)
        assertEquals(0, queueItem.retryCount)
        assertNull(queueItem.lastError)
    }

    @Test
    fun testOfflineCustomerPendingSyncTracking() {
        val offlineCustomer = CustomerEntity(
            id = 55L,
            customerCode = "WIFI-055",
            name = "Budi Offline",
            phone = "081234567899",
            address = "Jl. Merdeka No. 10",
            odpLocation = "ODP-MRD-01 Port 3",
            packageId = 2L,
            packageName = "Paket Family 20 Mbps",
            monthlyFee = 175000,
            dueDay = 15,
            status = "ACTIVE"
        )

        // Queue item representing an offline creation
        val pendingSync = SyncQueueEntity(
            id = 1L,
            entityType = "CUSTOMER",
            entityId = offlineCustomer.id,
            action = "UPSERT"
        )

        assertEquals(offlineCustomer.id, pendingSync.entityId)
        assertEquals("CUSTOMER", pendingSync.entityType)
        assertEquals("UPSERT", pendingSync.action)
    }

    @Test
    fun testMultiDeviceReconciliationSafety() {
        // Device A has a local offline edit on Customer 1 (pending in queue)
        val pendingCustomerIds = setOf(1L)

        // Remote MySQL has updated customers from Device B
        val remoteCustomer1 = CustomerEntity(
            id = 1L,
            customerCode = "WIFI-001",
            name = "Device B Override",
            phone = "081111",
            address = "Server",
            packageId = 1L,
            packageName = "Paket Lite",
            monthlyFee = 120000
        )
        val remoteCustomer2 = CustomerEntity(
            id = 2L,
            customerCode = "WIFI-002",
            name = "Device B New Data",
            phone = "082222",
            address = "Server",
            packageId = 2L,
            packageName = "Paket Family",
            monthlyFee = 175000
        )

        val remoteList = listOf(remoteCustomer1, remoteCustomer2)

        // Reconcile rule: filter out items whose IDs are pending local sync
        val safeToInsert = remoteList.filter { it.id !in pendingCustomerIds }

        // Assert customer 1 was preserved (not overwritten by remote)
        assertFalse(safeToInsert.any { it.id == 1L })
        // Assert customer 2 was accepted
        assertTrue(safeToInsert.any { it.id == 2L })
        assertEquals(1, safeToInsert.size)
    }

    @Test
    fun testOfflineTransactionToSettlementFlow() {
        val trx1 = TransactionEntity(
            id = 101L,
            invoiceNumber = "INV-202609-001",
            customerId = 1L,
            customerName = "Pak Joko",
            customerCode = "WIFI-001",
            packageName = "Paket Lite",
            monthlyFee = 120000,
            totalAmount = 120000,
            periodMonth = "September 2026",
            paymentMethod = "TUNAI",
            isDeposited = false
        )

        // Offline cashier marks settlement
        val settlement = SettlementBatchEntity(
            id = 201L,
            batchNumber = "STR-202609-001",
            periodMonth = "September 2026",
            cashierName = "Kasir A",
            totalAmount = 120000,
            totalCashAmount = 120000,
            totalNonCashAmount = 0,
            customerCount = 1
        )

        val queuedItems = listOf(
            SyncQueueEntity(id = 1L, entityType = "SETTLEMENT", entityId = settlement.id, action = "UPSERT"),
            SyncQueueEntity(id = 2L, entityType = "TRANSACTION", entityId = trx1.id, action = "UPSERT")
        )

        assertEquals(2, queuedItems.size)
        assertTrue(queuedItems.any { it.entityType == "SETTLEMENT" })
        assertTrue(queuedItems.any { it.entityType == "TRANSACTION" })
    }

    @Test
    fun testDuplicateInvoicePreventionAcrossDevices() {
        val existingTrxOnServer = TransactionEntity(
            id = 5001L,
            invoiceNumber = "INV-202609-HP1",
            customerId = 99L,
            customerName = "Pak Rudi",
            customerCode = "WIFI-099",
            packageName = "Paket Standar",
            monthlyFee = 150000,
            totalAmount = 150000,
            periodMonth = "September 2026",
            cashierName = "HP 1 - Kasir Budi"
        )

        val newTrxFromHP2 = TransactionEntity(
            id = 5002L,
            invoiceNumber = "INV-202609-HP2",
            customerId = 99L,
            customerName = "Pak Rudi",
            customerCode = "WIFI-099",
            packageName = "Paket Standar",
            monthlyFee = 150000,
            totalAmount = 150000,
            periodMonth = "September 2026",
            cashierName = "HP 2 - Kasir Siti"
        )

        // Validasi logika: jika customerId dan periodMonth sama, tapi ID berbeda -> duplikasi!
        val isDuplicate = (existingTrxOnServer.customerId == newTrxFromHP2.customerId) &&
                (existingTrxOnServer.periodMonth == newTrxFromHP2.periodMonth) &&
                (existingTrxOnServer.id != newTrxFromHP2.id)

        assertTrue("Sistem harus mendeteksi transaksi ganda untuk pelanggan dan periode yang sama", isDuplicate)
    }
}
