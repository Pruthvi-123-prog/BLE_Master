# BLE Master ProGuard Rules

# Keep BLE related classes
-keep class android.bluetooth.** { *; }
-keep class android.bluetooth.le.** { *; }

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Compose
-dontwarn androidx.compose.**
