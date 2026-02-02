package com.blemaster.app.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.blemaster.app.ble.protocols.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages BLE advertising operations with multi-protocol support.
 * Supports: Custom, Fast Pair, Apple Continuity, Swift Pair
 * Implements exponential backoff retry and rotation mode.
 */
class BleAdvertiserManager(private val context: Context) {

    companion object {
        private const val TAG = "BleAdvertiserManager"
        private const val MANUFACTURER_ID = 0xFFFF // Experimental/testing ID
        private const val MAX_PAYLOAD_BYTES = 24
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_RETRY_DELAY_MS = 100L
        private const val DEFAULT_ROTATION_INTERVAL_MS = 1000L
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private var currentCallback: AdvertiseCallback? = null
    private var retryJob: Job? = null
    private var rotationJob: Job? = null
    private var retryCount = 0
    
    // Current broadcast configuration
    private var currentConfig: BroadcastConfig? = null
    private var rotationIndex = 0

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()
    
    private val _currentProtocol = MutableStateFlow(ProtocolType.CUSTOM)
    val currentProtocol: StateFlow<ProtocolType> = _currentProtocol.asStateFlow()
    
    private val _currentPresetName = MutableStateFlow<String?>(null)
    val currentPresetName: StateFlow<String?> = _currentPresetName.asStateFlow()

    private val _errorState = MutableStateFlow<AdvertiseError?>(null)
    val errorState: StateFlow<AdvertiseError?> = _errorState.asStateFlow()

    /**
     * Checks if BLE advertising is supported on this device.
     */
    fun isAdvertisingSupported(): Boolean {
        return bluetoothAdapter?.isMultipleAdvertisementSupported == true &&
                advertiser != null
    }

    /**
     * Checks if Bluetooth is currently enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Builds the BLE advertisement data with manufacturer-specific payload.
     * 
     * @param message The text message to broadcast (max 24 bytes UTF-8)
     * @return AdvertiseData containing the manufacturer-specific data
     */
    fun buildAdvertiseData(message: String): AdvertiseData {
        val bytes = message.toByteArray(Charsets.UTF_8)
        val finalData = bytes.copyOfRange(0, minOf(bytes.size, MAX_PAYLOAD_BYTES))

        return AdvertiseData.Builder().apply {
            addManufacturerData(MANUFACTURER_ID, finalData)
            setIncludeDeviceName(false)
            setIncludeTxPowerLevel(false)
        }.build()
    }

    /**
     * Builds the advertising settings.
     * 
     * @param powerLevel The transmission power level (LOW, MEDIUM, HIGH)
     * @param intervalMs The advertising interval in milliseconds (100-5000)
     * @return AdvertiseSettings configured with the specified parameters
     */
    fun buildAdvertiseSettings(powerLevel: TxPowerLevel, intervalMs: Int): AdvertiseSettings {
        val advertiseMode = when {
            intervalMs <= 100 -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            intervalMs <= 1000 -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
            else -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        }

        val txPower = when (powerLevel) {
            TxPowerLevel.LOW -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            TxPowerLevel.MEDIUM -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
            TxPowerLevel.HIGH -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        }

        return AdvertiseSettings.Builder()
            .setAdvertiseMode(advertiseMode)
            .setTxPowerLevel(txPower)
            .setConnectable(false)
            .setTimeout(0) // No timeout - continuous advertising
            .build()
    }
    
    // ============================================
    // PROTOCOL-SPECIFIC ADVERTISING
    // ============================================
    
    /**
     * Starts advertising with a specific broadcast configuration.
     * Supports all protocol types including rotation mode.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertisingWithConfig(
        config: BroadcastConfig,
        powerLevel: TxPowerLevel = TxPowerLevel.HIGH,
        onError: ((AdvertiseError) -> Unit)? = null
    ) {
        currentConfig = config
        
        if (config.rotationEnabled && config.rotationPresets.isNotEmpty()) {
            startRotationMode(config, powerLevel, onError)
        } else {
            startSingleProtocolAdvertising(config, powerLevel, onError)
        }
    }
    
    /**
     * Starts advertising a single protocol/preset.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startSingleProtocolAdvertising(
        config: BroadcastConfig,
        powerLevel: TxPowerLevel,
        onError: ((AdvertiseError) -> Unit)?
    ) {
        val protocolData = buildProtocolData(config, powerLevel)
        
        _currentProtocol.value = config.protocol
        _currentPresetName.value = config.selectedPreset?.name ?: when (config.protocol) {
            ProtocolType.CUSTOM -> "Custom Message"
            ProtocolType.SWIFT_PAIR -> config.swiftPairCustomName
            else -> null
        }
        
        startAdvertisingInternal(
            protocolData.advertiseData,
            protocolData.scanResponse,
            protocolData.settings,
            onError
        )
    }
    
    /**
     * Starts rotation mode - cycles through multiple presets.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startRotationMode(
        config: BroadcastConfig,
        powerLevel: TxPowerLevel,
        onError: ((AdvertiseError) -> Unit)?
    ) {
        rotationIndex = 0
        
        rotationJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val preset = config.rotationPresets[rotationIndex]
                val rotatingConfig = config.copy(
                    protocol = preset.protocol,
                    selectedPreset = preset,
                    rotationEnabled = false
                )
                
                val protocolData = buildProtocolData(rotatingConfig, powerLevel)
                
                _currentProtocol.value = preset.protocol
                _currentPresetName.value = preset.name
                
                // Stop current and start new
                stopAdvertisingInternal()
                delay(50) // Brief pause between switches
                startAdvertisingInternal(
                    protocolData.advertiseData,
                    protocolData.scanResponse,
                    protocolData.settings,
                    onError
                )
                
                delay(config.rotationIntervalMs)
                
                rotationIndex = (rotationIndex + 1) % config.rotationPresets.size
            }
        }
    }
    
    /**
     * Data class for protocol advertising configuration
     */
    private data class ProtocolAdvertisingData(
        val advertiseData: AdvertiseData,
        val scanResponse: AdvertiseData?,
        val settings: AdvertiseSettings
    )
    
    /**
     * Builds advertise data, scan response, and settings based on protocol type.
     * Scan response is used for additional data when devices request it.
     */
    private fun buildProtocolData(
        config: BroadcastConfig,
        powerLevel: TxPowerLevel
    ): ProtocolAdvertisingData {
        return when (config.protocol) {
            ProtocolType.CUSTOM -> {
                ProtocolAdvertisingData(
                    advertiseData = ProtocolAdvertisers.Custom.buildAdvertiseData(config.customMessage),
                    scanResponse = ProtocolAdvertisers.Custom.buildScanResponse(config.customMessage),
                    settings = ProtocolAdvertisers.Custom.buildSettings(txPower = getTxPowerInt(powerLevel))
                )
            }
            
            ProtocolType.FAST_PAIR -> {
                val device = config.selectedPreset as? FastPairDevice
                    ?: FastPairPresets.devices.first()
                ProtocolAdvertisingData(
                    advertiseData = ProtocolAdvertisers.FastPair.buildAdvertiseData(device),
                    scanResponse = ProtocolAdvertisers.FastPair.buildScanResponse(),
                    settings = ProtocolAdvertisers.FastPair.buildSettings(getTxPowerInt(powerLevel))
                )
            }
            
            ProtocolType.APPLE_CONTINUITY -> {
                val device = config.selectedPreset as? AppleContinuityDevice
                    ?: AppleContinuityPresets.proximityDevices.first()
                ProtocolAdvertisingData(
                    advertiseData = ProtocolAdvertisers.AppleContinuity.buildAdvertiseData(device),
                    scanResponse = null,  // Apple doesn't use scan response
                    settings = ProtocolAdvertisers.AppleContinuity.buildSettings(getTxPowerInt(powerLevel))
                )
            }
            
            ProtocolType.SWIFT_PAIR -> {
                val device = config.selectedPreset as? SwiftPairDevice
                val displayName = device?.displayName ?: config.swiftPairCustomName
                ProtocolAdvertisingData(
                    advertiseData = ProtocolAdvertisers.SwiftPair.buildAdvertiseData(displayName),
                    scanResponse = null,  // Swift Pair uses only main advertisement
                    settings = ProtocolAdvertisers.SwiftPair.buildSettings(getTxPowerInt(powerLevel))
                )
            }
        }
    }
    
    private fun getTxPowerInt(powerLevel: TxPowerLevel): Int {
        return when (powerLevel) {
            TxPowerLevel.LOW -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            TxPowerLevel.MEDIUM -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
            TxPowerLevel.HIGH -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        }
    }

    /**
     * Starts BLE advertising with the specified message and settings.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising(
        message: String,
        powerLevel: TxPowerLevel,
        intervalMs: Int,
        onError: ((AdvertiseError) -> Unit)? = null
    ) {
        // Legacy method - use custom protocol
        val config = BroadcastConfig(
            protocol = ProtocolType.CUSTOM,
            customMessage = message
        )
        startAdvertisingWithConfig(config, powerLevel, onError)
    }
    
    /**
     * Internal method to start advertising with pre-built data.
     * Uses scan response when available for additional data payload.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startAdvertisingInternal(
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData?,
        settings: AdvertiseSettings,
        onError: ((AdvertiseError) -> Unit)?
    ) {
        if (!isBluetoothEnabled()) {
            val error = AdvertiseError.BluetoothDisabled
            _errorState.value = error
            onError?.invoke(error)
            return
        }

        val bleAdvertiser = advertiser
        if (bleAdvertiser == null) {
            val error = AdvertiseError.NotSupported
            _errorState.value = error
            onError?.invoke(error)
            return
        }

        // Stop any existing advertising
        stopAdvertisingInternal()

        retryCount = 0
        _errorState.value = null

        currentCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Advertising started successfully: ${_currentProtocol.value}")
                retryCount = 0
                _isAdvertising.value = true
                _errorState.value = null
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed with error code: $errorCode")
                val error = mapErrorCode(errorCode)
                _isAdvertising.value = false
                _errorState.value = error
                onError?.invoke(error)
            }
        }

        try {
            // Use startAdvertising with scan response for better discoverability
            if (scanResponse != null) {
                bleAdvertiser.startAdvertising(settings, advertiseData, scanResponse, currentCallback)
                Log.d(TAG, "Started advertising with scan response")
            } else {
                bleAdvertiser.startAdvertising(settings, advertiseData, currentCallback)
                Log.d(TAG, "Started advertising without scan response")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing BLUETOOTH_ADVERTISE permission", e)
            val error = AdvertiseError.PermissionDenied
            _errorState.value = error
            onError?.invoke(error)
        }
    }

    /**
     * Stops the current BLE advertising.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        rotationJob?.cancel()
        rotationJob = null
        retryJob?.cancel()
        retryJob = null
        stopAdvertisingInternal()
        _currentPresetName.value = null
    }
    
    /**
     * Internal stop without affecting rotation job.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun stopAdvertisingInternal() {
        currentCallback?.let { callback ->
            try {
                advertiser?.stopAdvertising(callback)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while stopping advertising", e)
            }
        }
        currentCallback = null
        _isAdvertising.value = false
    }

    /**
     * Maps the BLE advertise error code to our error type.
     */
    private fun mapErrorCode(errorCode: Int): AdvertiseError {
        return when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> AdvertiseError.DataTooLarge
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> AdvertiseError.TooManyAdvertisers
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> AdvertiseError.AlreadyStarted
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> AdvertiseError.InternalError
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> AdvertiseError.FeatureUnsupported
            else -> AdvertiseError.Unknown(errorCode)
        }
    }

    /**
     * Calculates the byte size of a message when encoded as UTF-8.
     */
    fun getMessageByteSize(message: String): Int {
        return message.toByteArray(Charsets.UTF_8).size
    }

    /**
     * Returns the maximum allowed payload size.
     */
    fun getMaxPayloadSize(): Int = MAX_PAYLOAD_BYTES
}

/**
 * Transmission power levels for BLE advertising.
 */
enum class TxPowerLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Sealed class representing possible advertising errors.
 */
sealed class AdvertiseError {
    object NotSupported : AdvertiseError()
    object BluetoothDisabled : AdvertiseError()
    object PermissionDenied : AdvertiseError()
    object DataTooLarge : AdvertiseError()
    object TooManyAdvertisers : AdvertiseError()
    object AlreadyStarted : AdvertiseError()
    object InternalError : AdvertiseError()
    object FeatureUnsupported : AdvertiseError()
    data class Unknown(val errorCode: Int) : AdvertiseError()
}
