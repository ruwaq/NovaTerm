import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    namespace = "com.novaterm.app"
    compileSdk = property("novaterm.compileSdk").toString().toInt()

    defaultConfig {
        applicationId = "com.nvterm"
        minSdk = property("novaterm.minSdk").toString().toInt()
        targetSdk = property("novaterm.targetSdk").toString().toInt()
        versionCode = property("novaterm.versionCode").toString().toInt()
        versionName = property("novaterm.versionName").toString()

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    ndkVersion = property("novaterm.ndkVersion").toString()

    // Bootstrap native library: embeds bootstrap ZIP via .incbin assembly.
    // Only enabled when building on x86_64 (CI) where cmake/ninja can run.
    // On ARM64 (Termux), cmake is x86_64-only and can't execute.
    // The BootstrapInstaller handles the missing library gracefully.
    val cmakePath = file("src/main/cpp/CMakeLists.txt")
    val cmakeBin = file("${System.getenv("ANDROID_HOME") ?: ""}/cmake/3.22.1/bin/cmake")
    if (cmakePath.exists() && cmakeBin.exists() && cmakeBin.canExecute()) {
        externalNativeBuild {
            cmake {
                path = cmakePath
                version = "3.22.1"
            }
        }
    }

    packaging {
        jniLibs {
            // Don't compress .so files — allows mmap and efficient Play Store delta updates
            useLegacyPackaging = true
        }
    }

    // Don't double-compress the bootstrap ZIP inside the APK
    androidResources {
        noCompress += "zip"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                // CI / keystore.properties takes priority
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            } else {
                // Local dev: env vars required (no hardcoded passwords)
                storeFile = file("../release-keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProperties["storePassword"] as? String ?: throw IllegalStateException("KEYSTORE_PASSWORD env var or keystore.properties required")
                keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties["keyAlias"] as? String ?: "novaterm"
                keyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProperties["keyPassword"] as? String ?: throw IllegalStateException("KEY_PASSWORD env var or keystore.properties required")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:session"))
    implementation(project(":core:bootstrap"))
    implementation(project(":core:terminal-view"))
    implementation(project(":core:mcp"))
    implementation(project(":core:llm"))

    // Feature modules
    implementation(project(":feature:terminal"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:oem-compat"))
    implementation(project(":feature:agent"))

    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // On-device LLM inference (GGUF models via llama.cpp, optional feature)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.litertlm.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
}
