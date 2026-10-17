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

## 0.12 (Now Playing oben links, Einstellungen)

| | 0.11 | 0.12 |
|---|---|---|
| Release-APK | 59.060 B | **73.146 B** |
| `classes.dex` | 47.800 B | **68.608 B** |
| Dateien im APK | 27 | **31** |
| Unit-Tests | 63 | **94** |
| Coverage (Zeilen / Zweige) | 18,3 % / 25,6 % | **20,1 % / 27,3 %** |

Neu: das „Now Playing“-Feld oben links (App, Interpret, Titel, Cover — aus der
laufenden Media-Session, anklickbar: OK holt die spielende App zurück), eine
Einstellungsseite (langes OK auf „…“) und die gemeinsame Datei-Mechanik
(`ConfigFile`) für Reihenfolge und Einstellungen.

Was die Zahlen erklären und was nicht: die **vier zusätzlichen Dateien** im APK
sind die neuen Ressourcen — zwei Layouts und zwei Fokus-Drawables (Layouts und
Drawables liegen im APK als einzelne `res/*.xml`-Einträge, nicht in
`resources.arsc`). Die **Coverage steigt** gegenüber 0.11, obwohl die neuen
Android-Klassen (Einstellungsseite, Media-Session-Abfrage) im Nenner stehen: die
neuen Regeln (Einstellungsdatei, Now-Playing-Zeile) sind vollständig getestet.

Der Dex-Zuwachs ist der größte Posten, und er ist nachgemessen statt geschätzt:
die **neuen** Klassen (`NowPlayingListener`, `data/ConfigFile*`,
`data/NavigateSettings`, `data/NowPlaying*`, `data/Settings*`,
`ui/SettingsActivity*`) belegen im unminifizierten Debug-Dex **56.292 B**
(Summe der Einzel-Dex-Dateien aus `dexBuilderDebug`, ohne `LauncherActivity`).
Nach R8 bleiben davon die 20.808 B, um die `classes.dex` gewachsen ist — ein Teil
davon trägt Kotlin selbst bei (Sequenzen, `BaseAdapter`, die Kleinschreibung ohne
Locale). Was diese Aufteilung **nicht** zeigt: wie viel R8 an anderer Stelle
zusätzlich entfernt hat; dafür müssten beide Fassungen gebaut und die Dex-Ausgabe
verglichen werden.

Ehrlich dazu: die zwei Einstiegspunkte sind der Preis für das Feld. Wer die
59-KB-Fassung behalten will, braucht den Zugang nicht zu erteilen — ohne
Freigabe bleibt das Feld leer, die Klassen sind trotzdem im APK.

### Auf dem Zielgerät nachgesehen (2026-10-16)

Nicht nur gebaut, sondern auf dem Chromecast HD gesehen — mit einer pausierten
ZDF-Session als Beispiel:

| Prüfung | Ergebnis |
|---|---|
| Feld mit Cover, App-Name, Titel, Zustand | `[Cover] ZDF · Jagd auf Osama bin Laden · pausiert` (Screenshot) |
| Erreichbar mit der Fernbedienung | ▲ ▲ vom Raster → der Block hat den Fokus (blauer Rahmen) |
| **OK auf dem Block** | `topResumedActivity=com.zdf.android.mediathek/.feature.player.ui.tv.PlayerActivity` — der **Player**-Bildschirm, also `sessionActivity` der Session (nicht das Hauptmenü der App) |
| Danach HOME | `topResumedActivity=com.piotv.navigate/.LauncherActivity`, Feld wieder da |
| Freigabe | `settings get secure enabled_notification_listeners` enthält `com.piotv.navigate/com.piotv.navigate.NowPlayingListener`; logcat: „Allowing notification listener …“ |

Was das **nicht** zeigt: ein laufender (nicht pausierter) Titel wurde auf dem
Gerät nicht geprüft, und die Auswahl „erste wirklich spielende Session“ wurde nur
mit einer Session getestet — bei mehreren gleichzeitigen Sessions ist sie eine
Regel, keine Messung.

## 0.11 (Kontextmenü, laufende Aktualisierung, drei Grüntöne)

| | 0.10 | 0.11 |
|---|---|---|
| Release-APK | 58.302 B | **59.060 B** |
| `classes.dex` | 45.560 B | **47.800 B** |
| Dateien im APK | 29 | **27** |
| Unit-Tests | 60 | **63** |
| Coverage (Zeilen / Zweige) | 19,9 % / 26,4 % | **18,3 % / 25,6 %** |

Neu: Kontextmenü auf langes OK (Position, Deinstallieren, Schließen), laufende
Aktualisierung der grünen Schrift (5-Sekunden-Takt, nur solange sichtbar) und ein
eigener Grünton für die zuletzt geöffnete App. Die Dateizahl im APK **sinkt**,
weil zwei Farb-Selektoren entfallen sind — die Beschriftungsfarbe setzt jetzt der
Adapter direkt.

## 0.10 (grün = „in den letzten 24 Stunden im Vordergrund“)

0.9 wollte „die App läuft noch“ über die Ereignis-Faltung bestimmen („letztes
Ereignis nicht `STOPPED`“). Auf dem Zielgerät ist das **falsch herum**: weil unser
Fenster opak ist, bekommt die App darunter beim HOME-Druck ein
`ACTIVITY_STOPPED` — die Regel hätte also **nie** etwas grün gemacht.

Nachgemessen direkt nach dem HOME-Druck (`dumpsys activity processes`):

```
*APP* UID 10101 ProcessRecord{… 28159:com.netflix.ninja/u0a101}
*APP* UID 10102 ProcessRecord{… 31660:com.disney.disneyplus/u0a102}
*APP* UID 10106 ProcessRecord{… 16828:com.wbd.stream/u0a106}
*APP* UID 10064 ProcessRecord{… 16905:com.android.vending/u0a64}
```

Die **Prozesse** leben also weiter, nur ihre Activity ist gestoppt. Genau das
trifft „zuletzt im Vordergrund“ — und die Nutzungsstatistik liefert es billig als
Zeitstempel je Paket (`UsageStats.getLastTimeUsed`), ohne Durchlauf über alle
Ereignisse:

- grün = in den letzten **24 Stunden** im Vordergrund (nie vor dem letzten
  Systemstart),
- die Abfrage läuft im Hintergrund-Thread und höchstens alle 30 Sekunden,
- ohne Nutzungszugriff bleibt der letzte bekannte Stand stehen.

Ehrlich dazu: „Prozess lebt“ ist damit **angenähert**, nicht bewiesen — eine App,
die vor 23 Stunden benutzt und inzwischen vom System beendet wurde, ist grün.

| | 0.9 | 0.10 |
|---|---|---|
| Release-APK | 58.149 B | **58.302 B** |
| `classes.dex` | 44.964 B | **45.560 B** |
| Dateien im APK | 29 | 29 |
| Unit-Tests | 65 | **60** |
| Coverage (Zeilen / Zweige) | 20,9 % / 29,6 % | **19,9 % / 26,4 %** |

Die Testzahl sinkt, weil die Faltung `ForegroundLog.alive` samt ihren sechs Tests
entfallen ist: die Frage beantwortet jetzt ein Zeitstempel, keine Ereignisliste.

## 0.9 (grüne Schrift für laufende Apps)

Die App-Beschriftungen im Raster sind **grün, solange die App noch läuft**, und
die Beschriftung steht jetzt zentriert unter dem Symbol.

> **Korrigiert in 0.10:** Die Regel dieser Fassung („letztes Ereignis nicht
> `STOPPED`“) war auf dem Gerät wirkungslos — siehe den Abschnitt 0.10 oben. Der
> Rest dieses Abschnitts beschreibt, was 0.9 gebaut hat.

| | 0.8 | 0.9 |
|---|---|---|
| Release-APK | 56.616 B | **58.149 B** |
| `classes.dex` | 42.788 B | **44.964 B** |
| Dateien im APK | 28 | **29** |
| Unit-Tests | 59 | **65** |
| Coverage (Zeilen / Zweige) | 20,9 % / 27,8 % | **20,9 % / 29,6 %** |

## 0.8 (zurück in die letzte App) — historisch

Ein HOME-Druck auf der **schon sichtbaren** Home-Seite holt die zuletzt
benutzte App zurück — und BACK ebenso (erst klappt es das aufgeklappte Raster
zu, dann geht es in die App). Damit ist HOME ein Umschalter statt einer
Sackgasse.

Die Antwort auf „welche App war zuletzt vorne?" kommt aus zwei Quellen, in
dieser Reihenfolge:

1. **Nutzungsstatistik** (`UsageStatsManager.queryEvents`): exakt. Braucht den
   App-Op `GET_USAGE_STATS` (`adb shell appops set com.piotv.navigate
   GET_USAGE_STATS allow`) — ein Launcher kann ihn nicht selbst erfragen. Das
   kurze Fenster (5 Minuten) entscheidet im Normalfall, das lange (6 Stunden) ist
   die Absicherung: nicht die Abfrage kostet, sondern die Ereignismenge.
2. **Merker**: die letzte App, die über Navigate gestartet wurde
   (`SharedPreferences`, nach einem Systemstart verworfen). Ohne Zugriff ist das
   die ehrliche Näherung — richtig, wenn man die App von hier gestartet hat.

Warum nicht `ActivityManager.getRunningTasks`: seit Android 5 liefert das
normalen Apps nur noch die eigenen Tasks — für einen Launcher wertlos.

| | 0.7 | 0.8 |
|---|---|---|
| Release-APK | 55.537 B | **56.616 B** |
| `classes.dex` | 40.868 B | **42.788 B** |
| Dateien im APK | 28 | 28 |
| Unit-Tests | 54 | **59** |
| Coverage (Zeilen / Zweige) | 22,1 % / 27,4 % | **20,9 % / 27,8 %** |

Die Coverage sinkt leicht, weil `LastApp` (Nutzungsstatistik, SharedPreferences)
reine Android-Verdrahtung ist und ohne Gerät nicht läuft; die prüfbare Faltung
(`ForegroundLog`) liegt bei 100 %. Details: [ARCHITECTURE.md](ARCHITECTURE.md).

## 0.7 (von Grund auf neu gebaut) — historisch

Die Fassungen bis 0.6 waren auf dem Zielgerät **bedienbar, aber unsichtbar**:
`dumpsys window` meldete das Fenster als gezeichnet und sichtbar
(`mDrawState=HAS_DRAWN`, `mHasSurface=true`, `Surface: shown=true alpha=1.0`),
der Input-Dispatcher gab ihm den Fokus („Focus entering … LauncherActivity“), die
App ließ sich blind bedienen — zu sehen war nur ein kurzes Aufblitzen bei einem
Klick. Alle SurfaceFlinger-Ebenen meldeten `alpha=1.000000`.

Gemeinsam war allen unsichtbaren Fassungen ein **translucentes Fenster**
(`windowIsTranslucent=true`, alle Fenster-Animationen auf `@null`); jede auf
diesem Fernseher sichtbare App hat ein opakes Fenster, bei dem die App darunter
gestoppt wird. 0.7 nimmt deshalb das gewöhnlichste Theme, das es gibt, und baut
die Oberfläche neu: unten eine Zeile Apps, darunter der „…“-Knopf.

| | 0.6 | 0.7 |
|---|---|---|
| Release-APK | 54.463 B | **55.537 B** |
| `classes.dex` | 40.916 B | **40.868 B** |
| Dateien im APK | 27 | **28** |
| Unit-Tests | 51 | **54** |
| Coverage (Zeilen / Zweige) | 22,8 % / 26,7 % | **22,1 % / 27,4 %** |

Die Baugrößen sind nachgebaut und vom Doku-Gate geprüft. **Nicht** neu gemessen
sind Startzeit, Speicher und Frame-Zeit (siehe unten) — und ob das opake Fenster
tatsächlich die Ursache war, ist aus einer Korrelation geschlossen, nicht
bewiesen (Details in [ARCHITECTURE.md](ARCHITECTURE.md)).

## 0.6 (die Zeile ist entfernt) — historisch

Die untere Zeile gibt es nicht mehr: ein HOME-Druck zeigt nur noch das Raster
(siehe [CHANGELOG](../CHANGELOG.md)). Für den Launcher heißt das: keine
Nutzungsstatistik-Abfrage, kein Zeilen-Aufbau, kein Fenster-Relayout pro Druck —
und deutlich weniger Code.

| | 0.4 | 0.6 |
|---|---|---|
| Release-APK | 76.410 B | **54.463 B** |
| `classes.dex` | 69.260 B | **40.916 B** |
| Dateien im APK | 37 | **27** |
| Unit-Tests | 93 | **51** |
| Coverage (Zeilen / Zweige) | 18,3 % / 25,4 % | **22,8 % / 26,7 %** |

Die Baugrößen sind nachgebaut und vom Doku-Gate geprüft. **Nicht** neu gemessen
sind Startzeit, Speicher und Frame-Zeit: die weiter unten dokumentierten Werte
(128 ms, ~56 MB, 23 ms) stammen aus Fassungen **mit** der Zeile. Die Erwartung
für 0.6 ist kleiner — kein Nutzungsstatistik-Aufruf, kein Zeilen-Aufbau, ein
Fenster weniger —, aber das ist eine Erwartung, keine Messung. Messbefehle:
„Nachmessen" am Ende dieser Datei.

## 0.4 auf dem Gerät (gemessen 2026-10-14) — historisch

> Diese Messreihe gehört zur Fassung **mit** der unteren Zeile. Sie steht hier,
> weil sie die beiden Fehler belegt, die den Umbau ausgelöst haben: das Fenster
> war korrekt angemeldet, die Zeile war trotzdem nicht zu sehen — und das
> Zwei-Zustands-Design kostete bei jedem HOME-Druck Arbeit für einen Inhalt, den
> das Raster auch liefert.

Erste Messung seit 0.1 — 0.2, 0.3 und 0.4 liefen bis hierher nur auf dem
Rechner. Installiert mit `adb install -r` (gleiche Signatur, App-Daten erhalten),
Gerät wie oben, die drei Einrichtungsschritte aus [INSTALL.md](INSTALL.md) waren
bereits aktiv.

### Was funktioniert (funktional geprüft)

| Prüfung | Ergebnis |
|---|---|
| Home-Seite | **15 Apps** im Raster: ARD Mediathek, Dateimanager +, Disney+, Einstellungen, HBO Max, Netflix, Play Store, Prime Video, Projectivy Launcher, Shizuku, Stremio, Termux, Termux:Boot, YouTube, ZDF |
| davon nur über LEANBACK gefunden | **Prime Video, HBO Max (`wbd.stream`), ARD Mediathek** — genau die drei, die eine reine `LAUNCHER`-Abfrage verschweigt |
| Favoritenzeile | **9 Elemente**: Netflix · YouTube · HBO Max · Play Store · Disney+ · ZDF · Home · Navigate · Einstellungen |
| HOME über laufender App | Fenster korrekt angemeldet (`fillxwrap`, `Requested 1920 × 272`, `frame=[0,808][1920,1080]`) — **die Zeile war auf dem Bildschirm nicht zu sehen** (Nutzer-Beobachtung, siehe Fenstergeometrie unten) |
| HOME erneut | App wieder vorn und fokussiert (Play Store) |
| Abstürze | keine (Crash-Puffer leer) |

Die Favoritenzeile ist zugleich der Beweis für die Import-Reparatur aus 0.4: die
sechs Favoriten standen nur in der per `adb push` abgelegten `navigate.conf` im
externen Import-Pfad. Vor 0.4 hätte der Import nie stattgefunden (siehe
[CHANGELOG](../CHANGELOG.md)), die Zeile wäre leer geblieben.

### Werte

| | 0.1 (auf dem Gerät) | 0.4 (auf dem Gerät) |
|---|---|---|
| TOTAL PSS (Raster warm) | ~56 MB | **53,4 MB** (53.384–53.424 kB, 3 Proben) |
| TOTAL RSS | nicht gemessen | 110 MB |
| Views | 76 | **85** |
| Frame-Zeit beim HOME-Druck | nicht gemessen | **23 ms** (50./90./95. Perzentil über zwei Drucke) |

`am start -W` ist für eine Home-App keine sinnvolle Messung: das System hält den
Prozess warm, der Befehl meldet dann `TotalTime: 0`, weil nur das vorhandene
Fenster nach vorn kommt (die 128 ms aus 0.1 waren der Kaltstart-Sonderfall).
Belastbar ist die Frame-Zeit des Übergangs: **23 ms** für das Aufklappen der
Zeile.

### Zwei dokumentierte Aussagen reproduzieren nicht

1. **„Die App hinter der Zeile wird gestoppt"** — sie wird **pausiert**.
   Gemessen mit dem Play Store und mit WOW: `ACTIVITY_PAUSED`, Fenster bleibt
   (`mHasSurface=true`, `isOnScreen=true`). Die App läuft also weiter. Das ist
   das bessere Verhalten, aber es ist nicht das, was in 0.1 gemessen wurde.
2. **„Mit `TRANSLUCENT` ist die Zeile nicht sichtbar"** — diese Aussage stützte
   sich auf `screencap`, und `screencap` ist auf diesem Gerät für diese Frage
   untauglich: die Zeile wird als **Hardware-Overlay** komponiert
   (`composition type=DEVICE`, gültiger Puffer `1920 × 272`, `alpha=1.0`), und
   genau solche Ebenen lässt die Aufnahme weg. Belegt: bei stehender Zeile sind
   drei Aufnahmen in verschiedenen Zuständen **byte-identisch** (gleiche
   MD5-Summe) und zeigen die App darunter, während die Fensterebene der App einen
   gültigen Puffer hat.

   Der ausgelieferte Weg (`TRANSPARENT`) bleibt damit richtig — nur die
   Begründung war falsch: er liefert auf diesem Gerät **beides**, sichtbare Zeile
   und weiterlaufende App. Wer das nachmessen will, braucht die Fenster- und
   SurfaceFlinger-Daten, nicht `screencap`:

   ```bash
   adb shell dumpsys window windows | grep -A12 'navigate'
   adb shell dumpsys SurfaceFlinger | grep -A6 'LauncherActivity#'
   #   gültig: geomBufferSize=[0 0 1920 272]  composition type=DEVICE  alpha=1.000000
   ```

   Merkregel neben der DRM-Regel weiter unten: **ein leeres oder unverändertes
   `screencap`-Bild heißt nicht „nicht sichtbar"** — auf diesem Gerät fehlen
   Overlay-Ebenen im Bild.

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
release_apk_bytes=73146
release_dex_bytes=68608
release_dex_files=1
apk_files=31
unit_tests=94
coverage_lines_percent=20,1
coverage_branches_percent=27,3
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
| (einziger) | `(0,0)(fillxfill) gr=TOP START CENTER` | 1920 × 1080 | Home-Seite über den ganzen Bildschirm |

Seit 0.6 gibt es nur noch einen Zustand, und das Fenster ist immer der ganze
Bildschirm. Bis 0.5 war das DOCK-Fenster auf die Zeile verkleinert — gemessen
mit 0.4:

```
mAttrs={(0,0)(fillxwrap) gr=BOTTOM CENTER … ty=BASE_APPLICATION fmt=TRANSPARENT
Requested w=1920 h=272
Frames: frame=[0,808][1920,1080]
```

Das Fenster war also korrekt angemeldet, und das System lieferte den HOME-Intent
normal aus (`wm_on_resume_called`, `input_focus: Focus entering … reason=Window
became focusable`). Auf dem Bildschirm war die Zeile trotzdem nicht zu sehen —
gemeldet vom Nutzer, und genau das war die einzige strukturelle Differenz zum
Raster-Zustand, der sichtbar funktioniert. 0.5 hat die Größe angeglichen, 0.6
den zweiten Zustand entfernt: ein Fenster, ein Inhalt.

## Fensterformat: der gemessene Kompromiss

```bash
adb shell dumpsys usagestats | grep -E 'ACTIVITY_(RESUMED|PAUSED|STOPPED)'
adb shell dumpsys window windows | grep -A12 'Window.*termux'
```

| Fensterformat | App darunter | Zeile sichtbar? |
|---|---|---|
| `TRANSPARENT` (Theme-Wert, so ausgeliefert), 0.1 | `ACTIVITY_STOPPED`, Fenster weg (`mHasSurface=false`) | **ja** |
| `TRANSLUCENT` (`window.setFormat`), 0.1 | `ACTIVITY_PAUSED`, Fenster bleibt (`mViewVisibility=0x0`) | „nein" — per `screencap` gemessen, siehe Vorbehalt unten |
| `TRANSPARENT`, **0.4 nachgemessen** | `ACTIVITY_PAUSED`, Fenster bleibt (`mHasSurface=true`) | **ja** (Overlay mit gültigem Puffer) |

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

> **Vorbehalt, nachgemessen am 2026-10-14 mit 0.4:** Beide Zeilen dieser Tabelle
> sind so nicht mehr reproduzierbar. Die App darunter wird auch mit
> `TRANSPARENT` nur pausiert, und die „nicht sichtbar"-Feststellung für
> `TRANSLUCENT` beruhte auf `screencap`, das auf diesem Gerät
> Hardware-Overlay-Ebenen (und damit genau die Zeile) aus dem Bild lässt. Details
> und Belege im Abschnitt [„0.4 auf dem Gerät"](#04-auf-dem-gerät-gemessen-2026-10-14).
> Die Entscheidung für `TRANSPARENT` bleibt richtig — sie ist nur besser als
> angenommen: sichtbare Zeile **und** weiterlaufende App.

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

| | 0.1 | 0.4 (2026-10-14) |
|---|---|---|
| TOTAL PSS | ~56 MB | **53,4 MB** |
| TOTAL RSS | nicht gemessen | 110 MB |
| Private Dirty | ~17,6 MB | nicht einzeln erfasst |
| Private Clean | ~25,7 MB (gemappte App-Ressourcen für die Symbole, vom Kernel verdrängbar) | nicht einzeln erfasst |
| Native Heap | ~10,8 MB | ~7,0 MB |
| Dalvik Heap | ~1,3 MB | ~6,5 MB |
| Views | 76 | **85** |
| Activities | 1 | 1 |

Gemessen direkt nach dem Aufbau des Rasters mit 15 App-Symbolen (LRU-Cache max.
8 MB). Der „Private Clean"-Anteil entsteht, weil zum Dekodieren eines Symbols
die Ressourcen der jeweiligen App gemappt werden müssen — file-backed, also
unter Speicherdruck verdrängbar.

Beide Spalten gehören zur Fassung **mit** der unteren Zeile (85 Views = Raster +
9 Zeilen-Elemente). Für 0.6 ist der Speicher **nicht** neu gemessen; die
Erwartung ist niedriger, weil die Zeile und ihre neun Symbol-Views entfallen —
aber das ist eine Erwartung.

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
| Launcher im Hintergrund | 2 | `START_TASK_TO_FRONT` | Raster zeigen (`onCreate`/`onResume`) |
| Launcher steht schon vorn | 3 | `START_DELIVERED_TO_TOP` | `onNewIntent` → Raster zeigen |

Beide Wege enden in derselben idempotenten Arbeit (`onHomePressed()`), und das
ist Absicht: bei **jedem** HOME-Druck läuft vor `onNewIntent` ein `onPause` der
Activity (gemessen). Ein Zustandsmerker „Intent schon gesehen?" wäre dort
wertlos — genau daran ist die erste Fassung des zweiten HOME-Drucks gescheitert.

## Nachmessen (0.6 ist gebaut — die Gerätemessung dieser Fassung steht noch aus)

Stand 0.6, hier im Projekt gebaut und geprüft; die Zahlen sind vom Doku-Gate
geprüft. Die Gerätemessungen weiter oben (Startzeit, Speicher, Frame-Zeit)
stammen aus Fassungen **mit** der unteren Zeile. Die folgende Tabelle führt die
Baugrößen über die Fassungen:

| | 0.1 (gemessen auf dem Gerät) | 0.2 (lokal gebaut) | 0.3 (lokal gebaut) | 0.4 (lokal gebaut) | 0.6 (lokal gebaut) |
|---|---|---|---|---|---|
| Release-APK | 71.321 B | 73.721 B | 73.686 B | 76.430 B | **54.463 B** |
| `classes.dex` | 61 KB | 65.664 B (65 KB) | 65.608 B (64 KB) | 69.284 B (68 KB) | **40.916 B** (40 KB) |
| Dateien im APK | 36 | 36 | 36 | 37 | **27** |
| Unit-Tests | 23 `@Test` (README behauptete 25) | 40 | 47 | 93 | **51** |
| Lint-Warnungen | nicht geprüft | nicht geprüft | 15 | 0 | **0** |
| Coverage (Zeilen / Zweige) | nicht gemessen | nicht gemessen | nicht gemessen | 18,3 % / 25,4 % | **22,8 % / 26,7 %** |

0.3 ist **35 B kleiner** als 0.2, weil toter Code entfernt wurde
(`ForegroundProbe.hasUsageAccess()` und ein ungenutzter String) — kein
Laufzeitverhalten geändert, aber R8 hat weniger zu behalten.

0.4 ist **2.744 B größer** als 0.3, und die Dex-Datei wächst um **3.676 B**: vier
neue Klassen samt Tests und zwei neue Ressourcen-Dateien. Gegenläufig wirkt der
weggefallene VCS-Eintrag (120 B) — genau der Grund, warum die Zahl jetzt
nachprüfbar ist.

0.6 ist **21.947 B kleiner** als 0.4 und die Dex-Datei **28.344 B kleiner**: mit
der Zeile sind `DockController`, `DockSignature`, `SettingsActivity`,
`SettingsAdapter`, `FavoritesCodec`, `ForegroundProbe`, `UsageEventFolder`,
`ModeDecider`, `HomeKeyPolicy` und `Prefs` entfallen — dazu die Hälfte der
Ressourcen (27 statt 37 Dateien im APK). Das ist kein Sparentscheid am Detail,
sondern die Folge davon, dass der Launcher einen Zustand hat statt zwei.

### Lint und Coverage (die zwei neuen Gates)

```bash
sh gradlew :app:lintDebug                    # 0 Warnungen, warningsAsErrors = true
sh gradlew :app:unitTestCoverageReport       # HTML + XML unter build/reports/jacoco/
sh gradlew :app:unitTestCoverageVerification # Gate: 16 % Zeilen / 22 % Zweige
bash tools/check_docs_drift.sh               # der Block oben gegen das gebaute APK
```

Die Coverage-Zahl braucht eine Einordnung, sonst ist sie irreführend: **22,8 %
Zeilen** klingt nach wenig, ist aber das Verhältnis über *alle* Klassen — und die
Klassen, die ein Gerät brauchen (Activity, Adapter, Loader, Repository, Executor,
Toasts: rund 400 der 530 Zeilen), stehen darin und liegen bei 0 %. Jede Klasse,
die ohne Gerät prüfbar ist, ist **vollständig** abgedeckt:

| Klasse | Zeilen | Coverage |
|---|---|---|
| `ConfigCodec` (+ `Config`) | 40 | 100 % |
| `NavigateConfig` | 54 | 94,4 % (ohne `get(context)`) |
| `AppCatalog` (+ `Candidate`) | 15 | 100 % |
| `GridColumns` | 2 | 100 % |
| `IconGeometry` (+ `Fit`) | 10 | 100 % |
| `IconCacheKeys` | 4 | 100 % |

Die Gesamtquote ist damit ein **Regressions**schutz, kein Qualitätsziel: neue
Logik ohne Test senkt sie und lässt den Build scheitern. Wer die Zahl wirklich
heben will, braucht Robolectric oder instrumentierte Tests — beides wäre eine
neue Abhängigkeit, und das ist in diesem Projekt eine bewusste Entscheidung
(siehe app/build.gradle.kts).

Die Dex-Datei bleibt eine einzige und winzig — das ist die Voraussetzung dafür,
dass ein Startup-Profile/DEX-Layout überhaupt etwas bringen könnte
(developer.android.com/topic/performance/baselineprofiles/dex-layout-optimizations:
„startup code should fit into `classes.dex`"). Bei einem 41-KB-Dex ist dieser
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

### Erwartete Zielwerte für 0.6 (Schätzung, nicht Messung)

| | 0.1 gemessen | Erwartung 0.6 |
|---|---|---|
| HOME-Druck warm | 128 ms | **unter** den 128 ms — es entfallen Nutzungsstatistik-Abfrage, Zeilen-Aufbau und der zweite Fenster-Relayout |
| PSS nach Rasteraufbau | ~56 MB | **unter** 56 MB — die Zeile und ihre neun Symbol-Views sind weg |
| Kaltstart | nicht gemessen | unverändert (die App-Liste und die Symbole waren nie der Engpass) |

Diese Zeile ist bewusst als Schätzung markiert: sie steht hier, damit die
nächste Messung eine Erwartung hat, an der sie scheitern darf.

## Reproduzieren

```bash
bash build_navigate.sh release
NAVIGATE_DEVICE=<ip>:5555 bash install_navigate.sh
# HOME-Test: über einer laufenden App
adb -s <ip>:5555 shell am start -n com.netflix.ninja/.MainActivity
adb -s <ip>:5555 shell input keyevent KEYCODE_HOME
adb -s <ip>:5555 shell dumpsys window windows | grep -A12 'com.piotv.navigate/com'
adb -s <ip>:5555 exec-out screencap -p > home.png    # bei DRM-Apps 0 Bytes, siehe oben
```

Erwartung: **ein** Fenster, `(0,0)(fillxfill) gr=TOP START CENTER`,
`Requested w=1920 h=1080`, `mHasSurface=true`, `mDrawState=HAS_DRAWN` — und ein
`home.png` mit dem Raster.
