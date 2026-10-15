package com.example

import com.example.util.IdGenerator
import com.example.util.WhatsAppHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppHelperTest {

    @Test
    fun testNormalizePhoneNumberStandardIndonesian08() {
        val raw = "0812-3456-7890"
        val normalized = WhatsAppHelper.normalizePhoneNumber(raw)
        assertEquals("6281234567890", normalized)
    }

    @Test
    fun testNormalizePhoneNumberWithPlus62() {
        val raw = "+62 856 7890 1234"
        val normalized = WhatsAppHelper.normalizePhoneNumber(raw)
        assertEquals("6285678901234", normalized)
    }

    @Test
    fun testNormalizePhoneNumberStartingWith8() {
        val raw = "819-8765-4321"
        val normalized = WhatsAppHelper.normalizePhoneNumber(raw)
        assertEquals("6281987654321", normalized)
    }

    @Test
    fun testNormalizePhoneNumberAlready62() {
        val raw = "628123456789"
        val normalized = WhatsAppHelper.normalizePhoneNumber(raw)
        assertEquals("628123456789", normalized)
    }

    @Test
    fun testNormalizePhoneNumberBlankOrNull() {
        assertEquals("", WhatsAppHelper.normalizePhoneNumber(""))
        assertEquals("", WhatsAppHelper.normalizePhoneNumber(null))
        assertEquals("", WhatsAppHelper.normalizePhoneNumber("   "))
    }

    @Test
    fun testIdGeneratorUniqueAndPositive() {
        val id1 = IdGenerator.generateUniqueId()
        val id2 = IdGenerator.generateUniqueId()
        val id3 = IdGenerator.generateUniqueId()

        assertTrue(id1 > 0L)
        assertTrue(id2 > 0L)
        assertTrue(id3 > 0L)

        // Verifikasi tidak terjadi tabrakan ID
        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertNotEquals(id1, id3)
    }
}
