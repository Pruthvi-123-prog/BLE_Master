package com.blemaster.app.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages BLE scanning to detect nearby broadcasting devices.
 * Detects all protocol types: Custom, Fast Pair, Apple Continuity, Swift Pair.
 * Optimized for fast detection with aggressive scan settings.
 */
class BleScannerManager(private val context: Context) {

    companion object {
        private const val TAG = "BleScannerManager"
        private const val MANUFACTURER_ID_CUSTOM = 0xFFFF
        private const val MANUFACTURER_ID_APPLE = 0x004C
        private const val MANUFACTURER_ID_MICROSOFT = 0x0006
        
        // Custom service UUID for additional discoverability
        val CUSTOM_SERVICE_UUID: ParcelUuid = ParcelUuid(
            UUID.fromString("0000FFFF-0000-1000-8000-00805F9B34FB")
        )
        // Fast Pair service UUID
        val FAST_PAIR_UUID: ParcelUuid = ParcelUuid(
            UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")
        )
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private var scanCallback: ScanCallback? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _scanError = MutableStateFlow<ScanError?>(null)
    val scanError: StateFlow<ScanError?> = _scanError.asStateFlow()

    private val deviceMap = mutableMapOf<String, DiscoveredDevice>()

    fun isScanningSupported(): Boolean = bluetoothAdapter != null && scanner != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning(onDeviceFound: ((DiscoveredDevice) -> Unit)? = null) {
        if (!isBluetoothEnabled()) {
            _scanError.value = ScanError.BluetoothDisabled
            return
        }

        val bleScanner = scanner ?: run {
            _scanError.value = ScanError.NotSupported
            return
        }

        stopScanning()
        deviceMap.clear()
        _discoveredDevices.value = emptyList()
        _scanError.value = null

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { processResult(it, onDeviceFound) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { processResult(it, onDeviceFound) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                _isScanning.value = false
                _scanError.value = mapScanError(errorCode)
            }
        }

        try {
            bleScanner.startScan(listOf(), settings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "BLE scanning started")
        } catch (e: SecurityException) {
            _scanError.value = ScanError.PermissionDenied
        }
    }

    private fun processResult(result: ScanResult, onDeviceFound: ((DiscoveredDevice) -> Unit)?) {
        val scanRecord = result.scanRecord ?: return
        val deviceAddress = result.device?.address ?: return
        val deviceName = result.device?.name

        var detectedProtocol: String? = null
        var message: String? = null
        var rawData: ByteArray? = null

        // Check Custom (0xFFFF) - also check service UUID
        scanRecord.getManufacturerSpecificData(MANUFACTURER_ID_CUSTOM)?.let { data ->
            if (data.isNotEmpty()) {
                detectedProtocol = "Custom"
                message = try { String(data, Charsets.UTF_8) } catch (e: Exception) { "[Binary]" }
                rawData = data
            }
        }
        
        // Also check for custom service UUID (0xFFFF) with service data
        if (detectedProtocol == null) {
            scanRecord.getServiceData(CUSTOM_SERVICE_UUID)?.let { data ->
                if (data.isNotEmpty()) {
                    detectedProtocol = "Custom"
                    message = try { String(data, Charsets.UTF_8) } catch (e: Exception) { "[Binary]" }
                    rawData = data
                }
            }
        }

        // Check Apple (0x004C)
        if (detectedProtocol == null) {
            scanRecord.getManufacturerSpecificData(MANUFACTURER_ID_APPLE)?.let { data ->
                if (data.size >= 2) {
                    val type = data.getOrNull(1)?.toInt()?.and(0xFF)
                    detectedProtocol = when (type) {
                        0x07 -> "Apple Proximity"
                        0x0F -> "Apple Action"
                        else -> "Apple"
                    }
                    message = parseAppleData(data, type ?: 0)
                    rawData = data
                }
            }
        }

        // Check Swift Pair (0x0006)
        if (detectedProtocol == null) {
            scanRecord.getManufacturerSpecificData(MANUFACTURER_ID_MICROSOFT)?.let { data ->
                if (data.size >= 3 && data[0] == 0x03.toByte()) {
                    detectedProtocol = "Swift Pair"
                    message = try {
                        if (data.size > 3) String(data.sliceArray(3 until data.size), Charsets.UTF_8)
                        else deviceName ?: "Swift Pair"
                    } catch (e: Exception) { deviceName ?: "Swift Pair" }
                    rawData = data
                }
            }
        }

        // Check Fast Pair (FE2C UUID)
        if (detectedProtocol == null) {
            scanRecord.serviceUuids?.forEach { uuid ->
                if (uuid.toString().lowercase().contains("fe2c")) {
                    detectedProtocol = "Fast Pair"
                    val serviceData = scanRecord.getServiceData(uuid)
                    message = serviceData?.joinToString("") { "%02X".format(it) } ?: "Fast Pair"
                    rawData = serviceData
                }
            }
        }

        val finalMessage = message
        val finalProtocol = detectedProtocol
        if (finalProtocol != null && finalMessage != null) {
            val rssi = result.rssi
            val txPower = result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
            val distance = calculateDistance(rssi, txPower ?: -59)

            val device = DiscoveredDevice(
                address = deviceAddress,
                name = deviceName,
                message = finalMessage,
                rssi = rssi,
                txPower = txPower,
                estimatedDistance = distance,
                lastSeen = System.currentTimeMillis(),
                rawData = rawData ?: byteArrayOf(),
                protocol = finalProtocol
            )

            deviceMap[deviceAddress] = device
            _discoveredDevices.value = deviceMap.values.toList().sortedByDescending { it.rssi }
            onDeviceFound?.invoke(device)
        }
    }

    private fun parseAppleData(data: ByteArray, type: Int): String {
        return when (type) {
            0x07 -> if (data.size >= 5) "AirPods (%02X%02X)".format(data[3], data[4]) else "AirPods"
            0x0F -> {
                val action = data.getOrNull(3)?.toInt()?.and(0xFF) ?: 0
                when (action) {
                    0x09 -> "Setup iPhone"
                    0x0B -> "Apple Watch"
                    0x06 -> "Join AppleTV"
                    0x0D -> "HomePod"
                    else -> "Action $action"
                }
            }
            else -> "Apple Device"
        }
    }

    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) Math.pow(ratio, 10.0)
        else 0.89976 * Math.pow(ratio, 7.7095) + 0.111
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        scanCallback?.let { try { scanner?.stopScan(it) } catch (e: SecurityException) { } }
        scanCallback = null
        _isScanning.value = false
    }

    fun clearDevices() {
        deviceMap.clear()
        _discoveredDevices.value = emptyList()
    }

    private fun mapScanError(errorCode: Int): ScanError = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> ScanError.AlreadyStarted
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> ScanError.RegistrationFailed
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> ScanError.FeatureUnsupported
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> ScanError.InternalError
        else -> ScanError.Unknown(errorCode)
    }
}

data class DiscoveredDevice(
    val address: String,
    val name: String?,
    val message: String,
    val rssi: Int,
    val txPower: Int?,
    val estimatedDistance: Double,
    val lastSeen: Long,
    val rawData: ByteArray,
    val protocol: String? = null
) {
    fun getDistanceString(): String = when {
        estimatedDistance < 0 -> "Unknown"
        estimatedDistance < 1 -> "< 1m"
        estimatedDistance < 5 -> "~${estimatedDistance.toInt()}m"
        else -> "> 5m"
    }

    fun getSignalStrength(): String = when {
        rssi >= -50 -> "Excellent"
        rssi >= -60 -> "Good"
        rssi >= -70 -> "Fair"
        else -> "Weak"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscoveredDevice) return false
        return address == other.address
    }

    override fun hashCode(): Int = address.hashCode()
}

sealed class ScanError {
    object NotSupported : ScanError()
    object BluetoothDisabled : ScanError()
    object PermissionDenied : ScanError()
    object AlreadyStarted : ScanError()
    object RegistrationFailed : ScanError()
    object FeatureUnsupported : ScanError()
    object InternalError : ScanError()
    data class Unknown(val errorCode: Int) : ScanError()
}
