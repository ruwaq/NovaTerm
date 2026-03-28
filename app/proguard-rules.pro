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

# === Compose ===
# Compose compiler handles most of this, but keep @Composable metadata
-keep class androidx.compose.runtime.** { *; }
