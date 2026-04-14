plugins {
    id("novaterm.android.library")
}

android {
    namespace = "com.novaterm.core.session"
    compileSdk = property("novaterm.compileSdk").toString().toInt()

    defaultConfig {
        minSdk = property("novaterm.minSdk").toString().toInt()
        targetSdk = property("novaterm.targetSdk").toString().toInt()
    }
}

dependencies {
    api(project(":core:common"))
    implementation(project(":core:terminal-emulator"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation("org.json:json:20250107") // org.json for JVM tests
}
