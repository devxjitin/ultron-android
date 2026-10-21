plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.3.21" apply false
    id("io.objectbox") version "5.4.2" apply false
}

// Windows and Linux Kotlin incremental caches contain incompatible absolute paths.
// Keep container outputs separate from Android Studio/local builds.
if (System.getenv("ASSISTANT_DOCKER_BUILD") == "true") {
    allprojects {
        layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(".docker-build/${project.name}"))
    }
}
