package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val localeId = Locale("id", "ID")

    // ThreadLocal formatters to prevent race conditions and avoid repeated allocation
    private val shortDateTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("dd MMM, HH:mm", localeId)
    }

    private val fullDateTimeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("dd MMM yyyy, HH:mm", localeId)
    }

    private val invoiceDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }

    private val monthYearFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MMMM yyyy", localeId)
    }

    fun formatShortDateTime(timestamp: Long): String {
        return shortDateTimeFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatFullDateTime(timestamp: Long): String {
        return fullDateTimeFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatInvoiceDate(timestamp: Long): String {
        return invoiceDateFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatMonthYear(timestamp: Long): String {
        return monthYearFormat.get()?.format(Date(timestamp)) ?: ""
    }
}
