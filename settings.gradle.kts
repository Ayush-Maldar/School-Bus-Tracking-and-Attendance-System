// settings.gradle.kts

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    // --- ADDED/MODIFIED BLOCK ---
    // Declare plugins used in the project and their versions
    plugins {
        // Define the google-services plugin ID and its specific version here
        id("com.google.gms.google-services") version "4.4.2" apply false // Use latest stable version (Check for updates!)

        // It's good practice to define your other core plugins here too
        // Replace "8.x.y" and "1.9.zz" with the versions you are actually using
        id("com.android.application") version "8.x.y" apply false
        id("org.jetbrains.kotlin.android") version "1.9.zz" apply false
    }
    // --- END OF ADDED/MODIFIED BLOCK ---
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add other repositories if needed (e.g., Jitpack)
    }
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Add JitPack if needed
    }
}

// Keep your existing project name and included modules
rootProject.name = "fix"
include(":app")