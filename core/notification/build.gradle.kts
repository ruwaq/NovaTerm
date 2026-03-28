plugins {
    id("novaterm.android.library")
}

android {
    namespace = "com.novaterm.core.notification"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
}
