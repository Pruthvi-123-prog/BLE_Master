package com.blemaster.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.blemaster.app.BLEMasterApplication
import com.blemaster.app.MainActivity
import com.blemaster.app.R
import com.blemaster.app.ble.AdvertiseError
import com.blemaster.app.ble.BleAdvertiserManager
import com.blemaster.app.ble.TxPowerLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for continuous BLE advertising.
 * Maintains a persistent notification while broadcasting is active.
 */
class BleAdvertiseService : Service() {

    companion object {
        private const val TAG = "BleAdvertiseService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.blemaster.app.action.START"
        const val ACTION_STOP = "com.blemaster.app.action.STOP"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_POWER_LEVEL = "extra_power_level"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
    }

    private val binder = LocalBinder()
    private lateinit var advertiserManager: BleAdvertiserManager
    private var broadcastJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _currentMessage = MutableStateFlow("")
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()

    private val _errorState = MutableStateFlow<AdvertiseError?>(null)
    val errorState: StateFlow<AdvertiseError?> = _errorState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): BleAdvertiseService = this@BleAdvertiseService
    }

    override fun onCreate() {
        super.onCreate()
        advertiserManager = BleAdvertiserManager(this)
        Log.d(TAG, "Service created")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                val powerLevelOrdinal = intent.getIntExtra(EXTRA_POWER_LEVEL, TxPowerLevel.MEDIUM.ordinal)
                val powerLevel = TxPowerLevel.entries[powerLevelOrdinal]
                val intervalMs = intent.getIntExtra(EXTRA_INTERVAL_MS, 1000)

                startBroadcasting(message, powerLevel, intervalMs)
            }
            ACTION_STOP -> {
                stopBroadcasting()
                stopSelf()
            }
        }
        return START_STICKY
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startBroadcasting(message: String, powerLevel: TxPowerLevel, intervalMs: Int) {
        if (message.isEmpty()) {
            Log.w(TAG, "Cannot broadcast empty message")
            return
        }

        _currentMessage.value = message
        _isBroadcasting.value = true
        _errorState.value = null

        // Start foreground service with notification
        val notification = createNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start the advertising
        advertiserManager.startAdvertising(message, powerLevel, intervalMs) { error ->
            _errorState.value = error
            Log.e(TAG, "Advertising error: $error")
        }

        // Start broadcast scheduler loop for continuous re-advertising
        broadcastJob?.cancel()
        broadcastJob = serviceScope.launch {
            while (isActive && _isBroadcasting.value) {
                delay(intervalMs.toLong())
                // The BLE stack handles continuous advertising, but we monitor here
                if (!advertiserManager.isAdvertising.value && _isBroadcasting.value) {
                    Log.d(TAG, "Re-starting advertising...")
                    advertiserManager.startAdvertising(message, powerLevel, intervalMs) { error ->
                        _errorState.value = error
                    }
                }
            }
        }

        Log.d(TAG, "Broadcasting started: $message")
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null

        advertiserManager.stopAdvertising()
        _isBroadcasting.value = false
        _currentMessage.value = ""
        _errorState.value = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "Broadcasting stopped")
    }

    private fun createNotification(message: String): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BleAdvertiseService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayMessage = if (message.length > 20) {
            message.take(20) + "…"
        } else {
            message
        }

        return NotificationCompat.Builder(this, BLEMasterApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_message, displayMessage))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingContentIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop_action),
                pendingStopIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
