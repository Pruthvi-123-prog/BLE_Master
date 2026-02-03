package com.blemaster.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blemaster.app.R
import com.blemaster.app.ble.AdvertisingMode
import com.blemaster.app.ble.TxPowerLevel
import com.blemaster.app.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    broadcastInterval: Int,
    onIntervalChange: (Int) -> Unit,
    txPowerLevel: TxPowerLevel,
    onPowerLevelChange: (TxPowerLevel) -> Unit,
    advertisingMode: AdvertisingMode,
    onAdvertisingModeChange: (AdvertisingMode) -> Unit,
    onNavigateBack: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Broadcast Interval Section
            SettingsSection(title = stringResource(R.string.broadcast_interval)) {
                IntervalSlider(
                    value = broadcastInterval,
                    onValueChange = onIntervalChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transmission Power Section
            SettingsSection(title = stringResource(R.string.transmission_power)) {
                PowerLevelSelector(
                    selectedLevel = txPowerLevel,
                    onLevelChange = onPowerLevelChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Advertising Mode Section
            SettingsSection(title = "Device Compatibility") {
                AdvertisingModeSelector(
                    selectedMode = advertisingMode,
                    onModeChange = onAdvertisingModeChange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy Policy
            SettingsSection(title = "") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowPrivacyPolicy() },
                    colors = CardDefaults.cardColors(
                        containerColor = Surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = Accent
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.privacy_policy),
                            style = MaterialTheme.typography.bodyLarge,
                            color = White,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = OnSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
private fun IntervalSlider(
    value: Int,
    onValueChange: (Int) -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interval",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.interval_ms, value),
                    style = MaterialTheme.typography.titleMedium,
                    color = Accent,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = 100f..5000f,
                steps = 48, // (5000-100)/100 - 1 = 48 steps for 100ms increments
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = OnSurfaceVariant.copy(alpha = 0.3f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "100ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "5000ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PowerLevelSelector(
    selectedLevel: TxPowerLevel,
    onLevelChange: (TxPowerLevel) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TxPowerLevel.entries.forEach { level ->
                val isSelected = level == selectedLevel
                val backgroundColor = if (isSelected) Accent else Surface
                val textColor = if (isSelected) Black else OnSurfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable { onLevelChange(level) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = when (level) {
                                TxPowerLevel.LOW -> Icons.Default.SignalCellularAlt1Bar
                                TxPowerLevel.MEDIUM -> Icons.Default.SignalCellularAlt2Bar
                                TxPowerLevel.HIGH -> Icons.Default.SignalCellularAlt
                            },
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (level) {
                                TxPowerLevel.LOW -> stringResource(R.string.power_low)
                                TxPowerLevel.MEDIUM -> stringResource(R.string.power_medium)
                                TxPowerLevel.HIGH -> stringResource(R.string.power_high)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvertisingModeSelector(
    selectedMode: AdvertisingMode,
    onModeChange: (AdvertisingMode) -> Unit
) {
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
            // Mode selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AdvertisingMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val backgroundColor = if (isSelected) Accent else Surface
                    val textColor = if (isSelected) Black else OnSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(backgroundColor)
                            .clickable { onModeChange(mode) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    AdvertisingMode.LEGACY -> Icons.Default.PhonelinkSetup
                                    AdvertisingMode.EXTENDED -> Icons.Default.Speed
                                },
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (mode) {
                                    AdvertisingMode.LEGACY -> "Legacy"
                                    AdvertisingMode.EXTENDED -> "Extended"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            
            // Info card based on selected mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedMode == AdvertisingMode.LEGACY) 
                        Accent.copy(alpha = 0.1f) 
                    else 
                        Warning.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (selectedMode == AdvertisingMode.LEGACY)
                            Icons.Default.CheckCircle
                        else
                            Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (selectedMode == AdvertisingMode.LEGACY) Accent else Warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (selectedMode == AdvertisingMode.LEGACY)
                                "Maximum Compatibility"
                            else
                                "Limited Compatibility",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedMode == AdvertisingMode.LEGACY) Accent else Warning,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedMode == AdvertisingMode.LEGACY)
                                "Uses LE 1M PHY and legacy PDUs. Works with all BLE devices including older BT 5.0 phones. Payload limit: 20 bytes."
                            else
                                "Uses modern BT 5.x features. May NOT be detected by older or budget devices (e.g., Samsung M02). Payload limit: 24 bytes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
