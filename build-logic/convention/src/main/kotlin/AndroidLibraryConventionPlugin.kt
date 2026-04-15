import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        // AGP 9.x has built-in Kotlin support — no need for kotlin-android plugin
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            val compileSdkVersion = property("novaterm.compileSdk").toString().toInt()
            val minSdkVersion = property("novaterm.minSdk").toString().toInt()

            compileSdk = compileSdkVersion
            defaultConfig.minSdk = minSdkVersion
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            compileOptions {
                sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
            }
        }

        // Kotlin compiler options are now configured via AGP's built-in Kotlin support
        tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java) {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
}