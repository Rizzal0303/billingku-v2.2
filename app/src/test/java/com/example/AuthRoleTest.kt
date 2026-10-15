package com.example

import com.example.data.local.entity.StaffEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRoleTest {

    @Test
    fun testAdminRoleAccess() {
        val adminStaff = StaffEntity(
            id = 1L,
            username = "admin",
            name = "Budi Hartono",
            role = "ADMIN",
            phone = "081234567890",
            pin = "1234",
            isActive = true
        )

        val isAdmin = adminStaff.role.equals("ADMIN", ignoreCase = true)
        val isCashier = adminStaff.role.equals("KASIR", ignoreCase = true)

        assertTrue(isAdmin)
        assertFalse(isCashier)
    }

    @Test
    fun testCashierStaffRoleAccess() {
        val cashierStaff = StaffEntity(
            id = 2L,
            username = "kasir1",
            name = "Siti Rahma",
            role = "KASIR",
            phone = "081987654321",
            pin = "1111",
            isActive = true
        )

        val isAdmin = cashierStaff.role.equals("ADMIN", ignoreCase = true)
        val isCashier = cashierStaff.role.equals("KASIR", ignoreCase = true)

        assertFalse(isAdmin)
        assertTrue(isCashier)
        assertEquals("1111", cashierStaff.pin)
    }
}
