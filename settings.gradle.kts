pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // ADD THIS BLOCK TO DEFINE THE PLUGIN VERSION
    plugins {
        id("org.mozilla.rust-android-gradle.rust-android") version "0.9.6"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BlastEMST"
include(":app")