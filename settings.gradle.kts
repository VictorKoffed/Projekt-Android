pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Behövs i settings-filen (inte i build.gradle.kts)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    this.repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ProjektAndroid"
include(":app")
