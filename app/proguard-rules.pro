# === JNI native methods ===
-keepclasseswithmembernames class * {
    native <methods>;
}

# === NovaTerm terminal core (used via reflection and JNI callbacks) ===
-keep class com.novaterm.terminal.** { *; }
-keep class com.novaterm.view.** { *; }

# === Keep TerminalSession callback interfaces ===
# TerminalView uses TerminalSession.SessionChangedCallback via interface
-keep interface com.novaterm.terminal.TerminalSessionClient { *; }

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

# === Bootstrap (JNI + sealed state class) ===
-keep class com.novaterm.core.bootstrap.NativeBootstrap { *; }
-keep class com.novaterm.core.bootstrap.BootstrapInstaller$State { *; }
-keep class com.novaterm.core.bootstrap.BootstrapInstaller$State$* { *; }

# === BlockStore (SQLite persistence) ===
-keep class com.novaterm.core.session.persistence.db.BlockStore { *; }
-keep class com.novaterm.core.session.persistence.db.BlockStoreDb { *; }
-keep class com.novaterm.core.session.persistence.db.*Record { *; }
-keep class com.novaterm.core.session.persistence.db.StoreStats { *; }

# === Rust JNI bridge (Phase 2) ===
-keep class com.novaterm.core.session.engine.NativeTerminal { *; }
-keep class com.novaterm.core.session.engine.NativeSession { *; }
-keep class com.novaterm.core.session.engine.NativeRenderer { *; }
-keep class com.novaterm.core.session.engine.RustEngine { *; }
-keep class com.novaterm.core.session.engine.RustEngine$Factory { *; }
-keep class com.novaterm.core.session.engine.RustSessionEngine { *; }
-keep class com.novaterm.core.session.engine.RustSessionEngine$Companion { *; }
-keep class com.novaterm.core.session.engine.GpuRenderer { *; }
-keep class com.novaterm.core.common.model.CursorPosition { *; }

# === Compose ===
# Compose compiler handles most of this, but keep @Composable metadata
-keep class androidx.compose.runtime.** { *; }

# === LiteRT (on-device LLM inference, loaded via reflection) ===
-keep class com.google.ai.edge.litert.** { *; }
-keepclassmembers class com.google.ai.edge.litert.** {
    public <methods>;
    public <fields>;
}

# === LiteRT-LM (NPU/GPU accelerated inference, loaded via reflection) ===
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** {
    public <methods>;
    public <fields>;
    <init>(...);
}

# === MediaPipe Tasks GenAI (GGUF inference, loaded via reflection) ===
-keep class com.google.mediapipe.tasks.genai.** { *; }
-keepclassmembers class com.google.mediapipe.tasks.genai.** {
    public <methods>;
    public <fields>;
}

# === Kotlinx Serialization (used by Ktor/MCP JSON) ===
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.novaterm.**$$serializer { *; }
-keepclassmembers class com.novaterm.** {
    *** Companion;
}
-keepclasseswithmembers class com.novaterm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# === Ktor (MCP server) ===
# java.lang.management not available on Android
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# === MediaPipe / LiteRT protobuf internals (not on Android classpath) ===
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField

# === MediaPipe framework image classes (optional vision dependency, not used) ===
-dontwarn com.google.mediapipe.framework.image.BitmapExtractor
-dontwarn com.google.mediapipe.framework.image.ByteBufferExtractor
-dontwarn com.google.mediapipe.framework.image.MPImage
-dontwarn com.google.mediapipe.framework.image.MPImageProperties
-dontwarn com.google.mediapipe.framework.image.MediaImageExtractor

# === AutoValue annotation processor internals (compile-time only) ===
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# === Google Tink / ErrorProne annotations (compile-only, not on Android) ===
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
