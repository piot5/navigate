// Top-level build file
//
// Deliberately lean: Navigate has exactly two build plugins (AGP + Kotlin) and
// NO runtime dependencies (see app/build.gradle.kts). Detekt/Kover
// from KeyTab are deliberately absent here — the launcher is optimized for
// cold start time, and every extra plugin costs build minutes
// without benefit for the goal "minimal UI, maximum performance".
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
