package com.example.util

class DuplicatePaymentException(
    val existingInvoiceNumber: String,
    message: String = "Pelanggan sudah pernah dibayar untuk periode ini (No. Faktur: $existingInvoiceNumber)"
) : Exception(message)
