import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // jacoco is built into Gradle — no extra plugin repository, no
    // download at configuration time. Its only purpose is coverage measurement
    // of the unit tests (unitTestCoverageReport); the class itself works without
    // a runtime dependency, which is why app/build.gradle.kts stays lean.
    id("jacoco")
}

android {
    namespace = "com.piotv.navigate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.piotv.navigate"
        // minSdk 23: covers all Android TV devices that reliably have
        // LauncherApps (API 21) and UsageStatsManager (API 22).
        minSdk = 23
        // targetSdk deliberately 34 instead of 36: from 35 on Android forces
        // Edge-to-Edge, which on TV (no system bars) brings only risk without
        // benefit. The target device (Chromecast HD) runs API 34.
        targetSdk = 34
        versionCode = 22
        versionName = "0.17.0"
    }

    /**
     * Since 0.4 lint is a gate, not a report: `warningsAsErrors` makes the build
     * fail on every warning (locally as in CI, see .github/workflows/ci.yml).
     *
     * Why that is possible at all: the 15 warnings from 0.3 were worked off
     * instead of suppressed — either fixed (Plurals, Overdraw, RedundantLabel,
     * unused resources, contentDescription) or suppressed at the site with
     * a reason (TranslucentOrientation, DiscouragedApi). What remains
     * is `OldTargetApi`, and it is deliberately switched off here: the
     * reason for targetSdk 34 stands two lines further up and applies
     * unchanged.
     */
    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        // Four switch-offs, and none of the four checks the code — they check a
        // decision that was deliberately made this way here:
        //
        //   OldTargetApi               targetSdk 34 instead of 36 (reason two
        //                              lines above: Edge-to-Edge on TV)
        //   AndroidGradlePluginVersion
        //   GradleDependency           "a newer version is available"
        //   NewerVersionAvailable
        //
        // The last three need **network** to fetch the version list —
        // locally with `--offline` they stay silent, in CI they strike. That is
        // exactly what happened: the first CI run of this repo failed on
        // "A newer version of Gradle than 8.14.3 is available: 8.14.6", not on
        // a code site. Wrapper (with sha256) and AGP/Kotlin are deliberately
        // pinned here; a warning that turns red upstream on every release
        // is not a quality signal but noise.
        disable.addAll(
            listOf(
                "OldTargetApi",
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
            )
        )
    }

    /**
     * The VCS info file (META-INF/version-control-info.textproto) is not
     * packaged. It contains the Git state of the build — with it, the APK size
     * is **not** reproducible (0.3 documented 73.686 B, the
     * rebuild yielded 73.679 B: same source, different Git state; measured in 0.4:
     * 42 B "NO_VALID_GIT_FOUND" vs 120 B with commit hash). Size is a measured
     * value in this project (README, docs/DEVICE_PERF.md),
     * and a measured value that changes on a rebuild is none. That way the
     * docs gate (tools/check_docs_drift.sh) can check byte-exactly.
     *
     * The switch lives per build type (`vcsInfo.include`); on the `android`
     * block it does not exist — checked: "Unresolved reference: vcsInfo" at top
     * level, and AGP itself reports: "This feature is now enabled in the DSL per
     * build type with vcsInfo.include = true".
     */
    buildTypes {
        release {
            vcsInfo {
                include = false
            }
            // The whole performance plan hinges on this: R8 removes the unused
            // part of the Kotlin stdlib, shrinkResources the unused resources.
            // Result: small dex → short cold start on the Chromecast.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing as in KeyTab: credentials ONLY from the environment or
            // keystore/keystore.properties (gitignored) — no defaults in the
            // build file. Without credentials the release APK stays unsigned.
            //
            // Two conditions, and both were learned from CI (the release run
            // was red until 0.13, precisely here):
            //
            //  - An **empty** value does not count. GitHub puts an unset
            //    secret into the environment as an empty string, not
            //    as a missing variable — `System.getenv` then returns "" and
            //    not null. Without `isNotBlank()` this produced a
            //    signing configuration with an empty password, and
            //    `validateSigningRelease` broke the build
            //    ("Keystore file ... not found for signing config 'release'").
            //  - The keystore file must exist. If it is missing (CI without
            //    secret), the APK stays unsigned — as the sentence above says.
            val props = Properties().apply {
                val f = rootProject.file("keystore/keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun credential(envKey: String, propKey: String): String? =
                (System.getenv(envKey) ?: props.getProperty(propKey))?.takeIf { it.isNotBlank() }
            val ksPassword = credential("NAVIGATE_KEYSTORE_PASSWORD", "NAVIGATE_KEYSTORE_PASSWORD")
            val keyPassword = credential("NAVIGATE_KEY_PASSWORD", "NAVIGATE_KEY_PASSWORD")
            val store = rootProject.file(
                credential("NAVIGATE_KEYSTORE", "NAVIGATE_KEYSTORE") ?: "keystore/navigate-release.jks"
            )
            if (ksPassword != null && keyPassword != null && store.isFile) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = store
                    storePassword = ksPassword
                    this.keyAlias = credential("NAVIGATE_KEY_ALIAS", "NAVIGATE_KEY_ALIAS") ?: "navigate"
                    this.keyPassword = keyPassword
                }
            } else {
                logger.warn(
                    "Release signing skipped: no complete credential pair " +
                        "(NAVIGATE_KEYSTORE_PASSWORD/NAVIGATE_KEY_PASSWORD) or no keystore file " +
                        "(${store.name}). Release APK stays unsigned."
                )
            }
        }
        debug {
            isMinifyEnabled = false
            // In the debug build too: the debug APK size is recorded in
            // docs/DEVICE_PERF.md and should be reproducible.
            vcsInfo {
                include = false
            }
            // Side by side with the release build: the debug package is
            // com.piotv.navigate.debug and thus a *second* home candidate.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // No Play bundle → the AGP "Dependency metadata" block is not needed
    // and would make APKs unusable for fdroidserver.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Intentional: NO AndroidX, Compose or Coroutines dependency.
    // Everything the launcher needs (GridView, LauncherApps, UsageStats,
    // LruCache, SharedPreferences) is present in the framework.
    testImplementation("junit:junit:4.13.2")
}

// ---------------------------------------------------------------------------
// Unit test coverage
// ---------------------------------------------------------------------------
//
// Why at all: 0.3 had 47 tests and no number for it — "a lot" is not
// a statement. The number produced here is the denominator for any further
// discussion of test gaps, and the gate keeps new untested logic from
// sneaking in unnoticed.
//
// Only what runs without a device is measured (testDebugUnitTest). The
// Android classes (activity, adapter, loader) are deliberately **not** part of
// the expectation — they need a device, not a coverage tool.

jacoco {
    // Pinned: the version bundled with Gradle can change with the wrapper, and
    // a drifting threshold would be exactly the kind of silent drift the
    // docs gate in tools/check_docs_drift.sh forbids.
    toolVersion = "0.8.12"
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        // Android unit tests load part of the classes from the stub jar, which
        // has no source location. Without this option instrumentation aborts
        // with "Cannot process instrumented class".
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Classes and sources are the same for report and gate — defined once.
val coverageClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
    exclude("**/R.class", "**/R$*.class", "**/BuildConfig.class")
}
val coverageExec = layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")

tasks.register<JacocoReport>("unitTestCoverageReport") {
    group = "verification"
    description = "Unit test coverage as HTML (build/reports/jacoco/unitTestCoverageReport)."
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(coverageExec)
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}

/**
 * The gate. The thresholds sit below the measured state (0.13: 20.8% lines
 * / 28.9% branches) — not because more should be allowed, but so that a
 * rename or a moved branch does not instantly turn red. In 0.12 the values
 * here were 16%/22%; that was the state of 0.6 and thus so far below the
 * actual value that it could hardly guard anything anymore.
 *
 * What the number does **not** say: the denominator also includes the classes
 * that cannot run without a device at all (activity, panel, menu, adapter,
 * loader, repository). Without them the ratio would be near 100%: all classes
 * testable without a device (AppCatalog, Order, OrderCodec, OrderStore,
 * ConfigFile, SettingsCodec, SettingsStore, NowPlayingText, NowPlayingBoard,
 * GridColumns, IconGeometry, IconCacheKeys …) are fully covered. The gate is
 * therefore a **regression** guard: new logic without a test lowers the
 * ratio and fails the build.
 */
tasks.register<JacocoCoverageVerification>("unitTestCoverageVerification") {
    group = "verification"
    description = "Fails when the unit test coverage falls below the thresholds."
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(coverageExec)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.18".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.25".toBigDecimal()
            }
        }
    }
}
