import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // jacoco ist in Gradle eingebaut — kein zusätzliches Plugin-Repository, kein
    // Download zur Konfigurationszeit. Der einzige Zweck ist die Coverage-Messung
    // der Unit-Tests (unitTestCoverageReport); die Klasse selbst kommt ohne
    // Laufzeit-Abhängigkeit aus, deshalb bleibt app/build.gradle.kts schlank.
    id("jacoco")
}

android {
    namespace = "com.piotv.navigate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.piotv.navigate"
        // minSdk 23: deckt alle Android-TV-Geräte ab, die LauncherApps (API 21)
        // und UsageStatsManager (API 22) sicher haben.
        minSdk = 23
        // targetSdk bewusst 34 statt 36: ab 35 erzwingt Android Edge-to-Edge,
        // was auf TV (keine Systembars) nur Risiko ohne Nutzen bringt. Das
        // Zielgerät (Chromecast HD) läuft auf API 34.
        targetSdk = 34
        versionCode = 16
        versionName = "0.13.3"
    }

    /**
     * Lint ist ab 0.4 ein Gate, kein Bericht: `warningsAsErrors` lässt den Build
     * an jeder Warnung scheitern (lokal wie in CI, siehe .github/workflows/ci.yml).
     *
     * Warum das überhaupt möglich ist: die 15 Warnungen aus 0.3 sind abgearbeitet
     * statt unterdrückt — entweder behoben (Plurals, Overdraw, RedundantLabel,
     * ungenutzte Ressourcen, contentDescription) oder an der Fundstelle mit
     * Begründung unterdrückt (TranslucentOrientation, DiscouragedApi). Übrig
     * bleibt `OldTargetApi`, und der wird hier bewusst abgeschaltet: die
     * Begründung für targetSdk 34 steht zwei Zeilen weiter oben und gilt
     * unverändert.
     */
    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        // Vier Abschaltungen, und alle vier prüfen nicht den Code, sondern eine
        // Entscheidung, die hier bewusst so getroffen wurde:
        //
        //   OldTargetApi               targetSdk 34 statt 36 (Begründung zwei
        //                              Zeilen darüber: Edge-to-Edge auf TV)
        //   AndroidGradlePluginVersion
        //   GradleDependency           „eine neuere Version ist verfügbar"
        //   NewerVersionAvailable
        //
        // Die letzten drei brauchen **Netz**, um die Versionsliste zu holen —
        // lokal mit `--offline` schweigen sie, in der CI schlagen sie zu. Genau
        // das ist passiert: der erste CI-Lauf dieses Repos scheiterte an
        // „A newer version of Gradle than 8.14.3 is available: 8.14.6", nicht an
        // einer Code-Stelle. Wrapper (mit sha256) und AGP/Kotlin sind hier
        // absichtlich gepinnt; eine Warnung, die bei jeder Veröffentlichung
        // stromaufwärts rot wird, ist kein Qualitätssignal, sondern Rauschen.
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
     * Die VCS-Info-Datei (META-INF/version-control-info.textproto) wird nicht
     * mitgepackt. Sie enthält den Git-Zustand des Builds — damit ist die
     * APK-Größe **nicht** reproduzierbar (0.3 dokumentierte 73.686 B, der
     * Nachbau ergab 73.679 B: dieselbe Quelle, anderer Git-Zustand; in 0.4
     * gemessen: 42 B „NO_VALID_GIT_FOUND" gegen 120 B mit Commit-Hash). Die
     * Größe ist in diesem Projekt ein Messwert (README, docs/DEVICE_PERF.md),
     * und ein Messwert, der sich beim Nachbauen ändert, ist keiner. Das
     * Doku-Gate (tools/check_docs_drift.sh) kann so byte-genau prüfen.
     *
     * Der Schalter sitzt pro Build-Typ (`vcsInfo.include`); auf `android` gibt
     * es ihn nicht — geprüft: „Unresolved reference: vcsInfo" auf oberster
     * Ebene, und AGP meldet selbst: „This feature is now enabled in the DSL per
     * build type with vcsInfo.include = true".
     */
    buildTypes {
        release {
            vcsInfo {
                include = false
            }
            // Der ganze Performance-Plan hängt daran: R8 entfernt den ungenutzten
            // Teil der Kotlin-Stdlib, shrinkResources die ungenutzten Ressourcen.
            // Ergebnis: kleines Dex → kurze Kaltstartzeit auf dem Chromecast.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing wie in KeyTab: Credentials NUR aus Env oder
            // keystore/keystore.properties (gitignored) — keine Defaults im
            // Buildfile. Ohne Credentials bleibt das Release-APK unsigniert.
            //
            // Zwei Bedingungen, und beide sind aus der CI gelernt (der
            // Release-Lauf war bis 0.13 rot, und zwar genau hier):
            //
            //  - Ein **leerer** Wert zählt nicht. GitHub setzt ein nicht
            //    gesetztes Secret als leere Zeichenkette in die Umgebung, nicht
            //    als fehlende Variable — `System.getenv` liefert dann "" und
            //    nicht null. Ohne `isNotBlank()` entstand deshalb eine
            //    Signing-Konfiguration mit leerem Passwort, und
            //    `validateSigningRelease` brach den Build ab
            //    („Keystore file ... not found for signing config 'release'").
            //  - Die Keystore-Datei muss existieren. Fehlt sie (CI ohne Secret),
            //    bleibt das APK unsigniert — wie der Satz darüber sagt.
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
                    "Release-Signing übersprungen: kein vollständiges Credential-Paar " +
                        "(NAVIGATE_KEYSTORE_PASSWORD/NAVIGATE_KEY_PASSWORD) oder keine Keystore-Datei " +
                        "(${store.name}). Release-APK bleibt unsigniert."
                )
            }
        }
        debug {
            isMinifyEnabled = false
            // Auch im Debug-Build: die Debug-APK-Größe steht in
            // docs/DEVICE_PERF.md und soll nachbaubar sein.
            vcsInfo {
                include = false
            }
            // Side-by-side mit dem Release-Build: das Debug-Paket ist
            // com.piotv.navigate.debug und damit eine *zweite* Home-Kandidatin.
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

    // Kein Play-Bundle → der AGP-Block "Dependency metadata" wird nicht gebraucht
    // und macht APKs für fdroidserver unbrauchbar.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Absicht: KEINE AndroidX-, Compose- oder Coroutines-Abhängigkeit.
    // Alles, was der Launcher braucht (GridView, LauncherApps, UsageStats,
    // LruCache, SharedPreferences), ist im Framework vorhanden.
    testImplementation("junit:junit:4.13.2")
}

// ---------------------------------------------------------------------------
// Coverage der Unit-Tests
// ---------------------------------------------------------------------------
//
// Warum überhaupt: 0.3 hatte 47 Tests und keine Zahl dazu — „viel" ist keine
// Aussage. Die Zahl, die hier entsteht, ist der Nenner für jede weitere
// Diskussion über Testlücken, und das Gate verhindert, dass neue ungetestete
// Logik unbemerkt dazukommt.
//
// Gemessen wird nur, was ohne Gerät läuft (testDebugUnitTest). Die
// Android-Klassen (Activity, Adapter, Loader) sind bewusst **nicht** Teil der
// Erwartung — sie brauchen ein Gerät, nicht ein Coverage-Werkzeug.

jacoco {
    // Gepinnt: die Version aus Gradle kann sich mit dem Wrapper ändern, und ein
    // wandernder Schwellwert wäre genau die Art stiller Drift, die das
    // Doku-Gate in tools/check_docs_drift.sh verbietet.
    toolVersion = "0.8.12"
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        // Android-Unit-Tests laden einen Teil der Klassen aus dem Stub-Jar, das
        // keinen Quellort hat. Ohne diese Option bricht die Instrumentierung mit
        // „Cannot process instrumented class" ab.
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Klassen und Quellen sind für Bericht und Gate dieselben — einmal definiert.
val coverageClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
    exclude("**/R.class", "**/R$*.class", "**/BuildConfig.class")
}
val coverageExec = layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")

tasks.register<JacocoReport>("unitTestCoverageReport") {
    group = "verification"
    description = "Coverage der Unit-Tests als HTML (build/reports/jacoco/unitTestCoverageReport)."
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
 * Das Gate. Die Schwellen liegen unter dem gemessenen Stand (0.13: 20,8 % Zeilen
 * / 28,9 % Zweige) — nicht, weil mehr erlaubt sein soll, sondern damit eine
 * Umbenennung oder ein verschobener Zweig nicht sofort rot wird. In 0.12 standen
 * hier 16 %/22 %; das war der Stand von 0.6 und damit so weit unter dem Ist-Wert,
 * dass es kaum noch etwas bewachen konnte.
 *
 * Was die Zahl **nicht** sagt: im Nenner stehen auch die Klassen, die ohne
 * Gerät gar nicht laufen können (Activity, Panel, Menü, Adapter, Loader,
 * Repository). Ohne sie wäre die Quote nahe 100 %: alle Klassen, die
 * ohne Gerät prüfbar sind (AppCatalog, Order, OrderCodec, OrderStore, ConfigFile,
 * SettingsCodec, SettingsStore, NowPlayingText, NowPlayingBoard, GridColumns,
 * IconGeometry, IconCacheKeys …), sind vollständig abgedeckt. Das
 * Gate ist deshalb ein **Regressions**schutz: neue Logik ohne Test senkt die
 * Quote und lässt den Build scheitern.
 */
tasks.register<JacocoCoverageVerification>("unitTestCoverageVerification") {
    group = "verification"
    description = "Bricht ab, wenn die Unit-Test-Coverage unter die Schwellen fällt."
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
