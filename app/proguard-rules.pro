# Terminal JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep terminal session classes used via reflection
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }
