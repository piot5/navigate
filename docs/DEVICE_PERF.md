# Geräte-Messwerte — Chromecast HD

Alle Werte mit dem Release-APK (`assembleRelease`, R8 + Resource-Shrinking)
auf dem Zielgerät gemessen, nicht geschätzt.

## Gerät

| | |
|---|---|
| Modell | Chromecast HD (Google TV) |
| Android | 14 (API 34) |
| CPU/ABI | Amlogic, `armeabi-v7a` (32-bit) |
| Anzeige | 1920 × 1080 bei Dichte 320 → **960 × 540 dp** |
| RAM | 1.439.332 kB total, ~200 MB verfügbar im Betrieb |
| Heap-Limit | 256 MB |

## Größe

| | |
|---|---|
| Release-APK | **75 KB** (76.430 B, 37 Dateien) |
| davon `classes.dex` | **69.284 B** (68 KB) — die einzige Dex-Datei |
| Debug-APK (ohne R8) | 890.792 B (870 KB) |
| Abhängigkeiten | keine außer `kotlin-stdlib` (von R8 beschnitten) |

Der Vergleich Debug → Release ist der ganze Performance-Plan in einer Zeile:
R8 entfernt die ungenutzte Kotlin-Stdlib, `shrinkResources` die ungenutzten
Ressourcen. Weniger Dex heißt kürzere Kaltstartzeit.

## Messwerte, maschinenlesbar

Dieser Block ist die Quelle der Wahrheit für
[`tools/check_docs_drift.sh`](../tools/check_docs_drift.sh): das Skript liest die
Werte hier, vergleicht sie mit dem gebauten Release-APK und bricht bei einer
Abweichung ab. Die Prosa in dieser Datei und in README.md muss dieselben Zahlen
nennen — sonst driften die Dokumente auseinander und der Leser glaubt der
falschen. (Genau das war in 0.3 der Fall: 73.686 B dokumentiert, 73.679 B
gebaut.)

```
release_apk_bytes=76430
release_dex_bytes=69284
release_dex_files=1
apk_files=37
unit_tests=93
```

Warum die APK-Größe reproduzierbar ist: die VCS-Info-Datei
(`META-INF/version-control-info.textproto`) wird seit 0.4 nicht mehr mitgepackt
(`vcsInfo.include = false`). Sie enthielt den Git-Zustand des Builds und
variierte zwischen 42 B („NO_VALID_GIT_FOUND") und 120 B (Commit-Hash) — eine
dokumentierte Byte-Zahl wäre damit nicht nachprüfbar gewesen.

## Startzeit

```bash
adb shell am force-stop com.piotv.navigate
adb shell am start -W -n com.piotv.navigate/.LauncherActivity
```

| Fall | `TotalTime` |
|---|---|
| Prozess läuft bereits (Normalfall, HOME-Druck) | **128 ms** |
| HOME-Druck über laufender App | nur Fensteränderung (`fillxwrap`), kein Activity-Neustart → nicht messbar, nicht sichtbar |

Der Launcher-Prozess wird vom System warmgehalten (Home-App). Der HOME-Druck
ändert in der Regel nur Fenstergröße und Sichtbarkeit — deshalb ist die
gefühlt relevante Zeit der erste Frame nach dem Aufklappen des Docks, nicht
ein Prozessstart.

`force-stop` auf die Home-App ist ein Sonderfall: das System startet sie sofort
wieder (`LaunchState: HOT`), die Messung ist also realistisch.

## Fenstergeometrie (der Beweis für die zwei Modi)

```bash
adb shell dumpsys window windows | grep -A12 'com.piotv.navigate/com.piotv.navigate'
```

| Modus | `mAttrs` | `Requested w/h` | Bedeutung |
|---|---|---|---|
| DOCK | `(0,0)(fillxwrap) gr=BOTTOM CENTER` | 1920 × **272** | 136dp Dock + Rand, die App bleibt darüber sichtbar |
| GRID | `(0,0)(fillxfill) gr=TOP START CENTER` | 1920 × 1080 | Home-Seite über den ganzen Bildschirm |

## Fensterformat: der gemessene Kompromiss

```bash
adb shell dumpsys usagestats | grep -E 'ACTIVITY_(RESUMED|PAUSED|STOPPED)'
adb shell dumpsys window windows | grep -A12 'Window.*termux'
```

| Fensterformat | App darunter | Zeile sichtbar? |
|---|---|---|
| `TRANSPARENT` (Theme-Wert, so ausgeliefert) | `ACTIVITY_STOPPED`, Fenster weg (`mHasSurface=false`) | **ja** |
| `TRANSLUCENT` (`window.setFormat`) | `ACTIVITY_PAUSED`, Fenster bleibt (`mViewVisibility=0x0`) | **nein** (gemessen) |

Messung zum zweiten Fall (eigenes Wurzel-Layout probeweise knallrot gefüllt):

```bash
adb shell screencap -p /data/local/tmp/shot.png
# Pixel im Fensterbereich: (4,3,3) und (2,2,2) — die App darunter, kein Rot
```

Gleichzeitig meldete das System das Fenster als gesund:

```
mDrawState=HAS_DRAWN   mLastHidden=false
Surface: shown=true layer=0 alpha=1.0
SurfaceFlinger: VisibleRegion [0,808,1920,1080]
```

Der Compositor hielt die Fläche also für sichtbar, die Pixel sagten etwas
anderes. Ausgeliefert wird deshalb `TRANSPARENT`: eine Zeile, die man nicht
sieht, ist keine Zeile. Der Preis (die App wird gestoppt) steht in
[ARCHITECTURE.md](ARCHITECTURE.md).

## Warum `screencap` lange „kaputt" war

`adb shell screencap` lieferte 0 Bytes, solange Netflix im Vordergrund war:

```bash
$ adb shell screencap -p /data/local/tmp/s.png ; echo $?
1
$ adb shell ls -la /data/local/tmp/s.png
-rw-rw-r-- 1 shell shell 0 ...        # leere Datei
```

Ursache ist **nicht** das Gerät und **nicht** die App: Netflix setzt für seine
Wiedergabe `FLAG_SECURE` (DRM), und eine gesicherte Fläche lässt sich nicht
aufnehmen — die Aufnahme schlägt dann komplett fehl. Nach `am force-stop
com.netflix.ninja` (bzw. mit jeder anderen App im Vordergrund) funktioniert
`screencap` sofort:

```bash
$ adb shell screencap -p /data/local/tmp/shot.png ; ls -la ...
-rw-rw-r-- 1 shell shell 103982 ...   # 103 KB, 1920x1080 RGBA
```

Merkregel für die Fehlersuche auf diesem Gerät: erst `screencap` prüfen, dann
urteilen. Eine leere Datei heißt „hier läuft gerade DRM", nicht „Screenshot geht
nicht".


## Speicher

```bash
adb shell dumpsys meminfo com.piotv.navigate
```

| | |
|---|---|
| TOTAL PSS | ~56 MB |
| Private Dirty | ~17,6 MB |
| Private Clean | ~25,7 MB (gemappte App-Ressourcen für die Symbole, vom Kernel verdrängbar) |
| Native Heap | ~10,8 MB |
| Dalvik Heap | ~1,3 MB |
| Views | 76 |
| Activities | 1 |

Gemessen direkt nach dem Aufbau des Rasters mit 15 App-Symbolen (Raster- und
Dock-Größe, LRU-Cache max. 8 MB). Der „Private Clean"-Anteil entsteht, weil
zum Dekodieren eines Symbols die Ressourcen der jeweiligen App gemappt werden
müssen — file-backed, also unter Speicherdruck verdrängbar.

## App-Erkennung (die Messung, die die Architektur bestimmt hat)

```bash
adb shell cmd package query-activities -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER | grep packageName
adb shell cmd package query-activities -a android.intent.action.MAIN \
  -c android.intent.category.LEANBACK_LAUNCHER | grep packageName
```

| Kategorie | Pakete (Gerät) |
|---|---|
| `LAUNCHER` | filemanager, vending, disney+, youtube, netflix, projengmenu, stremio, termux, termux.boot, zdf, shizuku |
| `LEANBACK_LAUNCHER` | dieselben **+ amazonvideo, wbd.stream, ard.tv, tv.settings** |

→ 15 Apps im Raster, davon 3, die eine reine `LAUNCHER`-Abfrage (und damit
`LauncherApps.getActivityList`) nie gefunden hätte.

## HOME-Auflösung (die Messung, die die Priorität bestimmt hat)

```bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Vorher:

```
priority=1 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.google.android.tungsten.setupwraith/.RecoveryActivity
```

Obwohl Navigate die HOME-Rolle hielt. Nach `pm disable-user` von `setupwraith`
und `priority=10` im eigenen HOME-Filter:

```
START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]
          cmp=com.piotv.navigate/.LauncherActivity} from uid 0
```

## HOME-Druck: die zwei Zustellwege

```bash
adb shell logcat -d | grep 'START u0.*HOME'
```

| Zustellweg | `result code` | Bedeutung | Reaktion |
|---|---|---|---|
| Launcher im Hintergrund | 2 | `START_TASK_TO_FRONT` | Dock/Home-Seite aufbauen |
| Zeile steht schon | 3 | `START_DELIVERED_TO_TOP` | `onNewIntent` → Zeile schließen, zurück zur App |

Wichtig für eigene Änderungen: bei **jedem** HOME-Druck läuft vor `onNewIntent`
ein `onPause` der Activity. Ein Lebenszyklus-Flag (`isResumed`) ist dort
deshalb wertlos — die erste Fassung des zweiten HOME-Drucks scheiterte genau
daran (nachgemessen mit einer Diagnoseausgabe in `onPause`/`onNewIntent`).

## Nachmessen (0.4 ist gebaut, aber nicht auf dem Gerät gemessen)

Stand 0.4, hier im Projekt gebaut und geprüft — **nicht** auf dem Chromecast:

| | 0.1 (gemessen auf dem Gerät) | 0.2 (lokal gebaut) | 0.3 (lokal gebaut) | 0.4 (lokal gebaut) |
|---|---|---|---|---|
| Release-APK | 71.321 B | 73.721 B | 73.686 B | **76.430 B** |
| `classes.dex` | 61 KB | 65.664 B (65 KB) | 65.608 B (64 KB) | **69.284 B** (68 KB) |
| Dateien im APK | 36 | 36 | 36 | **37** |
| Unit-Tests | 23 `@Test` (README behauptete 25) | 40 | 47 | **93** |
| Lint-Warnungen | nicht geprüft | nicht geprüft | 15 | **0** |
| Coverage (Zeilen / Zweige) | nicht gemessen | nicht gemessen | nicht gemessen | **18,3 % / 25,4 %** |

0.3 ist **35 B kleiner** als 0.2, weil toter Code entfernt wurde
(`ForegroundProbe.hasUsageAccess()` und ein ungenutzter String) — kein
Laufzeitverhalten geändert, aber R8 hat weniger zu behalten.

0.4 ist **2.744 B größer** als 0.3, und die Dex-Datei wächst um **3.676 B**: vier
neue Klassen samt Tests (HOME-Regeln, App-Auswahl, Symbol-Einpassung,
Dock-Schlüssel) und zwei neue Ressourcen-Dateien (Backup-Regeln für Android 11
und älter). Gegenläufig wirkt der weggefallene VCS-Eintrag (120 B) — genau der
Grund, warum die Zahl jetzt nachprüfbar ist. Ein 76-KB-APK bleibt für das
Zielgerät irrelevant klein; die Größe wird hier weitergeführt, weil sie der
billigste Frühwarn-Indikator für eine gewachsene Abhängigkeit ist.

### Lint und Coverage (die zwei neuen Gates)

```bash
sh gradlew :app:lintDebug                    # 0 Warnungen, warningsAsErrors = true
sh gradlew :app:unitTestCoverageReport       # HTML + XML unter build/reports/jacoco/
sh gradlew :app:unitTestCoverageVerification # Gate: 16 % Zeilen / 22 % Zweige
bash tools/check_docs_drift.sh               # der Block oben gegen das gebaute APK
```

Die Coverage-Zahl braucht eine Einordnung, sonst ist sie irreführend: **18,3 %
Zeilen** klingt nach wenig, ist aber das Verhältnis über *alle* Klassen — und die
Klassen, die ein Gerät brauchen (Activity, Adapter, Loader, Repository, Probe:
rund 700 der 987 Zeilen), stehen darin und liegen bei 0 %. Jede Klasse, die ohne
Gerät prüfbar ist, ist **vollständig** abgedeckt:

| Klasse | Zeilen | Coverage |
|---|---|---|
| `ModeDecider` | 3 | 100 % |
| `HomeKeyPolicy` | 3 | 100 % |
| `ConfigCodec` (+ `Config`) | 61 | 100 % |
| `FavoritesCodec` | 14 | 100 % |
| `FavoritesConfig` | 55 | 92,7 % (ohne `get(context)`) |
| `AppCatalog` (+ `Candidate`) | 14 | 100 % |
| `UsageEventFolder` | 10 | 100 % |
| `GridColumns` | 2 | 100 % |
| `IconGeometry` (+ `Fit`) | 10 | 100 % |
| `IconCacheKeys` | 4 | 100 % |
| `DockSignature` | 1 | 100 % |

Die Gesamtquote ist damit ein **Regressions**schutz, kein Qualitätsziel: neue
Logik ohne Test senkt sie und lässt den Build scheitern. Wer die Zahl wirklich
heben will, braucht Robolectric oder instrumentierte Tests — beides wäre eine
neue Abhängigkeit, und das ist in diesem Projekt eine bewusste Entscheidung
(siehe app/build.gradle.kts).

Die Dex-Datei bleibt eine einzige und winzig — das ist die Voraussetzung dafür,
dass ein Startup-Profile/DEX-Layout überhaupt etwas bringen könnte
(developer.android.com/topic/performance/baselineprofiles/dex-layout-optimizations:
„startup code should fit into `classes.dex`"). Bei einem 65-KB-Dex ist dieser
Hebel bereits ausgeschöpft; bei einem Flutter-APK mit 10 MB wäre er es nicht.

### `am start -W` misst den HOME-Druck nicht

Ein HOME-Druck über einer laufenden App ist meist nur eine Fensteränderung
(`fillxwrap`), kein Activity-Start — `TotalTime` sagt dazu nichts. Die
belastbare Messung sind die Frame-Zeiten:

```bash
# 1) Ist-Zustand des HOME-Drucks
adb -s <ip>:5555 shell dumpsys gfxinfo com.piotv.navigate reset
adb -s <ip>:5555 shell input keyevent KEYCODE_HOME
adb -s <ip>:5555 shell dumpsys gfxinfo com.piotv.navigate framestats | head -40
adb -s <ip>:5555 shell dumpsys meminfo com.piotv.navigate | head -20

# 2) Kaltstart (falls das System den Prozess getötet hat)
adb -s <ip>:5555 shell am force-stop com.piotv.navigate
adb -s <ip>:5555 shell am start -W -n com.piotv.navigate/.LauncherActivity
```

### Die drei Posten einzeln belegen

Jeder Posten aus „Das Zeitbudget eines HOME-Drucks" (ARCHITECTURE.md) lässt sich
isolieren, indem man ihn kurz ausschaltet und dieselbe Messung wiederholt:

| Frage | Eingriff | Erwartung |
|---|---|---|
| Was kostet die Vordergrund-Abfrage? | `ForegroundProbe.snapshot()` auf `null` festnageln (Heuristik greift) | Differenz = Preis der Abfrage |
| Was kostet die Konfigurationsdatei? | `reloadIfChanged()` im `onResume` überspringen | Differenz = FUSE-`stat` |
| Was kostet das Dekodieren der Symbole? | `show_icons=false` in `navigate.conf` | Differenz in PSS und ersten Frames |

### AOT-Kompilierung (kein Code, wirkt auf jeden Kaltstart)

```bash
adb -s <ip>:5555 shell pm compile -m speed -f com.piotv.navigate
# Rückweg:  pm compile --reset com.piotv.navigate
```

`pm compile -m` kennt auf modernen Geräten nur `speed`, `speed-profile` und
`verify` (auf einem Android-16-Gerät per `pm help` geprüft). Google nennt für
profilgeführte AOT-Kompilierung „about 30 %" schnellere Code-Ausführung ab dem
ersten Start — genau der Hebel, den ein per ADB installiertes APK **nicht**
automatisch bekommt (Baseline-Profile greifen bei Nicht-Play-Installationen erst
beim nächtlichen Background-Dexopt).

### Erwartete Zielwerte (Schätzung, nicht Messung)

| | 0.1 gemessen | Ziel 0.2 |
|---|---|---|
| HOME-Druck warm | 128 ms | 60–80 ms |
| PSS nach Rasteraufbau | ~56 MB | ~35 MB (mit `show_icons=false` ~30 MB) |
| Kaltstart | nicht gemessen | −20…30 % gegenüber 0.1 |

Diese Zeile ist bewusst als Schätzung markiert: sie steht hier, damit die
nächste Messung eine Erwartung hat, an der sie scheitern darf.

## Reproduzieren

```bash
bash build_navigate.sh release
NAVIGATE_DEVICE=<ip>:5555 bash install_navigate.sh
# HOME-Test
adb -s <ip>:5555 shell am start -n com.netflix.ninja/.MainActivity
adb -s <ip>:5555 shell input keyevent KEYCODE_HOME   # Dock
adb -s <ip>:5555 shell input keyevent KEYCODE_HOME   # zurück zu Netflix
adb -s <ip>:5555 shell dumpsys window windows | grep -A12 'com.piotv.navigate/com'
```
