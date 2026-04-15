package com.novaterm.app.mcp

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import com.novaterm.core.mcp.security.SecurityPolicy.RiskLevel
import com.novaterm.core.mcp.tool.InputSchema
import com.novaterm.core.mcp.tool.McpTool
import com.novaterm.core.mcp.tool.PropertySchema
import com.novaterm.core.mcp.tool.ToolResult

/**
 * Device API MCP tools — expose Android device capabilities to AI agents.
 *
 * Unlike Termux's separate APK approach, NovaTerm integrates device APIs directly
 * as MCP tools. This means:
 * - No separate install required
 * - AI agents can access device features via natural language
 * - All access goes through the approval system (DANGEROUS tools need user approval)
 */

// ── Clipboard ──────────────────────────────────────────────

class ClipboardReadTool(private val context: Context) : McpTool {
    override val name = "clipboard_read"
    override val description = "Read text from the Android clipboard."
    override val riskLevel = RiskLevel.MODERATE
    override val inputSchema = InputSchema(properties = emptyMap(), required = emptyList())

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ToolResult.Success("")
        val text = clip.getItemAt(0)?.text?.toString() ?: ""
        return ToolResult.Success(text)
    }
}

class ClipboardWriteTool(private val context: Context) : McpTool {
    override val name = "clipboard_write"
    override val description = "Write text to the Android clipboard."
    override val riskLevel = RiskLevel.MODERATE
    override val inputSchema = InputSchema(
        properties = mapOf("text" to PropertySchema("string", "Text to copy to clipboard")),
        required = listOf("text"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val text = arguments["text"] as? String ?: return ToolResult.Error("Missing required parameter: text")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NovaTerm", text))
        return ToolResult.Success("Copied ${text.length} characters to clipboard")
    }
}

// ── Battery ─────────────────────────────────────────────────

class BatteryStatusTool(private val context: Context) : McpTool {
    override val name = "battery_status"
    override val description = "Get battery level, charging state, and battery health information."
    override val riskLevel = RiskLevel.SAFE
    override val inputSchema = InputSchema(properties = emptyMap(), required = emptyList())

    override suspend fun execute(arguments: Map<String,Any?>): ToolResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return ToolResult.Success(buildString {
            appendLine("Battery level: $level%")
            appendLine("Charging: $charging")
            appendLine("Power source: ${if (charging) "AC/Battery" else "Battery"}")
        })
    }
}

// ── Vibration ───────────────────────────────────────────────

class VibrateTool(private val context: Context) : McpTool {
    override val name = "vibrate"
    override val description = "Vibrate the device for feedback. Duration in milliseconds (max 1000)."
    override val riskLevel = RiskLevel.MODERATE
    override val inputSchema = InputSchema(
        properties = mapOf("duration_ms" to PropertySchema("integer", "Duration in ms (default: 200, max: 1000)", default = 200)),
        required = emptyList(),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val duration = (arguments["duration_ms"] as? Number)?.toLong()?.coerceIn(1, 1000) ?: 200L
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        return ToolResult.Success("Vibrated for ${duration}ms")
    }
}

// ── Torch / Flashlight ──────────────────────────────────────

class TorchTool(private val context: Context) : McpTool {
    override val name = "torch"
    override val description = "Toggle the device flashlight on or off."
    override val riskLevel = RiskLevel.MODERATE
    override val inputSchema = InputSchema(
        properties = mapOf("on" to PropertySchema("boolean", "true to turn on, false to turn off")),
        required = listOf("on"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val on = arguments["on"] as? Boolean ?: return ToolResult.Error("Missing required parameter: on")
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return ToolResult.Error("No back camera found")
        cameraManager.setTorchMode(cameraId, on)
        return ToolResult.Success("Torch ${if (on) "on" else "off"}")
    }
}

// ── Device Info ─────────────────────────────────────────────

class DeviceInfoTool(private val context: Context) : McpTool {
    override val name = "device_info"
    override val description = "Get device information: model, Android version, screen size, etc."
    override val riskLevel = RiskLevel.SAFE
    override val inputSchema = InputSchema(properties = emptyMap(), required = emptyList())

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val displayMetrics = context.resources.displayMetrics
        return ToolResult.Success(buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Screen: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels} (${displayMetrics.density}x)")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
        })
    }
}

// ── Notification ────────────────────────────────────────────

class NotificationTool(private val context: Context) : McpTool {
    override val name = "device_notification"
    override val description = "Post a notification to the Android status bar."
    override val riskLevel = RiskLevel.MODERATE
    override val inputSchema = InputSchema(
        properties = mapOf(
            "title" to PropertySchema("string", "Notification title"),
            "text" to PropertySchema("string", "Notification text"),
        ),
        required = listOf("title", "text"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val title = arguments["title"] as? String ?: return ToolResult.Error("Missing: title")
        val text = arguments["text"] as? String ?: return ToolResult.Error("Missing: text")
        val channelId = "novaterm_device_api"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Device API", android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.novaterm.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
        return ToolResult.Success("Notification posted: $title")
    }
}

// ── WiFi Info ──────────────────────────────────────────────

class WifiInfoTool(private val context: Context) : McpTool {
    override val name = "wifi_info"
    override val description = "Get WiFi connection information (SSID, signal strength, link speed)."
    override val riskLevel = RiskLevel.SAFE
    override val inputSchema = InputSchema(properties = emptyMap(), required = emptyList())

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        return ToolResult.Success(buildString {
            appendLine("SSID: ${info.ssid}")
            appendLine("BSSID: ${info.bssid}")
            appendLine("Link speed: ${info.linkSpeed} Mbps")
            appendLine("Signal: ${WifiManager.calculateSignalLevel(info.rssi, 5)}/4")
            appendLine("IP: ${android.text.format.Formatter.formatIpAddress(info.ipAddress)}")
            appendLine("WiFi enabled: ${wifiManager.isWifiEnabled}")
        })
    }
}

// ── Settings ───────────────────────────────────────────────

class OpenSettingsTool(private val context: Context) : McpTool {
    override val name = "open_settings"
    override val description = "Open an Android settings screen. Options: wifi, bluetooth, display, sound, battery, apps, storage, about"
    override val riskLevel = RiskLevel.MODERATE
    override val inputSchema = InputSchema(
        properties = mapOf("screen" to PropertySchema("string", "Settings screen to open",
            enum = listOf("wifi", "bluetooth", "display", "sound", "battery", "apps", "storage", "about"))),
        required = listOf("screen"),
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val screen = arguments["screen"] as? String ?: return ToolResult.Error("Missing: screen")
        val intent = when (screen) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "about" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            else -> return ToolResult.Error("Unknown settings screen: $screen")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ToolResult.Success("Opened $screen settings")
    }
}

// ── Registration helper ────────────────────────────────────

object DeviceApiTools {
    private const val TAG = "NovaTerm"

    fun registerAll(registry: com.novaterm.core.mcp.tool.ToolRegistry, context: Context) {
        registry.registerAll(
            ClipboardReadTool(context),
            ClipboardWriteTool(context),
            BatteryStatusTool(context),
            VibrateTool(context),
            TorchTool(context),
            DeviceInfoTool(context),
            NotificationTool(context),
            WifiInfoTool(context),
            OpenSettingsTool(context),
        )
        Log.d(TAG, "Registered ${registry.size} device API MCP tools")
    }
}