package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class BatteryCompatibilityInfo(
    val manufacturerName: String,
    val isIgnoringBatteryOptimizations: Boolean,
    val areNotificationsEnabled: Boolean,
    val recommendationTitle: String,
    val steps: List<String>
)

object BatteryOptimizationHelper {

    fun getCompatibilityInfo(context: Context): BatteryCompatibilityInfo {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val rawManufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val manufacturer = rawManufacturer.lowercase()
        val displayManufacturer = rawManufacturer.ifBlank { "Android" }
            .replaceFirstChar { it.uppercase() }

        val (title, steps) = when {
            manufacturer.contains("samsung") -> Pair(
                "Samsung One UI Battery Guide",
                listOf(
                    "Open App Info → Battery and select 'Unrestricted'.",
                    "Go to Device Care → Battery → Background usage limits and make sure Radio Symphony is NOT in Sleeping or Deep Sleeping apps.",
                    "Keep Media Playback notifications enabled so Android preserves the foreground audio service."
                )
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> Pair(
                "Xiaomi / HyperOS / MIUI Battery Guide",
                listOf(
                    "Open App Info → Battery saver and select 'No restrictions'.",
                    "Enable 'Autostart' for Radio Symphony if you resume playback from Bluetooth or car head units.",
                    "In Recent Apps, pull down or lock Radio Symphony if your ROM aggressively clears background services."
                )
            )
            manufacturer.contains("oneplus") || manufacturer.contains("oppo") || manufacturer.contains("realme") -> Pair(
                "OxygenOS / ColorOS / Realme UI Guide",
                listOf(
                    "Open App Info → Battery usage and enable 'Allow background activity'.",
                    "Disable 'Pause app activity if unused' for uninterrupted long listening sessions.",
                    "Ensure Lock Screen Media Controls are enabled under Sound & Vibration."
                )
            )
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> Pair(
                "Funtouch OS / OriginOS Battery Guide",
                listOf(
                    "Open App Info → Battery and enable 'High background power consumption'.",
                    "Allow Radio Symphony to run in the background under iManager → App Manager.",
                    "Keep playback notifications allowed for lock-screen and headset controls."
                )
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> Pair(
                "EMUI / MagicOS Battery Guide",
                listOf(
                    "Open Settings → Battery → App launch → Radio Symphony.",
                    "Switch to 'Manage manually' and enable Auto-launch, Secondary launch, and Run in background.",
                    "Keep notifications active so foreground media playback continues when screen locks."
                )
            )
            else -> Pair(
                "Standard Android Background Audio Guide",
                listOf(
                    "Open App Battery Settings and select 'Unrestricted' for continuous screen-off streaming.",
                    "Keep Media Playback notifications enabled so Android treats Radio Symphony as an active foreground media service.",
                    "Bluetooth headsets, car audio, and wired headphones can control Play/Pause and switch favourite stations directly."
                )
            )
        }

        return BatteryCompatibilityInfo(
            manufacturerName = displayManufacturer,
            isIgnoringBatteryOptimizations = isUnrestricted,
            areNotificationsEnabled = notificationsEnabled,
            recommendationTitle = title,
            steps = steps
        )
    }

    /**
     * Opens system Battery Optimization settings list (Google Play Policy compliant —
     * does not require restricted REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission).
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val batteryListIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(batteryListIntent)
        }.isSuccess

        if (!launched) {
            openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) openAppDetailsSettings(context)
    }
}
