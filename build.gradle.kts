buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Match your Android Gradle Plugin version if different.
        classpath("com.android.tools.build:gradle:8.2.0")
        // Hilt Gradle plugin
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.55")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
