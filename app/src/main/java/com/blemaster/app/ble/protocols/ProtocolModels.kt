package com.blemaster.app.ble.protocols

/**
 * Protocol types for BLE spoofing
 */
enum class ProtocolType {
    CUSTOM,         // Original BLE Master custom broadcast
    FAST_PAIR,      // Google Fast Pair (Android)
    APPLE_CONTINUITY, // Apple Continuity (iOS)
    SWIFT_PAIR      // Microsoft Swift Pair (Windows)
}

/**
 * Base class for all device presets
 */
sealed class DevicePreset {
    abstract val name: String
    abstract val protocol: ProtocolType
}

// ============================================
// FAST PAIR PRESETS (Google - Android)
// ============================================
data class FastPairDevice(
    override val name: String,
    val modelId: ByteArray,  // 3-byte device model ID
    val isDebugModel: Boolean = false
) : DevicePreset() {
    override val protocol = ProtocolType.FAST_PAIR
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FastPairDevice
        return name == other.name && modelId.contentEquals(other.modelId)
    }
    
    override fun hashCode(): Int = 31 * name.hashCode() + modelId.contentHashCode()
}

object FastPairPresets {
    // Service UUID for Fast Pair
    const val SERVICE_UUID = "0000fe2c-0000-1000-8000-00805f9b34fb"
    
    // Anti-spoofing disabled model ID prefix for debug
    val DEBUG_MODEL_PREFIX = byteArrayOf(0x00, 0x00)
    
    val devices = listOf(
        // Headphones & Earbuds
        FastPairDevice("Google Pixel Buds Pro", byteArrayOf(0xD9.toByte(), 0x9C.toByte(), 0xA1.toByte())),
        FastPairDevice("Google Pixel Buds A-Series", byteArrayOf(0x91.toByte(), 0x8E.toByte(), 0xF3.toByte())),
        FastPairDevice("JBL Tune 760NC", byteArrayOf(0xF5.toByte(), 0x25.toByte(), 0x1B.toByte())),
        FastPairDevice("JBL Live Pro 2", byteArrayOf(0x95.toByte(), 0x11.toByte(), 0x9A.toByte())),
        FastPairDevice("Sony WF-1000XM4", byteArrayOf(0xF0.toByte(), 0xBB.toByte(), 0x84.toByte())),
        FastPairDevice("Sony WH-1000XM5", byteArrayOf(0xCD.toByte(), 0x81.toByte(), 0x06.toByte())),
        FastPairDevice("Samsung Galaxy Buds Pro", byteArrayOf(0xD4.toByte(), 0x46.toByte(), 0xB1.toByte())),
        FastPairDevice("Samsung Galaxy Buds 2", byteArrayOf(0x60.toByte(), 0x1C.toByte(), 0xF5.toByte())),
        FastPairDevice("Beats Studio Buds", byteArrayOf(0xEA.toByte(), 0xCB.toByte(), 0x42.toByte())),
        FastPairDevice("Bose QuietComfort Earbuds", byteArrayOf(0x94.toByte(), 0x0B.toByte(), 0x35.toByte())),
        
        // Fun/Debug Models
        FastPairDevice("Flipper Zero", byteArrayOf(0xD9.toByte(), 0x9C.toByte(), 0xA1.toByte())),
        FastPairDevice("Free Robux", byteArrayOf(0x77.toByte(), 0xFF.toByte(), 0x67.toByte()), true),
        FastPairDevice("Rickroll Speaker", byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()), true),
        
        // Debug models (allow custom display text)
        FastPairDevice("[Debug] Custom Text", byteArrayOf(0x00, 0x00, 0x01), true)
    )
    
    fun getById(modelId: ByteArray): FastPairDevice? {
        return devices.find { it.modelId.contentEquals(modelId) }
    }
}

// ============================================
// APPLE CONTINUITY PRESETS (iOS)
// ============================================
enum class ContinuityType(val value: Byte) {
    PROXIMITY_PAIR(0x07),    // Device popup (AirPods, etc)
    NEARBY_ACTION(0x0F)      // Action modal
}

data class AppleContinuityDevice(
    override val name: String,
    val continuityType: ContinuityType,
    val deviceCode: ByteArray,  // 2 bytes for device type
    val action: Byte = 0x00,    // For NearbyAction type
    val flags: Byte = 0x00
) : DevicePreset() {
    override val protocol = ProtocolType.APPLE_CONTINUITY
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AppleContinuityDevice
        return name == other.name && deviceCode.contentEquals(other.deviceCode)
    }
    
    override fun hashCode(): Int = 31 * name.hashCode() + deviceCode.contentHashCode()
}

object AppleContinuityPresets {
    // Apple's Manufacturer ID
    const val MANUFACTURER_ID = 0x004C
    
    // ProximityPair devices (popup with device image)
    val proximityDevices = listOf(
        AppleContinuityDevice("AirPods", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x02, 0x20)),
        AppleContinuityDevice("AirPods Pro", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x0E, 0x20)),
        AppleContinuityDevice("AirPods Pro 2", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x14, 0x20)),
        AppleContinuityDevice("AirPods Max", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x0A, 0x20)),
        AppleContinuityDevice("AirPods 3rd Gen", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x13, 0x20)),
        AppleContinuityDevice("Beats Fit Pro", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x12, 0x20)),
        AppleContinuityDevice("Beats Solo 3", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x06, 0x20)),
        AppleContinuityDevice("Beats Studio 3", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x09, 0x20)),
        AppleContinuityDevice("Powerbeats Pro", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x0B, 0x20)),
        AppleContinuityDevice("Beats X", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x05, 0x20)),
        AppleContinuityDevice("AppleTV Setup", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x01, 0x00)),
        AppleContinuityDevice("Apple Vision Pro", ContinuityType.PROXIMITY_PAIR, byteArrayOf(0x24, 0x20))
    )
    
    // NearbyAction devices (action popup)
    val actionDevices = listOf(
        AppleContinuityDevice("Setup New iPhone", ContinuityType.NEARBY_ACTION, byteArrayOf(0x09, 0x00), 0x09),
        AppleContinuityDevice("Transfer Number", ContinuityType.NEARBY_ACTION, byteArrayOf(0x02, 0x00), 0x02),
        AppleContinuityDevice("Apple Watch Setup", ContinuityType.NEARBY_ACTION, byteArrayOf(0x0B, 0x00), 0x0B),
        AppleContinuityDevice("AppleTV Keyboard", ContinuityType.NEARBY_ACTION, byteArrayOf(0x13, 0x00), 0x13),
        AppleContinuityDevice("Join This AppleTV?", ContinuityType.NEARBY_ACTION, byteArrayOf(0x06, 0x00), 0x06),
        AppleContinuityDevice("HomePod Setup", ContinuityType.NEARBY_ACTION, byteArrayOf(0x0D, 0x00), 0x0D),
        AppleContinuityDevice("Connect to WiFi", ContinuityType.NEARBY_ACTION, byteArrayOf(0x04, 0x00), 0x04),
        AppleContinuityDevice("Login with Apple ID", ContinuityType.NEARBY_ACTION, byteArrayOf(0x0F, 0x00), 0x0F)
    )
    
    val allDevices = proximityDevices + actionDevices
}

// ============================================
// SWIFT PAIR PRESETS (Microsoft - Windows)
// ============================================
data class SwiftPairDevice(
    override val name: String,
    val displayName: String  // The name shown in Windows popup
) : DevicePreset() {
    override val protocol = ProtocolType.SWIFT_PAIR
}

object SwiftPairPresets {
    // Microsoft's Manufacturer ID
    const val MANUFACTURER_ID = 0x0006
    
    // Swift Pair header bytes
    val HEADER = byteArrayOf(0x03, 0x00, 0x80.toByte())
    
    val devices = listOf(
        SwiftPairDevice("Microsoft Surface Earbuds", "Surface Earbuds"),
        SwiftPairDevice("Xbox Controller", "Xbox Wireless Controller"),
        SwiftPairDevice("Microsoft Arc Mouse", "Arc Mouse"),
        SwiftPairDevice("Surface Headphones 2", "Surface Headphones 2"),
        SwiftPairDevice("Bluetooth Keyboard", "BT Keyboard Pro"),
        SwiftPairDevice("Gaming Headset", "Pro Gaming Headset"),
        SwiftPairDevice("Wireless Speaker", "Portable Speaker"),
        SwiftPairDevice("Custom Device", "BLE Master Device")
    )
    
    /**
     * Builds manufacturer data for Swift Pair
     * Format: [0x03, 0x00, 0x80] + device name bytes
     */
    fun buildManufacturerData(deviceName: String): ByteArray {
        val nameBytes = deviceName.toByteArray(Charsets.UTF_8).take(20).toByteArray()
        return HEADER + nameBytes
    }
}

// ============================================
// BROADCAST CONFIGURATION
// ============================================
data class BroadcastConfig(
    val protocol: ProtocolType = ProtocolType.CUSTOM,
    val customMessage: String = "",
    val selectedPreset: DevicePreset? = null,
    val swiftPairCustomName: String = "BLE Master",
    val rotationEnabled: Boolean = false,
    val rotationIntervalMs: Long = 1000L,
    val rotationPresets: List<DevicePreset> = emptyList()
)
