package com.novaterm.feature.oemcompat.detection

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class OemBrand(val displayName: String, val aggressiveness: Int) {
    XIAOMI("Xiaomi", 5),
    SAMSUNG("Samsung", 4),
    HUAWEI("Huawei", 5),
    OPPO("OPPO", 3),
    VIVO("Vivo", 3),
    ONEPLUS("OnePlus", 4),
    REALME("Realme", 3),
    MEIZU("Meizu", 4),
    ASUS("ASUS", 2),
    LENOVO("Lenovo", 2),
    MOTOROLA("Motorola", 1),
    GOOGLE("Google", 0),
    OTHER("Other", 1),
}

data class OemInfo(
    val brand: OemBrand,
    val manufacturer: String,
    val model: String,
    val androidVersion: Int,
    val isBatteryOptimized: Boolean,
)

object OemDetector {

    fun detect(context: Context): OemInfo {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> OemBrand.XIAOMI
            manufacturer.contains("samsung") -> OemBrand.SAMSUNG
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> OemBrand.HUAWEI
            manufacturer.contains("oppo") -> OemBrand.OPPO
            manufacturer.contains("vivo") -> OemBrand.VIVO
            manufacturer.contains("oneplus") -> OemBrand.ONEPLUS
            manufacturer.contains("realme") -> OemBrand.REALME
            manufacturer.contains("meizu") -> OemBrand.MEIZU
            manufacturer.contains("asus") -> OemBrand.ASUS
            manufacturer.contains("lenovo") -> OemBrand.LENOVO
            manufacturer.contains("motorola") -> OemBrand.MOTOROLA
            manufacturer.contains("google") -> OemBrand.GOOGLE
            else -> OemBrand.OTHER
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)

        return OemInfo(
            brand = brand,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.SDK_INT,
            isBatteryOptimized = isOptimized,
        )
    }

    fun needsBatteryWhitelist(oemInfo: OemInfo): Boolean =
        oemInfo.isBatteryOptimized && oemInfo.brand.aggressiveness >= 3

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun getInstructions(brand: OemBrand): List<String> = when (brand) {
        OemBrand.XIAOMI -> listOf(
            "Settings > Apps > Manage Apps > NovaTerm",
            "Enable 'Autostart'",
            "Battery Saver > No restrictions",
            "Lock app in recent apps (swipe down on card)",
        )
        OemBrand.SAMSUNG -> listOf(
            "Settings > Device Care > Battery",
            "App power management > NovaTerm > Never sleeping",
            "Lock app in recent apps (long press > Lock)",
        )
        OemBrand.HUAWEI -> listOf(
            "Settings > Battery > App launch",
            "NovaTerm > Manage manually > Enable all three toggles",
            "Settings > Advanced > Battery > Protected apps > Enable",
        )
        OemBrand.OPPO, OemBrand.REALME -> listOf(
            "Settings > Battery > More battery settings",
            "Optimize battery use > NovaTerm > Don't optimize",
            "Settings > App management > Auto-launch > Enable",
        )
        OemBrand.ONEPLUS -> listOf(
            "Settings > Battery > Battery optimization",
            "NovaTerm > Don't optimize",
        )
        OemBrand.VIVO -> listOf(
            "Settings > Battery > Background power consumption",
            "NovaTerm > Don't restrict",
        )
        OemBrand.MEIZU -> listOf(
            "Settings > Battery > Power saving mode",
            "NovaTerm > Keep running in background",
        )
        OemBrand.ASUS -> listOf(
            "Settings > Power Management > Auto-start Manager",
            "NovaTerm > Allow",
        )
        else -> listOf(
            "Settings > Battery > Battery optimization",
            "NovaTerm > Don't optimize",
        )
    }
}
