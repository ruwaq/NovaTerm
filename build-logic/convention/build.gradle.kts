plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.plugins.android.application.toDep())
    compileOnly(libs.plugins.android.library.toDep())
    compileOnly(libs.plugins.kotlin.android.toDep())
    compileOnly(libs.plugins.kotlin.compose.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "novaterm.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "novaterm.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("kotlinJvm") {
            id = "novaterm.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
    }
}
