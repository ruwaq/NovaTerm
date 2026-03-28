# Consumer ProGuard rules for terminal-emulator

# Keep all JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep the full terminal API surface - used by terminal-view and session modules
-keep class com.termux.terminal.** { *; }
