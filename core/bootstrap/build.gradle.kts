plugins {
    id("novaterm.android.library")
}

android {
    namespace = "com.novaterm.core.bootstrap"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
