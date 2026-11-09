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
rootProject.name = "KrasivyjLauncher"
include(":app")
// СуперСмарт modules (former standalone apps)
include(":modules:mudrost-engine", ":modules:mudrost", ":modules:kodeks", ":modules:boec", ":modules:lite")
include(":notes-app")
