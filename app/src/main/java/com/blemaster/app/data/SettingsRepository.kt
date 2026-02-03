package com.blemaster.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.blemaster.app.ble.AdvertisingMode
import com.blemaster.app.ble.TxPowerLevel
import com.blemaster.app.ble.protocols.ProtocolType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ble_master_settings")

/**
 * Repository for persisting app settings using DataStore.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val BROADCAST_INTERVAL_KEY = intPreferencesKey("broadcast_interval")
        private val TX_POWER_LEVEL_KEY = intPreferencesKey("tx_power_level")
        private val ETHICAL_WARNING_SHOWN_KEY = booleanPreferencesKey("ethical_warning_shown")
        private val LAST_MESSAGE_KEY = stringPreferencesKey("last_message")
        private val SELECTED_PROTOCOL_KEY = intPreferencesKey("selected_protocol")
        private val SWIFT_PAIR_DEVICE_NAME_KEY = stringPreferencesKey("swift_pair_device_name")
        private val ROTATION_ENABLED_KEY = booleanPreferencesKey("rotation_enabled")
        private val ROTATION_INTERVAL_KEY = longPreferencesKey("rotation_interval")
        private val ADVERTISING_MODE_KEY = intPreferencesKey("advertising_mode")

        const val DEFAULT_INTERVAL_MS = 1000
        val DEFAULT_POWER_LEVEL = TxPowerLevel.MEDIUM
        val DEFAULT_PROTOCOL = ProtocolType.CUSTOM
        const val DEFAULT_SWIFT_PAIR_NAME = "BLE Master"
        const val DEFAULT_ROTATION_INTERVAL_MS = 1000L
        val DEFAULT_ADVERTISING_MODE = AdvertisingMode.LEGACY  // Default to legacy for max compatibility
    }

    /**
     * Flow of the broadcast interval setting.
     */
    val broadcastIntervalFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[BROADCAST_INTERVAL_KEY] ?: DEFAULT_INTERVAL_MS
    }

    /**
     * Flow of the transmission power level setting.
     */
    val txPowerLevelFlow: Flow<TxPowerLevel> = context.dataStore.data.map { preferences ->
        val ordinal = preferences[TX_POWER_LEVEL_KEY] ?: DEFAULT_POWER_LEVEL.ordinal
        TxPowerLevel.entries.getOrElse(ordinal) { DEFAULT_POWER_LEVEL }
    }

    /**
     * Flow indicating whether the ethical warning has been shown.
     */
    val ethicalWarningShownFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ETHICAL_WARNING_SHOWN_KEY] ?: false
    }

    /**
     * Flow of the last broadcast message.
     */
    val lastMessageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_MESSAGE_KEY] ?: ""
    }
    
    /**
     * Flow of the selected protocol type.
     */
    val selectedProtocolFlow: Flow<ProtocolType> = context.dataStore.data.map { preferences ->
        val ordinal = preferences[SELECTED_PROTOCOL_KEY] ?: DEFAULT_PROTOCOL.ordinal
        ProtocolType.entries.getOrElse(ordinal) { DEFAULT_PROTOCOL }
    }
    
    /**
     * Flow of the Swift Pair custom device name.
     */
    val swiftPairDeviceNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SWIFT_PAIR_DEVICE_NAME_KEY] ?: DEFAULT_SWIFT_PAIR_NAME
    }
    
    /**
     * Flow indicating whether rotation mode is enabled.
     */
    val rotationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ROTATION_ENABLED_KEY] ?: false
    }
    
    /**
     * Flow of the rotation interval in milliseconds.
     */
    val rotationIntervalFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[ROTATION_INTERVAL_KEY] ?: DEFAULT_ROTATION_INTERVAL_MS
    }
    
    /**
     * Flow of the advertising mode (Legacy vs Extended).
     * Legacy mode is default for maximum device compatibility.
     */
    val advertisingModeFlow: Flow<AdvertisingMode> = context.dataStore.data.map { preferences ->
        val ordinal = preferences[ADVERTISING_MODE_KEY] ?: DEFAULT_ADVERTISING_MODE.ordinal
        AdvertisingMode.entries.getOrElse(ordinal) { DEFAULT_ADVERTISING_MODE }
    }

    /**
     * Saves the broadcast interval setting.
     */
    suspend fun saveBroadcastInterval(intervalMs: Int) {
        context.dataStore.edit { preferences ->
            preferences[BROADCAST_INTERVAL_KEY] = intervalMs.coerceIn(100, 5000)
        }
    }

    /**
     * Saves the transmission power level setting.
     */
    suspend fun saveTxPowerLevel(powerLevel: TxPowerLevel) {
        context.dataStore.edit { preferences ->
            preferences[TX_POWER_LEVEL_KEY] = powerLevel.ordinal
        }
    }

    /**
     * Marks the ethical warning as shown.
     */
    suspend fun setEthicalWarningShown() {
        context.dataStore.edit { preferences ->
            preferences[ETHICAL_WARNING_SHOWN_KEY] = true
        }
    }

    /**
     * Saves the last broadcast message.
     */
    suspend fun saveLastMessage(message: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_MESSAGE_KEY] = message
        }
    }
    
    /**
     * Saves the selected protocol type.
     */
    suspend fun saveSelectedProtocol(protocol: ProtocolType) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_PROTOCOL_KEY] = protocol.ordinal
        }
    }
    
    /**
     * Saves the Swift Pair custom device name.
     */
    suspend fun saveSwiftPairDeviceName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[SWIFT_PAIR_DEVICE_NAME_KEY] = name.take(20)
        }
    }
    
    /**
     * Saves the rotation mode setting.
     */
    suspend fun saveRotationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ROTATION_ENABLED_KEY] = enabled
        }
    }
    
    /**
     * Saves the rotation interval.
     */
    suspend fun saveRotationInterval(intervalMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[ROTATION_INTERVAL_KEY] = intervalMs.coerceIn(500, 5000)
        }
    }
    
    /**
     * Saves the advertising mode setting.
     */
    suspend fun saveAdvertisingMode(mode: AdvertisingMode) {
        context.dataStore.edit { preferences ->
            preferences[ADVERTISING_MODE_KEY] = mode.ordinal
        }
    }
}
