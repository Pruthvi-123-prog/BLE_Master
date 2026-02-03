package com.blemaster.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blemaster.app.ui.components.EthicalWarningDialog
import com.blemaster.app.ui.components.PermissionRequestDialog
import com.blemaster.app.ui.components.PrivacyPolicyDialog
import com.blemaster.app.ui.screens.MainScreen
import com.blemaster.app.ui.screens.PresetsScreen
import com.blemaster.app.ui.screens.ScannerScreen
import com.blemaster.app.ui.screens.SettingsScreen
import com.blemaster.app.ui.theme.BLEMasterTheme
import com.blemaster.app.ui.theme.Black
import com.blemaster.app.viewmodel.BleViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BLEMasterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Black
                ) {
                    BLEMasterApp()
                }
            }
        }
    }
}

@Composable
fun BLEMasterApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel: BleViewModel = viewModel()
    val navController = rememberNavController()

    // Collect states
    val message by viewModel.message.collectAsState()
    val isBroadcasting by viewModel.isBroadcasting.collectAsState()
    val errorState by viewModel.errorState.collectAsState()
    val byteCount by viewModel.messageByteCount.collectAsState()
    val broadcastInterval by viewModel.broadcastInterval.collectAsState()
    val txPowerLevel by viewModel.txPowerLevel.collectAsState()
    val showEthicalWarning by viewModel.showEthicalWarning.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()

    // Scanner states
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val scanError by viewModel.scanError.collectAsState()
    
    // Protocol states
    val selectedProtocol by viewModel.selectedProtocol.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val swiftPairDeviceName by viewModel.swiftPairDeviceName.collectAsState()
    val rotationEnabled by viewModel.rotationEnabled.collectAsState()
    val rotationPresets by viewModel.rotationPresets.collectAsState()
    val currentPresetName by viewModel.currentPresetName.collectAsState()
    val advertisingMode by viewModel.advertisingMode.collectAsState()

    // Permission state
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    // Required permissions for Android 12+
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    // Check if all permissions are granted
    fun arePermissionsGranted(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        viewModel.setPermissionsGranted(allGranted)
        
        if (!allGranted) {
            // Check if permanently denied
            val shouldShowRationale = requiredPermissions.any { permission ->
                activity?.shouldShowRequestPermissionRationale(permission) == true
            }
            isPermanentlyDenied = !shouldShowRationale && !arePermissionsGranted()
            showPermissionDialog = true
        } else {
            showPermissionDialog = false
        }
    }

    // Bluetooth enable launcher
    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Bluetooth enabled, check permissions
            if (!arePermissionsGranted()) {
                permissionLauncher.launch(requiredPermissions)
            }
        }
    }

    // Initial permission check
    LaunchedEffect(Unit) {
        if (arePermissionsGranted()) {
            viewModel.setPermissionsGranted(true)
        } else {
            showPermissionDialog = true
        }
    }

    // Ethical Warning Dialog
    if (showEthicalWarning) {
        EthicalWarningDialog(
            onAccept = {
                viewModel.acknowledgeEthicalWarning()
            },
            onDecline = {
                activity?.finish()
            }
        )
    }

    // Permission Dialog
    if (showPermissionDialog && !showEthicalWarning) {
        PermissionRequestDialog(
            onRequestPermission = {
                permissionLauncher.launch(requiredPermissions)
            },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            isPermanentlyDenied = isPermanentlyDenied
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicy = false }
        )
    }

    // Navigation
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                message = message,
                onMessageChange = { viewModel.updateMessage(it) },
                byteCount = byteCount,
                maxBytes = viewModel.maxPayloadSize,
                isBroadcasting = isBroadcasting,
                selectedProtocol = selectedProtocol,
                selectedPreset = selectedPreset,
                rotationEnabled = rotationEnabled,
                rotationPresetsCount = rotationPresets.size,
                currentPresetName = currentPresetName,
                onToggleBroadcast = {
                    if (!permissionsGranted) {
                        showPermissionDialog = true
                        return@MainScreen
                    }
                    
                    if (!viewModel.isBluetoothEnabled) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        bluetoothEnableLauncher.launch(enableBtIntent)
                        return@MainScreen
                    }
                    
                    if (isBroadcasting) {
                        viewModel.stopProtocolBroadcast()
                    } else {
                        viewModel.startProtocolBroadcast()
                    }
                },
                errorState = errorState,
                onDismissError = { viewModel.clearError() },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToScanner = {
                    navController.navigate("scanner")
                },
                onNavigateToPresets = {
                    navController.navigate("presets")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                broadcastInterval = broadcastInterval,
                onIntervalChange = { viewModel.updateBroadcastInterval(it) },
                txPowerLevel = txPowerLevel,
                onPowerLevelChange = { viewModel.updateTxPowerLevel(it) },
                advertisingMode = advertisingMode,
                onAdvertisingModeChange = { viewModel.updateAdvertisingMode(it) },
                onNavigateBack = { navController.popBackStack() },
                onShowPrivacyPolicy = { showPrivacyPolicy = true }
            )
        }
        
        composable("presets") {
            PresetsScreen(
                selectedProtocol = selectedProtocol,
                selectedPreset = selectedPreset,
                rotationEnabled = rotationEnabled,
                rotationPresets = rotationPresets,
                swiftPairDeviceName = swiftPairDeviceName,
                onProtocolSelected = { viewModel.selectProtocol(it) },
                onPresetSelected = { viewModel.selectPreset(it) },
                onSwiftPairNameChanged = { viewModel.updateSwiftPairDeviceName(it) },
                onRotationToggled = { viewModel.setRotationEnabled(it) },
                onAddToRotation = { viewModel.addPresetToRotation(it) },
                onRemoveFromRotation = { viewModel.removePresetFromRotation(it) },
                onClearRotation = { viewModel.clearRotationPresets() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("scanner") {
            ScannerScreen(
                isScanning = isScanning,
                discoveredDevices = discoveredDevices,
                scanError = scanError,
                onStartScan = {
                    if (!permissionsGranted) {
                        showPermissionDialog = true
                        return@ScannerScreen
                    }
                    if (!viewModel.isBluetoothEnabled) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        bluetoothEnableLauncher.launch(enableBtIntent)
                        return@ScannerScreen
                    }
                    viewModel.startScanning()
                },
                onStopScan = { viewModel.stopScanning() },
                onClearDevices = { viewModel.clearDiscoveredDevices() },
                onNavigateBack = {
                    viewModel.stopScanning()
                    navController.popBackStack()
                }
            )
        }
    }
}
