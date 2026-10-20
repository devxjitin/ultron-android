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
        // Only the YouTube extractor comes from JitPack.
        maven("https://jitpack.io") { content { includeGroup("com.github.TeamNewPipe") } }
    }
}

rootProject.name = "Assistant"
include(":app")
