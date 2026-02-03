package com.blemaster.app.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
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
        private const val MAX_PAYLOAD_BYTES_LEGACY = 20  // Legacy mode has stricter limit
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
    
    // Legacy mode support for older BLE devices
    private var legacyModeEnabled: Boolean = true
    private var currentAdvertisingSet: AdvertisingSet? = null
    private var advertisingSetCallback: AdvertisingSetCallback? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()
    
    private val _currentProtocol = MutableStateFlow(ProtocolType.CUSTOM)
    val currentProtocol: StateFlow<ProtocolType> = _currentProtocol.asStateFlow()
    
    private val _currentPresetName = MutableStateFlow<String?>(null)
    val currentPresetName: StateFlow<String?> = _currentPresetName.asStateFlow()

    private val _errorState = MutableStateFlow<AdvertiseError?>(null)
    val errorState: StateFlow<AdvertiseError?> = _errorState.asStateFlow()
    
    private val _advertisingMode = MutableStateFlow(AdvertisingMode.LEGACY)
    val advertisingMode: StateFlow<AdvertisingMode> = _advertisingMode.asStateFlow()

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
     * Sets the advertising mode (Legacy for older devices, Extended for BT 5.0+).
     */
    fun setAdvertisingMode(mode: AdvertisingMode) {
        _advertisingMode.value = mode
        legacyModeEnabled = (mode == AdvertisingMode.LEGACY)
        Log.d(TAG, "Advertising mode set to: $mode (legacyEnabled=$legacyModeEnabled)")
    }
    
    /**
     * Returns the maximum payload size based on current mode.
     * Legacy: 20 bytes, Extended: 24 bytes
     */
    fun getMaxPayloadForCurrentMode(): Int {
        return if (legacyModeEnabled) MAX_PAYLOAD_BYTES_LEGACY else MAX_PAYLOAD_BYTES
    }
    
    /**
     * Internal method to start advertising with pre-built data.
     * Uses AdvertisingSet API with legacy mode for Android 8+ for better compatibility.
     * Falls back to standard advertising on older Android versions.
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

        // Use AdvertisingSet API for Android 8+ with legacy mode for better device compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && legacyModeEnabled) {
            startAdvertisingWithLegacyMode(bleAdvertiser, advertiseData, scanResponse, settings, onError)
        } else {
            startAdvertisingStandard(bleAdvertiser, advertiseData, scanResponse, settings, onError)
        }
    }
    
    /**
     * Starts advertising using AdvertisingSet API with legacy PDUs.
     * This ensures compatibility with BT 5.0 and older devices.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startAdvertisingWithLegacyMode(
        bleAdvertiser: BluetoothLeAdvertiser,
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData?,
        settings: AdvertiseSettings,
        onError: ((AdvertiseError) -> Unit)?
    ) {
        val txPowerLevel = when (settings.txPowerLevel) {
            AdvertiseSettings.ADVERTISE_TX_POWER_HIGH -> AdvertisingSetParameters.TX_POWER_HIGH
            AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM -> AdvertisingSetParameters.TX_POWER_MEDIUM
            AdvertiseSettings.ADVERTISE_TX_POWER_LOW -> AdvertisingSetParameters.TX_POWER_LOW
            else -> AdvertisingSetParameters.TX_POWER_MEDIUM
        }
        
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)  // Force legacy PDUs for BT 5.0 compatibility
            .setConnectable(true)  // Better discoverability
            .setScannable(true)    // Allow scan requests for scan response
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)  // Fast advertising
            .setTxPowerLevel(txPowerLevel)
            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)  // LE 1M PHY for max compatibility
            .build()
        
        advertisingSetCallback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    Log.d(TAG, "Legacy advertising started successfully (PHY_LE_1M)")
                    currentAdvertisingSet = advertisingSet
                    _isAdvertising.value = true
                    _errorState.value = null
                } else {
                    Log.e(TAG, "Legacy advertising failed with status: $status")
                    val error = mapAdvertisingSetStatus(status)
                    _isAdvertising.value = false
                    _errorState.value = error
                    onError?.invoke(error)
                }
            }
            
            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                Log.d(TAG, "Legacy advertising stopped")
                currentAdvertisingSet = null
                _isAdvertising.value = false
            }
        }
        
        try {
            bleAdvertiser.startAdvertisingSet(
                parameters,
                advertiseData,
                scanResponse,
                null,  // No periodic advertising
                null,  // No duration
                advertisingSetCallback
            )
            Log.d(TAG, "Started legacy mode advertising with AdvertisingSet API")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing BLUETOOTH_ADVERTISE permission", e)
            val error = AdvertiseError.PermissionDenied
            _errorState.value = error
            onError?.invoke(error)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start legacy advertising, falling back to standard", e)
            startAdvertisingStandard(bleAdvertiser, advertiseData, scanResponse, settings, onError)
        }
    }
    
    /**
     * Standard advertising using the classic API.
     * Used for pre-Android 8 or when extended mode is preferred.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startAdvertisingStandard(
        bleAdvertiser: BluetoothLeAdvertiser,
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData?,
        settings: AdvertiseSettings,
        onError: ((AdvertiseError) -> Unit)?
    ) {
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
            if (scanResponse != null) {
                bleAdvertiser.startAdvertising(settings, advertiseData, scanResponse, currentCallback)
                Log.d(TAG, "Started standard advertising with scan response")
            } else {
                bleAdvertiser.startAdvertising(settings, advertiseData, currentCallback)
                Log.d(TAG, "Started standard advertising without scan response")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing BLUETOOTH_ADVERTISE permission", e)
            val error = AdvertiseError.PermissionDenied
            _errorState.value = error
            onError?.invoke(error)
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun mapAdvertisingSetStatus(status: Int): AdvertiseError {
        return when (status) {
            AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> AdvertiseError.DataTooLarge
            AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> AdvertiseError.TooManyAdvertisers
            AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED -> AdvertiseError.AlreadyStarted
            AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> AdvertiseError.InternalError
            AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> AdvertiseError.FeatureUnsupported
            else -> AdvertiseError.Unknown(status)
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
     * Handles both standard and AdvertisingSet advertising.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun stopAdvertisingInternal() {
        // Stop AdvertisingSet if using legacy mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            currentAdvertisingSet?.let { advSet ->
                try {
                    advertisingSetCallback?.let { callback ->
                        advertiser?.stopAdvertisingSet(callback)
                    }
                    Log.d(TAG, "Stopped AdvertisingSet")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException while stopping AdvertisingSet", e)
                }
            }
            currentAdvertisingSet = null
            advertisingSetCallback = null
        }
        
        // Stop standard advertising
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

/**
 * Advertising mode selection for device compatibility.
 * 
 * LEGACY: Uses LE 1M PHY and legacy PDUs for maximum compatibility with older BT 5.0 devices.
 *         Limited to 20-byte payloads. Recommended for cross-device compatibility.
 * 
 * EXTENDED: Uses modern BT 5.x features for longer payloads (up to 24 bytes).
 *           May not be detected by older devices with budget BLE chipsets.
 */
enum class AdvertisingMode {
    LEGACY,    // Max compatibility with BT 5.0 devices (20 byte limit)
    EXTENDED   // Modern mode with extended features (24 byte limit)
}
