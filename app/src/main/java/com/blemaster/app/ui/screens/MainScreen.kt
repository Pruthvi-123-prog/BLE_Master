package com.blemaster.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blemaster.app.R
import com.blemaster.app.ble.AdvertiseError
import com.blemaster.app.ble.protocols.DevicePreset
import com.blemaster.app.ble.protocols.ProtocolType
import com.blemaster.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    message: String,
    onMessageChange: (String) -> Unit,
    byteCount: Int,
    maxBytes: Int,
    isBroadcasting: Boolean,
    selectedProtocol: ProtocolType,
    selectedPreset: DevicePreset?,
    rotationEnabled: Boolean,
    rotationPresetsCount: Int,
    currentPresetName: String?,
    onToggleBroadcast: () -> Unit,
    errorState: AdvertiseError?,
    onDismissError: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToPresets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canBroadcast = when (selectedProtocol) {
        ProtocolType.CUSTOM -> message.isNotBlank() && byteCount <= maxBytes
        else -> selectedPreset != null || rotationEnabled
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = White
                        )
                        Text(
                            text = "Pro Edition",
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Scan",
                            tint = Scanning
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = OnSurfaceVariant
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Protocol Mode Card
            ProtocolModeCard(
                selectedProtocol = selectedProtocol,
                selectedPreset = selectedPreset,
                rotationEnabled = rotationEnabled,
                rotationPresetsCount = rotationPresetsCount,
                currentPresetName = currentPresetName,
                isBroadcasting = isBroadcasting,
                onClick = onNavigateToPresets
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Conditional content based on protocol
            if (selectedProtocol == ProtocolType.CUSTOM) {
                // Message input for custom protocol
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.enter_message)) },
                    placeholder = { Text("Enter text to broadcast...") },
                    enabled = !isBroadcasting,
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = SurfaceElevated,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = OnSurfaceVariant,
                        cursorColor = Accent,
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        disabledTextColor = OnSurfaceVariant,
                        disabledBorderColor = SurfaceElevated,
                        focusedContainerColor = Surface,
                        unfocusedContainerColor = Surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Byte counter
                val byteCountColor = when {
                    byteCount > maxBytes -> Error
                    byteCount > maxBytes * 0.8 -> AccentVariant
                    else -> OnSurfaceVariant
                }

                Text(
                    text = stringResource(R.string.byte_counter, byteCount, maxBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = byteCountColor,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Broadcast toggle button
            BroadcastToggleButton(
                isBroadcasting = isBroadcasting,
                isEnabled = canBroadcast || isBroadcasting,
                selectedProtocol = selectedProtocol,
                onClick = onToggleBroadcast
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status display
            StatusDisplay(
                isBroadcasting = isBroadcasting,
                currentPresetName = currentPresetName,
                rotationEnabled = rotationEnabled
            )

            Spacer(modifier = Modifier.weight(1f))

            // Error display
            if (errorState != null) {
                ErrorCard(
                    error = errorState,
                    onDismiss = onDismissError
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProtocolModeCard(
    selectedProtocol: ProtocolType,
    selectedPreset: DevicePreset?,
    rotationEnabled: Boolean,
    rotationPresetsCount: Int,
    currentPresetName: String?,
    isBroadcasting: Boolean,
    onClick: () -> Unit
) {
    val protocolColor = when (selectedProtocol) {
        ProtocolType.CUSTOM -> CustomColor
        ProtocolType.FAST_PAIR -> FastPairColor
        ProtocolType.APPLE_CONTINUITY -> AppleColor
        ProtocolType.SWIFT_PAIR -> SwiftPairColor
    }
    
    val protocolName = when (selectedProtocol) {
        ProtocolType.CUSTOM -> "Custom Message"
        ProtocolType.FAST_PAIR -> "Google Fast Pair"
        ProtocolType.APPLE_CONTINUITY -> "Apple Continuity"
        ProtocolType.SWIFT_PAIR -> "Microsoft Swift Pair"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBroadcasting, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Protocol indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(protocolColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (selectedProtocol) {
                        ProtocolType.CUSTOM -> Icons.Default.Edit
                        ProtocolType.FAST_PAIR -> Icons.Default.Bluetooth
                        ProtocolType.APPLE_CONTINUITY -> Icons.Default.PhoneIphone
                        ProtocolType.SWIFT_PAIR -> Icons.Default.Computer
                    },
                    contentDescription = null,
                    tint = protocolColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = protocolName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
                
                Text(
                    text = when {
                        rotationEnabled -> "🔄 Rotation: $rotationPresetsCount presets"
                        selectedPreset != null -> selectedPreset.name
                        selectedProtocol == ProtocolType.CUSTOM -> "Tap to select protocol"
                        else -> "No preset selected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rotationEnabled) RotationActive else OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (!isBroadcasting) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Select protocol",
                    tint = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BroadcastToggleButton(
    isBroadcasting: Boolean,
    isEnabled: Boolean,
    selectedProtocol: ProtocolType,
    onClick: () -> Unit
) {
    val protocolColor = when (selectedProtocol) {
        ProtocolType.CUSTOM -> CustomColor
        ProtocolType.FAST_PAIR -> FastPairColor
        ProtocolType.APPLE_CONTINUITY -> AppleColor
        ProtocolType.SWIFT_PAIR -> SwiftPairColor
    }
    
    val buttonColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> OnSurfaceDim
            isBroadcasting -> Error
            else -> protocolColor
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "buttonColor"
    )
    
    // Smooth scale animation on press
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale"
    )
    
    // Subtle continuous glow when broadcasting
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.2f,
        targetValue = if (isBroadcasting) 0.5f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    // Progress rotation when broadcasting
    val progressRotation by rememberInfiniteTransition(label = "progress").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressRotation"
    )

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring - animated when broadcasting
        if (isBroadcasting) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                buttonColor.copy(alpha = glowAlpha),
                                buttonColor.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }
        
        // Progress ring when broadcasting
        if (isBroadcasting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(155.dp)
                    .graphicsLayer { rotationZ = progressRotation },
                strokeWidth = 2.dp,
                color = buttonColor.copy(alpha = 0.6f),
                trackColor = Color.Transparent
            )
        }
        
        // Subtle border ring when not broadcasting
        if (!isBroadcasting && isEnabled) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = buttonColor.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
        }

        // Main button - Surface based for no ripple
        Surface(
            onClick = onClick,
            enabled = isEnabled,
            modifier = Modifier
                .size(140.dp)
                .scale(pressScale)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            isPressed = event.changes.any { it.pressed }
                        }
                    }
                },
            shape = CircleShape,
            color = buttonColor,
            shadowElevation = if (isBroadcasting) 16.dp else 8.dp,
            tonalElevation = 4.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Icon with smooth transition
                androidx.compose.animation.AnimatedContent(
                    targetState = isBroadcasting,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f))
                            .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.8f))
                    },
                    label = "iconTransition"
                ) { broadcasting ->
                    Icon(
                        imageVector = if (broadcasting) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (broadcasting) {
                            stringResource(R.string.stop_broadcast)
                        } else {
                            stringResource(R.string.start_broadcast)
                        },
                        modifier = Modifier.size(52.dp),
                        tint = if (isEnabled) Black else OnSurfaceDim
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDisplay(
    isBroadcasting: Boolean,
    currentPresetName: String?,
    rotationEnabled: Boolean
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            isBroadcasting && rotationEnabled -> RotationActive
            isBroadcasting -> Broadcasting
            else -> Idle
        },
        animationSpec = tween(300),
        label = "statusColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Animated indicator dot
            val alpha by rememberInfiniteTransition(label = "blink").animateFloat(
                initialValue = 1f,
                targetValue = if (isBroadcasting) 0.3f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = if (isBroadcasting) alpha else 1f))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = when {
                    isBroadcasting && rotationEnabled -> "Rotating..."
                    isBroadcasting -> stringResource(R.string.status_broadcasting)
                    else -> stringResource(R.string.status_stopped)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )
        }
        
        // Current preset name when broadcasting
        if (isBroadcasting && currentPresetName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentPresetName,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(
    error: AdvertiseError,
    onDismiss: () -> Unit
) {
    val errorMessage = when (error) {
        is AdvertiseError.NotSupported -> stringResource(R.string.error_ble_not_supported)
        is AdvertiseError.BluetoothDisabled -> stringResource(R.string.error_bluetooth_disabled)
        is AdvertiseError.DataTooLarge -> stringResource(R.string.error_data_too_large)
        is AdvertiseError.TooManyAdvertisers -> stringResource(R.string.error_too_many_advertisers)
        is AdvertiseError.InternalError -> stringResource(R.string.error_internal)
        is AdvertiseError.FeatureUnsupported -> stringResource(R.string.error_feature_unsupported)
        is AdvertiseError.AlreadyStarted -> "Already broadcasting"
        is AdvertiseError.PermissionDenied -> stringResource(R.string.permission_denied)
        is AdvertiseError.Unknown -> stringResource(R.string.error_unknown, error.errorCode)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Error.copy(alpha = 0.15f)
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
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = Error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Error,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Error
                )
            }
        }
    }
}
