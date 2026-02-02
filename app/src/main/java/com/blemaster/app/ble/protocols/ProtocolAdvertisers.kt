package com.blemaster.app.ble.protocols

import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetParameters
import android.os.Build
import android.os.ParcelUuid
import java.util.UUID

/**
 * Protocol-specific advertise data builders
 */
object ProtocolAdvertisers {
    
    // ============================================
    // FAST PAIR ADVERTISER
    // ============================================
    object FastPair {
        private val SERVICE_UUID = ParcelUuid(UUID.fromString(FastPairPresets.SERVICE_UUID))
        
        /**
         * Builds Fast Pair advertise data
         * Uses Service Data with the Fast Pair UUID and model ID
         */
        fun buildAdvertiseData(device: FastPairDevice): AdvertiseData {
            return AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(SERVICE_UUID)
                .addServiceData(SERVICE_UUID, device.modelId)
                .build()
        }
        
        /**
         * Builds scan response for additional data
         */
        fun buildScanResponse(): AdvertiseData {
            return AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .build()
        }
        
        fun buildSettings(txPower: Int = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH): AdvertiseSettings {
            return AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(txPower)
                .setConnectable(false)
                .setTimeout(0)
                .build()
        }
    }
    
    // ============================================
    // APPLE CONTINUITY ADVERTISER
    // ============================================
    object AppleContinuity {
        /**
         * Builds Apple Continuity advertise data
         * Uses Manufacturer Specific Data with Apple's ID (0x004C)
         */
        fun buildAdvertiseData(device: AppleContinuityDevice): AdvertiseData {
            val manufacturerData = buildContinuityPacket(device)
            
            return AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(AppleContinuityPresets.MANUFACTURER_ID, manufacturerData)
                .build()
        }
        
        /**
         * Builds the continuity packet based on type
         */
        private fun buildContinuityPacket(device: AppleContinuityDevice): ByteArray {
            return when (device.continuityType) {
                ContinuityType.PROXIMITY_PAIR -> buildProximityPairPacket(device)
                ContinuityType.NEARBY_ACTION -> buildNearbyActionPacket(device)
            }
        }
        
        /**
         * ProximityPair packet structure:
         * [Length][0x07][Status][Device Model 2B][Status][Battery Levels 5B][Lid Counter]
         */
        private fun buildProximityPairPacket(device: AppleContinuityDevice): ByteArray {
            val status = 0x55.toByte()  // Paired with left, right, case
            val batteryLevels = byteArrayOf(
                0x55.toByte(),  // Left bud battery (5 = full, 5 = full)
                0x15.toByte(),  // Right bud battery
                0x55.toByte(),  // Case battery
                0x00,           // Lid open counter
                0x00            // Reserved
            )
            
            return byteArrayOf(
                0x10,  // Length (16 bytes total after this)
                device.continuityType.value,  // 0x07 = ProximityPair
                status,
                device.deviceCode[0],
                device.deviceCode[1],
                status,
                *batteryLevels,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00  // Padding
            )
        }
        
        /**
         * NearbyAction packet structure:
         * [Length][0x0F][Flags][Action Type][Authentication Tag 3B]
         */
        private fun buildNearbyActionPacket(device: AppleContinuityDevice): ByteArray {
            val flags = 0xC0.toByte()  // Show UI + perform action
            
            return byteArrayOf(
                0x06,  // Length
                device.continuityType.value,  // 0x0F = NearbyAction
                flags,
                device.action,
                0x00, 0x00, 0x00  // Auth tag placeholder
            )
        }
        
        fun buildSettings(txPower: Int = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH): AdvertiseSettings {
            return AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(txPower)
                .setConnectable(false)
                .setTimeout(0)
                .build()
        }
    }
    
    // ============================================
    // SWIFT PAIR ADVERTISER
    // ============================================
    object SwiftPair {
        /**
         * Builds Swift Pair advertise data
         * Uses Manufacturer Specific Data with Microsoft's ID (0x0006)
         * Format: [0x03, 0x00, 0x80] + device name
         */
        fun buildAdvertiseData(deviceName: String): AdvertiseData {
            val manufacturerData = SwiftPairPresets.buildManufacturerData(deviceName)
            
            return AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(SwiftPairPresets.MANUFACTURER_ID, manufacturerData)
                .build()
        }
        
        fun buildAdvertiseData(device: SwiftPairDevice): AdvertiseData {
            return buildAdvertiseData(device.displayName)
        }
        
        fun buildSettings(txPower: Int = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH): AdvertiseSettings {
            return AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(txPower)
                .setConnectable(true)  // Swift Pair devices should be connectable
                .setTimeout(0)
                .build()
        }
    }
    
    // ============================================
    // CUSTOM MESSAGE ADVERTISER (Original BLE Master)
    // Optimized for maximum discoverability
    // ============================================
    object Custom {
        const val MANUFACTURER_ID = 0xFFFF  // Experimental range
        const val SERVICE_UUID_STRING = "0000FFFF-0000-1000-8000-00805F9B34FB"
        val SERVICE_UUID = ParcelUuid(UUID.fromString(SERVICE_UUID_STRING))
        
        /**
         * Builds custom message advertise data
         * Uses both manufacturer data AND service UUID for maximum discoverability
         */
        fun buildAdvertiseData(message: String): AdvertiseData {
            val messageBytes = message.toByteArray(Charsets.UTF_8).take(20).toByteArray()
            
            return AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(SERVICE_UUID)  // Adds discoverability
                .addManufacturerData(MANUFACTURER_ID, messageBytes)
                .build()
        }
        
        /**
         * Builds scan response with the full message for devices that request it
         */
        fun buildScanResponse(message: String): AdvertiseData {
            val messageBytes = message.toByteArray(Charsets.UTF_8).take(24).toByteArray()
            
            return AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData(SERVICE_UUID, messageBytes)
                .build()
        }
        
        fun buildSettings(
            mode: Int = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
            txPower: Int = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        ): AdvertiseSettings {
            return AdvertiseSettings.Builder()
                .setAdvertiseMode(mode)
                .setTxPowerLevel(txPower)
                .setConnectable(true)  // Connectable = better detection
                .setTimeout(0)
                .build()
        }
    }
}

/**
 * Advanced advertising set parameters for Android 8.0+
 * Provides more control over advertising
 */
object AdvancedAdvertisingParams {
    
    fun buildParameters(
        isConnectable: Boolean = false,
        isLegacy: Boolean = true,
        primaryPhy: Int = 1,  // LE 1M
        txPower: Int = 1
    ): AdvertisingSetParameters? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AdvertisingSetParameters.Builder()
                .setLegacyMode(isLegacy)
                .setConnectable(isConnectable)
                .setScannable(!isConnectable)
                .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                .setTxPowerLevel(txPower)
                .setPrimaryPhy(primaryPhy)
                .build()
        } else {
            null
        }
    }
}
