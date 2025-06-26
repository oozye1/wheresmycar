// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.3.2" apply false // Use your project's version
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false // Use your project's version
    // Add this line to tell the project where to find the secrets plugin
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}
