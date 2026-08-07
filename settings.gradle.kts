pluginManagement {
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

rootProject.name = "SwiftShare"

include(
    ":app",
    ":core-security",
    ":core-discovery",
    ":core-transport",
    ":core-transfer",
    ":core-compression",
)
