# Changelog

Alle nennenswerten Änderungen an Navigate, neueste zuerst.

Das Format folgt [Keep a Changelog](https://keepachangelog.com/de/1.1.0/);
die Versionierung folgt [Semantic Versioning](https://semver.org/lang/de/).

> Die Version wird in `app/build.gradle.kts` gepflegt (`versionCode`/`versionName`),
> nicht hier.

## 0.4

Kein neues Bedienverhalten: diese Fassung schließt Prüflücken (Lint, Coverage,
Doku), repariert den Import der Konfigurationsdatei und macht die dokumentierten
Messwerte prüfbar. Auf dem Gerät **nicht** nachgemessen — die Werte für Startzeit
und Speicher stammen weiter aus 0.1.

### Behoben

- **Der Import der Konfigurationsdatei (`adb push`) fand nie statt.** Der Merker
  der Drossel stand auf `Long.MIN_VALUE`; `now - Long.MIN_VALUE` läuft über und
  wird negativ, die Bedingung „innerhalb des Intervalls" war damit **immer** wahr
  — der Import-Pfad wurde nie erreicht. Gefunden hat das der Test, der in 0.4
  neu geschrieben wurde (`FavoritesConfigTest.eine neuere Import-Datei wird
  uebernommen`), also genau die Prüfung, die es vorher nicht gab. Betroffen war
  nur der in docs/CONFIG.md beschriebene Weg; die interne Datei und die
  Navigate-Einstellungen waren nie betroffen.
- **Die Symbole in den Navigate-Einstellungen hatten keine Bildbeschreibung** —
  ein Screenreader las für jede Zeile „App-Symbol". Raster und Dock setzten den
  App-Namen schon, die Einstellungen fehlten.
- **„Maximal 1 Favoriten"** und „Favoriten: 1 von 6" — beide Texte sind jetzt
  Plurals (`<plurals>`), die Zahl entscheidet die Form.

### Hinzugefügt

- **Lint als Gate** (`warningsAsErrors = true` in app/build.gradle.kts): der
  Build bricht an jeder Warnung, lokal wie in der CI. Die 15 Warnungen aus 0.3
  sind abgearbeitet statt unterdrückt — behoben (Plurals, Overdraw,
  RedundantLabel, zwei ungenutzte Ressourcen, fehlende `contentDescription`,
  Monochrom-Ebene des Icons, Backup-Regeln für Android 11 und älter) oder an der
  Fundstelle mit Begründung unterdrückt (`TranslucentOrientation`,
  `DiscouragedApi`). `lint` meldet jetzt „No issues found."
- **Coverage der Unit-Tests** über das in Gradle eingebaute JaCoCo:
  `unitTestCoverageReport` schreibt HTML und XML, `unitTestCoverageVerification`
  ist das Gate (16 % Zeilen / 22 % Zweige). Gemessen in 0.4: **18,3 % Zeilen /
  25,4 % Zweige** über alle Klassen; ohne die Android-Klassen wären es ~100 % —
  jede ohne Gerät prüfbare Klasse ist vollständig abgedeckt.
- **Doku-Gate** (`tools/check_docs_drift.sh`): prüft die dokumentierten Messwerte
  gegen das gebaute APK (APK-Bytes, Dex-Bytes, Dex-Dateien, Dateizahl,
  Testzahl). Es hat sich beim ersten Lauf selbst bewiesen — die in 0.3
  dokumentierten 73.686 B stimmten nicht mit dem Nachbau (73.679 B).
- **Release-Workflow** (`.github/workflows/release.yml`): auf einem Tag `v*`
  laufen Tests, Coverage, Lint und Doku-Gate, danach entsteht ein GitHub-Release
  mit dem APK. Signiert, wenn `NAVIGATE_KEYSTORE_BASE64` gesetzt ist.
- **46 neue Unit-Tests** (47 → **93**) und vier neue, ohne Gerät prüfbare
  Bausteine: `HomeKeyPolicy` (die HOME-Regeln — genau die Fälle, die auf dem
  Gerät schiefgingen), `AppCatalog` (Auswahl und Reihenfolge der App-Liste,
  inklusive des Fehlers „Prime Video/WOW/ARD fehlen"), `IconGeometry`
  (Einpassung der Symbole) und `DockSignature` (Cache-Schlüssel der Zeile).
- **Gradle-Cache in der CI** — der Release-Build allein braucht auf schwacher
  Hardware ~15 min, ohne Cache lädt jeder Lauf die Abhängigkeiten neu.

### Geändert

- **`FavoritesConfig` ist ohne Gerät prüfbar**: der Konstruktor bekommt zwei
  Dateien und eine Uhr statt eines `Context`; der Produktivpfad liegt in
  `get(context)`. Damit ist die Datei-Semantik (kaputter Inhalt, Import-Regeln,
  Drossel) testbar geworden — und hat den Fehler oben gefunden.
- **`AppRepository`** enthält nur noch die PackageManager-Abfragen; die Regeln
  (deaktivierte Apps, eigener Launcher in beiden Varianten, erster Eintrag
  gewinnt, leere Beschriftung, Sortierung) stehen in `AppCatalog`.
- **`LauncherActivity`** enthält nur noch die Verdrahtung der HOME-Regeln; die
  Regeln selbst stehen in `HomeKeyPolicy`. Ebenso wanderten die Symbol-Einpassung
  nach `IconGeometry` und der Dock-Cache-Schlüssel nach `DockSignature`.
- **Die VCS-Info-Datei wird nicht mehr mitgepackt** (`vcsInfo.include = false`,
  pro Build-Typ — auf oberster Ebene gibt es den Schalter nicht). Sie enthielt den
  Git-Zustand und machte die APK-Größe nicht reproduzierbar: genau die Ursache
  der 7-B-Abweichung in 0.3 (73.686 B dokumentiert, 73.679 B gebaut; in 0.4
  gemessen: 42 B „NO_VALID_GIT_FOUND" gegen 120 B mit Commit-Hash). Das Release-APK
  ist dadurch 120 B kleiner, und die dokumentierte Größe ist nachbaubar.
- **Toter Zustand entfernt:** `LauncherActivity.launchedSinceDock` wurde an fünf
  Stellen geschrieben und nie gelesen — dieselbe Art Fund wie
  `ForegroundProbe.hasUsageAccess()` in 0.3.
- `versionCode` 4, `versionName` „0.4"; `targetSdk` bleibt 34 (Begründung im
  Buildfile).

### Bekannt / offen

- Unverändert aus 0.3: die App hinter der Zeile wird gestoppt (Theme-Format
  `TRANSPARENT`), der Rückweg von der Zeile ist eine Taste, ohne Nutzungszugriff
  entscheidet eine Heuristik, und **0.4 ist auf dem Gerät nicht nachgemessen**.
- Die Gesamt-Coverage bleibt niedrig (18,3 %), weil die Android-Klassen im
  Nenner stehen. Wer sie heben will, braucht Robolectric oder instrumentierte
  Tests — beides bewusst nicht in diesem Projekt (keine zusätzliche Abhängigkeit).

Kein Laufzeitverhalten geändert: diese Fassung räumt auf, schließt eine
Testlücke und stellt die Doku richtig. Auf dem Gerät **nicht** nachgemessen —
die Werte für Startzeit und Speicher stammen weiter aus 0.1.

### Geändert

- **Die Spaltenzahl des Rasters ist eine reine Funktion** (`ui/GridColumns.kt`,
  vorher eine private Methode der `LauncherActivity`). Verhalten unverändert:
  eine konfigurierte Zahl gilt weiter unverändert, `0` heißt Automatik
  (Breite / 150dp, begrenzt auf 4–8). Der Grund ist Testbarkeit, nicht Logik.
- **Toter Code entfernt:** `ForegroundProbe.hasUsageAccess()` wurde nie
  aufgerufen (die Abfrage fällt ohnehin auf `null` zurück), ebenso der String
  `toast_usage_hint`. Das Release-APK ist dadurch 35 B kleiner.
- **README richtiggestellt:** Der Abschnitt „Architektur in fünf Sätzen" nannte
  das Fensterformat `TRANSLUCENT`. Ausgeliefert wird `TRANSPARENT` (Theme) —
  genau wie in ARCHITECTURE.md und DEVICE_PERF.md beschrieben.
- **Kaputter Doku-Link** in `docs/INSTALL.md` (Anker zeigte auf eine Überschrift,
  die es nicht mehr gibt).
- **Debug-APK-Größe** in `docs/DEVICE_PERF.md`: 858 KB → 868 KB (888.854 B,
  nachgemessen).

### Hinzugefügt

- **7 Unit-Tests für die Spaltenzahl** (jetzt 47): Chromecast ergibt 6, die
  Automatik fällt nie unter 4 und geht nie über 8, eine konfigurierte Zahl wird
  nicht beschnitten.
- **CI** (`.github/workflows/ci.yml`): Unit-Tests und Release-Build auf jedem
  Push; das APK liegt danach als Artefakt bereit (unsigniert, solange keine
  Signing-Secrets gesetzt sind).
- **Versionskontrolle:** Das Projekt liegt jetzt unter `git` (`keystore/`,
  `local.properties` und alle Build-Ordner sind per `.gitignore` ausgenommen).

## 0.2

Auf dem Gerät noch nicht nachgemessen (kein Zugang zum Fernseher während der
Änderung) — die Änderungen zielen auf die in 0.1 gemessenen Kosten des
HOME-Drucks. Messbefehle: docs/DEVICE_PERF.md, Abschnitt „Nachmessen".

### Geändert

- **Vordergrund-Erkennung in zwei Fenstern.** Vorher wurde bei jedem HOME-Druck
  ein 6-Stunden-Fenster der Nutzungsstatistik auf dem Main-Thread durchlaufen
  (Tausende Ereignisse). Jetzt zuerst ein 5-Minuten-Fenster — der Normalfall,
  wenn eine App gerade lief — und nur bei leerem Ergebnis das lange Fenster als
  Absicherung.
- **Konfigurationsdatei liegt app-intern.** `/sdcard` läuft über den
  MediaProvider-FUSE-Daemon: `stat` und `open` gehen dort immer durch einen
  Userspace-Daemon, und die Datei wurde bei jedem HOME-Druck mehrfach geprüft.
  Der externe Pfad bleibt als **Import** für `adb push` erhalten (gedrosselt
  geprüft, nur bei neuerem Zeitstempel übernommen).
- **`FavoritesConfig.current()` liest nicht mehr.** Der Stand liegt im Speicher;
  nachgesehen wird nur noch in `reloadIfChanged()`.
- **Favoriten werden über einen Index nachgeschlagen.** `AppRepository` hält
  `byPackageName` — vorher entstand bei jedem HOME-Druck ein neues HashMap über
  alle Apps.
- **Fenstergröße wird nur noch bei echter Änderung gesetzt.** Ein HOME-Druck
  setzte sie zweimal (onNewIntent und onResume) — das zweite Mal war ein
  Relayout über Binder für nichts.
- **Fokus ohne Umweg.** `grid.post { requestFocus() }` ist jetzt ein direktes
  `requestFocus()` mit `post` nur als Rückfallebene.
- **Symbole liegen auf der Platte.** Jedes dekodierte Symbol wird als
  WebP-Datei (v1, 90 %) im internen Speicher gehalten; `IconLoader.warm()`
  füllt Speicher und Platte im Hintergrund vor, sobald die App-Liste steht.

### Hinzugefügt

- **Textmodus** (`show_icons=false`): das Raster zeigt nur App-Namen, die
  Symbol-Bitmaps werden freigegeben. Gedacht für minimale Last auf dem
  1,4-GB-Gerät; die Zeile behält ihre Symbole.
- **Import-Pfad im Einstellungsbildschirm** — beide Wege (intern und für
  `adb push`) stehen dort.
- **17 neue Unit-Tests** (jetzt 40): Ereignis-Faltung
  (`UsageEventFolderTest`), Cache-Schlüssel (`IconCacheKeysTest`),
  `show_icons` im Parser (`ConfigCodecTest`).

### Korrigiert

- **Die Testzahl in der 0.1-Dokumentation war falsch.** Sie nannte 25 Tests,
  im Code standen 23 `@Test`. Beide Zahlen sind jetzt ausgezählt und stimmen:
  23 (0.1) + 17 (0.2) = 40.

## 0.1

Erste Fassung. Auf dem Zielgerät (Chromecast HD, Google TV, Android 14)
durchgemessen und bedient.

### Hinzugefügt

- **Zwei Modi in einer Activity**: `GRID` (Home-Seite, alle Apps im Raster)
  und `DOCK` (nur die untere Zeile über der laufenden App). Der HOME-Druck
  entscheidet, welcher Modus erscheint.
- **Untere Zeile** mit Favoriten, Home, Navigate-Einstellungen und
  Systemeinstellungen. Fokus wandert mit ▼ aus dem Raster in die Zeile und mit
  ▲ zurück.
- **App-Raster** mit 6 Spalten auf 1080p/TV-Dichte, D-Pad-Navigation,
  Uhr oben rechts, Fokus-Markierung ohne Animationen.
- **App-Liste aus zwei Kategorien** (`LAUNCHER` ∪ `LEANBACK_LAUNCHER`) —
  auf dem Testgerät wären sonst Prime Video, WOW und ARD unsichtbar.
- **Konfigurationsdatei** `navigate.conf` für die Favoritenzeile
  (`favorites`, `max_favorites`, `columns`, `show_clock`), mit
  Kommentaren, Fehlertoleranz und Änderungserkennung zur Laufzeit.
- **Navigate-Einstellungen** als eigener Bildschirm: OK schaltet einen
  Favoriten an/aus, ◀ ▶ verschiebt ihn, jede Änderung geht sofort in die Datei.
- **Langes Drücken** im Raster setzt/entfernt einen Favoriten, in der Zeile
  entfernt es ihn.
- **Vordergrund-Erkennung** über die Nutzungsstatistik (Lebenszyklus der
  letzten fremden App) mit dokumentierter Heuristik als Rückfallebene.
- **Symbole** werden im Hintergrund dekodiert, auf Zielgröße skaliert, mit
  22-%-Rundung versehen und in einem 8-MB-LRU-Cache gehalten.
- **23 Unit-Tests** für Entscheidungslogik, Konfigurationsparser und
  Favoritenliste — ohne Gerät lauffähig.
- Generator für Icon-Vektoren und TV-Banner (`tools/make_assets.py`).
- Doku: README, INSTALL, CONFIG, ARCHITECTURE, DEVICE_PERF.

### Behoben (während der Entwicklung, auf dem Gerät gefunden)

- **HOME landete bei der System-App statt bei Navigate.** Ursache: Googles
  `com.google.android.tungsten.setupwraith/.RecoveryActivity` trägt einen
  HOME-Filter mit `priority=1` und ein Watchdog startet ihn immer wieder neu.
  Lösung: eigener HOME-Filter mit `priority=10` plus Deaktivieren von
  `setupwraith` (dokumentiert, umkehrbar).
- **Die Zeile war eine Sackgasse: die App darunter ließ sich nicht mehr
  bedienen.** Ursache: die Zeile hält den Tastaturfokus, die App ist nur
  pausiert. Lösung: HOME (zweiter Druck) und BACK schließen die Zeile und
  bringen die App per explizitem Launcher-Intent zurück in den Vordergrund —
  `finish()` funktioniert für eine Home-Activity nicht, das System startet sie
  sofort neu (gemessen: Task wechselte von t2667 auf t2668).
- **Der zweite HOME-Druck wurde nicht erkannt.** Ursache: das System pausiert
  die Activity bei jedem HOME-Druck, *bevor* es den Intent zustellt
  (`onPause → onNewIntent → onResume`) — ein Lebenszyklus-Flag ist dort immer
  schon `false`. Lösung: eigener Merker „Zeile steht sichtbar" statt `isResumed`.
- **Der HOME-Druck zeigte die Home-Seite statt der Zeile.** Ursache: der
  Launcher steht beim HOME-Druck selbst schon als `RESUMED` in der
  Nutzungsstatistik, das „neueste Vordergrund-Ereignis" war also immer er
  selbst. Lösung: es wird der Lebenszyklus der letzten **fremden** App
  ausgewertet (`PAUSED`/`RESUMED` = lebt, `STOPPED` = beendet).
- **Drei Apps fehlten im Raster.** `LauncherApps.getActivityList` kennt nur
  `CATEGORY_LAUNCHER`; Prime Video, WOW und ARD tragen nur
  `CATEGORY_LEANBACK_LAUNCHER`. Lösung: Vereinigung beider Kategorien über den
  PackageManager (mit `QUERY_ALL_PACKAGES`).
- **Screenshot ging nicht** — `screencap` lieferte 0 Bytes, solange Netflix im
  Vordergrund war (DRM → `FLAG_SECURE`). Kein App-Fehler, aber die Ursache für
  mehrere falsche Diagnosen; siehe docs/DEVICE_PERF.md.
- **`setting_row_height`** war im Layout falsch geschrieben (Build-Fehler).
- **`IconLoader`** war als Singleton gedacht, der Companion aber privat.

### Bekannt / offen

- **Die App hinter der Zeile wird gestoppt** (Theme-Format `TRANSPARENT`, damit
  die Zeile sichtbar ist). Mit `window.setFormat(TRANSLUCENT)` bleibt sie
  pausiert und sichtbar, dann war der Fensterinhalt auf dem Zielgerät aber
  nicht mehr zu sehen. Beide Varianten gemessen, Entscheidung dokumentiert:
  README „Bekannte offene Punkte", docs/DEVICE_PERF.md.
- Ohne Nutzungszugriff entscheidet eine Heuristik statt der Messung.
- Eine offene IME-Tastatur der App darunter liegt über der Zeile (Fenster-Ebene).

