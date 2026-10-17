# Navigate

Ein Android-TV-Launcher in Kotlin. Ziel: **minimale Oberfläche, maximale
Performance** — entwickelt und gemessen auf einem **Chromecast HD (Google TV)**.

```
HOME-Taste drücken
   →  unten eine Zeile mit den ersten Apps
   →  darunter der „…“-Knopf  →  OK: ganzes Raster auf-/zuklappen
```

Mehr tut dieser Launcher nicht. Die Home-Seite ist ein App-Raster, von dem
zunächst **nur die untere Zeile** sichtbar ist; darunter, in der Zeile darunter,
sitzt der „…“-Knopf und klappt den Rest auf. Oben stehen die Uhr rechts und —
wenn eingeschaltet — „Now Playing“ links. Einzustellen gibt es zwei Sachen:
**die Reihenfolge der Apps** und **ob das Now-Playing-Feld erscheint**.

| | |
|---|---|
| **Release-APK** | 74 KB / 76.187 B signiert (66 KB / 67.840 B unsigniert) · davon 71 KB / 73.112 B `classes.dex` — eine einzige Dex-Datei |
| **Unit-Tests** | 108, ohne Gerät lauffähig · Coverage-Gate 18 % Zeilen / 25 % Zweige (gemessen 20,8 % / 28,9 %) |
| **Abhängigkeiten zur Laufzeit** | keine — nur Android-Framework + Kotlin-Stdlib |
| **Berechtigungen** | `QUERY_ALL_PACKAGES` (ohne sie wären leanback-only Apps unsichtbar); `PACKAGE_USAGE_STATS` optional, nur für „zurück in die letzte App“; dazu optional der **Benachrichtigungszugriff** für „Now Playing“ (keine Manifest-Berechtigung, sondern eine Freigabe per adb — siehe unten) |
| **Startzeit / Speicher** | 128 ms / ~56 MB PSS — gemessen mit 0.1, für 0.13 **nicht** neu gemessen ([docs/DEVICE_PERF.md](docs/DEVICE_PERF.md)) |

> APK-Größe, Dex-Größe, Testzahl **und** die Coverage-Zahlen werden von
> [`tools/check_docs_drift.sh`](tools/check_docs_drift.sh) gegen das gebaute APK
> bzw. den JaCoCo-Bericht geprüft — sie können deshalb nicht still veralten. Das
> Skript prüft außerdem, dass in README und Architektur **keine andere Testzahl**
> steht als im gebauten Testset. Startzeit und Speicher sind bewusst als **alte**
> Messwerte gekennzeichnet: sie stammen aus der Fassung mit der unteren Zeile, die
> es seit 0.7 nicht mehr gibt.

## Funktionen

- **Home-Taste:** die Home-Seite mit der unteren App-Zeile erscheint.
- **HOME erneut oder BACK:** holt die zuletzt benutzte App zurück — die Home-Seite
  ist damit ein Umschalter, keine Sackgasse. Mit Nutzungszugriff ist die Antwort
  exakt; ohne ihn greift die zuletzt über Navigate gestartete App.
- **„…“ (Zeile unter der App-Zeile):** klappt das restliche Raster auf — und
  wieder zu. BACK tut dasselbe, wenn aufgeklappt ist.
- **OK auf einer App:** starten.
- **Langes OK auf einer App:** schiebt sie **eine Position nach vorne** und
  speichert die Reihenfolge. Der Weg, die Reihenfolge auf dem Fernseher zu
  ändern; der andere ist die Konfigurationsdatei (`adb push`).
- **Uhr** oben rechts, im Minutentakt.
- **„Now Playing“ oben links** (0.12, mehrzeilig seit 0.13, optional): **eine Zeile
  je App mit aktiver Media-Session** — **App-Name, Interpret und Titel**, dazu
  `· pausiert`, wenn sie pausiert ist, und das **Cover**, wenn die Session eines
  mitliefert (sonst das App-Symbol). Läuft ein Hörbuch pausiert und daneben ein
  Radiosender, stehen beide Zeilen da; was **spielt**, steht oben. Sichtbar nur im
  zugeklappten Zustand — aufgeklappt gehört der Platz den Apps.
  **OK auf einer Zeile holt genau diese App zurück** (erst ihr „Jetzt läuft“-Bildschirm,
  sonst die App selbst). Eingeschaltet wird es in den Einstellungen; ohne
  **Benachrichtigungszugriff** bleibt es leer (eine normale App darf die
  Media-Sessions nicht abfragen, siehe [docs/INSTALL.md](docs/INSTALL.md)
  Schritt 5b). Die Abfrage läuft alle 5 Sekunden im Hintergrund-Thread, nur
  solange die Home-Seite sichtbar ist.
- **Eine Zeile ändert sich nur, wenn sich für diese App etwas ändert.** Sonst bleibt
  sie stehen, wie sie ist — samt Cover. Eine verschwundene Session lässt ihre Zeile
  erst nach **zwei** erfolgreichen Abfragen ohne sie verschwinden (zehn Sekunden),
  weil eine Session beim Titelwechsel kurz fehlt; ein **fehlgeschlagener** Blick
  (kein Zugriff, Dienst antwortet nicht) löscht gar nichts. Die Regel steht in
  `data/NowPlayingBoard.kt` und ist ohne Gerät geprüft.
- **Einstellungen (langes OK auf „…“):** eine Seite mit dem Schalter *Now Playing
  anzeigen*, mit der Fernbedienung bedient (`▲ ▼` wählt, `OK` schaltet um,
  `◀`/`BACK` zurück). Sie schreibt sofort in die Datei und nennt die Pfade —
  die Einstellung ist damit auch per `adb push` zu setzen
  ([docs/CONFIG.md](docs/CONFIG.md)).
- **Grüne Beschriftung für Apps, die aktiv sind.** Die Frage dahinter ist
  dieselbe wie bei „zurück in die letzte App“ (Nutzungsstatistik): aktiv heißt
  „in den letzten 24 Stunden im Vordergrund“ — auf diesem Gerät leben die
  App-Prozesse danach noch, obwohl ihre Activity gestoppt ist. Die **zuletzt
  geöffnete** App bekommt einen helleren Grünton, und die Markierung wird
  **laufend** nachgeführt (5-Sekunden-Takt, solange die Home-Seite sichtbar ist).
  Grün bleibt auch bei ausgewählter Zelle grün — die Auswahl zeigt der Rahmen.
  Ohne Nutzungszugriff ist nichts grün — lieber nichts als falsch.
- **Kontextmenü (langes OK):** Position nach vorne/hinten, App deinstallieren
  (System-Dialog), App schließen, alle Apps schließen. Schließen wirkt nur auf
  Apps im Hintergrund — mehr erlaubt Android einer normalen App nicht.
- **Die Beschriftung steht zentriert unter dem Symbol** (bis 0.8 nicht: ohne
  `gravity` setzt Android Text linksbündig in die Zelle).
- **Alle Apps, wirklich alle:** das Raster vereinigt `CATEGORY_LAUNCHER` und
  `CATEGORY_LEANBACK_LAUNCHER`. Auf dem Testgerät fehlten ohne diesen Schritt
  Prime Video, WOW und ARD — sie tragen nur den Leanback-Eintrag.
- **Live-Aktualisierung:** App installiert/deinstalliert → das Raster zieht nach.
- **Symbole liegen auf der Platte:** jedes einmal dekodierte Symbol wird als
  kleine WebP-Datei im app-internen Speicher gehalten. Nach einem
  Prozess-Neustart (auf einem 1,4-GB-Gerät der Normalfall) muss keine fremde
  APK mehr gemappt werden — das war der größte Einzelposten im Speicher.
- Kein Netzwerk, keine Werbung, keine Analyse, kein Konto.

## Bedienung

| Taste | Wirkung |
|---|---|
| HOME | von einer App: Home-Seite (untere Zeile) · auf der Home-Seite: zurück in die letzte App |
| ◀ ▶ ▲ ▼ | Fokus im Raster |
| OK | App starten · auf „…“ das Raster auf-/zuklappen · auf einer „Now Playing“-Zeile zurück in die spielende App |
| BACK | aufgeklapptes Raster zuklappen · sonst zurück in die letzte App |
| langes OK | auf einer App: Kontextmenü (Position nach vorne/hinten · deinstallieren · schließen · alle schließen) · auf „…“: Einstellungen |

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
sh gradlew :app:testDebugUnitTest              # 108 Unit-Tests (ohne Gerät)
sh gradlew :app:unitTestCoverageVerification   # Coverage-Gate (Bericht: unitTestCoverageReport)
sh gradlew :app:lintDebug                      # Lint — Warnungen sind Fehler
bash tools/check_docs_drift.sh                 # Messwerte der Doku ↔ gebautes APK
```

Die CI läuft auf jedem Push dieselben Schritte (plus Release-Build) und legt
Testbericht, Coverage-Bericht, Lint-Bericht und APK als Artefakte ab. Ein Tag
`v*` löst zusätzlich `.github/workflows/release.yml` aus: dieselben Gates, danach
ein GitHub-Release mit dem APK — signiert, wenn das Secret
`NAVIGATE_KEYSTORE_BASE64` gesetzt ist.

Die 108 Tests laufen **ohne Gerät**: App-Auswahl (`AppCatalog`), die
Anzeige-Reihenfolge (`Order`), die Konfigurationsdateien (`OrderCodec`,
`OrderStore`, `SettingsCodec`, `SettingsStore`, `ConfigFile`), die
Now-Playing-Zeilen (`NowPlayingText`, `NowPlayingBoard`), Raster-Spalten,
Symbol-Einpassung, Cache-Schlüssel. Diese Klassen liegen bei 100 %
Zeilen-Coverage; die Gesamtquote
(20,8 % Zeilen / 28,9 % Zweige) enthält auch die Android-Klassen und steht mit
Begründung in [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).

## Installieren und als Home-App einrichten

```bash
bash install_navigate.sh          # installiert auf dem Fernseher (nur dort)
```

Der Installer sucht sich das erste erreichbare **Fernsehgerät** — Telefone und
Emulatoren werden übersprungen. Ein anderes Ziel: `NAVIGATE_DEVICE=<ip>:5555`.

Danach zwei Befehle auf dem Gerät (Details und Rückweg in
[docs/INSTALL.md](docs/INSTALL.md)):

```bash
adb shell cmd role add-role-holder android.app.role.HOME com.piotv.navigate
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Ohne den zweiten Befehl holt sich der Google-TV-Watchdog die HOME-Taste zurück.

Optional, damit „zurück in die letzte App“ **exakt** antwortet (statt sich die
zuletzt hier gestartete App zu merken):

```bash
adb shell appops set com.piotv.navigate GET_USAGE_STATS allow
```

Optional, für das „Now Playing“-Feld oben links (ohne diese Freigabe bleibt es
leer, siehe [docs/INSTALL.md](docs/INSTALL.md) Schritt 5b):

```bash
adb shell cmd notification allow_listener com.piotv.navigate/.NowPlayingListener
```

## Konfiguration

Einstellbar sind **zwei Sachen**: die Reihenfolge der Apps und ob das
„Now Playing“-Feld erscheint. Beides steht in einer Datei:

```
/data/data/com.piotv.navigate/files/navigate.conf                          (Reihenfolge)
/data/data/com.piotv.navigate/files/settings.conf                          (Einstellungen)
/storage/emulated/0/Android/data/com.piotv.navigate/files/…                (adb push, beide)
```

```ini
# navigate.conf — Reihenfolge der Apps
com.netflix.ninja
de.swr.avp.ard.tv
```

```ini
# settings.conf — Einstellungen
now_playing=on
```

Eine Paketnamen-Zeile pro Eintrag, in Anzeige-Reihenfolge. Nicht (mehr)
installierte Einträge fallen still weg, der Rest folgt sprachsortiert. Format,
Grenzen und Beispiele für beide Dateien: [docs/CONFIG.md](docs/CONFIG.md).



## Architektur in fünf Sätzen

1. **Eine** Activity, **ein** Zustand: das Raster. Zwei Home-Activities in einer
   App machen die HOME-Auflösung mehrdeutig — der HOME-Druck zeigt dann einen
   Auswahldialog.
2. Der „zugeklappt / aufgeklappt“-Umschalter ist **kein zweites Layout**: der
   Adapter meldet zugeklappt nur eine Zeile, das Raster hängt unten am Rand und
   ist eine Zeile hoch. Beim Aufklappen wird nichts umgebaut — es kommen Zellen
   hinzu.
3. **Ein opakes, gewöhnliches Fenster.** Das Theme setzt nur
   `windowBackground`/`colorBackground`: keine Translucenz, keine eigenen
   Fenster-Animationen, keine Fenstergrößen-Tricks. Der Grund steht in
   [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — die translucenten Fassungen bis
   0.6 waren auf dem Zielgerät bedienbar, aber unsichtbar.
4. Keine AndroidX, kein Compose, kein Hilt, keine Coroutines: `GridView`,
   `LauncherApps`, `LruCache`, ein Executor, ein Handler. Was fehlt, kostet
   nichts.
5. **Symbole liegen zusätzlich auf der Platte** (WebP): nach einem
   Prozess-Neustart muss keine fremde APK mehr gemappt werden — auf einem
   1,4-GB-Gerät war das der größte Einzelposten im Speicher.

Ausführlich: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Projektstruktur

```
app/src/main/java/com/piotv/navigate/
  LauncherActivity.kt        die Home-Seite: Raster, „…“, Reihenfolge, Starten, Lebenszyklus
  NowPlayingListener.kt      der Dienst, der den Blick in die Media-Sessions erlaubt
  data/AppRepository.kt      App-Liste (zwei Kategorien), Prozess-Singleton
  data/AppEntry.kt           ein startbarer Eintrag
  data/AppCatalog.kt         Auswahl und Grundordnung der App-Liste (getestet)
  data/Order.kt              Anzeige-Reihenfolge, „eine Position nach vorne“ (getestet)
  data/OrderCodec.kt         Parser/Serializer der Reihenfolge-Datei (getestet)
  data/OrderStore.kt         die Datei selbst, mit Import über adb push
  data/ConfigFile.kt         Datei an zwei Orten: lesen, schreiben, Import (getestet)
  data/NavigateSettings.kt   die Einstellungen als Wert (heute: nowPlaying)
  data/SettingsCodec.kt      Parser/Serializer der Einstellungsdatei (getestet)
  data/SettingsStore.kt      settings.conf — dieselbe Mechanik wie OrderStore
  data/NowPlaying.kt         die laufende Media-Session lesen (MediaSessionManager)
  data/NowPlayingText.kt     daraus eine Zeile machen (getestet)
  data/NowPlayingBoard.kt    eine Zeile je App — und wann sie sich ändert (getestet)
  ui/AppAdapter.kt           Rasterzellen (Apps; `limit` = eine Zeile oder alle)
  ui/HomeGridView.kt         GridView + Fokus-Korrektur (Markierung beim Fokusverlust)
  ui/GridColumns.kt          Spaltenzahl des Rasters (getestet)
  ui/IconGeometry.kt         Einpassung der Symbole in die Zelle (getestet)
  ui/IconLoader.kt           Symbole: Speicher-Cache → Platte → Ressourcen
  ui/IconDiskCache.kt        WebP-Symbole im internen Speicher (+ Schlüssel, getestet)
  ui/SettingsActivity.kt     die Einstellungsseite (langes OK auf „…“)
  ui/NowPlayingPanel.kt      die Zeilen oben links: bauen, takten, anspringen
  ui/AppMenu.kt              das Kontextmenü (langes OK auf einer App)
  ui/Ticker.kt               ein Takt auf dem Main-Thread (Uhr · grün · Now Playing)
tools/make_assets.py         Icon-Vektoren + TV-Banner generieren
tools/check_docs_drift.sh    Doku-Gate: dokumentierte Messwerte ↔ gebautes APK
docs/                        ARCHITECTURE · CONFIG · INSTALL · DEVICE_PERF
```

## Bekannte offene Punkte

Ehrlich aufgeführt, weil sie beim Lesen der Doku sonst überraschen:

1. **Startzeit und Speicher sind alte Werte.** Die dokumentierten 128 ms und
   ~56 MB stammen aus 0.1, der Fassung mit der unteren Zeile. Für 0.13 sind sie
   **nicht** neu gemessen. Messbefehle: [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).
2. **Der Grund der früheren Unsichtbarkeit ist eine Korrelation, kein Beweis.**
   Alle translucenten Fassungen waren auf dem Zielgerät unsichtbar, alle dort
   sichtbaren Apps haben opake Fenster, und ein translucentes Fenster hält die
   App darunter am Leben (nur pausiert). 0.7 nimmt deshalb ein opakes Fenster —
   ob genau das die Ursache war, zeigt erst der Blick auf das Gerät.
3. **Die Reihenfolge auf dem Fernseher ändert sich nur schrittweise.** Langes
   Drücken schiebt eine App *eine* Position nach vorne; für einen großen Umbau
   ist die Datei der bequemere Weg.
4. **BACK auf der Home-Seite geht in die letzte App** (ohne bekannte App: ohne
   Wirkung). `finish()` wäre der naheliegende Weg, aber das System startet eine
   Home-Activity danach sofort neu (gemessen: der Task wechselte von t2667 auf
   t2668) — nur ein Flackern.
5. **Die exakte „letzte App“ braucht den Nutzungszugriff.** Ohne ihn entscheidet
   der Merker (die zuletzt über Navigate gestartete App) — richtig im Normalfall,
   blind, wenn die App von woanders kam (Fernbedienungstaste, Google-TV-Zeile).
6. **Die Einrichtung braucht ADB.** Home-Rolle und Google-TV-Watchdog sind zwei
   Befehle (docs/INSTALL.md) — ein Launcher kann keine Berechtigungsdialoge
   zeigen.
7. **Eine Konfigurationsdatei aus einer älteren Fassung bleibt liegen.** Auf dem
   Testgerät lag noch eine `navigate.conf` der 0.4-Reihe
   (`favorites=…`, `max_favorites=6`, `columns=0`, `show_clock=false`). Die
   Reihenfolge-Datei liest sie als Paketnamen-Liste: die Favoriten-Zeile wirkt
   damit als Reihenfolge (das ist brauchbar), die drei Einstellungszeilen landen
   als unsichtbarer Müll (`6`, `0`, `false`) in der Liste und werden beim
   nächsten Speichern mitgeschrieben. Aufräumen:
   `adb push` einer Datei mit einer Paketnamen-Zeile pro Eintrag (docs/CONFIG.md).
