# Changelog

Alle nennenswerten Änderungen an Navigate, neueste zuerst.

Das Format folgt [Keep a Changelog](https://keepachangelog.com/de/1.1.0/);
die Versionierung folgt [Semantic Versioning](https://semver.org/lang/de/).

> Die Version wird in `app/build.gradle.kts` gepflegt (`versionCode`/`versionName`),
> nicht hier.

## 0.13.2

### Behoben

- **Der Release-Lauf scheiterte am Doku-Gate:** „unitTestCoverageReport.xml
  fehlt". `release.yml` ließ nur `unitTestCoverageVerification` laufen — das Gate
  rechnet aus den Ausführungsdaten, es schreibt aber keinen Bericht. Die
  Coverage-**Zahlen** liest das Doku-Gate aus der XML-Datei. Jetzt laufen beide
  Schritte auch dort, wie in `ci.yml` (der CI-Lauf war schon grün).

## 0.13.1

### Behoben

- **Die CI war rot — seit dem ersten Push.** Zwei Fehler, beide nur in der
  CI-Umgebung sichtbar, beide hier behoben:
  1. **`Release-APK` brach ab:** `Execution failed for task
     ':app:validateSigningRelease'` — „Keystore file … not found for signing
     config 'release'". Ursache: GitHub setzt ein **nicht gesetztes** Secret als
     leere Zeichenkette in die Umgebung, nicht als fehlende Variable;
     `System.getenv` liefert dann `""` statt `null`. Das Buildfile baute daraus
     eine Signing-Konfiguration mit leerem Passwort — und die zeigte auf einen
     Keystore, den es in der CI nicht gibt. Jetzt zählt ein leerer Wert als
     „nicht gesetzt", **und** die Keystore-Datei muss existieren; ohne beides
     bleibt das APK unsigniert, wie es diese Datei seit 0.4 beschreibt.
  2. **`Doku-Gate` brach ab:** es suchte `app-release.apk` und fand nichts, weil
     ein unsigniertes Release-APK `app-release-unsigned.apk` heißt. Das Gate nimmt
     jetzt **das gebaute** APK und vergleicht es mit dem passenden dokumentierten
     Wert: die unsignierte Fassung hat einen eigenen (67.840 B, 29 Dateien), weil
     die Signatur als `META-INF/CERT.*` im Archiv liegt. Das Dex ist in beiden
     Fällen byte-gleich.

### Geändert

- `ci.yml` und `release.yml` legen das gebaute APK unter einem **festen** Namen ab
  (`navigate-release.apk` bzw. `navigate-<version>.apk`). Ein fest verdrahteter
  Pfad auf `app-release.apk` war in einem der beiden Fälle falsch.
- Die beiden APK-Varianten samt Zahlen stehen in
  [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).

## 0.13

### Hinzugefügt

- **Eine „Now Playing“-Zeile je App** — statt genau einer Zeile für „die“ Session.
  Bis 0.12 verschwand eine der Angaben, wenn ein Hörbuch pausiert lief und daneben
  ein Radiosender: es gewann, wer „den Ton macht“. Jetzt bekommt **jede App mit
  einer aktiven Media-Session** ihre eigene Zeile (Cover inklusive), was spielt
  steht oben, und **OK auf einer Zeile holt genau diese App zurück**
  (`sessionActivity` der Session, sonst ihr Launcher-Intent).
- **Die Zeilen bleiben stehen.** Eine Zeile ändert sich nur, wenn sich für **diese
  App** etwas ändert (Textvergleich in `NowPlayingPanel.bind`); sonst wird weder
  geschrieben noch ein neues Cover geholt. Bis 0.12 wurde der Block bei jedem
  Durchlauf neu gesetzt.
- **Schonfrist und ehrliche Fehler.** Eine Session, die in **einem** Durchlauf
  fehlt (beim Titelwechsel der Normalfall), lässt ihre Zeile erst nach zwei
  erfolgreichen Abfragen ohne sie verschwinden. Eine **fehlgeschlagene** Abfrage
  (kein Benachrichtigungszugriff, Dienst antwortet nicht) löscht gar nichts:
  `NowPlaying.active` gibt dann `null` statt einer leeren Liste.
- `data/NowPlayingBoard.kt` — die Regel dahinter, ohne Android-Typen und mit 14
  Unit-Tests: eine Zeile je App, Reihenfolge, Persistenz, Schonfrist.

### Geändert

- **Drei Bausteine sind aus `LauncherActivity` ausgezogen** — die Datei ist von
  **769 auf 596 Zeilen** gefallen:
  `ui/Ticker.kt` (ein Takt statt drei `Handler`/`Runnable`-Paare),
  `ui/NowPlayingPanel.kt` (die Zeilen, ihr Takt, ihre Höhe),
  `ui/AppMenu.kt` (Kontextmenü und seine Aktionen). Die Activity hält weiterhin den
  einen Zustand und beantwortet als `Listener` die Fragen der Teile.
- Der **Cover-Cache** ist ein LRU (4 MB) statt **ein** Platz: mit einer Zeile je
  App werden mehrere Cover gleichzeitig gebraucht.
- Das **Doku-Gate** prüft zwei Dinge mehr: die Coverage-Zahlen (aus dem
  JaCoCo-Bericht) und dass in README/ARCHITECTURE keine andere Testzahl steht als
  im gebauten Testset — genau dieser Fehler stand in 0.12 in der Architektur (54
  neben 94).
- Das **Coverage-Gate** steht bei 18 % Zeilen / 25 % Zweige (vorher 16 %/22 %,
  gemessen 20,8 %/28,9 %).
- Die Home-Seite **begründet den oberen Rand des Rasters** jetzt aus der Höhe des
  Now-Playing-Blocks (`onHeightChanged`): bei zwei Zeilen läge die erste App-Zeile
  sonst darunter.

### Bekannt / offen

- **0.13 ist nicht auf dem Gerät gesehen.** Gebaut, getestet und gemessen, aber die
  Darstellung mehrerer Zeilen, der Abstand zum Raster und die Uhr neben einem
  mehrzeiligen Block sind ungeprüft ([docs/DEVICE_PERF.md](docs/DEVICE_PERF.md),
  „Was daran nicht auf dem Gerät geprüft ist“).

## 0.12

### Hinzugefügt

- **„Now Playing“ oben links** — sichtbar im zugeklappten Zustand, also dort, wo
  der Platz leer ist: **App-Name, Interpret und Titel** der laufenden
  Media-Session, dazu `· pausiert`, wenn die Session pausiert ist, und das
  **Cover** aus den Metadaten der Session (fehlt es, steht dort das App-Symbol).
  Quelle ist der `MediaSessionManager` (dieselbe Instanz, die
  `dumpsys media_session` zeigt); genommen wird die Session, die **wirklich
  spielt**, sonst die erste mit einem Titel. Die Zeile ist **eine** Zeile und
  schneidet mit einer Ellipse ab, damit ein langer Titel die Uhr rechts nicht
  überholt.
- **OK auf „Now Playing“ holt die spielende App zurück** — zuerst über
  `sessionActivity` der Session (der „Jetzt läuft“-Bildschirm, den die App selbst
  anbietet), sonst über ihren Launcher-Intent. Der Block ist dafür fokussierbar
  und zeigt seinen Fokus wie die Rasterzellen.
- **Einstellungen** — langes OK auf „…“ öffnet eine eigene Seite mit dem Schalter
  *Now Playing anzeigen* (`▲ ▼` wählt, `OK` schaltet um, `◀`/`BACK` zurück). Sie
  schreibt **sofort** in `files/settings.conf` (siehe
  [docs/CONFIG.md](docs/CONFIG.md)) und nennt beide Datei-Pfade, damit ein
  `adb push` nicht geraten werden muss.
- Ehrliche Rückmeldung statt stiller Untätigkeit: ist der Schalter an und der
  Zugriff fehlt, sagt die Seite es ausdrücklich.

### Wichtig zu wissen

- **Ohne Benachrichtigungszugriff bleibt das Feld leer.** Eine normale App darf
  die aktiven Media-Sessions nicht abfragen: `getActiveSessions` verlangt einen
  eingetragenen Benachrichtigungs-Listener (oder `MEDIA_CONTENT_CONTROL` — eine
  Signatur-Berechtigung, die `pm grant` nicht vergeben kann). Navigate bringt
  dafür `NowPlayingListener` mit: einen Dienst, der **nichts** liest — sein
  eingetragener Name ist der Ausweis. Die Freigabe ist ein Befehl
  ([docs/INSTALL.md](docs/INSTALL.md), Schritt 5b).
- Die Abfrage läuft **alle 5 Sekunden**, im Hintergrund-Thread und nur, solange
  die Home-Seite sichtbar und das Raster zugeklappt ist. Aufgeklappt gehört der
  Platz den Apps — dann verschwindet das Feld.

### Geändert

- **Die Datei-Mechanik liegt jetzt einmal** (`data/ConfigFile.kt`): Reihenfolge
  und Einstellungen teilen sich internen Speicher, `adb push`-Import, Drossel und
  Cache. In genau dieser Mechanik saß der Fehler, den `OrderStoreTest` in 0.4
  gefunden hat — zwei Kopien wären zwei Chancen, ihn wieder einzubauen.
  `OrderStore` verhält sich unverändert (seine Tests sind unverändert grün).
- `README.md`, `docs/ARCHITECTURE.md`, `docs/CONFIG.md` und `docs/INSTALL.md`
  beschreiben die zweite Einstellung und den optionalen Zugriff. Die Aussage
  „einstellbar ist nur die Reihenfolge“ stimmt nicht mehr und ist überall
  richtiggestellt.

## 0.11

### Hinzugefügt

- **Kontextmenü auf langes OK** (langes Drücken im Raster): *Position nach
  vorne*, *Position nach hinten*, *App deinstallieren*, *App schließen*, *Alle
  Apps schließen*. Deinstallieren zeigt den System-Dialog (`ACTION_DELETE` +
  `REQUEST_DELETE_PACKAGES`); Schließen geht über
  `ActivityManager.killBackgroundProcesses` (+ `KILL_BACKGROUND_PROCESSES`) —
  das ist der einzige Weg, den eine normale App hat, und er greift nur für Apps
  im Hintergrund (auf der Home-Seite der Normalfall).
- **Die grüne Schrift aktualisiert sich laufend** (alle 5 Sekunden, nur solange
  die Home-Seite sichtbar ist). Die Abfrage bleibt ein Zeitstempel-Blick in die
  Nutzungsstatistik, im Hintergrund-Thread.
- **Die zuletzt geöffnete App hat einen eigenen Grünton** — eine Stufe heller als
  die übrigen aktiven Apps.

### Behoben

- **Grün war bei ausgewählter Zelle nicht zu sehen.** Die Farbe hing an einem
  Zustands-Selector (`state_selected` → weiß). Jetzt hängt sie nur noch am
  Zustand der App: grün bleibt grün, die Auswahl zeigt der blaue Rahmen.
- **Der „…“-Knopf steht jetzt zentriert** unter der App-Zeile (vorher rechts).

## 0.10

### Behoben

- **Die grüne Markierung aus 0.9 hätte nie gegriffen.** Die Regel war „das letzte
  Lebenszyklus-Ereignis ist nicht `STOPPED`“ — auf dem Zielgerät bekommt die App
  darunter beim HOME-Druck aber genau dieses `ACTIVITY_STOPPED` (Folge des opaken
  Fensters), obwohl ihr **Prozess** weiterlebt. Nachgemessen mit
  `dumpsys activity processes`: Netflix, Disney+, WOW und der Play Store waren
  alle noch da.
- **Neue Regel:** grün = in den letzten **24 Stunden** im Vordergrund (nie vor dem
  letzten Systemstart), gelesen aus den Zeitstempeln der Nutzungsstatistik
  (`UsageStats.getLastTimeUsed`) — billig, im Hintergrund-Thread, höchstens alle
  30 Sekunden. Ehrlich dazugesagt: „Prozess lebt“ ist damit angenähert, nicht
  bewiesen.
- Die Ereignis-Faltung `ForegroundLog.alive` und ihre sechs Tests sind entfallen
  (sie beantwortete die Frage nicht); `ForegroundLog.lastResumed` bleibt für
  „zurück in die letzte App“.

## 0.9

### Hinzugefügt

- **Grüne Beschriftung für Apps, die noch laufen.** Grundlage ist dieselbe
  Nutzungsstatistik wie bei „zurück in die letzte App“: ein Paket gilt als
  laufend, solange sein letztes Lebenszyklus-Ereignis nicht `STOPPED` ist
  (pausiert = läuft weiter). Die Faltung (`ForegroundLog.alive`) ist reine Logik
  und getestet; die Abfrage läuft im Hintergrund-Thread und höchstens alle 30
  Sekunden — ein 6-Stunden-Fenster auf dem HOME-Pfad wäre genau der Posten, den
  0.2 dort entfernt hat. Ohne Nutzungszugriff bleibt nichts grün, statt etwas
  falsch grün zu färben.
- Zwei Zustandslisten `app_label` / `app_label_running`: die **Auswahl** bleibt in
  beiden Fällen weiß, damit „ausgewählt“ unabhängig davon aussieht, ob die App
  läuft.

### Behoben

- **Die App-Beschriftung stand nicht zentriert unter dem Symbol.** `item_app.xml`
  hatte kein `android:gravity` — Android setzt Text dann linksbündig in die
  Zelle, und ein kurzer Name klebte am linken Rand. Jetzt `gravity="center"` +
  `textAlignment="center"`.

## 0.8

### Hinzugefügt

- **HOME und BACK holen die zuletzt benutzte App zurück.** Ein HOME-Druck auf der
  schon sichtbaren Home-Seite ist damit ein Umschalter statt einer Sackgasse;
  BACK klappt erst ein aufgeklapptes Raster zu und geht dann in die App.
  Die Antwort auf „welche App war zuletzt vorne?“ kommt aus der
  Nutzungsstatistik (App-Op `GET_USAGE_STATS`, optional) und sonst aus dem
  Merker der zuletzt über Navigate gestarteten App — die Faltung der Ereignisse
  (`ForegroundLog`) ist reine Logik und getestet.
- **`PACKAGE_USAGE_STATS` wieder im Manifest** — ausdrücklich optional und nur
  für diese eine Frage; ohne den App-Op startet Navigate unverändert.

## 0.7

**Von Grund auf neu gebaut** — weil die Fassungen bis 0.6 auf dem Zielgerät zwar
bedienbar, aber **unsichtbar** waren. Ein HOME-Druck zeigt jetzt: unten eine
Zeile mit Apps, darunter den „…“-Knopf; „…“ klappt das ganze Raster auf.

### Geändert

- **Das Fenster ist opak und völlig gewöhnlich.** Das Theme setzt nur noch
  `windowBackground`/`colorBackground`; `windowIsTranslucent`,
  `windowDisablePreview`, der eigene `windowAnimationStyle` mit lauter
  `@null`-Einträgen und alle Fenstergrößen-Tricks sind entfernt.
  **Grund (gemessen):** Mit dem translucenten Theme war das Fenster auf dem
  Gerät gezeichnet, fokussiert, bedienbar — und nicht zu sehen; nur bei einem
  Klick blitzte kurz etwas auf. Alle SurfaceFlinger-Ebenen meldeten
  `alpha=1.000000`, `mDrawState=HAS_DRAWN`, `Surface: shown=true alpha=1.0`. Ein
  translucentes Fenster lässt die App darunter am Leben (nur pausiert); jede auf
  diesem Fernseher sichtbare App hat ein opakes Fenster. Details:
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- **Aufbau neu:** eine senkrechte Spalte am unteren Rand. Zugeklappt meldet der
  Adapter genau **eine Zeile** (das Raster ist `wrap_content` hoch), darunter
  steht der „…“-Knopf in einer eigenen Zeile. Aufgeklappt bekommt das Raster per
  `layout_weight` den ganzen Platz und scrollt. Kein zweites Layout, kein
  zweites Fenster, keine Animation.
- **Ein Symbol mehr in der Zeile.** Der „…“-Knopf belegt keine Rasterzelle mehr:
  die Zeile zeigt jetzt alle Spalten (auf dem Chromecast 6 statt 5 Apps).
- **Orange Auswahl-Highlight entfernt, blauer Rahmen bleibt.** Der orange
  Hintergrund kam vom Theme (`listSelector`); `android:listSelector="@null"`
  schaltet ihn nicht ab, jetzt steht dort `@android:color/transparent`. Die
  Markierung selbst bleibt wie sie war: `bg_grid_item.xml` (abgerundet, blauer
  2dp-Rahmen). `HomeGridView` räumt sie ab, wenn der Fokus auf den „…“-Knopf
  wandert.
- **Einstellbar ist nur noch die Reihenfolge.** `Order`, `OrderCodec`,
  `OrderStore` ersetzen `ConfigCodec`/`NavigateConfig`/`FavoritesCodec`; die
  Konfigurationsdatei enthält eine Paketnamen-Zeile pro Eintrag. `columns`,
  `show_clock`, `show_icons` und die Favoriten sind entfallen (die Uhr ist
  immer an, Symbole immer an, Spalten kommen aus der Bildschirmbreite).
- **Reihenfolge auf dem Fernseher:** langes OK auf einer App schiebt sie eine
  Position nach vorne und speichert sofort.
- **Kleiner gemessen:** Release-APK **55.537 B**, `classes.dex` **40.868 B**,
  54 Tests, Zeilen-Coverage **22,1 %** (Zweige 27,4 %).

## 0.6

**Die untere Zeile ist weg.** Ein HOME-Druck zeigt nur noch das Raster mit allen
startbaren Apps — kein Overlay, keine Favoritenzeile, keinen zweiten Zustand.
Das ist zugleich die Performance-Änderung: der HOME-Druck hat keinen zweiten
Inhalt mehr, für den er vorher bei **jedem** Druck eine Nutzungsstatistik-Abfrage,
einen Aufbau der Zeile und einen Fenster-Relayout bezahlt hat.

### Geändert

- **Ein Zustand statt zwei.** `LauncherMode`, `ModeDecider` und die ganze
  Fenstergrößen-Umschaltung entfallen; das Fenster ist immer der ganze
  Bildschirm. Was bleibt, ist der Inhalt: das Raster.
- **Vordergrund-Erkennung entfernt** (`ForegroundProbe`, `UsageEventFolder`,
  `ForegroundSnapshot`, `Prefs`). Sie existierte nur, um „läuft eine App?" zu
  beantworten — die Frage stellt sich nicht mehr. Damit fällt auch die
  Berechtigung `PACKAGE_USAGE_STATS` aus dem Manifest (und mit ihr der
  Einrichtungsschritt „Nutzungszugriff").
- **Untere Zeile entfernt** (`DockController`, `DockSignature`, `item_dock.xml`,
  `bg_dock.xml`, `ic_home`/`ic_tune`/`ic_settings`, Dock-Dimensionen und -Farben).
  Langes Drücken im Raster schaltet damit auch keinen Favoriten mehr um.
- **Navigate-Einstellungen entfernt** (`SettingsActivity`, `SettingsAdapter`) —
  der Bildschirm war ausschließlich der Favoriten-Editor. Die verbleibenden
  Einstellungen (`columns`, `show_clock`, `show_icons`) stehen weiterhin in der
  Konfigurationsdatei (docs/CONFIG.md).
- **`FavoritesConfig` → `NavigateConfig`, `FavoritesCodec` entfällt.** Die Datei
  hält jetzt nur noch die Raster-Einstellungen. Schlüssel früherer Fassungen
  (`favorites`, `max_favorites`) werden ignoriert statt als Fehler behandelt —
  eine alte Datei auf dem Gerät stört also nicht.
- **Kleiner und schlanker gemessen:** Release-APK **54.463 B** statt 76.410 B,
  `classes.dex` **40.916 B** statt 69.260 B, 27 statt 37 Dateien im APK. Die
  Testzahl sinkt mit dem entfernten Code von 93 auf 51 (die Tests für die
  entfernten Regeln sind mit ihnen gegangen); die Zeilen-Coverage steigt dabei
  von 18,3 % auf **22,8 %** (Zweige 26,7 %).
- **Doku-Ehrlichkeit:** Der Theme-Eintrag mit lauter `@null`-Animationen schaltet
  den Übergang **nicht** ab — die Shell ersetzt eine fehlende Animation durch
  ihre eigene Vorgabe (im Log: `loadAnimation: anim=AlphaAnimation …
  isEntrance=true`). Das steht jetzt an der Fundstelle und in docs/ARCHITECTURE.md.
- **`install_navigate.sh` trifft nur noch Fernsehgeräte.** Ohne
  `NAVIGATE_DEVICE` nahm das Skript das *erste erreichbare* Gerät — bei einem
  gleichzeitig am adb hängenden Test-Telefon war das die falsche Zielscheibe, und
  der Fehler wäre lautlos gewesen. Jetzt wird `ro.build.characteristics` geprüft
  (muss `tv` enthalten), Emulatoren und Telefone werden übersprungen, und das
  gewählte Gerät wird mit Modell ausgegeben. Ein anderes Ziel erzwingt
  `NAVIGATE_DEVICE`.

## 0.5

### Behoben

- **Die Zeile über der laufenden App war auf dem Zielgerät nicht zu sehen.** Das
  DOCK-Fenster war bis 0.4 auf die Höhe der Zeile verkleinert (Höhe
  `WRAP_CONTENT`, `Gravity.BOTTOM`). `dumpsys window` meldete es dabei korrekt
  (`(0,0)(fillxwrap) gr=BOTTOM CENTER`, `Requested w=1920 h=272`,
  `frame=[0,808][1920,1080]`, Fläche mit gültigem Puffer, `alpha=1.0`), und das
  System lieferte den HOME-Intent normal aus (`wm_on_resume_called`,
  `input_focus: Focus entering … reason=Window became focusable`) — auf dem
  Bildschirm kam von der Zeile nichts an. Die Fenstergröße war die **einzige**
  strukturelle Differenz zum GRID-Modus, der sichtbar funktioniert; sie ist jetzt
  in beiden Modi dieselbe (ganzer Bildschirm). Der Modus steckt nur noch im
  Inhalt: GRID malt die Wurzel deckend, DOCK lässt sie transparent und zeigt nur
  die Zeile. Messbelege: [docs/DEVICE_PERF.md](docs/DEVICE_PERF.md).

- **Die CI scheiterte in der Stufe „Android SDK".** `android-actions/setup-android@v3`
  ruft `sdkmanager tools` auf, und das Paket „tools" gibt es im SDK-Repository
  nicht mehr (ersetzt durch cmdline-tools). Der Schritt stand seit 0.3 in der
  Datei und war nie gelaufen. Jetzt wird nur geprüft, was fehlt — das
  Runner-Image bringt SDK, Lizenzen und cmdline-tools mit.
- **Lint schlug in der CI fehl, obwohl es lokal grün war.** Ursache ist keine
  Code-Stelle, sondern eine netzabhängige Prüfung: `AndroidGradlePluginVersion`
  meldete „A newer version of Gradle than 8.14.3 is available: 8.14.6". Lokal
  lief Lint mit `--offline` und schwieg deshalb. Die drei Versions-Prüfungen
  (`AndroidGradlePluginVersion`, `GradleDependency`, `NewerVersionAvailable`)
  sind jetzt abgeschaltet: Wrapper (mit sha256) und AGP/Kotlin sind hier
  absichtlich gepinnt, und eine Warnung, die bei jeder Veröffentlichung
  stromaufwärts rot wird, ist kein Qualitätssignal. Lehre für den lokalen Lauf:
  das Lint-Gate mindestens einmal **online** prüfen, sonst prüft man eine andere
  Menge als die CI.

### Geändert

- **`.gitignore` vervollständigt**: Kotlin-/AGP-Ausgaben (`.kotlin/`, `captures/`,
  `*.dex`, `*.class`, `*.hprof`), Signing-Material auch einzeln (`*.jks`,
  `*.keystore`), Editor- und Betriebssystem-Reste, Logs.
  `gradle/wrapper/gradle-wrapper.jar` bleibt bewusst versioniert (deshalb kein
  `*.jar` in der Datei).

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

