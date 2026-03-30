plugins {
    id("novaterm.android.library")
}

android {
    namespace = "com.novaterm.core.llm"
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(project(":core:common"))
    implementation(project(":core:session"))

    // LiteRT / TFLite is loaded via reflection at runtime.
    // The app module provides the actual runtime dependency when available.
    // This keeps core:llm lightweight and compilable on any platform.

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
