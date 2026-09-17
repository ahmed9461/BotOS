buildscript {
    repositories { google(); mavenCentral() }
    // AGP 9 built-in Kotlin: documented KGP override, same version as Compose compiler.
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
tasks.register<Delete>("clean") { delete(layout.buildDirectory) }
