# Navigate

Ein Android-TV-Launcher in Kotlin. Ziel: **minimale Oberfläche, maximale
Performance** — entwickelt und gemessen auf einem **Chromecast HD (Google TV)**.

```
HOME-Taste drücken
   ├── es läuft eine App   →  untere Zeile erscheint (Favoriten · Home · Navigate · Einstellungen)
   │                          nochmal HOME oder BACK schließt sie und kehrt zur App zurück
   └── es läuft nichts     →  Home-Seite mit allen Apps im Raster
```

| | |
|---|---|
| **Release-APK** | 75 KB / 76.430 B (davon 68 KB / 69.284 B `classes.dex`) — eine einzige Dex-Datei |
| **Unit-Tests** | 93, ohne Gerät lauffähig · Coverage-Gate 16 % Zeilen / 22 % Zweige |
| **Startzeit** | 128 ms (`am start -W`, Prozess läuft bereits — der Normalfall) |
| **Speicher** | ~56 MB PSS nach vollem Rasteraufbau (1,4-GB-Gerät) |
| **Abhängigkeiten zur Laufzeit** | keine — nur Android-Framework + Kotlin-Stdlib |
| **Berechtigungen** | `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` (App-Op) |

> APK-Größe, Dex-Größe und Testzahl werden von
> [`tools/check_docs_drift.sh`](tools/check_docs_drift.sh) gegen das gebaute APK
> geprüft — sie können deshalb nicht still veralten. Startzeit und Speicher
> stammen aus der Messreihe mit **0.1** auf dem Gerät (docs/DEVICE_PERF.md) —
> 0.2, 0.3 und 0.4 ändern genau die Pfade, die diese Werte bestimmen
> (Vordergrund-Abfrage, Konfigurationsdatei, Symbol-Cache), sind aber noch nicht
> auf dem Fernseher nachgemessen. Der Abschnitt „Nachmessen" in derselben Datei
> nennt die Befehle dafür.

## Funktionen

- **Home-Taste:** unten erscheint eine Zeile mit Favoriten, Home, den
  Navigate-Einstellungen und den Systemeinstellungen. Ein zweiter HOME-Druck
  oder BACK schließt sie wieder — die App kommt zurück in den Vordergrund und
  bekommt den Tastaturfokus. Ohne diesen Rückweg wäre die Zeile eine Sackgasse:
  sie hält den Fokus, die App darunter bekommt keine Taste mehr.
- **Home-Seite:** alle startbaren Apps in einem Raster (6 Spalten auf dem
  Chromecast), D-Pad-Navigation, Uhr oben rechts.
- **Favoriten** stehen in einer **Konfigurationsdatei** und sind in den
  Navigate-Einstellungen direkt auf dem Fernseher bearbeitbar
  (OK = an/aus, ◀ ▶ = Position). Langes Drücken im Raster ist die Abkürzung.
- **Alle Apps, wirklich alle:** das Raster vereinigt `CATEGORY_LAUNCHER` und
  `CATEGORY_LEANBACK_LAUNCHER`. Auf dem Testgerät fehlten ohne diesen Schritt
  Prime Video, WOW und ARD — sie tragen nur den Leanback-Eintrag.
- **Live-Aktualisierung:** App installiert/deinstalliert → das Raster zieht nach.
- **Symbole liegen auf der Platte:** jedes einmal dekodierte Symbol wird als
  kleine WebP-Datei im app-internen Speicher gehalten. Nach einem
  Prozess-Neustart (auf einem 1,4-GB-Gerät der Normalfall) muss keine fremde
  APK mehr gemappt werden — das war der größte Einzelposten im Speicher.
- **Textmodus (`show_icons=false`):** das Raster zeigt nur App-Namen. Für
  minimale Last auf schwachen Geräten; die Zeile behält ihre Symbole.
- Kein Netzwerk, keine Werbung, keine Analyse, kein Konto.

## Bedienung

| Taste | Wirkung |
|---|---|
| HOME | Zeile über der laufenden App, sonst Home-Seite |
| HOME (Zeile steht) | Zeile schließen, zurück zur App |
| BACK (Zeile steht) | Zeile schließen, zurück zur App |
| BACK (Home-Seite) | ohne Wirkung |
| ◀ ▲ ▼ ▶ | Fokus (Raster ↔ Zeile mit ▼ / ▲) |
| OK | App starten · in den Einstellungen Favorit an/aus |
| ◀ ▶ (Einstellungen) | Favorit in der Zeile verschieben |
| langes OK (Raster) | Favorit setzen/entfernen |
| langes OK (Zeile) | Favorit entfernen |
| langes OK (Einstellungen) | Nutzungszugriff öffnen |


## Bauen

```bash
cd projects/navigate
bash build_navigate.sh            # Debug-APK
bash build_navigate.sh release    # Release-APK (R8, signiert wenn keystore/ vorhanden)
```

Voraussetzungen: JDK 17, Android SDK mit Plattform 36 und Build-Tools
(`local.properties` → `sdk.dir`). Gradle kommt über den Wrapper (8.14.3).

Vier Prüfungen, alle auch in der CI (`.github/workflows/ci.yml`):

```bash
sh gradlew :app:testDebugUnitTest              # 93 Unit-Tests (ohne Gerät)
sh gradlew :app:unitTestCoverageVerification   # Coverage-Gate (Bericht: unitTestCoverageReport)
sh gradlew :app:lintDebug                      # Lint — Warnungen sind Fehler
bash tools/check_docs_drift.sh                 # Messwerte der Doku ↔ gebautes APK
```

Die CI läuft auf jedem Push dieselben Schritte (plus Release-Build) und legt
Testbericht, Coverage-Bericht, Lint-Bericht und APK als Artefakte ab. Ein Tag
`v*` löst zusätzlich `.github/workflows/release.yml` aus: dieselben Gates, danach
ein GitHub-Release mit dem APK — signiert, wenn das Secret
`NAVIGATE_KEYSTORE_BASE64` gesetzt ist. Das Projekt liegt unter `git`;
`keystore/`, `local.properties` und alle Build-Ordner sind per `.gitignore`
ausgenommen.

Die 93 Tests laufen **ohne Gerät**: HOME-Regeln (`HomeKeyPolicy`), App-Auswahl
(`AppCatalog`), Entscheidungslogik, Konfigurationsparser **und** -datei,
Favoritenliste, Ereignis-Faltung, Symbol-Einpassung, Cache-Schlüssel,
Raster-Spalten. Diese Klassen liegen bei 100 % Zeilen-Coverage; die Gesamtquote
(18,3 % Zeilen / 25,4 % Zweige) enthält auch die Android-Klassen und steht mit
Begründung in [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).

## Installieren und als Home-App einrichten

```bash
bash install_navigate.sh          # installiert auf dem Chromecast
```

Danach drei Befehle auf dem Gerät (Details und Rückweg in
[docs/INSTALL.md](docs/INSTALL.md)):

```bash
adb shell cmd role add-role-holder android.app.role.HOME com.piotv.navigate
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb shell appops set com.piotv.navigate GET_USAGE_STATS allow
```

Ohne den zweiten Befehl holt sich der Google-TV-Watchdog die HOME-Taste zurück.
Ohne den dritten arbeitet Navigate mit einer Heuristik statt mit der
Nutzungsstatistik (siehe unten) — funktioniert, ist aber ungenauer.

## Konfiguration

Die Favoritenzeile kommt aus einer Datei. Sie liegt **app-intern** (dort kostet
das Lesen keinen FUSE-Zugriff, siehe [docs/CONFIG.md](docs/CONFIG.md)):

```
/data/data/com.piotv.navigate/files/navigate.conf
```

Von außen erreichbar ist der Import-Pfad — eine dort neuere Datei wird
übernommen (`adb push`):

```
/storage/emulated/0/Android/data/com.piotv.navigate/files/navigate.conf
```

Beide Pfade zeigt der Einstellungsbildschirm selbst an.

```ini
favorites=com.netflix.ninja,de.swr.avp.ard.tv,com.google.android.youtube.tv
max_favorites=6
columns=0
show_clock=false
show_icons=true
```

Bearbeiten: über **Navigate** in der unteren Zeile, oder von Hand
(`adb push`). Format, Grenzen und Beispiele stehen in
[docs/CONFIG.md](docs/CONFIG.md).


## Architektur in fünf Sätzen

1. **Eine** Activity, zwei Fenster-Modi. Zwei Home-Activities in einer App
   machen die HOME-Auflösung mehrdeutig — der HOME-Druck zeigt dann einen
   Auswahldialog.
2. Der Modus ist **Fenstergröße + Hintergrund**: `GRID` = ganzes Fenster,
   deckend gemalt; `DOCK` = Fenster auf Dock-Höhe am unteren Rand,
   Hintergrund transparent.
3. Das Fensterformat bleibt beim **Theme-Wert `PixelFormat.TRANSPARENT`**. Mit
   `window.setFormat(TRANSLUCENT)` bliebe die App darunter nur pausiert statt
   gestoppt — dafür war der Inhalt des eigenen Fensters auf dem Zielgerät dann
   nicht mehr zu sehen (per `screencap` geprüft: nur die App darunter, obwohl
   SurfaceFlinger die Fläche als sichtbar meldete). Sichtbares Dock schlägt
   laufende App im Hintergrund, deshalb bleibt es beim Theme-Format. Beide
   Varianten gemessen, Entscheidung und Messwerte:
   [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
4. Keine AndroidX, kein Compose, kein Hilt, keine Coroutines: `GridView`,
   `LauncherApps`, `LruCache`, ein Executor, ein Handler. Was fehlt, kostet
   nichts.
5. Die Entscheidung „App läuft / läuft nicht" kommt aus dem **Lebenszyklus der
   letzten fremden App** in der Nutzungsstatistik (`PAUSED`/`RESUMED` = läuft,
   `STOPPED` = beendet). Ohne Nutzungszugriff greift eine dokumentierte
   Heuristik.

Ausführlich: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Projektstruktur

```
app/src/main/java/com/piotv/navigate/
  LauncherActivity.kt        Orchestrierung: Modus, Fenster, Aktionen
  LauncherMode.kt            GRID | DOCK
  ModeDecider.kt             reine Entscheidungslogik (getestet)
  HomeKeyPolicy.kt           HOME-Regeln: zweiter Druck, Rückfall, Startart (getestet)
  ForegroundSnapshot.kt      Datenträger zwischen Messung und Entscheidung
  data/AppRepository.kt      App-Liste (zwei Kategorien), Prozess-Singleton
  data/AppEntry.kt           ein startbarer Eintrag
  data/AppCatalog.kt         Auswahl und Reihenfolge der App-Liste (getestet)
  data/ConfigCodec.kt        Parser/Serializer der Konfigurationsdatei (getestet)
  data/FavoritesConfig.kt    die Datei selbst, mit Änderungserkennung
  data/FavoritesCodec.kt     Favoritenliste: an/aus/verschieben (getestet)
  data/Prefs.kt              Laufzeitzustand (2 Werte)
  fg/ForegroundProbe.kt      Nutzungsstatistik (kurzes Fenster, dann langes)
  fg/UsageEventFolder.kt     Ereignisse zum Vordergrund-Zustand falten (getestet)
  ui/AppGridView.kt          GridView + Fernbedienungs-Korrekturen
  ui/GridColumns.kt          Spaltenzahl des Rasters (getestet)
  ui/IconGeometry.kt         Einpassung der Symbole in die Zelle (getestet)
  ui/AppGridAdapter.kt       Rasterzellen (mit Textmodus)
  ui/DockController.kt       Aufbau der unteren Zeile
  ui/DockSignature.kt        Cache-Schlüssel der Zeile (getestet)
  ui/IconLoader.kt           Symbole: Speicher-Cache → Platte → Ressourcen
  ui/IconDiskCache.kt        WebP-Symbole im internen Speicher (+ Schlüssel, getestet)
  ui/SettingsActivity.kt     Navigate-Einstellungen (Favoriten)
tools/make_assets.py         Icon-Vektoren + TV-Banner generieren
tools/check_docs_drift.sh    Doku-Gate: dokumentierte Messwerte ↔ gebautes APK
docs/                        ARCHITECTURE · CONFIG · INSTALL · DEVICE_PERF
```

## Bekannte offene Punkte

Ehrlich aufgeführt, weil sie beim Lesen der Doku sonst überraschen:

1. **Die App hinter der Zeile läuft nicht weiter.** Auf dem Zielgerät gemessen
   kostet das Fensterformat die Entscheidung: mit dem Theme-Format
   `TRANSPARENT` ist die Zeile sichtbar, die Plattform **stoppt** aber die App
   darunter (ihr Fenster verschwindet, `mHasSurface=false`). Mit
   `window.setFormat(TRANSLUCENT)` bleibt die App nur pausiert — dafür war der
   Inhalt des eigenen Fensters auf dem Gerät nicht mehr zu sehen
   (Pixelprüfung per `screencap`: nur die App darunter). Sichtbares Dock
   schlägt laufende App im Hintergrund, deshalb bleibt es beim Theme-Format.
   Messwerte: [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).
2. **Der Rückweg von der Zeile ist eine Taste, kein Automatismus.** Die Zeile
   hält den Tastaturfokus (das ist ihr Zweck — man soll sie bedienen können),
   also bekommt die App darunter keine D-Pad-Eingabe mehr, solange sie steht.
   HOME oder BACK schließen sie. Getestet: HOME über Netflix → Zeile, HOME
   erneut → Netflix wieder vorn und fokussiert.
3. **Exakte Erkennung braucht den Nutzungszugriff.** Ohne ihn entscheidet eine
   Heuristik („wurde zuletzt über Navigate eine App gestartet?"). Sie ist
   dokumentiert und funktioniert, ist aber ungenauer bei Apps, die außerhalb
   von Navigate gestartet wurden.
4. **Ein Bildschirm-Keyboard liegt über der Zeile.** Ist in der App darunter
   eine IME-Tastatur offen (z. B. in Termux), zeichnet das System sie über die
   Zeile — IME-Fenster liegen in einer höheren Ebene. Für Fernsehnutzung ohne
   Tastatur irrelevant, aber es ist so.
5. **Die Einrichtung braucht ADB.** Home-Rolle, Google-TV-Watchdog und
   Nutzungszugriff sind drei Befehle (docs/INSTALL.md) — ein Launcher kann
   keine Berechtigungsdialoge zeigen.

## Doku

| Datei | Inhalt |
|---|---|
| [docs/INSTALL.md](docs/INSTALL.md) | Chromecast/Google-TV-Einrichtung, Rückweg, Fehlersuche |
| [docs/CONFIG.md](docs/CONFIG.md) | Konfigurationsdatei: Schlüssel, Grenzen, Beispiele |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Warum ein Fenster, warum kein AndroidX, Zustandsmaschine |
| [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md) | gemessene Werte auf dem Chromecast HD |
| [CHANGELOG.md](CHANGELOG.md) | Änderungen |

## Lizenz

MIT — siehe [LICENSE](LICENSE).
