// Top-level build file
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    // Use a stable Kotlin 1.9.x release compatible with Compose and existing tooling
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    // Note: the Kotlin Compose plugin is not published with the same artifact on plugin portal for some versions.
    // We avoid declaring org.jetbrains.kotlin.plugin.compose here to prevent plugin resolution failures and
    // instead rely on composeOptions + dependencies in the module's build file.
    id("com.google.dagger.hilt.android") version "2.55" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    // KSP version compatible with Kotlin 1.9.x
    id("com.google.devtools.ksp") version "1.9.22-1.0.13" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
