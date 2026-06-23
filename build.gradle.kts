// Top-level build file
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    // Use a stable Kotlin 1.9.x release compatible with Compose and existing tooling
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    // Note: the Kotlin Compose plugin is not declared here to avoid plugin resolution failures.
    id("com.google.dagger.hilt.android") version "2.55" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    // Removed com.google.devtools.ksp plugin declaration because some runners cannot resolve it from the Gradle Plugin Portal.
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
