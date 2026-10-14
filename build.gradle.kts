// Top-level build file
//
// Bewusst schlank: Navigate hat genau zwei Build-Plugins (AGP + Kotlin) und
// KEINE Laufzeit-Abhängigkeiten (siehe app/build.gradle.kts). Detekt/Kover
// aus KeyTab sind hier absichtlich nicht dabei — der Launcher wird auf
// Fremdstartzeit optimiert, jedes zusätzliche Plugin kostet Build-Minuten
// ohne Nutzen für die Zielsetzung "minimale UI, maximale Performance".
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
