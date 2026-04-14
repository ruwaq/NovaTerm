plugins {
    id("novaterm.android.library")
}

android {
    namespace = "com.novaterm.core.mcp"
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":core:common"))
    implementation(project(":core:session"))

    // MCP SDK (server-side only)
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.10.0")

    // Ktor CIO (embedded HTTP server, pure Kotlin, no native deps)
    implementation("io.ktor:ktor-server-cio:3.1.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("io.ktor:ktor-server-sse:3.1.2")

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20250107") // org.json for JVM unit tests

    // Property-based testing (generates 1000s of random inputs)
    testImplementation("io.kotest:kotest-property-jvm:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
}