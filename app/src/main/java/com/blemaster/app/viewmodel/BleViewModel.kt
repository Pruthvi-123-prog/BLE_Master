package com.blemaster.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blemaster.app.ble.AdvertiseError
import com.blemaster.app.ble.AdvertisingMode
import com.blemaster.app.ble.BleAdvertiserManager
import com.blemaster.app.ble.BleScannerManager
import com.blemaster.app.ble.DiscoveredDevice
import com.blemaster.app.ble.ScanError
import com.blemaster.app.ble.TxPowerLevel
import com.blemaster.app.ble.protocols.*
import com.blemaster.app.data.SettingsRepository
import com.blemaster.app.service.BleAdvertiseService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the BLE Master application.
 * Manages broadcast state, scanning, settings, and service communication.
 */
class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val advertiserManager = BleAdvertiserManager(application)
    private val scannerManager = BleScannerManager(application)

    // Service binding
    private var bleService: BleAdvertiseService? = null
    private var isServiceBound = false

    // UI State
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _errorState = MutableStateFlow<AdvertiseError?>(null)
    val errorState: StateFlow<AdvertiseError?> = _errorState.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _showEthicalWarning = MutableStateFlow(false)
    val showEthicalWarning: StateFlow<Boolean> = _showEthicalWarning.asStateFlow()
    
    // Protocol Selection State
    private val _selectedProtocol = MutableStateFlow(ProtocolType.CUSTOM)
    val selectedProtocol: StateFlow<ProtocolType> = _selectedProtocol.asStateFlow()
    
    private val _selectedPreset = MutableStateFlow<DevicePreset?>(null)
    val selectedPreset: StateFlow<DevicePreset?> = _selectedPreset.asStateFlow()
    
    private val _swiftPairDeviceName = MutableStateFlow("BLE Master")
    val swiftPairDeviceName: StateFlow<String> = _swiftPairDeviceName.asStateFlow()
    
    private val _rotationEnabled = MutableStateFlow(false)
    val rotationEnabled: StateFlow<Boolean> = _rotationEnabled.asStateFlow()
    
    private val _rotationPresets = MutableStateFlow<List<DevicePreset>>(emptyList())
    val rotationPresets: StateFlow<List<DevicePreset>> = _rotationPresets.asStateFlow()
    
    // Current broadcast info
    val currentProtocol: StateFlow<ProtocolType> = advertiserManager.currentProtocol
    val currentPresetName: StateFlow<String?> = advertiserManager.currentPresetName

    // Settings from DataStore
    val broadcastInterval: StateFlow<Int> = settingsRepository.broadcastIntervalFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_INTERVAL_MS)

    val txPowerLevel: StateFlow<TxPowerLevel> = settingsRepository.txPowerLevelFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_POWER_LEVEL)

    val ethicalWarningShown: StateFlow<Boolean> = settingsRepository.ethicalWarningShownFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    val rotationInterval: StateFlow<Long> = settingsRepository.rotationIntervalFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_ROTATION_INTERVAL_MS)
    
    val advertisingMode: StateFlow<AdvertisingMode> = settingsRepository.advertisingModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_ADVERTISING_MODE)

    // Computed properties
    val messageByteCount: StateFlow<Int> = _message.map { msg ->
        advertiserManager.getMessageByteSize(msg)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val maxPayloadSize: Int = advertiserManager.getMaxPayloadSize()

    val isBluetoothEnabled: Boolean
        get() = advertiserManager.isBluetoothEnabled()

    val isAdvertisingSupported: Boolean
        get() = advertiserManager.isAdvertisingSupported()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BleAdvertiseService.LocalBinder
            bleService = binder?.getService()
            isServiceBound = true

            // Observe service state
            bleService?.let { svc ->
                viewModelScope.launch {
                    svc.isBroadcasting.collect { broadcasting ->
                        _isBroadcasting.value = broadcasting
                    }
                }
                viewModelScope.launch {
                    svc.errorState.collect { error ->
                        _errorState.value = error
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isServiceBound = false
        }
    }

    init {
        // Load last message
        viewModelScope.launch {
            settingsRepository.lastMessageFlow.collect { lastMessage ->
                if (_message.value.isEmpty() && lastMessage.isNotEmpty()) {
                    _message.value = lastMessage
                }
            }
        }

        // Check if ethical warning should be shown
        viewModelScope.launch {
            settingsRepository.ethicalWarningShownFlow.collect { shown ->
                _showEthicalWarning.value = !shown
            }
        }
        
        // Load protocol settings
        viewModelScope.launch {
            settingsRepository.selectedProtocolFlow.collect { protocol ->
                _selectedProtocol.value = protocol
            }
        }
        
        viewModelScope.launch {
            settingsRepository.swiftPairDeviceNameFlow.collect { name ->
                _swiftPairDeviceName.value = name
            }
        }
        
        viewModelScope.launch {
            settingsRepository.rotationEnabledFlow.collect { enabled ->
                _rotationEnabled.value = enabled
            }
        }

        // Bind to service
        bindService()
    }

    private fun bindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, BleAdvertiseService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Updates the message to broadcast.
     */
    fun updateMessage(newMessage: String) {
        _message.value = newMessage
        viewModelScope.launch {
            settingsRepository.saveLastMessage(newMessage)
        }
    }

    /**
     * Updates the broadcast interval setting.
     */
    fun updateBroadcastInterval(intervalMs: Int) {
        viewModelScope.launch {
            settingsRepository.saveBroadcastInterval(intervalMs)
        }
    }

    /**
     * Updates the transmission power level setting.
     */
    fun updateTxPowerLevel(powerLevel: TxPowerLevel) {
        viewModelScope.launch {
            settingsRepository.saveTxPowerLevel(powerLevel)
        }
    }
    
    /**
     * Updates the advertising mode (Legacy vs Extended).
     * Also updates the advertiser manager immediately.
     */
    fun updateAdvertisingMode(mode: AdvertisingMode) {
        viewModelScope.launch {
            settingsRepository.saveAdvertisingMode(mode)
            advertiserManager.setAdvertisingMode(mode)
        }
    }

    /**
     * Sets permissions granted state.
     */
    fun setPermissionsGranted(granted: Boolean) {
        _permissionsGranted.value = granted
    }

    /**
     * Marks the ethical warning as acknowledged.
     */
    fun acknowledgeEthicalWarning() {
        viewModelScope.launch {
            settingsRepository.setEthicalWarningShown()
            _showEthicalWarning.value = false
        }
    }

    /**
     * Starts broadcasting the current message.
     */
    fun startBroadcast() {
        if (_message.value.isEmpty()) return

        val context = getApplication<Application>()
        val intent = Intent(context, BleAdvertiseService::class.java).apply {
            action = BleAdvertiseService.ACTION_START
            putExtra(BleAdvertiseService.EXTRA_MESSAGE, _message.value)
            putExtra(BleAdvertiseService.EXTRA_POWER_LEVEL, txPowerLevel.value.ordinal)
            putExtra(BleAdvertiseService.EXTRA_INTERVAL_MS, broadcastInterval.value)
        }
        context.startForegroundService(intent)
    }

    /**
     * Stops the current broadcast.
     */
    fun stopBroadcast() {
        val context = getApplication<Application>()
        val intent = Intent(context, BleAdvertiseService::class.java).apply {
            action = BleAdvertiseService.ACTION_STOP
        }
        context.startService(intent)
    }
    
    // ==================== PROTOCOL SELECTION ====================
    
    /**
     * Sets the selected protocol type.
     */
    fun selectProtocol(protocol: ProtocolType) {
        _selectedProtocol.value = protocol
        _selectedPreset.value = null  // Clear preset when switching protocols
        viewModelScope.launch {
            settingsRepository.saveSelectedProtocol(protocol)
        }
    }
    
    /**
     * Sets the selected device preset.
     */
    fun selectPreset(preset: DevicePreset?) {
        _selectedPreset.value = preset
        _selectedProtocol.value = preset?.protocol ?: ProtocolType.CUSTOM
    }
    
    /**
     * Updates the Swift Pair custom device name.
     */
    fun updateSwiftPairDeviceName(name: String) {
        _swiftPairDeviceName.value = name.take(20)
        viewModelScope.launch {
            settingsRepository.saveSwiftPairDeviceName(name)
        }
    }
    
    /**
     * Toggles rotation mode.
     */
    fun setRotationEnabled(enabled: Boolean) {
        _rotationEnabled.value = enabled
        viewModelScope.launch {
            settingsRepository.saveRotationEnabled(enabled)
        }
    }
    
    /**
     * Updates rotation interval.
     */
    fun updateRotationInterval(intervalMs: Long) {
        viewModelScope.launch {
            settingsRepository.saveRotationInterval(intervalMs)
        }
    }
    
    /**
     * Adds a preset to rotation list.
     */
    fun addPresetToRotation(preset: DevicePreset) {
        if (preset !in _rotationPresets.value) {
            _rotationPresets.value = _rotationPresets.value + preset
        }
    }
    
    /**
     * Removes a preset from rotation list.
     */
    fun removePresetFromRotation(preset: DevicePreset) {
        _rotationPresets.value = _rotationPresets.value - preset
    }
    
    /**
     * Clears all rotation presets.
     */
    fun clearRotationPresets() {
        _rotationPresets.value = emptyList()
    }
    
    /**
     * Builds the current broadcast configuration.
     */
    fun buildBroadcastConfig(): BroadcastConfig {
        return BroadcastConfig(
            protocol = _selectedProtocol.value,
            customMessage = _message.value,
            selectedPreset = _selectedPreset.value,
            swiftPairCustomName = _swiftPairDeviceName.value,
            rotationEnabled = _rotationEnabled.value,
            rotationIntervalMs = rotationInterval.value,
            rotationPresets = _rotationPresets.value
        )
    }
    
    /**
     * Starts broadcasting with the current protocol configuration.
     */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startProtocolBroadcast() {
        val config = buildBroadcastConfig()
        
        // For custom protocol, validate message
        if (config.protocol == ProtocolType.CUSTOM && config.customMessage.isEmpty()) {
            return
        }
        
        advertiserManager.startAdvertisingWithConfig(
            config = config,
            powerLevel = txPowerLevel.value,
            onError = { error ->
                _errorState.value = error
            }
        )
        _isBroadcasting.value = true
    }
    
    /**
     * Stops protocol broadcasting.
     */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopProtocolBroadcast() {
        advertiserManager.stopAdvertising()
        _isBroadcasting.value = false
    }
    
    // ==================== PRESET HELPERS ====================
    
    /**
     * Gets all available Fast Pair presets.
     */
    fun getFastPairPresets(): List<FastPairDevice> = FastPairPresets.devices
    
    /**
     * Gets all available Apple Continuity presets.
     */
    fun getAppleContinuityPresets(): List<AppleContinuityDevice> = AppleContinuityPresets.allDevices
    
    /**
     * Gets Apple proximity pair presets only.
     */
    fun getAppleProximityPresets(): List<AppleContinuityDevice> = AppleContinuityPresets.proximityDevices
    
    /**
     * Gets Apple action presets only.
     */
    fun getAppleActionPresets(): List<AppleContinuityDevice> = AppleContinuityPresets.actionDevices
    
    /**
     * Gets all available Swift Pair presets.
     */
    fun getSwiftPairPresets(): List<SwiftPairDevice> = SwiftPairPresets.devices

    /**
     * Clears any error state.
     */
    fun clearError() {
        _errorState.value = null
    }

    // ==================== SCANNER FUNCTIONALITY ====================

    val isScanning: StateFlow<Boolean> = scannerManager.isScanning
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = scannerManager.discoveredDevices
    val scanError: StateFlow<ScanError?> = scannerManager.scanError

    /**
     * Starts scanning for nearby BLE Master broadcasts.
     */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        scannerManager.startScanning()
    }

    /**
     * Stops the BLE scan.
     */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        scannerManager.stopScanning()
    }

    /**
     * Clears all discovered devices.
     */
    fun clearDiscoveredDevices() {
        scannerManager.clearDevices()
    }

    override fun onCleared() {
        super.onCleared()
        // Stop scanning if active
        try {
            scannerManager.stopScanning()
        } catch (e: SecurityException) {
            // Ignore permission issues during cleanup
        }
        if (isServiceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
