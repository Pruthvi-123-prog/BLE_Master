package com.blemaster.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blemaster.app.ble.protocols.*
import com.blemaster.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    selectedProtocol: ProtocolType,
    selectedPreset: DevicePreset?,
    rotationEnabled: Boolean,
    rotationPresets: List<DevicePreset>,
    swiftPairDeviceName: String,
    onProtocolSelected: (ProtocolType) -> Unit,
    onPresetSelected: (DevicePreset?) -> Unit,
    onSwiftPairNameChanged: (String) -> Unit,
    onRotationToggled: (Boolean) -> Unit,
    onAddToRotation: (DevicePreset) -> Unit,
    onRemoveFromRotation: (DevicePreset) -> Unit,
    onClearRotation: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showBatchSelect by remember { mutableStateOf(false) }
    var selectedForBatch by remember { mutableStateOf(setOf<DevicePreset>()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (showBatchSelect) "Select Devices" else "Protocol Selection",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showBatchSelect) {
                            showBatchSelect = false
                            selectedForBatch = emptySet()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            if (showBatchSelect) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (showBatchSelect) {
                        TextButton(
                            onClick = {
                                selectedForBatch.forEach { onAddToRotation(it) }
                                showBatchSelect = false
                                selectedForBatch = emptySet()
                            },
                            enabled = selectedForBatch.isNotEmpty()
                        ) {
                            Text("ADD ${selectedForBatch.size}", color = Accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black,
                    titleContentColor = White
                )
            )
        },
        containerColor = Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            
            // Protocol Tabs
            item {
                ProtocolTabs(
                    selectedProtocol = selectedProtocol,
                    onProtocolSelected = onProtocolSelected
                )
            }
            
            // Rotation Mode Section
            item {
                RotationSection(
                    enabled = rotationEnabled,
                    count = rotationPresets.size,
                    onToggle = onRotationToggled,
                    onClear = onClearRotation,
                    onBatchSelect = { showBatchSelect = true }
                )
            }
            
            // Selected rotation presets chips
            if (rotationPresets.isNotEmpty()) {
                item {
                    SelectedPresetsChips(
                        presets = rotationPresets,
                        onRemove = onRemoveFromRotation
                    )
                }
            }
            
            // Protocol-specific content
            when (selectedProtocol) {
                ProtocolType.CUSTOM -> {
                    item {
                        CustomModeCard(
                            message = swiftPairDeviceName,
                            onMessageChange = onSwiftPairNameChanged
                        )
                    }
                }
                
                ProtocolType.SWIFT_PAIR -> {
                    item {
                        ProtocolHeader(
                            icon = Icons.Default.Computer,
                            title = "Windows Swift Pair",
                            subtitle = "Shows your custom text in Windows popup"
                        )
                    }
                    
                    item {
                        CustomNameInput(
                            label = "Device Name (shown in popup)",
                            value = swiftPairDeviceName,
                            onValueChange = onSwiftPairNameChanged,
                            maxLength = 20
                        )
                    }
                    
                    item {
                        DeviceList(
                            title = "Quick Presets",
                            devices = SwiftPairPresets.devices,
                            selectedPreset = selectedPreset,
                            rotationPresets = rotationPresets,
                            showBatchSelect = showBatchSelect,
                            selectedForBatch = selectedForBatch,
                            onPresetSelected = onPresetSelected,
                            onBatchToggle = { preset ->
                                selectedForBatch = if (preset in selectedForBatch) {
                                    selectedForBatch - preset
                                } else {
                                    selectedForBatch + preset
                                }
                            }
                        )
                    }
                }
                
                ProtocolType.FAST_PAIR -> {
                    item {
                        ProtocolHeader(
                            icon = Icons.Default.PhoneAndroid,
                            title = "Google Fast Pair",
                            subtitle = "Android device pairing popup"
                        )
                    }
                    
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "May be patched on Android 13+ devices",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    item {
                        DeviceList(
                            title = "Headphones & Earbuds",
                            devices = FastPairPresets.devices.filter { !it.isDebugModel },
                            selectedPreset = selectedPreset,
                            rotationPresets = rotationPresets,
                            showBatchSelect = showBatchSelect,
                            selectedForBatch = selectedForBatch,
                            onPresetSelected = onPresetSelected,
                            onBatchToggle = { preset ->
                                selectedForBatch = if (preset in selectedForBatch) {
                                    selectedForBatch - preset
                                } else {
                                    selectedForBatch + preset
                                }
                            }
                        )
                    }
                    
                    item {
                        DeviceList(
                            title = "Debug Models",
                            devices = FastPairPresets.devices.filter { it.isDebugModel },
                            selectedPreset = selectedPreset,
                            rotationPresets = rotationPresets,
                            showBatchSelect = showBatchSelect,
                            selectedForBatch = selectedForBatch,
                            onPresetSelected = onPresetSelected,
                            onBatchToggle = { preset ->
                                selectedForBatch = if (preset in selectedForBatch) {
                                    selectedForBatch - preset
                                } else {
                                    selectedForBatch + preset
                                }
                            }
                        )
                    }
                }
                
                ProtocolType.APPLE_CONTINUITY -> {
                    item {
                        ProtocolHeader(
                            icon = Icons.Default.Headphones,
                            title = "Apple Continuity",
                            subtitle = "iOS device popups and actions"
                        )
                    }
                    
                    item {
                        DeviceList(
                            title = "Device Popups (AirPods, Beats)",
                            devices = AppleContinuityPresets.proximityDevices,
                            selectedPreset = selectedPreset,
                            rotationPresets = rotationPresets,
                            showBatchSelect = showBatchSelect,
                            selectedForBatch = selectedForBatch,
                            onPresetSelected = onPresetSelected,
                            onBatchToggle = { preset ->
                                selectedForBatch = if (preset in selectedForBatch) {
                                    selectedForBatch - preset
                                } else {
                                    selectedForBatch + preset
                                }
                            }
                        )
                    }
                    
                    item {
                        DeviceList(
                            title = "Action Modals",
                            devices = AppleContinuityPresets.actionDevices,
                            selectedPreset = selectedPreset,
                            rotationPresets = rotationPresets,
                            showBatchSelect = showBatchSelect,
                            selectedForBatch = selectedForBatch,
                            onPresetSelected = onPresetSelected,
                            onBatchToggle = { preset ->
                                selectedForBatch = if (preset in selectedForBatch) {
                                    selectedForBatch - preset
                                } else {
                                    selectedForBatch + preset
                                }
                            }
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProtocolTabs(
    selectedProtocol: ProtocolType,
    onProtocolSelected: (ProtocolType) -> Unit
) {
    val protocols = listOf(
        Triple(ProtocolType.SWIFT_PAIR, "Windows", Icons.Default.Computer),
        Triple(ProtocolType.APPLE_CONTINUITY, "Apple", Icons.Default.Headphones),
        Triple(ProtocolType.FAST_PAIR, "Android", Icons.Default.PhoneAndroid),
        Triple(ProtocolType.CUSTOM, "Custom", Icons.Default.Edit)
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        protocols.forEach { (protocol, label, icon) ->
            val isSelected = selectedProtocol == protocol
            
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onProtocolSelected(protocol) },
                color = if (isSelected) Accent.copy(alpha = 0.15f) else SurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = if (isSelected) 4.dp else 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) Accent else OnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Accent else OnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RotationSection(
    enabled: Boolean,
    count: Int,
    onToggle: (Boolean) -> Unit,
    onClear: () -> Unit,
    onBatchSelect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (enabled) RotationActive else OnSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Rotation Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface
                    )
                    Text(
                        if (count > 0) "$count devices in queue" else "Cycle through multiple devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = RotationActive,
                        checkedTrackColor = RotationActive.copy(alpha = 0.3f)
                    )
                )
            }
            
            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBatchSelect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Devices")
                    }
                    
                    if (count > 0) {
                        OutlinedButton(
                            onClick = onClear,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPresetsChips(
    presets: List<DevicePreset>,
    onRemove: (DevicePreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "In Rotation Queue:",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant
        )
        
        presets.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { preset ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = SurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                preset.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemove(preset) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProtocolHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun CustomModeCard(
    message: String,
    onMessageChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Accent)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Custom Broadcast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Broadcasts your message as manufacturer-specific data. Requires a compatible receiver to decode.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Tip: Use Swift Pair for custom text that appears directly in Windows popups.",
                style = MaterialTheme.typography.bodySmall,
                color = Accent
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomNameInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = SurfaceElevated,
            focusedContainerColor = SurfaceVariant,
            unfocusedContainerColor = SurfaceVariant
        ),
        trailingIcon = {
            Text(
                "${value.length}/$maxLength",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
        }
    )
}

@Composable
private fun DeviceList(
    title: String,
    devices: List<DevicePreset>,
    selectedPreset: DevicePreset?,
    rotationPresets: List<DevicePreset>,
    showBatchSelect: Boolean,
    selectedForBatch: Set<DevicePreset>,
    onPresetSelected: (DevicePreset) -> Unit,
    onBatchToggle: (DevicePreset) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = OnSurface,
                    modifier = Modifier.weight(1f)
                )
                
                if (showBatchSelect) {
                    val allSelected = devices.all { it in selectedForBatch }
                    TextButton(
                        onClick = {
                            if (allSelected) {
                                devices.forEach { onBatchToggle(it) }
                            } else {
                                devices.filter { it !in selectedForBatch }.forEach { onBatchToggle(it) }
                            }
                        }
                    ) {
                        Text(
                            if (allSelected) "Deselect All" else "Select All",
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent
                        )
                    }
                }
                
                Text(
                    "${devices.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
                )
                
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = OnSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Divider(color = SurfaceElevated)
                    devices.forEach { device ->
                        DeviceItem(
                            device = device,
                            isSelected = device == selectedPreset,
                            isInRotation = device in rotationPresets,
                            showCheckbox = showBatchSelect,
                            isChecked = device in selectedForBatch,
                            onSelect = { onPresetSelected(device) },
                            onCheckToggle = { onBatchToggle(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: DevicePreset,
    isSelected: Boolean,
    isInRotation: Boolean,
    showCheckbox: Boolean,
    isChecked: Boolean,
    onSelect: () -> Unit,
    onCheckToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (showCheckbox) onCheckToggle() else onSelect()
            }
            .background(
                if (isSelected && !showCheckbox) Accent.copy(alpha = 0.1f)
                else if (isChecked) Accent.copy(alpha = 0.05f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheckbox) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { onCheckToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Accent,
                    uncheckedColor = OnSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Accent,
                    unselectedColor = OnSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Text(
            device.name,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        if (isInRotation && !showCheckbox) {
            Icon(
                Icons.Default.Check,
                contentDescription = "In rotation",
                tint = RotationActive,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
