<p align="center">
  <img src="LS20260203094005.png" alt="BLE Master Pro" width="160" style="border-radius: 24px;"/>
</p>

<h1 align="center">BLE Master Pro</h1>

<p align="center">
  <b>Advanced Bluetooth Low Energy Broadcasting and Protocol Analysis Tool for Android</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/Min_SDK-26-blue?style=for-the-badge" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-Educational_Use-orange?style=flat-square" alt="License"/>
  <img src="https://img.shields.io/badge/Status-Active_Development-brightgreen?style=flat-square" alt="Status"/>
</p>

---

## Overview

BLE Master Pro is a comprehensive Android application designed for Bluetooth Low Energy research, security testing, and protocol analysis. The application enables broadcasting of custom BLE advertisements and supports spoofing of major device pairing protocols including Google Fast Pair, Apple Continuity, and Microsoft Swift Pair.

This tool is intended for security researchers, developers, and educators who need to understand and test BLE protocol implementations.

---

## Key Features

<table>
<tr>
<td width="50%">

### Broadcasting Capabilities
- Custom message broadcasting with manufacturer-specific data
- Multi-protocol support with preset device profiles
- Rotation mode for cycling through multiple presets
- Configurable TX power and advertising interval
- Foreground service for persistent broadcasting

</td>
<td width="50%">

### Protocol Support
- **Google Fast Pair** - Android device pairing popups
- **Apple Continuity** - AirPods/Beats proximity pairing
- **Microsoft Swift Pair** - Windows pairing notifications
- **Custom Protocol** - Raw BLE advertisement data

</td>
</tr>
<tr>
<td width="50%">

### Scanner Module
- Real-time detection of nearby BLE broadcasts
- Protocol identification and decoding
- Signal strength (RSSI) monitoring
- Distance estimation algorithms
- Raw packet inspection

</td>
<td width="50%">

### User Interface
- Material Design 3 with dynamic theming
- AMOLED-optimized dark theme
- Intuitive preset selection
- Batch device selection for rotation
- Real-time status indicators

</td>
</tr>
</table>

---

## System Architecture

```
+------------------------------------------------------------------+
|                         BLE MASTER PRO                            |
+------------------------------------------------------------------+
|                                                                   |
|  +---------------------+    +---------------------+               |
|  |    Presentation     |    |      Service        |               |
|  |       Layer         |    |       Layer         |               |
|  |                     |    |                     |               |
|  |  +---------------+  |    |  +---------------+  |               |
|  |  |  MainScreen   |  |    |  |  BleService   |  |               |
|  |  +---------------+  |    |  | (Foreground)  |  |               |
|  |  | PresetsScreen |  |    |  +---------------+  |               |
|  |  +---------------+  |    |                     |               |
|  |  | ScannerScreen |  |    +---------------------+               |
|  |  +---------------+  |              |                           |
|  |  | SettingsScreen|  |              v                           |
|  |  +---------------+  |    +---------------------+               |
|  |         |           |    |     BLE Layer       |               |
|  +---------|-----------+    |                     |               |
|            |                |  +---------------+  |               |
|            v                |  | Advertiser    |  |               |
|  +---------------------+    |  | Manager       |  |               |
|  |    ViewModel        |    |  +---------------+  |               |
|  |       Layer         |    |  | Scanner       |  |               |
|  |                     |    |  | Manager       |  |               |
|  |  +---------------+  |    |  +---------------+  |               |
|  |  | BleViewModel  |<----->|  | Protocol      |  |               |
|  |  | - StateFlow   |  |    |  | Advertisers   |  |               |
|  |  | - Repository  |  |    |  +---------------+  |               |
|  |  +---------------+  |    |                     |               |
|  +---------------------+    +---------------------+               |
|                                                                   |
+------------------------------------------------------------------+
```

---

## Protocol Specifications

### Custom Protocol

| Property | Value |
|----------|-------|
| Manufacturer ID | `0xFFFF` (Experimental Range) |
| Service UUID | `0000FFFF-0000-1000-8000-00805F9B34FB` |
| Max Payload | 24 bytes (UTF-8) |
| Connectable | Yes |
| Scan Response | Enabled |

### Google Fast Pair

| Property | Value |
|----------|-------|
| Service UUID | `0000FE2C-0000-1000-8000-00805F9B34FB` |
| Payload | 3-byte Model ID |
| Target | Android 6.0+ devices |
| Behavior | Triggers pairing popup with device image |

### Apple Continuity

| Property | Value |
|----------|-------|
| Manufacturer ID | `0x004C` (Apple Inc.) |
| Proximity Pair | Type `0x07` - AirPods/Beats popups |
| Nearby Action | Type `0x0F` - Setup modals |
| Target | iOS 10+ devices |

### Microsoft Swift Pair

| Property | Value |
|----------|-------|
| Manufacturer ID | `0x0006` (Microsoft) |
| Header | `0x03 0x00 0x80` |
| Payload | Device name (max 20 chars) |
| Target | Windows 10/11 |
| Behavior | Toast notification with custom device name |

---

## Technical Requirements

| Requirement | Specification |
|-------------|---------------|
| Minimum SDK | API 26 (Android 8.0 Oreo) |
| Target SDK | API 34 (Android 14) |
| Compile SDK | API 34 |
| Language | Kotlin 1.9+ |
| UI Framework | Jetpack Compose 1.5+ |
| Architecture | MVVM with Repository Pattern |
| State Management | Kotlin StateFlow |
| Persistence | Jetpack DataStore |
| Build System | Gradle 8.2 with Kotlin DSL |

### Required Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
```

---

## Installation

### Prerequisites

- Android device with BLE peripheral mode support
- Android 8.0 (API 26) or higher
- USB debugging enabled for ADB installation
- Java Development Kit (JDK) 17+

### Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/ble-master-pro.git

# Navigate to project directory
cd ble-master-pro

# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Quick Install (Windows)

```batch
build_and_install.bat
```

---

## Usage

### Custom Message Broadcasting

1. Launch the application and grant required permissions
2. Select **Custom** from the protocol selection tabs
3. Enter your message in the text field (maximum 24 bytes UTF-8)
4. Press the broadcast button to begin advertising
5. Use another device with the scanner to verify reception

### Protocol Spoofing

1. Tap the protocol card to access preset selection
2. Choose the target protocol: Windows, Apple, or Android
3. Select a device preset from the available list
4. Initiate broadcasting to trigger popups on nearby devices

### Rotation Mode

1. Enable the rotation toggle in the preset selection screen
2. Enter batch selection mode by tapping "Add Devices"
3. Select multiple presets using checkboxes
4. The application will cycle through selected presets during broadcast

### Scanning for Broadcasts

1. Access the scanner via the search icon in the toolbar
2. Grant location permission when prompted
3. Detected broadcasts display with protocol type, message, and signal strength
4. Tap entries for detailed packet information

---

## Project Structure

```
BLE/
├── app/
│   ├── src/main/
│   │   ├── java/com/blemaster/app/
│   │   │   ├── ble/
│   │   │   │   ├── BleAdvertiserManager.kt      [Advertising Control]
│   │   │   │   ├── BleScannerManager.kt         [Scan Management]
│   │   │   │   └── protocols/
│   │   │   │       ├── ProtocolModels.kt        [Data Models]
│   │   │   │       └── ProtocolAdvertisers.kt   [Packet Builders]
│   │   │   ├── data/
│   │   │   │   └── SettingsRepository.kt        [Preferences]
│   │   │   ├── service/
│   │   │   │   └── BleService.kt                [Foreground Service]
│   │   │   ├── ui/
│   │   │   │   ├── components/                  [Reusable Components]
│   │   │   │   ├── screens/                     [Application Screens]
│   │   │   │   └── theme/                       [Material Theme]
│   │   │   ├── viewmodel/
│   │   │   │   └── BleViewModel.kt              [UI State]
│   │   │   └── MainActivity.kt                  [Entry Point]
│   │   └── res/
│   │       ├── mipmap-*/                        [Application Icons]
│   │       └── values/                          [Resources]
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Broadcast Flow Diagram

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│   User      │     │  ViewModel   │     │  Advertiser     │
│   Input     │────>│  Processing  │────>│  Manager        │
└─────────────┘     └──────────────┘     └─────────────────┘
                                                  │
                                                  v
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Nearby     │     │   BLE        │     │  Protocol       │
│  Devices    │<────│   Radio      │<────│  Advertiser     │
└─────────────┘     └──────────────┘     └─────────────────┘
                           │
                           v
                    ┌──────────────┐
                    │  ~100ms      │
                    │  Interval    │
                    │  (Low Latency│
                    └──────────────┘
```

---

## Ethical Use Policy

### Permitted Uses

- Educational research and learning about BLE protocols
- Security research and authorized penetration testing
- Testing personal devices and custom implementations
- Academic demonstrations and presentations
- Development and debugging of BLE applications

### Prohibited Uses

- Harassment or disruption of individuals or public spaces
- Impersonation of devices for fraudulent purposes
- Unauthorized access to systems or networks
- Any activity violating applicable laws or regulations
- Commercial exploitation without proper licensing

**By using this application, you acknowledge full responsibility for ensuring compliance with all applicable laws and ethical standards.**

---

## Troubleshooting

### Broadcasting Issues

| Problem | Solution |
|---------|----------|
| Broadcasting fails to start | Verify Bluetooth is enabled; Check BLUETOOTH_ADVERTISE permission |
| Error: Feature not supported | Device may not support BLE peripheral mode |
| Advertising timeout | Some devices limit advertising duration; restart broadcast |

### Detection Issues

| Problem | Solution |
|---------|----------|
| Other devices not detecting | Reduce distance to under 5 meters; Increase TX power |
| Slow detection time | Ensure LOW_LATENCY mode is enabled |
| Scanner not finding devices | Enable Location Services (Android requirement for BLE) |

### Protocol-Specific Issues

| Problem | Solution |
|---------|----------|
| Fast Pair not triggering | May be patched on Android 13+; Try debug model IDs |
| Apple popups not appearing | Requires iOS device with Bluetooth enabled |
| Swift Pair not showing | Ensure Windows Swift Pair is enabled in Bluetooth settings |

---

## Performance Optimization

### Battery Considerations

| Mode | Interval | Battery Impact |
|------|----------|----------------|
| Low Latency | ~100ms | High |
| Balanced | ~250ms | Medium |
| Low Power | ~1000ms | Low |

### Recommended Settings

- Use **Low Latency** mode for demonstrations and testing
- Switch to **Balanced** mode for extended operation
- Disable rotation mode when single-preset broadcasting is sufficient

---

## References

| Resource | Link |
|----------|------|
| Bluetooth Core Specification | [bluetooth.com/specifications](https://www.bluetooth.com/specifications/specs/) |
| Google Fast Pair | [developers.google.com/nearby/fast-pair](https://developers.google.com/nearby/fast-pair/specifications/introduction) |
| Apple Continuity Research | [github.com/furiousMAC/continuity](https://github.com/furiousMAC/continuity) |
| Microsoft Swift Pair | [docs.microsoft.com](https://docs.microsoft.com/en-us/windows-hardware/design/component-guidelines/bluetooth-swift-pair) |
| Android BLE API | [developer.android.com](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeAdvertiser) |

---

## Contributing

Contributions are welcome. Please follow these guidelines:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/description`
3. Commit changes with descriptive messages
4. Push to your fork: `git push origin feature/description`
5. Submit a pull request with detailed description

### Code Standards

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Document public APIs with KDoc comments
- Include unit tests for new functionality

---

## License

This project is provided for **educational and research purposes only**.

The authors assume no liability for misuse of this software. Users are solely responsible for ensuring their use complies with all applicable laws and regulations.

---

<p align="center">
  <sub>Developed with Kotlin and Jetpack Compose</sub>
</p>

<p align="center">
  <sub>For educational and research purposes only</sub>
</p>
