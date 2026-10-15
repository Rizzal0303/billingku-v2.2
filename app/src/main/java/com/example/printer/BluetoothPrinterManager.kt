package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

sealed class PrinterStatus {
    object Idle : PrinterStatus()
    object Connecting : PrinterStatus()
    data class Connected(val deviceName: String, val address: String) : PrinterStatus()
    object Printing : PrinterStatus()
    data class Success(val message: String) : PrinterStatus()
    data class Error(val errorMessage: String) : PrinterStatus()
}

data class PairedPrinter(
    val name: String,
    val address: String
)

class BluetoothPrinterManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("billingku_printer_prefs", Context.MODE_PRIVATE)

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _printerStatus = MutableStateFlow<PrinterStatus>(PrinterStatus.Idle)
    val printerStatus: StateFlow<PrinterStatus> = _printerStatus

    private val printMutex = kotlinx.coroutines.sync.Mutex()
    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    // Settings
    fun getBusinessName(): String = prefs.getString("business_name", "BILLINGKU WIFI NETWORK") ?: "BILLINGKU WIFI NETWORK"
    fun setBusinessName(name: String) = prefs.edit().putString("business_name", name).apply()

    fun getBusinessAddress(): String = prefs.getString("business_address", "Jl. Merdeka RT 02/RW 05 No. 45") ?: "Jl. Merdeka RT 02/RW 05 No. 45"
    fun setBusinessAddress(address: String) = prefs.edit().putString("business_address", address).apply()

    fun getBusinessPhone(): String = prefs.getString("business_phone", "0812-3456-7890") ?: "0812-3456-7890"
    fun setBusinessPhone(phone: String) = prefs.edit().putString("business_phone", phone).apply()

    fun getFooterMessage(): String = prefs.getString("footer_message", "Simpan struk ini sebagai bukti pembayaran sah. Terima kasih!") ?: "Simpan struk ini sebagai bukti pembayaran sah. Terima kasih!"
    fun setFooterMessage(msg: String) = prefs.edit().putString("footer_message", msg).apply()

    fun getReceiptTemplate(): String = prefs.getString("receipt_template", "MODERN") ?: "MODERN"
    fun setReceiptTemplate(template: String) = prefs.edit().putString("receipt_template", template).apply()

    fun isAutoCutEnabled(): Boolean = false
    fun setAutoCutEnabled(enabled: Boolean) {}

    // Default 1 tingkat = gap 2mm (paling pas untuk sobekan struk & hemat termal)
    fun getFeedLines(): Int = prefs.getInt("printer_feed_lines", 1)
    fun setFeedLines(lines: Int) = prefs.edit().putInt("printer_feed_lines", lines.coerceIn(0, 5)).apply()

    fun getSelectedPrinterAddress(): String? = prefs.getString("selected_printer_mac", null)
    fun getSelectedPrinterName(): String? = prefs.getString("selected_printer_name", null)

    fun setSelectedPrinter(name: String, address: String) {
        prefs.edit()
            .putString("selected_printer_name", name)
            .putString("selected_printer_mac", address)
            .apply()
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<PairedPrinter> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return try {
            adapter.bondedDevices?.map { device ->
                PairedPrinter(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.e("BluetoothPrinter", "Missing Bluetooth permission", e)
            emptyList()
        }
    }

    suspend fun printReceipt(data: EscPosHelper.ReceiptData): Result<String> = withContext(Dispatchers.IO) {
        if (!printMutex.tryLock()) {
            Log.w("BluetoothPrinter", "Double-click prevented: print already in progress")
            return@withContext Result.failure(Exception("Sedang memproses cetak struk, mohon tunggu sebentar..."))
        }
        _isPrinting.value = true
        _printerStatus.value = PrinterStatus.Printing
        try {
            val macAddress = getSelectedPrinterAddress()
            val feedLines = getFeedLines()

            // If no printer selected or running on emulator, provide graceful simulation & preview
            if (macAddress == null || bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
                // Emulated/Preview print success with short simulated duration
                kotlinx.coroutines.delay(600)
                val previewText = EscPosHelper.generatePlainTextReceipt(data, feedLines = feedLines)
                Log.d("BluetoothPrinter", "Printed (Preview Mode):\n$previewText")
                _printerStatus.value = PrinterStatus.Success("Struk berhasil dicetak (Mode Simulasi/Preview)")
                return@withContext Result.success("Struk berhasil diproses (Simulasi).")
            }

            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null

            try {
                _printerStatus.value = PrinterStatus.Connecting
                val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(macAddress)

                @SuppressLint("MissingPermission")
                val rawSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = rawSocket

                bluetoothAdapter?.cancelDiscovery()
                socket.connect()

                outputStream = socket.outputStream
                val bytes = EscPosHelper.buildEscPosBytes(data, feedLines = feedLines)

                _printerStatus.value = PrinterStatus.Printing
                outputStream.write(bytes)
                outputStream.flush()
                kotlinx.coroutines.delay(400)

                _printerStatus.value = PrinterStatus.Success("Berhasil mencetak struk ke printer ${device.name ?: macAddress}")
                Result.success("Struk tercetak di ${device.name}")
            } catch (e: Exception) {
                Log.e("BluetoothPrinter", "Printing error: ${e.message}", e)
                _printerStatus.value = PrinterStatus.Error("Gagal mencetak: ${e.localizedMessage ?: "Koneksi printer terputus"}")
                Result.failure(e)
            } finally {
                try {
                    outputStream?.close()
                    socket?.close()
                } catch (ignored: IOException) {}
            }
        } finally {
            _isPrinting.value = false
            printMutex.unlock()
        }
    }

    suspend fun printSettlementReceipt(data: EscPosHelper.SettlementReceiptData): Result<String> = withContext(Dispatchers.IO) {
        if (!printMutex.tryLock()) {
            Log.w("BluetoothPrinter", "Double-click prevented: print settlement already in progress")
            return@withContext Result.failure(Exception("Sedang memproses cetak setoran, mohon tunggu sebentar..."))
        }
        _isPrinting.value = true
        _printerStatus.value = PrinterStatus.Printing
        try {
            val macAddress = getSelectedPrinterAddress()
            val feedLines = getFeedLines()

            if (macAddress == null || bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
                kotlinx.coroutines.delay(600)
                val previewText = EscPosHelper.generatePlainTextSettlementReceipt(data)
                Log.d("BluetoothPrinter", "Printed Settlement (Preview Mode):\n$previewText")
                _printerStatus.value = PrinterStatus.Success("Bukti setoran tercetak (Mode Simulasi/Preview)")
                return@withContext Result.success("Bukti setoran tercetak (Simulasi)")
            }

            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null

            try {
                _printerStatus.value = PrinterStatus.Connecting
                val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(macAddress)

                @SuppressLint("MissingPermission")
                val rawSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = rawSocket

                bluetoothAdapter?.cancelDiscovery()
                socket.connect()

                outputStream = socket.outputStream
                val bytes = EscPosHelper.buildEscPosSettlementBytes(data, feedLines = feedLines)

                _printerStatus.value = PrinterStatus.Printing
                outputStream.write(bytes)
                outputStream.flush()
                kotlinx.coroutines.delay(400)

                _printerStatus.value = PrinterStatus.Success("Berhasil mencetak bukti setoran ke printer ${device.name ?: macAddress}")
                Result.success("Bukti setoran tercetak di ${device.name}")
            } catch (e: Exception) {
                Log.e("BluetoothPrinter", "Printing error: ${e.message}", e)
                _printerStatus.value = PrinterStatus.Error("Gagal mencetak: ${e.localizedMessage ?: "Koneksi printer terputus"}")
                Result.failure(e)
            } finally {
                try {
                    outputStream?.close()
                    socket?.close()
                } catch (ignored: IOException) {}
            }
        } finally {
            _isPrinting.value = false
            printMutex.unlock()
        }
    }

    suspend fun printRawBytes(bytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        if (!printMutex.tryLock()) {
            return@withContext Result.failure(Exception("Printer sedang sibuk, mohon tunggu..."))
        }
        _isPrinting.value = true
        _printerStatus.value = PrinterStatus.Printing
        try {
            val macAddress = getSelectedPrinterAddress()
            if (macAddress == null || bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
                kotlinx.coroutines.delay(400)
                _printerStatus.value = PrinterStatus.Success("Rekap tercetak (Mode Simulasi)")
                return@withContext Result.success("Rekap tercetak (Mode Simulasi)")
            }

            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null
            try {
                _printerStatus.value = PrinterStatus.Connecting
                val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(macAddress)
                @SuppressLint("MissingPermission")
                val rawSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = rawSocket
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                outputStream = socket.outputStream
                _printerStatus.value = PrinterStatus.Printing
                outputStream.write(bytes)
                outputStream.flush()
                kotlinx.coroutines.delay(300)
                _printerStatus.value = PrinterStatus.Success("Rekap berhasil dicetak ke ${device.name ?: macAddress}")
                Result.success("Rekap tercetak di ${device.name}")
            } catch (e: Exception) {
                Log.e("BluetoothPrinter", "Print raw error: ${e.message}", e)
                _printerStatus.value = PrinterStatus.Error("Gagal cetak rekap: ${e.localizedMessage ?: "Koneksi printer terputus"}")
                Result.failure(e)
            } finally {
                try {
                    outputStream?.close()
                    socket?.close()
                } catch (ignored: IOException) {}
            }
        } finally {
            _isPrinting.value = false
            printMutex.unlock()
        }
    }

    fun resetStatus() {
        _printerStatus.value = PrinterStatus.Idle
    }
}
