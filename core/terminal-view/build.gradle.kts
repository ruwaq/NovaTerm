plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.view"
    compileSdk = property("novaterm.compileSdk").toString().toInt()

    defaultConfig {
        minSdk = property("novaterm.minSdk").toString().toInt()
        consumerProguardFiles("proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:terminal-emulator"))
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
