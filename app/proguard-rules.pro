# === JNI native methods ===
-keepclasseswithmembernames class * {
    native <methods>;
}

# === Termux terminal core (used via reflection and JNI callbacks) ===
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }

# === Keep TerminalSession callback interfaces ===
# TerminalView uses TerminalSession.SessionChangedCallback via interface
-keep interface com.termux.terminal.TerminalSessionClient { *; }

# === Kotlin coroutines ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# === DataStore ===
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# === NovaTerm model/data classes (used by DataStore, SharedPreferences, enum valueOf) ===
-keep class com.novaterm.core.common.model.TerminalConfig { *; }
-keep class com.novaterm.core.common.model.SessionInfo { *; }
-keep class com.novaterm.core.common.model.TerminalDimensions { *; }
-keep class com.novaterm.core.common.model.SessionStatus { *; }
-keep class com.novaterm.core.common.model.ColorScheme { *; }
-keep class com.novaterm.core.common.util.OpResult { *; }
-keep class com.novaterm.core.common.util.OpResult$* { *; }
-keep class com.novaterm.feature.oemcompat.detection.OemBrand { *; }
-keep class com.novaterm.feature.oemcompat.detection.OemInfo { *; }
-keep class com.novaterm.feature.settings.data.TerminalPreferences { *; }

# === Compose ===
# Compose compiler handles most of this, but keep @Composable metadata
-keep class androidx.compose.runtime.** { *; }
