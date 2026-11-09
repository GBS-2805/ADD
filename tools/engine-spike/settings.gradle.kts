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
        // Robolectric's native runtime is already in the local cache; repo1 is the
        // non-rate-limited mirror used when Maven Central returns 429 here.
        mavenLocal()
        maven("https://repo1.maven.org/maven2")
        google()
        mavenCentral()
    }
}

rootProject.name = "ADD-Engine-Spike"
include(":app")
