plugins {
    id("novaterm.android.library")
}

android {
    namespace = "com.novaterm.core.session"
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
    testImplementation("org.json:json:20250107") // org.json for JVM tests
}
