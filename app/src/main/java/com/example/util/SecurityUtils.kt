package com.example.util

import java.security.MessageDigest

object SecurityUtils {
    private const val SALT = "billingku_secure_salt_v2_"

    /**
     * Menghasilkan hash SHA-256 tersensitisasi salt untuk keamanan PIN staf.
     */
    fun hashPin(pin: String): String {
        val trimmed = pin.trim()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((SALT + trimmed).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Memverifikasi PIN input terhadap PIN atau hash yang tersimpan di database.
     * Mendukung backward-compatibility untuk PIN plain text lama sekaligus mengamankan PIN baru.
     */
    fun verifyPin(enteredPin: String, storedPinOrHash: String): Boolean {
        if (enteredPin.isBlank() || storedPinOrHash.isBlank()) return false
        val entered = enteredPin.trim()
        val stored = storedPinOrHash.trim()

        // 1. Backward-compatible: jika tersimpan sebagai plain text (panjang <= 8)
        if (stored.length <= 8 && stored == entered) {
            return true
        }

        // 2. Hash SHA-256 verification (constant-time comparison)
        val computedHash = hashPin(entered)
        return MessageDigest.isEqual(
            computedHash.toByteArray(Charsets.UTF_8),
            stored.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Mengecek apakah string sudah berupa hash SHA-256 64 karakter heksadesimal.
     */
    fun isHashed(pinString: String): Boolean {
        return pinString.length == 64 && pinString.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}
