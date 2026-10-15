package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.printer.EscPosHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfInvoiceGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width in points (72 DPI)
    private const val PAGE_HEIGHT = 842 // A4 standard height in points (72 DPI)

    /**
     * Membuat file PDF Faktur Invoice Resmi Tabular (Opsi B)
     * Menggunakan native Android PdfDocument tanpa dependensi eksternal.
     */
    fun generateInvoicePdf(context: Context, receiptData: EscPosHelper.ReceiptData): File {
        val invoicesDir = File(context.cacheDir, "invoices")
        if (!invoicesDir.exists()) {
            invoicesDir.mkdirs()
        }

        val sanitizedInvoice = receiptData.invoiceNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val pdfFile = File(invoicesDir, "Faktur_${sanitizedInvoice}.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        drawInvoiceContent(canvas, receiptData)

        document.finishPage(page)

        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return pdfFile
    }

    /**
     * Membagikan file PDF ke WhatsApp, Telegram, Gmail, atau aplikasi lain
     */
    fun shareInvoicePdf(context: Context, receiptData: EscPosHelper.ReceiptData) {
        try {
            val file = generateInvoicePdf(context, receiptData)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Faktur Pembayaran Internet - ${receiptData.invoiceNo}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Berikut terlampir dokumen Faktur Pembayaran Internet resmi (${receiptData.invoiceNo}) atas nama *${receiptData.customerName}*. Terima kasih!"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Faktur PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal membuat PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Membuka dokumen PDF di aplikasi pembaca PDF internal perangkat
     */
    fun openInvoicePdf(context: Context, receiptData: EscPosHelper.ReceiptData) {
        try {
            val file = generateInvoicePdf(context, receiptData)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "Buka Dokumen Faktur PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Tidak ada aplikasi pembuka PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // ENGINE RENDER DOKUMEN FAKTUR RESMI TABULAR
    // =========================================================================
    private fun drawInvoiceContent(canvas: Canvas, receiptData: EscPosHelper.ReceiptData) {
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
        val dateStr = sdf.format(Date(receiptData.date))

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG)
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG)
        val paintLine = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Top Decorative Brand Accent Strip
        paintBg.color = Color.parseColor("#2563EB") // Electric Blue
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 10f, paintBg)

        val leftMargin = 40f
        val rightMargin = PAGE_WIDTH - 40f

        // 2. KOP SURAT & HEADER USAHA
        var currentY = 46f

        // Nama Usaha / ISP
        paintText.color = Color.parseColor("#0F172A")
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paintText.textSize = 17f
        canvas.drawText(receiptData.businessName, leftMargin, currentY, paintText)

        // Judul Dokumen (Kanan)
        paintText.color = Color.parseColor("#2563EB")
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paintText.textSize = 18f
        paintText.textAlign = Paint.Align.RIGHT
        canvas.drawText("FAKTUR RESMI", rightMargin, currentY, paintText)

        currentY += 14f
        paintText.color = Color.parseColor("#64748B")
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paintText.textSize = 8.5f
        paintText.textAlign = Paint.Align.LEFT
        canvas.drawText("PENYEDIA LAYANAN INTERNET BROADBAND & RT/RW NET", leftMargin, currentY, paintText)

        paintText.textAlign = Paint.Align.RIGHT
        canvas.drawText("BUKTI PEMBAYARAN SAH (LUNAS)", rightMargin, currentY, paintText)

        currentY += 13f
        paintText.textAlign = Paint.Align.LEFT
        if (receiptData.businessAddress.isNotBlank()) {
            canvas.drawText(receiptData.businessAddress, leftMargin, currentY, paintText)
            currentY += 12f
        }
        if (receiptData.businessPhone.isNotBlank()) {
            canvas.drawText("WhatsApp / CS: ${receiptData.businessPhone}", leftMargin, currentY, paintText)
            currentY += 14f
        }

        // Garis Pembatas Header
        currentY += 4f
        paintLine.color = Color.parseColor("#E2E8F0")
        paintLine.strokeWidth = 1.5f
        canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)

        // 3. KARTU INFORMASI PELANGGAN & DETAIL FAKTUR (DUA KOLOM)
        currentY += 16f
        val cardHeight = 82f
        val colWidth = (rightMargin - leftMargin - 16f) / 2f
        val col1Left = leftMargin
        val col1Right = col1Left + colWidth
        val col2Left = col1Right + 16f
        val col2Right = rightMargin

        // Box Kiri: Pelanggan
        paintBg.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(RectF(col1Left, currentY, col1Right, currentY + cardHeight), 8f, 8f, paintBg)
        paintLine.color = Color.parseColor("#E2E8F0")
        paintLine.style = Paint.Style.STROKE
        paintLine.strokeWidth = 1f
        canvas.drawRoundRect(RectF(col1Left, currentY, col1Right, currentY + cardHeight), 8f, 8f, paintLine)
        paintLine.style = Paint.Style.FILL

        // Box Kanan: Transaksi
        canvas.drawRoundRect(RectF(col2Left, currentY, col2Right, currentY + cardHeight), 8f, 8f, paintBg)
        paintLine.style = Paint.Style.STROKE
        canvas.drawRoundRect(RectF(col2Left, currentY, col2Right, currentY + cardHeight), 8f, 8f, paintLine)
        paintLine.style = Paint.Style.FILL

        // Teks Box Kiri
        var boxY = currentY + 16f
        paintText.textAlign = Paint.Align.LEFT
        paintText.color = Color.parseColor("#64748B")
        paintText.textSize = 8f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TAGIHAN KEPADA (PELANGGAN):", col1Left + 12f, boxY, paintText)

        boxY += 14f
        paintText.color = Color.parseColor("#0F172A")
        paintText.textSize = 12f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(receiptData.customerName, col1Left + 12f, boxY, paintText)

        boxY += 13f
        paintText.color = Color.parseColor("#475569")
        paintText.textSize = 8.5f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("ID Pelanggan : ${receiptData.customerCode}", col1Left + 12f, boxY, paintText)

        if (receiptData.odpLocation.isNotBlank()) {
            boxY += 12f
            canvas.drawText("Lokasi / ODP : ${receiptData.odpLocation}", col1Left + 12f, boxY, paintText)
        }

        // Teks Box Kanan
        boxY = currentY + 16f
        paintText.color = Color.parseColor("#64748B")
        paintText.textSize = 8f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("INFORMASI FAKTUR & TRANSAKSI:", col2Left + 12f, boxY, paintText)

        boxY += 14f
        paintText.color = Color.parseColor("#0F172A")
        paintText.textSize = 10f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("No. Faktur : #${receiptData.invoiceNo}", col2Left + 12f, boxY, paintText)

        boxY += 13f
        paintText.color = Color.parseColor("#475569")
        paintText.textSize = 8.5f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Waktu Bayar : $dateStr WIB", col2Left + 12f, boxY, paintText)

        boxY += 12f
        canvas.drawText("Metode Bayar : ${receiptData.paymentMethod}", col2Left + 12f, boxY, paintText)

        currentY += cardHeight + 20f

        // 4. TABEL TABULAR RINCIAN BIAYA (OFFICIAL TABLE)
        val tableTop = currentY
        val tableHeaderHeight = 24f
        val tableWidth = rightMargin - leftMargin

        // Header Background
        paintBg.color = Color.parseColor("#F1F5F9")
        canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + tableHeaderHeight, paintBg)

        // Table Header Border
        paintLine.color = Color.parseColor("#CBD5E1")
        paintLine.style = Paint.Style.STROKE
        paintLine.strokeWidth = 1f
        canvas.drawRect(leftMargin, tableTop, rightMargin, tableTop + tableHeaderHeight, paintLine)

        // Column X coordinates
        val colNoX = leftMargin + 16f
        val colDescX = leftMargin + 48f
        val colPeriodX = rightMargin - 160f
        val colAmountX = rightMargin - 14f

        // Table Header Titles
        paintText.color = Color.parseColor("#1E293B")
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paintText.textSize = 8.5f

        paintText.textAlign = Paint.Align.CENTER
        canvas.drawText("NO", colNoX, tableTop + 16f, paintText)

        paintText.textAlign = Paint.Align.LEFT
        canvas.drawText("DESKRIPSI LAYANAN / ITEM", colDescX, tableTop + 16f, paintText)

        paintText.textAlign = Paint.Align.CENTER
        canvas.drawText("PERIODE", colPeriodX, tableTop + 16f, paintText)

        paintText.textAlign = Paint.Align.RIGHT
        canvas.drawText("JUMLAH", colAmountX, tableTop + 16f, paintText)

        // Table Rows
        var rowY = tableTop + tableHeaderHeight
        val rowHeight = 26f
        var rowNumber = 1

        // Item 1: Paket Langganan
        paintBg.color = Color.WHITE
        canvas.drawRect(leftMargin, rowY, rightMargin, rowY + rowHeight, paintBg)
        canvas.drawRect(leftMargin, rowY, rightMargin, rowY + rowHeight, paintLine)

        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paintText.color = Color.parseColor("#0F172A")
        paintText.textSize = 9f

        paintText.textAlign = Paint.Align.CENTER
        canvas.drawText(rowNumber.toString(), colNoX, rowY + 17f, paintText)

        paintText.textAlign = Paint.Align.LEFT
        canvas.drawText("Biaya Langganan WiFi Paket ${receiptData.packageName}", colDescX, rowY + 17f, paintText)

        paintText.textAlign = Paint.Align.CENTER
        paintText.color = Color.parseColor("#475569")
        canvas.drawText(receiptData.period, colPeriodX, rowY + 17f, paintText)

        paintText.textAlign = Paint.Align.RIGHT
        paintText.color = Color.parseColor("#0F172A")
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(EscPosHelper.formatRupiah(receiptData.monthlyFee), colAmountX, rowY + 17f, paintText)
        rowY += rowHeight
        rowNumber++

        // Item 2: Biaya Admin Kasir (jika ada)
        if (receiptData.adminFee > 0) {
            paintBg.color = Color.parseColor("#FAFAFA")
            canvas.drawRect(leftMargin, rowY, rightMargin, rowY + rowHeight, paintBg)
            canvas.drawRect(leftMargin, rowY, rightMargin, rowY + rowHeight, paintLine)

            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paintText.color = Color.parseColor("#0F172A")
            paintText.textAlign = Paint.Align.CENTER
            canvas.drawText(rowNumber.toString(), colNoX, rowY + 17f, paintText)

            paintText.textAlign = Paint.Align.LEFT
            canvas.drawText("Biaya Administrasi Kasir", colDescX, rowY + 17f, paintText)

            paintText.textAlign = Paint.Align.CENTER
            paintText.color = Color.parseColor("#64748B")
            canvas.drawText("-", colPeriodX, rowY + 17f, paintText)

            paintText.textAlign = Paint.Align.RIGHT
            paintText.color = Color.parseColor("#0F172A")
            canvas.drawText(EscPosHelper.formatRupiah(receiptData.adminFee), colAmountX, rowY + 17f, paintText)
            rowY += rowHeight
            rowNumber++
        }

        // Item 3: Potongan / Diskon (jika ada)
        if (receiptData.discount > 0) {
            paintBg.color = Color.parseColor("#F0FDF4") // soft green tint
            canvas.drawRect(leftMargin, rowY, rightMargin, rowY + rowHeight, paintBg)
            canvas.drawRect(leftMargin, rowY, rightMargin, rowY + rowHeight, paintLine)

            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paintText.color = Color.parseColor("#15803D")
            paintText.textAlign = Paint.Align.CENTER
            canvas.drawText(rowNumber.toString(), colNoX, rowY + 17f, paintText)

            paintText.textAlign = Paint.Align.LEFT
            canvas.drawText("Potongan Harga / Diskon Khusus", colDescX, rowY + 17f, paintText)

            paintText.textAlign = Paint.Align.CENTER
            canvas.drawText("-", colPeriodX, rowY + 17f, paintText)

            paintText.textAlign = Paint.Align.RIGHT
            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("-${EscPosHelper.formatRupiah(receiptData.discount)}", colAmountX, rowY + 17f, paintText)
            rowY += rowHeight
        }

        // 5. RINGKASAN & TOTAL PEMBAYARAN
        paintLine.style = Paint.Style.FILL
        val summaryWidth = 230f
        val summaryLeft = rightMargin - summaryWidth
        var summaryY = rowY + 10f

        // Subtotal row
        paintText.textAlign = Paint.Align.LEFT
        paintText.color = Color.parseColor("#64748B")
        paintText.textSize = 9f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Subtotal Paket :", summaryLeft, summaryY + 12f, paintText)

        paintText.textAlign = Paint.Align.RIGHT
        paintText.color = Color.parseColor("#0F172A")
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(EscPosHelper.formatRupiah(receiptData.monthlyFee), colAmountX, summaryY + 12f, paintText)
        summaryY += 16f

        if (receiptData.discount > 0) {
            paintText.textAlign = Paint.Align.LEFT
            paintText.color = Color.parseColor("#15803D")
            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Potongan / Diskon :", summaryLeft, summaryY + 12f, paintText)

            paintText.textAlign = Paint.Align.RIGHT
            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("-${EscPosHelper.formatRupiah(receiptData.discount)}", colAmountX, summaryY + 12f, paintText)
            summaryY += 16f
        }

        if (receiptData.adminFee > 0) {
            paintText.textAlign = Paint.Align.LEFT
            paintText.color = Color.parseColor("#64748B")
            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Biaya Admin :", summaryLeft, summaryY + 12f, paintText)

            paintText.textAlign = Paint.Align.RIGHT
            paintText.color = Color.parseColor("#0F172A")
            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(EscPosHelper.formatRupiah(receiptData.adminFee), colAmountX, summaryY + 12f, paintText)
            summaryY += 16f
        }

        // Total Box Highlight (Electric Blue / Green)
        summaryY += 6f
        val totalBoxHeight = 36f
        paintBg.color = Color.parseColor("#EFF6FF") // Light blue
        canvas.drawRoundRect(RectF(summaryLeft - 10f, summaryY, rightMargin, summaryY + totalBoxHeight), 8f, 8f, paintBg)

        paintLine.color = Color.parseColor("#2563EB")
        paintLine.style = Paint.Style.STROKE
        paintLine.strokeWidth = 1.5f
        canvas.drawRoundRect(RectF(summaryLeft - 10f, summaryY, rightMargin, summaryY + totalBoxHeight), 8f, 8f, paintLine)
        paintLine.style = Paint.Style.FILL

        paintText.textAlign = Paint.Align.LEFT
        paintText.color = Color.parseColor("#1E3A8A")
        paintText.textSize = 10f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL LUNAS", summaryLeft, summaryY + 23f, paintText)

        paintText.textAlign = Paint.Align.RIGHT
        paintText.color = Color.parseColor("#1D4ED8")
        paintText.textSize = 15f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(EscPosHelper.formatRupiah(receiptData.totalAmount), colAmountX, summaryY + 24f, paintText)

        // 6. STEMPEL RESMI DIGITAL "LUNAS" (EMERALD STAMP)
        val stampX = leftMargin + 20f
        val stampY = rowY + 16f
        canvas.save()
        canvas.rotate(-5f, stampX + 70f, stampY + 40f)

        // Double Border Box for Stamp
        paintLine.color = Color.parseColor("#16A34A")
        paintLine.style = Paint.Style.STROKE
        paintLine.strokeWidth = 2f
        canvas.drawRoundRect(RectF(stampX, stampY, stampX + 155f, stampY + 70f), 10f, 10f, paintLine)

        paintLine.strokeWidth = 0.8f
        canvas.drawRoundRect(RectF(stampX + 4f, stampY + 4f, stampX + 151f, stampY + 66f), 7f, 7f, paintLine)
        paintLine.style = Paint.Style.FILL

        // Stamp Text
        paintText.textAlign = Paint.Align.CENTER
        paintText.color = Color.parseColor("#15803D")

        paintText.textSize = 7.5f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(receiptData.businessName.take(24).uppercase(), stampX + 77.5f, stampY + 18f, paintText)

        paintText.textSize = 17f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("★ L U N A S ★", stampX + 77.5f, stampY + 42f, paintText)

        paintText.textSize = 7f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TERVERIFIKASI DIGITAL", stampX + 77.5f, stampY + 56f, paintText)
        canvas.drawText(dateStr, stampX + 77.5f, stampY + 64f, paintText)

        canvas.restore()

        // 7. TANDA TANGAN / PENGESAHAN PETUGAS
        val signY = summaryY + totalBoxHeight + 35f
        val signRight = rightMargin - 20f

        paintText.textAlign = Paint.Align.RIGHT
        paintText.color = Color.parseColor("#64748B")
        paintText.textSize = 8.5f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Diterima & Disahkan oleh,", signRight, signY, paintText)

        val signLineY = signY + 42f
        paintLine.color = Color.parseColor("#CBD5E1")
        paintLine.strokeWidth = 1f
        paintLine.style = Paint.Style.STROKE
        canvas.drawLine(signRight - 140f, signLineY, signRight, signLineY, paintLine)
        paintLine.style = Paint.Style.FILL

        paintText.color = Color.parseColor("#0F172A")
        paintText.textSize = 9.5f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Petugas Kasir", signRight - 20f, signLineY + 13f, paintText)

        paintText.color = Color.parseColor("#64748B")
        paintText.textSize = 8f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Petugas Kasir / Finance", signRight - 20f, signLineY + 24f, paintText)

        // 8. FOOTER / CATATAN RESMI (LEGAL DISCLAIMER)
        val footerY = PAGE_HEIGHT - 65f
        paintLine.color = Color.parseColor("#E2E8F0")
        paintLine.strokeWidth = 1f
        paintLine.style = Paint.Style.STROKE
        canvas.drawLine(leftMargin, footerY, rightMargin, footerY, paintLine)
        paintLine.style = Paint.Style.FILL

        paintText.textAlign = Paint.Align.LEFT
        paintText.color = Color.parseColor("#64748B")
        paintText.textSize = 7.8f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Catatan:", leftMargin, footerY + 12f, paintText)
        canvas.drawText("1. Faktur ini merupakan dokumen resmi bukti pembayaran internet yang sah dan diterbitkan secara digital oleh sistem Billingku.", leftMargin, footerY + 22f, paintText)
        canvas.drawText("2. Simpan dokumen ini sebagai bukti pembayaran Anda. Layanan internet Anda aktif sesuai periode yang tercantum.", leftMargin, footerY + 32f, paintText)

        paintText.textAlign = Paint.Align.RIGHT
        paintText.color = Color.parseColor("#94A3B8")
        paintText.textSize = 7f
        canvas.drawText("ID Faktur: ${receiptData.invoiceNo} • Dicetak: $dateStr", rightMargin, footerY + 44f, paintText)
    }
}
