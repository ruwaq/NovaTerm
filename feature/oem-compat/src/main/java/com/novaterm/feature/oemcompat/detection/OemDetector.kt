package com.novaterm.feature.oemcompat.detection

import android.content.ComponentName
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
    NOTHING("Nothing", 3),
    TECNO("Tecno", 4),
    INFINIX("Infinix", 4),
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
    val androidVersionName: String,
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
            manufacturer.contains("nothing") -> OemBrand.NOTHING
            manufacturer.contains("tecno") -> OemBrand.TECNO
            manufacturer.contains("infinix") -> OemBrand.INFINIX
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
            androidVersionName = sdkToVersionName(Build.VERSION.SDK_INT),
            isBatteryOptimized = isOptimized,
        )
    }

    fun needsBatteryWhitelist(oemInfo: OemInfo): Boolean =
        oemInfo.isBatteryOptimized && oemInfo.brand.aggressiveness >= 3

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Returns an intent that opens the OEM-specific autostart / app management
     * settings directly, or null if no known deep-link is available.
     */
    fun autostartSettingsIntent(context: Context, brand: OemBrand): Intent? {
        val pkg = context.packageName
        val intents: List<Intent> = when (brand) {
            OemBrand.XIAOMI -> listOf(
                // HyperOS 2 / MIUI 15+ autostart manager
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                ),
                // Fallback: app info in MIUI Security
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.appmanager.AppManagerMainActivity"
                    )
                ),
            )
            OemBrand.SAMSUNG -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                ),
            )
            OemBrand.HUAWEI -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                ),
            )
            OemBrand.OPPO, OemBrand.REALME -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                ),
            )
            OemBrand.VIVO -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                ),
            )
            OemBrand.TECNO, OemBrand.INFINIX -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.transsion.phonemanager",
                        "com.transsion.phonemanager.module.autostart.AutoStartActivity"
                    )
                ),
            )
            else -> emptyList()
        }

        // Return the first intent whose target activity is resolvable.
        return intents.firstOrNull { intent ->
            intent.resolveActivity(context.packageManager) != null
        }
    }

    /**
     * Per-brand step-by-step instructions.
     *
     * Xiaomi instructions target HyperOS 2 (Android 15+ / MIUI 15+).
     * Older MIUI paths may differ slightly but the HyperOS flow is the primary target.
     */
    fun getInstructions(brand: OemBrand): List<String> = when (brand) {
        OemBrand.XIAOMI -> listOf(
            "Settings > Apps > App management > NovaTerm",
            "Tap 'Autostart' and enable it",
            "Go back to Settings > Battery > NovaTerm",
            "Select 'No restrictions'",
            "In recent apps, swipe down on NovaTerm's card to lock it",
        )
        OemBrand.SAMSUNG -> listOf(
            "Settings > Battery and device care > Battery",
            "Background usage limits > Never sleeping apps > Add NovaTerm",
            "In recent apps, long-press NovaTerm > Lock this app",
        )
        OemBrand.HUAWEI -> listOf(
            "Settings > Battery > App launch",
            "Find NovaTerm > Switch to 'Manage manually'",
            "Enable 'Auto-launch', 'Secondary launch', and 'Run in background'",
        )
        OemBrand.OPPO, OemBrand.REALME -> listOf(
            "Settings > Battery > More battery settings",
            "Optimize battery use > NovaTerm > Don't optimize",
            "Settings > App management > App list > NovaTerm > Auto-launch > Allow",
        )
        OemBrand.ONEPLUS -> listOf(
            "Settings > Battery > Battery optimization",
            "NovaTerm > Don't optimize",
            "Settings > Apps > NovaTerm > Battery > Unrestricted",
        )
        OemBrand.VIVO -> listOf(
            "Settings > Battery > Background power consumption management",
            "Find NovaTerm > Don't restrict background power consumption",
            "Settings > Apps > Autostart > Enable NovaTerm",
        )
        OemBrand.NOTHING -> listOf(
            "Settings > Battery > Battery optimization",
            "NovaTerm > Don't optimize",
            "Settings > Apps > NovaTerm > Battery > Unrestricted",
        )
        OemBrand.TECNO, OemBrand.INFINIX -> listOf(
            "Phone Manager > Auto-start management > Enable NovaTerm",
            "Settings > Battery > Battery optimization > NovaTerm > Don't optimize",
            "Lock NovaTerm in recent apps (swipe down on card)",
        )
        OemBrand.MEIZU -> listOf(
            "Settings > Battery > Power saving mode",
            "NovaTerm > Keep running in background",
        )
        OemBrand.ASUS -> listOf(
            "Settings > Battery > Auto-start Manager",
            "NovaTerm > Allow",
        )
        else -> listOf(
            "Settings > Battery > Battery optimization",
            "NovaTerm > Don't optimize",
        )
    }

    /** Maps SDK_INT to a human-readable Android version name. */
    private fun sdkToVersionName(sdk: Int): String = when (sdk) {
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        else -> if (sdk > 36) "${16 + (sdk - 36)}" else sdk.toString()
    }
}
