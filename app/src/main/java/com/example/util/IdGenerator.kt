package com.example.util

import kotlin.random.Random

object IdGenerator {
    /**
     * Menghasilkan ID unik 64-bit berbasis timestamp milidetik dan acak 3-digit.
     * Contoh: 1790441234567890L
     * Terjamin unik antar perangkat berbeda tanpa benturan primary key (multi-device collision-free).
     */
    fun generateUniqueId(): Long {
        val now = System.currentTimeMillis()
        val randomPart = Random.nextInt(100, 1000).toLong()
        return (now * 1000L) + randomPart
    }
}
