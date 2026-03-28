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
    testImplementation(libs.junit)
}
