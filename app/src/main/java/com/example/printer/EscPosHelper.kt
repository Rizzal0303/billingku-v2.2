package com.example.printer

import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.local.entity.TransactionEntity

object EscPosHelper {
    // Standard ESC/POS Commands
    val ESC: Byte = 0x1B
    val GS: Byte = 0x1D
    val LF: Byte = 0x0A

    // Initialization
    val CMD_INIT = byteArrayOf(ESC, 0x40)

    // Alignment
    val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 0x02)

    // Font styles
    val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)

    val DOUBLE_HEIGHT_ON = byteArrayOf(GS, 0x21, 0x01)
    val DOUBLE_WIDTH_ON = byteArrayOf(GS, 0x21, 0x10)
    val DOUBLE_SIZE_ON = byteArrayOf(GS, 0x21, 0x11)
    val TEXT_NORMAL = byteArrayOf(GS, 0x21, 0x00)

    // Paper Cut (Hemat Thermal: potong kertas tanpa feed berlebih)
    val PAPER_CUT = byteArrayOf(GS, 0x56, 0x01)
    val FEED_1_LINE = byteArrayOf(LF)
    val FEED_2_LINES = byteArrayOf(LF, LF)
    val FEED_3_LINES = byteArrayOf(LF, LF, LF)

    const val LINE_WIDTH_58MM = 32

    fun formatRupiah(amount: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        return format.format(amount).replace(",00", "")
    }

    fun formatTwoColumns(left: String, right: String, width: Int = LINE_WIDTH_58MM): String {
        val totalWidth = maxOf(12, width)

        // Ensure right string does not overflow width
        val maxRightLen = maxOf(4, totalWidth - 4)
        val safeRight = if (right.length > maxRightLen) {
            right.take(maxRightLen - 1) + "…"
        } else {
            right
        }

        val maxLeftLen = maxOf(1, totalWidth - safeRight.length - 1)
        val safeLeft = if (left.length > maxLeftLen) {
            if (maxLeftLen > 2) {
                left.take(maxLeftLen - 1) + "…"
            } else {
                left.take(maxLeftLen)
            }
        } else {
            left
        }

        val spaceCount = maxOf(1, totalWidth - safeLeft.length - safeRight.length)
        return safeLeft + " ".repeat(spaceCount) + safeRight
    }

    fun formatDivider(char: Char = '-', width: Int = LINE_WIDTH_58MM): String {
        return char.toString().repeat(maxOf(1, width))
    }

    fun centerText(text: String, width: Int = LINE_WIDTH_58MM): String {
        if (text.length >= width) return text.take(width)
        val spaces = maxOf(0, (width - text.length) / 2)
        return " ".repeat(spaces) + text
    }

    data class ReceiptData(
        val businessName: String = "BILLINGKU WIFI NETWORK",
        val businessAddress: String = "Jl. Merdeka No. 45 (RT/RW Net)",
        val businessPhone: String = "0812-3456-7890",
        val invoiceNo: String,
        val customerName: String,
        val customerCode: String,
        val packageName: String,
        val odpLocation: String = "",
        val period: String,
        val monthlyFee: Long,
        val adminFee: Long = 0,
        val discount: Long = 0,
        val totalAmount: Long,
        val paymentMethod: String = "TUNAI",
        val cashierName: String = "Kasir Utama",
        val date: Long = System.currentTimeMillis(),
        val template: String = "MODERN", // "MODERN", "COMPACT", "DETAILED", "MINIMALIST", "INVOICE"
        val footerMessage: String = "Simpan struk ini sebagai bukti pembayaran sah. Terima kasih!"
    )

    fun generatePlainTextReceipt(data: ReceiptData, autoCut: Boolean = false, feedLines: Int = 1): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(data.date))

        when (data.template) {
            "COMPACT" -> {
                sb.appendLine(centerText(data.businessName, LINE_WIDTH_58MM))
                sb.appendLine(formatDivider('='))
                sb.appendLine(formatTwoColumns("No.Inv", data.invoiceNo))
                sb.appendLine(formatTwoColumns("Tgl", dateStr))
                sb.appendLine(formatTwoColumns("Pelanggan", data.customerName))
                sb.appendLine(formatTwoColumns("Periode", data.period))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns(data.packageName, formatRupiah(data.monthlyFee)))
                if (data.adminFee > 0) sb.appendLine(formatTwoColumns("Biaya Admin", formatRupiah(data.adminFee)))
                if (data.discount > 0) sb.appendLine(formatTwoColumns("Diskon", "-${formatRupiah(data.discount)}"))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("TOTAL LUNAS", formatRupiah(data.totalAmount)))
                sb.appendLine(formatTwoColumns("Via", data.paymentMethod))
                sb.appendLine(formatDivider('='))
                sb.appendLine(centerText("Terima Kasih", LINE_WIDTH_58MM))
            }
            "DETAILED" -> {
                sb.appendLine(centerText(data.businessName, LINE_WIDTH_58MM))
                sb.appendLine(centerText(data.businessAddress, LINE_WIDTH_58MM))
                sb.appendLine(centerText("WA: " + data.businessPhone, LINE_WIDTH_58MM))
                sb.appendLine(formatDivider('='))
                sb.appendLine(centerText("** BUKTI PEMBAYARAN WIFI **", LINE_WIDTH_58MM))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("No. Invoice", data.invoiceNo))
                sb.appendLine(formatTwoColumns("Waktu Bayar", dateStr))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("ID Pelanggan", data.customerCode))
                sb.appendLine(formatTwoColumns("Nama", data.customerName))
                if (data.odpLocation.isNotEmpty()) {
                    sb.appendLine(formatTwoColumns("ODP/Port", data.odpLocation))
                }
                sb.appendLine(formatTwoColumns("Periode", data.period))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("Tagihan Internet", formatRupiah(data.monthlyFee)))
                sb.appendLine("  [${data.packageName}]")
                if (data.adminFee > 0) sb.appendLine(formatTwoColumns("Biaya Admin", formatRupiah(data.adminFee)))
                if (data.discount > 0) sb.appendLine(formatTwoColumns("Diskon Khusus", "-${formatRupiah(data.discount)}"))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("TOTAL BAYAR", formatRupiah(data.totalAmount)))
                sb.appendLine(formatTwoColumns("Metode Bayar", data.paymentMethod))
                sb.appendLine(formatTwoColumns("Status", "LUNAS [PAID]"))
                sb.appendLine(formatDivider('='))
                sb.appendLine(centerText(data.footerMessage, LINE_WIDTH_58MM))
                sb.appendLine(centerText("WiFi Lancar, Bisnis Lancar!", LINE_WIDTH_58MM))
            }
            "MINIMALIST" -> {
                sb.appendLine(centerText(data.businessName, LINE_WIDTH_58MM))
                sb.appendLine(centerText("WA: ${data.businessPhone}", LINE_WIDTH_58MM))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("Pelanggan", data.customerName))
                sb.appendLine(formatTwoColumns("No. Faktur", data.invoiceNo))
                sb.appendLine(formatTwoColumns("Tgl Bayar", dateStr))
                sb.appendLine(formatTwoColumns("Paket", data.packageName))
                sb.appendLine(formatTwoColumns("Periode", data.period))
                sb.appendLine(formatDivider('.'))
                sb.appendLine(formatTwoColumns("TOTAL LUNAS", formatRupiah(data.totalAmount)))
                sb.appendLine(formatTwoColumns("Metode Bayar", data.paymentMethod))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(centerText("Bukti Pembayaran Sah", LINE_WIDTH_58MM))
                sb.appendLine(centerText("Terima Kasih", LINE_WIDTH_58MM))
            }
            "INVOICE" -> {
                sb.appendLine(centerText(data.businessName, LINE_WIDTH_58MM))
                sb.appendLine(centerText(data.businessAddress, LINE_WIDTH_58MM))
                sb.appendLine(centerText("Layanan: ${data.businessPhone}", LINE_WIDTH_58MM))
                sb.appendLine(formatDivider('='))
                sb.appendLine(centerText("FAKTUR PEMBAYARAN INTERNET", LINE_WIDTH_58MM))
                sb.appendLine(centerText("[ STATUS: LUNAS / PAID ]", LINE_WIDTH_58MM))
                sb.appendLine(formatDivider('='))
                sb.appendLine(formatTwoColumns("No. Faktur", data.invoiceNo))
                sb.appendLine(formatTwoColumns("Tgl Transaksi", dateStr))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("ID Pelanggan", data.customerCode))
                sb.appendLine(formatTwoColumns("Nama Pelanggan", data.customerName))
                if (data.odpLocation.isNotEmpty()) {
                    sb.appendLine(formatTwoColumns("Titik ODP/Port", data.odpLocation))
                }
                sb.appendLine(formatTwoColumns("Periode Tagihan", data.period))
                sb.appendLine(formatDivider('-'))
                sb.appendLine("RINCIAN TAGIHAN:")
                sb.appendLine(formatTwoColumns("1. ${data.packageName}", formatRupiah(data.monthlyFee)))
                if (data.adminFee > 0) sb.appendLine(formatTwoColumns("2. Biaya Administrasi", formatRupiah(data.adminFee)))
                if (data.discount > 0) sb.appendLine(formatTwoColumns("3. Potongan Diskon", "-${formatRupiah(data.discount)}"))
                sb.appendLine(formatDivider('='))
                sb.appendLine(formatTwoColumns("TOTAL PEMBAYARAN", formatRupiah(data.totalAmount)))
                sb.appendLine(formatTwoColumns("Cara Pembayaran", data.paymentMethod))
                sb.appendLine(formatDivider('='))
                sb.appendLine(centerText(data.footerMessage, LINE_WIDTH_58MM))
                sb.appendLine(centerText("Simpan faktur ini sebagai bukti sah", LINE_WIDTH_58MM))
            }
            else -> { // "MODERN"
                sb.appendLine(centerText(data.businessName, LINE_WIDTH_58MM))
                sb.appendLine(centerText(data.businessAddress, LINE_WIDTH_58MM))
                sb.appendLine(centerText("Telp/WA: ${data.businessPhone}", LINE_WIDTH_58MM))
                sb.appendLine(formatDivider('='))
                sb.appendLine(centerText("STRUK PEMBAYARAN INTERNET", LINE_WIDTH_58MM))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("Invoice", data.invoiceNo))
                sb.appendLine(formatTwoColumns("Tanggal", dateStr))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("Kode Cust", data.customerCode))
                sb.appendLine(formatTwoColumns("Pelanggan", data.customerName))
                sb.appendLine(formatTwoColumns("Periode", data.period))
                sb.appendLine(formatTwoColumns("Paket", data.packageName))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(formatTwoColumns("Subtotal", formatRupiah(data.monthlyFee)))
                if (data.adminFee > 0) sb.appendLine(formatTwoColumns("Admin", formatRupiah(data.adminFee)))
                if (data.discount > 0) sb.appendLine(formatTwoColumns("Diskon", "-${formatRupiah(data.discount)}"))
                sb.appendLine(formatDivider('='))
                sb.appendLine(formatTwoColumns("TOTAL", formatRupiah(data.totalAmount)))
                sb.appendLine(formatTwoColumns("Bayar Via", data.paymentMethod))
                sb.appendLine(formatTwoColumns("Status", "LUNAS (SUCCESS)"))
                sb.appendLine(formatDivider('-'))
                sb.appendLine(centerText(data.footerMessage, LINE_WIDTH_58MM))
            }
        }

        if (feedLines > 0) {
            repeat(feedLines) {
                sb.appendLine()
            }
        }

        val gapLabel = when (feedLines) {
            0 -> "0 mm - TANPA GAP"
            1 -> "2 mm GAP"
            2 -> "4 mm GAP"
            3 -> "6 mm GAP"
            4 -> "8 mm GAP"
            else -> "${feedLines * 2} mm GAP"
        }
        sb.appendLine(centerText("[ ✂ SOBEK DISINI ($gapLabel) ]", LINE_WIDTH_58MM))

        return sb.toString()
    }

    fun buildEscPosBytes(data: ReceiptData, autoCut: Boolean = false, feedLines: Int = 1): ByteArray {
        val stream = ByteArrayOutputStream()
        val charset = Charsets.US_ASCII

        // Init
        stream.write(CMD_INIT)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(data.date))

        when (data.template) {
            "MINIMALIST" -> {
                stream.write(ALIGN_CENTER)
                stream.write(BOLD_ON)
                stream.write("${data.businessName}\n".toByteArray(charset))
                stream.write(BOLD_OFF)
                stream.write("WA: ${data.businessPhone}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))

                stream.write(ALIGN_LEFT)
                stream.write("${formatTwoColumns("Pelanggan", data.customerName)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("No. Faktur", data.invoiceNo)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Tgl Bayar", dateStr)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Paket", data.packageName)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Periode", data.period)}\n".toByteArray(charset))
                stream.write("${formatDivider('.')}\n".toByteArray(charset))

                stream.write(BOLD_ON)
                stream.write("${formatTwoColumns("TOTAL LUNAS", formatRupiah(data.totalAmount))}\n".toByteArray(charset))
                stream.write(BOLD_OFF)
                stream.write("${formatTwoColumns("Metode Bayar", data.paymentMethod)}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))

                stream.write(ALIGN_CENTER)
                stream.write("Bukti Pembayaran Sah\n".toByteArray(charset))
                stream.write("Terima Kasih\n".toByteArray(charset))
            }
            "INVOICE" -> {
                stream.write(ALIGN_CENTER)
                stream.write(BOLD_ON)
                stream.write("${data.businessName}\n".toByteArray(charset))
                stream.write(BOLD_OFF)
                stream.write("${data.businessAddress}\n".toByteArray(charset))
                stream.write("Layanan: ${data.businessPhone}\n".toByteArray(charset))
                stream.write("${formatDivider('=')}\n".toByteArray(charset))
                stream.write(BOLD_ON)
                stream.write("FAKTUR PEMBAYARAN INTERNET\n".toByteArray(charset))
                stream.write("[ STATUS: LUNAS / PAID ]\n".toByteArray(charset))
                stream.write(BOLD_OFF)
                stream.write("${formatDivider('=')}\n".toByteArray(charset))

                stream.write(ALIGN_LEFT)
                stream.write("${formatTwoColumns("No. Faktur", data.invoiceNo)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Tgl Transaksi", dateStr)}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("ID Pelanggan", data.customerCode)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Nama Pelanggan", data.customerName)}\n".toByteArray(charset))
                if (data.odpLocation.isNotEmpty()) {
                    stream.write("${formatTwoColumns("Titik ODP/Port", data.odpLocation)}\n".toByteArray(charset))
                }
                stream.write("${formatTwoColumns("Periode Tagihan", data.period)}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))
                stream.write("RINCIAN TAGIHAN:\n".toByteArray(charset))
                stream.write("${formatTwoColumns("1. ${data.packageName}", formatRupiah(data.monthlyFee))}\n".toByteArray(charset))
                if (data.adminFee > 0) {
                    stream.write("${formatTwoColumns("2. Biaya Administrasi", formatRupiah(data.adminFee))}\n".toByteArray(charset))
                }
                if (data.discount > 0) {
                    stream.write("${formatTwoColumns("3. Potongan Diskon", "-${formatRupiah(data.discount)}")}\n".toByteArray(charset))
                }
                stream.write("${formatDivider('=')}\n".toByteArray(charset))

                stream.write(BOLD_ON)
                stream.write(DOUBLE_HEIGHT_ON)
                stream.write("${formatTwoColumns("TOTAL BAYAR", formatRupiah(data.totalAmount))}\n".toByteArray(charset))
                stream.write(TEXT_NORMAL)
                stream.write(BOLD_OFF)
                stream.write("${formatTwoColumns("Cara Bayar", data.paymentMethod)}\n".toByteArray(charset))
                stream.write("${formatDivider('=')}\n".toByteArray(charset))

                stream.write(ALIGN_CENTER)
                stream.write("${data.footerMessage}\n".toByteArray(charset))
                stream.write("Simpan faktur ini sebagai bukti sah\n".toByteArray(charset))
            }
            "COMPACT" -> {
                stream.write(ALIGN_CENTER)
                stream.write(BOLD_ON)
                stream.write("${data.businessName}\n".toByteArray(charset))
                stream.write(BOLD_OFF)
                stream.write("${formatDivider('=')}\n".toByteArray(charset))

                stream.write(ALIGN_LEFT)
                stream.write("${formatTwoColumns("No.Inv", data.invoiceNo)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Tgl", dateStr)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Pelanggan", data.customerName)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Periode", data.period)}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))
                stream.write("${formatTwoColumns(data.packageName, formatRupiah(data.monthlyFee))}\n".toByteArray(charset))
                if (data.adminFee > 0) stream.write("${formatTwoColumns("Biaya Admin", formatRupiah(data.adminFee))}\n".toByteArray(charset))
                if (data.discount > 0) stream.write("${formatTwoColumns("Diskon", "-${formatRupiah(data.discount)}")}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))

                stream.write(BOLD_ON)
                stream.write("${formatTwoColumns("TOTAL LUNAS", formatRupiah(data.totalAmount))}\n".toByteArray(charset))
                stream.write(BOLD_OFF)
                stream.write("${formatTwoColumns("Via", data.paymentMethod)}\n".toByteArray(charset))
                stream.write("${formatDivider('=')}\n".toByteArray(charset))

                stream.write(ALIGN_CENTER)
                stream.write("Terima Kasih\n".toByteArray(charset))
            }
            else -> { // "MODERN" or "DETAILED"
                stream.write(ALIGN_CENTER)
                stream.write(BOLD_ON)
                stream.write("${data.businessName}\n".toByteArray(charset))
                stream.write(BOLD_OFF)

                stream.write("${data.businessAddress}\n".toByteArray(charset))
                stream.write("Telp/WA: ${data.businessPhone}\n".toByteArray(charset))
                stream.write("${formatDivider('=')}\n".toByteArray(charset))

                stream.write(BOLD_ON)
                stream.write("STRUK PEMBAYARAN WIFI\n".toByteArray(charset))
                stream.write(BOLD_OFF)
                stream.write("${formatDivider('-')}\n".toByteArray(charset))

                stream.write(ALIGN_LEFT)
                stream.write("${formatTwoColumns("No. Inv", data.invoiceNo)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Tanggal", dateStr)}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))

                stream.write("${formatTwoColumns("Kode Cust", data.customerCode)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Pelanggan", data.customerName)}\n".toByteArray(charset))
                if (data.odpLocation.isNotEmpty()) {
                    stream.write("${formatTwoColumns("ODP Port", data.odpLocation)}\n".toByteArray(charset))
                }
                stream.write("${formatTwoColumns("Periode", data.period)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Paket", data.packageName)}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))

                stream.write("${formatTwoColumns("Subtotal", formatRupiah(data.monthlyFee))}\n".toByteArray(charset))
                if (data.adminFee > 0) {
                    stream.write("${formatTwoColumns("Admin", formatRupiah(data.adminFee))}\n".toByteArray(charset))
                }
                if (data.discount > 0) {
                    stream.write("${formatTwoColumns("Diskon", "-${formatRupiah(data.discount)}")}\n".toByteArray(charset))
                }
                stream.write("${formatDivider('=')}\n".toByteArray(charset))

                // Total
                stream.write(BOLD_ON)
                stream.write(DOUBLE_HEIGHT_ON)
                stream.write("${formatTwoColumns("TOTAL", formatRupiah(data.totalAmount))}\n".toByteArray(charset))
                stream.write(TEXT_NORMAL)
                stream.write(BOLD_OFF)

                stream.write("${formatTwoColumns("Bayar Via", data.paymentMethod)}\n".toByteArray(charset))
                stream.write("${formatTwoColumns("Status", "LUNAS [PAID]")}\n".toByteArray(charset))
                stream.write("${formatDivider('-')}\n".toByteArray(charset))

                stream.write(ALIGN_CENTER)
                stream.write("${data.footerMessage}\n".toByteArray(charset))
                stream.write("Layanan Komplain: ${data.businessPhone}\n".toByteArray(charset))
            }
        }

        // Feed Gap 2mm per step (ESC J n: 16 dots per 2mm pada 203 DPI)
        if (feedLines > 0) {
            val dots = (feedLines * 16).coerceIn(8, 200)
            stream.write(byteArrayOf(ESC, 0x4A, dots.toByte()))
        }

        return stream.toByteArray()
    }

    data class SettlementReceiptData(
        val businessName: String = "BILLINGKU WIFI NETWORK",
        val businessAddress: String = "Jl. Merdeka No. 45 (RT/RW Net)",
        val businessPhone: String = "0812-3456-7890",
        val batchNumber: String,
        val periodMonth: String,
        val cashierName: String,
        val recipientName: String = "Owner / Admin",
        val date: Long = System.currentTimeMillis(),
        val totalAmount: Long,
        val totalCashAmount: Long = 0,
        val totalNonCashAmount: Long = 0,
        val customerCount: Int,
        val items: List<TransactionEntity> = emptyList(),
        val notes: String = ""
    )

    fun generatePlainTextSettlementReceipt(data: SettlementReceiptData): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(data.date))

        sb.appendLine(centerText(data.businessName, LINE_WIDTH_58MM))
        sb.appendLine(centerText("BUKTI TANDA TERIMA SETORAN", LINE_WIDTH_58MM))
        sb.appendLine(formatDivider('='))
        sb.appendLine(formatTwoColumns("No. Batch", data.batchNumber))
        sb.appendLine(formatTwoColumns("Tanggal", dateStr))
        sb.appendLine(formatTwoColumns("Periode", data.periodMonth))
        sb.appendLine(formatTwoColumns("Penyetor", data.cashierName))
        sb.appendLine(formatTwoColumns("Penerima", data.recipientName))
        sb.appendLine(formatDivider('-'))
        sb.appendLine("RINGKASAN PELANGGAN LUNAS:")
        data.items.forEachIndexed { index, item ->
            val num = "${index + 1}. ${item.customerName}"
            sb.appendLine(formatTwoColumns(num, formatRupiah(item.totalAmount)))
        }
        sb.appendLine(formatDivider('-'))
        sb.appendLine(formatTwoColumns("Jumlah Pelanggan", "${data.customerCount} Org"))
        sb.appendLine(formatTwoColumns("Uang Tunai (Cash)", formatRupiah(data.totalCashAmount)))
        if (data.totalNonCashAmount > 0) {
            sb.appendLine(formatTwoColumns("Non-Tunai (Bank/QR)", formatRupiah(data.totalNonCashAmount)))
        }
        sb.appendLine(formatDivider('='))
        sb.appendLine(formatTwoColumns("TOTAL DISETORKAN", formatRupiah(data.totalAmount)))
        sb.appendLine(formatDivider('='))
        if (data.notes.isNotBlank()) {
            sb.appendLine("Catatan: ${data.notes}")
            sb.appendLine(formatDivider('-'))
        }
        sb.appendLine(centerText("Setoran Sah & Terverifikasi", LINE_WIDTH_58MM))
        sb.appendLine(centerText("Billingku WiFi RT/RW Net", LINE_WIDTH_58MM))
        return sb.toString()
    }

    fun buildEscPosSettlementBytes(data: SettlementReceiptData, autoCut: Boolean = false, feedLines: Int = 1): ByteArray {
        val stream = ByteArrayOutputStream()
        val charset = Charsets.ISO_8859_1

        stream.write(CMD_INIT)
        stream.write(ALIGN_CENTER)
        stream.write(BOLD_ON)
        stream.write("${data.businessName}\n".toByteArray(charset))
        stream.write("BUKTI SETORAN KASIR\n".toByteArray(charset))
        stream.write(BOLD_OFF)
        stream.write("${formatDivider('=')}\n".toByteArray(charset))

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(data.date))

        stream.write(ALIGN_LEFT)
        stream.write("${formatTwoColumns("Batch", data.batchNumber)}\n".toByteArray(charset))
        stream.write("${formatTwoColumns("Tgl/Jam", dateStr)}\n".toByteArray(charset))
        stream.write("${formatTwoColumns("Periode", data.periodMonth)}\n".toByteArray(charset))
        stream.write("${formatTwoColumns("Penyetor", data.cashierName)}\n".toByteArray(charset))
        stream.write("${formatTwoColumns("Penerima", data.recipientName)}\n".toByteArray(charset))
        stream.write("${formatDivider('-')}\n".toByteArray(charset))

        stream.write("DAFTAR PELANGGAN:\n".toByteArray(charset))
        data.items.forEachIndexed { index, item ->
            val num = "${index + 1}. ${item.customerName.take(13)}"
            val tag = if (item.paymentMethod.equals("TUNAI", ignoreCase = true)) " [C]" else " [N]"
            stream.write("${formatTwoColumns(num + tag, formatRupiah(item.totalAmount))}\n".toByteArray(charset))
        }
        stream.write("${formatDivider('-')}\n".toByteArray(charset))

        stream.write("${formatTwoColumns("Total Cust", "${data.customerCount} Org")}\n".toByteArray(charset))
        stream.write("${formatTwoColumns("Uang Tunai", formatRupiah(data.totalCashAmount))}\n".toByteArray(charset))
        if (data.totalNonCashAmount > 0) {
            stream.write("${formatTwoColumns("Non-Tunai", formatRupiah(data.totalNonCashAmount))}\n".toByteArray(charset))
        }
        stream.write("${formatDivider('=')}\n".toByteArray(charset))

        stream.write(BOLD_ON)
        stream.write(DOUBLE_HEIGHT_ON)
        stream.write("${formatTwoColumns("TOTAL SETOR", formatRupiah(data.totalAmount))}\n".toByteArray(charset))
        stream.write(TEXT_NORMAL)
        stream.write(BOLD_OFF)
        stream.write("${formatDivider('=')}\n".toByteArray(charset))

        if (data.notes.isNotBlank()) {
            stream.write("Ket: ${data.notes}\n".toByteArray(charset))
            stream.write("${formatDivider('-')}\n".toByteArray(charset))
        }

        stream.write(ALIGN_CENTER)
        stream.write("Tanda Terima Sah Sistem\n".toByteArray(charset))
        if (feedLines > 0) {
            val dots = (feedLines * 16).coerceIn(8, 200)
            stream.write(byteArrayOf(ESC, 0x4A, dots.toByte()))
        }

        return stream.toByteArray()
    }

    fun generateWhatsAppSettlementText(data: SettlementReceiptData): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(data.date))
        val sb = StringBuilder()
        sb.appendLine("🧾 *BUKTI TANDA TERIMA SETORAN* 🧾")
        sb.appendLine("*${data.businessName}*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📌 *No. Batch:* ${data.batchNumber}")
        sb.appendLine("📅 *Tanggal:* $dateStr")
        sb.appendLine("🗓️ *Periode Tagihan:* ${data.periodMonth}")
        sb.appendLine("👤 *Penyetor (Kasir):* ${data.cashierName}")
        sb.appendLine("🏢 *Penerima:* ${data.recipientName}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("👥 *Daftar Pelanggan (${data.customerCount} org):*")
        data.items.forEachIndexed { idx, it ->
            sb.appendLine("${idx + 1}. ${it.customerCode} - ${it.customerName} (${formatRupiah(it.totalAmount)} • ${it.paymentMethod})")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("💵 *Uang Tunai (Fisik):* ${formatRupiah(data.totalCashAmount)}")
        if (data.totalNonCashAmount > 0) {
            sb.appendLine("💳 *Non-Tunai (Transfer/QRIS):* ${formatRupiah(data.totalNonCashAmount)}")
        }
        sb.appendLine("💰 *TOTAL SETORAN:* *${formatRupiah(data.totalAmount)}*")
        if (data.notes.isNotBlank()) {
            sb.appendLine("📝 *Catatan:* ${data.notes}")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("_Status: Sah & Disetorkan ke Pembukuan_")
        return sb.toString()
    }
}
