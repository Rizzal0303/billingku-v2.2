package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WhatsAppHelper {

    /**
     * Menormalkan nomor telepon ke format internasional WhatsApp (standar 628xxx Indonesia).
     * Contoh:
     * - "0812-3456-7890" -> "6281234567890"
     * - "+62 812 3456 7890" -> "6281234567890"
     * - "81234567890" -> "6281234567890"
     * - "6281234567890" -> "6281234567890"
     */
    fun normalizePhoneNumber(rawPhone: String?): String {
        if (rawPhone.isNullOrBlank()) return ""
        var cleaned = rawPhone.trim().replace(Regex("[^0-9+]"), "")
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1)
        }
        return when {
            cleaned.startsWith("08") -> "62" + cleaned.substring(1)
            cleaned.startsWith("8") -> "62$cleaned"
            cleaned.startsWith("62") -> cleaned
            else -> cleaned
        }
    }

    /**
     * Membuka aplikasi WhatsApp dengan proteksi crash komprehensif.
     * Mencegah ActivityNotFoundException jika WhatsApp tidak terinstal,
     * serta otomatis fallback ke WhatsApp Business, browser, atau Chooser.
     */
    fun sendWhatsAppMessage(
        context: Context,
        rawPhone: String?,
        message: String
    ): Boolean {
        val normalizedPhone = normalizePhoneNumber(rawPhone)
        val encodedMsg = Uri.encode(message)

        val url = if (normalizedPhone.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$normalizedPhone&text=$encodedMsg"
        } else {
            "https://api.whatsapp.com/send?text=$encodedMsg"
        }

        val uri = Uri.parse(url)

        // 1. Coba aplikasi WhatsApp Consumer
        val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(waIntent)
            return true
        } catch (_: Exception) {
            // 2. WhatsApp Consumer tidak ada, coba WhatsApp Business
            try {
                val waBusinessIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp.w4b")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(waBusinessIntent)
                return true
            } catch (_: Exception) {
                // 3. Fallback ke URL via Browser atau handler Intent terbuka
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    return true
                } catch (_: Exception) {
                    // 4. Fallback ke Intent Send Chooser umum
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Kirim Pesan Tagihan"))
                        return true
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "WhatsApp atau browser tidak tersedia di perangkat ini.",
                            Toast.LENGTH_LONG
                        ).show()
                        return false
                    }
                }
            }
        }
    }
}
