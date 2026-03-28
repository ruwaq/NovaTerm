plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.emulator"
    compileSdk = property("novaterm.compileSdk").toString().toInt()

    defaultConfig {
        minSdk = property("novaterm.minSdk").toString().toInt()
        consumerProguardFiles("proguard-rules.pro")

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Use prebuilt .so from jniLibs/ instead of ndk-build.
    // The .so is compiled with: clang -shared -target aarch64-linux-android30
    //   -std=c11 -Os -fPIC src/main/jni/termux.c
    // This allows building on ARM64 hosts (Termux) where ndk-build doesn't work.
    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
