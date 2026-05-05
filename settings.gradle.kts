pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NovaTerm"

// App shell - thin orchestrator
include(":app")

// Core modules - pure logic, no UI
include(":core:common")
include(":core:terminal-emulator")
include(":core:terminal-view")
include(":core:session")
include(":core:bootstrap")
// core:notification — reserved for Phase 2 (not used in Phase 1)
include(":core:config")
// Feature modules - UI + ViewModel per feature
include(":feature:terminal")
include(":feature:settings")
include(":feature:oem-compat")
