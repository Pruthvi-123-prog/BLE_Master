package com.blemaster.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blemaster.app.ble.DiscoveredDevice
import com.blemaster.app.ble.ScanError
import com.blemaster.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    isScanning: Boolean,
    discoveredDevices: List<DiscoveredDevice>,
    scanError: ScanError?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onClearDevices: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nearby Broadcasts",
                        color = White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Accent
                        )
                    }
                },
                actions = {
                    if (discoveredDevices.isNotEmpty()) {
                        IconButton(onClick = onClearDevices) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear",
                                tint = Accent
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Scan Button
            ScanToggleButton(
                isScanning = isScanning,
                onToggle = { if (isScanning) onStopScan() else onStartScan() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status text
            Text(
                text = when {
                    isScanning -> "Scanning for BLE Master broadcasts..."
                    discoveredDevices.isEmpty() -> "Tap to scan for nearby devices"
                    else -> "Found ${discoveredDevices.size} device(s)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isScanning) Green else OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Error display
            scanError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                ScanErrorCard(error = error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device list
            if (discoveredDevices.isEmpty() && !isScanning) {
                EmptyStateMessage()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = discoveredDevices,
                        key = { it.address }
                    ) { device ->
                        DiscoveredDeviceCard(device = device)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanToggleButton(
    isScanning: Boolean,
    onToggle: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isScanning) Error else Accent,
        animationSpec = tween(300),
        label = "buttonColor"
    )

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isScanning) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Button(
        onClick = onToggle,
        modifier = Modifier
            .size(100.dp)
            .scale(if (isScanning) pulseScale else 1f),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp
        )
    ) {
        Icon(
            imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
            contentDescription = if (isScanning) "Stop Scan" else "Start Scan",
            modifier = Modifier.size(40.dp),
            tint = Black
        )
    }
}

@Composable
private fun DiscoveredDeviceCard(device: DiscoveredDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with device info and signal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device icon and name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = device.name ?: "BLE Master Device",
                            style = MaterialTheme.typography.titleSmall,
                            color = White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Signal strength indicator
                SignalStrengthIndicator(rssi = device.rssi)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Message content - THE BROADCAST MESSAGE
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Broadcasting:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${device.message}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance
                DetailChip(
                    icon = Icons.Default.NearMe,
                    label = device.getDistanceString()
                )

                // Signal
                DetailChip(
                    icon = Icons.Default.SignalCellularAlt,
                    label = "${device.rssi} dBm"
                )

                // Last seen
                DetailChip(
                    icon = Icons.Default.AccessTime,
                    label = formatTime(device.lastSeen)
                )
            }
        }
    }
}

@Composable
private fun SignalStrengthIndicator(rssi: Int) {
    val color = when {
        rssi >= -50 -> Green
        rssi >= -60 -> Accent
        rssi >= -70 -> AccentVariant
        rssi >= -80 -> OnSurfaceVariant
        else -> Error
    }

    val bars = when {
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -70 -> 2
        rssi >= -80 -> 1
        else -> 0
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((8 + index * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < bars) color else OnSurfaceVariant.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
private fun DetailChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
    }
}

@Composable
private fun ScanErrorCard(error: ScanError) {
    val errorMessage = when (error) {
        is ScanError.NotSupported -> "BLE scanning not supported"
        is ScanError.BluetoothDisabled -> "Please enable Bluetooth"
        is ScanError.PermissionDenied -> "Bluetooth permission required"
        is ScanError.AlreadyStarted -> "Scan already in progress"
        is ScanError.RegistrationFailed -> "Failed to register scanner"
        is ScanError.FeatureUnsupported -> "Feature not supported"
        is ScanError.InternalError -> "Internal error occurred"
        is ScanError.Unknown -> "Error code: ${error.errorCode}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Error.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = Error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Error
            )
        }
    }
}

@Composable
private fun EmptyStateMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothSearching,
            contentDescription = null,
            tint = OnSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No broadcasts detected",
            style = MaterialTheme.typography.titleMedium,
            color = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Make sure another device is running BLE Master and actively broadcasting a message",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
