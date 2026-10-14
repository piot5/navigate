import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        versionCode = 3
        versionName = "0.3"
    }

    buildTypes {
        release {
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
            val props = Properties().apply {
                val f = rootProject.file("keystore/keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun credential(envKey: String, propKey: String): String? =
                System.getenv(envKey) ?: props.getProperty(propKey)
            val ksPassword = credential("NAVIGATE_KEYSTORE_PASSWORD", "NAVIGATE_KEYSTORE_PASSWORD")
            val keyPassword = credential("NAVIGATE_KEY_PASSWORD", "NAVIGATE_KEY_PASSWORD")
            if (ksPassword != null && keyPassword != null) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(
                        rootProject.file(
                            System.getenv("NAVIGATE_KEYSTORE")
                                ?: props.getProperty("NAVIGATE_KEYSTORE")
                                ?: "keystore/navigate-release.jks"
                        )
                    )
                    storePassword = ksPassword
                    this.keyAlias = credential("NAVIGATE_KEY_ALIAS", "NAVIGATE_KEY_ALIAS") ?: "navigate"
                    this.keyPassword = keyPassword
                }
            } else {
                logger.warn(
                    "Release-Signing übersprungen: NAVIGATE_KEYSTORE_PASSWORD/NAVIGATE_KEY_PASSWORD " +
                        "fehlen (Env oder keystore/keystore.properties). Release-APK bleibt unsigniert."
                )
            }
        }
        debug {
            isMinifyEnabled = false
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
