# Architektur

Zielsetzung: **minimale Oberfläche, maximale Performance** auf einem
Chromecast HD (1,4 GB RAM, 32-bit-ABI, Android 14). Jede Entscheidung unten
folgt aus dieser Vorgabe — und aus Messungen auf dem Gerät, nicht aus
Geschmack.

## Zwei Zustände, eine Activity

```
                 HOME-Druck
                     │
        ┌────────────┴────────────┐
        │  läuft eine App?        │
        │  (ModeDecider)          │
        └────────────┬────────────┘
             ja ─────┤───── nein
             │               │
          DOCK             GRID
   Fenster = untere Zeile   Fenster = ganzer Bildschirm
   Hintergrund transparent  Hintergrund deckend
```

Warum eine Activity und nicht zwei (Home + Overlay): eine App mit zwei
HOME-Activities macht die Auflösung des Systems mehrdeutig — der HOME-Druck
zeigt dann einen Auswahldialog statt des Launchers. Ein Fenster, zwei Zustände,
keine Mehrdeutigkeit.

Der Modus entsteht **nicht** über zwei Themes. `windowIsTranslucent` wird beim
Anlegen des Fensters aus dem Theme gelesen; zur Laufzeit ist es nicht mehr
änderbar. Also: ein translucentes Theme und der Modus als
Fenstergeometrie plus Hintergrund.

```kotlin
// LauncherActivity.applyWindowLayout()
params.width  = MATCH_PARENT
if (target == DOCK) {
    params.height  = WRAP_CONTENT                  // = Höhe des Docks
    params.gravity = Gravity.BOTTOM or CENTER_HORIZONTAL
} else {
    params.height  = MATCH_PARENT
    params.gravity = Gravity.TOP or Gravity.START
}
window.attributes = params
```

`WRAP_CONTENT` ergibt genau die Höhe des Wurzel-Layouts, und das ist im
DOCK-Modus das Dock samt Rand (das Raster ist dann `GONE` und wird nicht
gemessen). Deshalb steht die Dock-Höhe an genau einer Stelle im Layout
(`dimen/dock_height`) — und das Raster bekommt sie als unteren Rand
(`dimen/grid_margin_bottom`).

## Das Fensterformat: gemessener Kompromiss

Das Theme setzt `android:windowIsTranslucent=true`, was dem Fenster das Format
`PixelFormat.TRANSPARENT` gibt. Damit ist die Zeile sichtbar, aber die
Plattform **stoppt die App darunter** — ihr Fenster verschwindet
(`mHasSurface=false`), und hinter der Zeile liegt der vorherige
Home-Bildschirm statt der App.

Der naheliegende Gegenzug:

```kotlin
window.setFormat(PixelFormat.TRANSLUCENT)
```

Damit wird die App darunter nur **pausiert**, ihr Fenster bleibt sichtbar
(`mViewVisibility=0x0`, `PAUSED` statt `STOPPED` in der Nutzungsstatistik) —
**aber der Inhalt des eigenen Fensters war auf dem Zielgerät nicht mehr zu
sehen.** Belegt per `screencap` und Pixelprüfung: bei einem probeweise
knallrot gefüllten Wurzel-Layout zeigten die Pixel im Fensterbereich weiter die
App darunter, während `dumpsys window` `mDrawState=HAS_DRAWN`,
`Surface: shown=true alpha=1.0` und in SurfaceFlinger eine sichtbare Region
meldete. Der Compositor hielt die Fläche also für sichtbar, die Pixel sagten
etwas anderes.

Entschieden wurde zugunsten des **sichtbaren Docks**: eine Zeile, die man nicht
sieht, ist keine Zeile. Das Format bleibt deshalb beim Theme-Wert; der
Kompromiss („die App wird gestoppt") steht in der Doku, nicht im Verborgenen.
Wer das anders gewichten will, ändert genau eine Zeile in `onCreate`.

## Läuft eine App? — die einzige schwierige Frage

`ActivityManager.getRunningTasks()` und `getRunningAppProcesses()` liefern
normalen Apps seit Android 5 bzw. 5.1 nur noch die **eigenen** Prozesse. Für
einen Launcher sind sie wertlos. Bleibt `UsageStatsManager` — und der braucht
den Nutzungszugriff (App-Op `GET_USAGE_STATS`), den ein Launcher nicht zur
Laufzeit erfragen kann.

Der naheliegende Ansatz — „nimm das neueste Vordergrund-Ereignis" — ist falsch.
Beim HOME-Druck steht der Launcher **selbst** schon als `RESUMED` in der
Statistik (auf dem Gerät gemessen), das neueste Ereignis ist also immer er
selbst. Die Zählung „wie viele eigene Ereignisse kamen danach" wäre ein
Wettlauf gegen die Protokollierung.

Richtig ist der **Lebenszyklus der letzten fremden App**:

```kotlin
// ForegroundProbe.snapshot()
when (event.eventType) {
    EVENT_RESUMED -> { foreignPackage = pkg; foreignAlive = true }
    EVENT_PAUSED  -> if (pkg == foreignPackage) foreignAlive = true
    EVENT_STOPPED -> if (pkg == foreignPackage) foreignAlive = false
}
```

`PAUSED` und `RESUMED` heißen „lebt" (pausiert zählt als laufend — genau der
Normalfall, wenn die Zeile darüber liegt), `STOPPED` heißt „beendet". Damit sind
alle Fälle eindeutig, unabhängig davon, wann der eigene Eintrag protokolliert
wird:

| Situation | Ereignisse | Ergebnis |
|---|---|---|
| HOME über laufender App | App `PAUSED`, wir `RESUMED` | Zeile (DOCK) |
| App selbst beendet | App `STOPPED` | Home-Seite (GRID) |
| App über Navigate gestartet | App `RESUMED` | Zeile |
| App außerhalb von Navigate gestartet | App `RESUMED` | Zeile |
| Nutzer wählt „Home" in der Zeile | — | Home-Seite (explizit) |

Das Fenster der Abfrage beginnt nie vor dem letzten Systemstart: nach einem
Neustart ist kein Ereignis von davor mehr gültig.

Gefragt wird in **zwei Fenstern** (seit 0.2): zuerst 5 Minuten, und nur wenn
dort keine fremde App auftaucht, das lange Fenster über 6 Stunden. Der Grund ist
kein Geschwindigkeitsgefühl, sondern eine Messung: `queryEvents` kostet nicht
die Abfrage, sondern die **Ereignismenge**. Ein 6-Stunden-Fenster enthält auf
einem benutzten Gerät Tausende Ereignisse, und die wurden bei jedem HOME-Druck
auf dem Main-Thread durchlaufen. Wer HOME drückt, hatte die App Sekunden vorher
im Vordergrund — der kurze Durchgang beantwortet den Normalfall mit einer
Handvoll Ereignissen, der lange bleibt die ehrliche Absicherung für den Fall
„die App lief vor Minuten".

## Die Zeile schließen: Zustandsmerker statt Lebenszyklus

Ein HOME-Druck kommt als Intent auf die schon laufende Activity. Der
naheliegende Test „läuft die Activity gerade?" ist **wertlos**, weil das System
die Activity bei jedem HOME-Druck zuerst pausiert und erst danach den Intent
zustellt. Auf dem Gerät gemessen:

```
onPause  →  onNewIntent  →  onResume
```

Ein `isResumed`-Flag ist in `onNewIntent` also immer schon `false`; der zweite
HOME-Druck wurde damit nie erkannt (genau das war der Fehler in der ersten
Fassung, gefunden mit einer Diagnoseausgabe in den App-Ordner). Stattdessen
gibt es einen eigenen Merker:

```kotlin
private var dockOnScreen = false   // showDock() → true, showGrid()/launch()/dismissDock() → false
```

Er wird von den Übergängen gepflegt, nicht vom Lebenszyklus, und überlebt die
transiente Pause. Geschlossen wird die Zeile dann so:

```kotlin
// dismissDock(): die App, die gerade läuft, wieder nach vorn holen
val packageName = probe.snapshot()?.foreignPackage ?: prefs.lastLaunchedPackage
repository.apps.firstOrNull { it.packageName == packageName }?.let { launch(it) }
```

`finish()` wäre der naheliegende Weg, funktioniert für eine Home-Activity aber
nicht: das System startet sie sofort neu (gemessen — der Task wechselte von
`t2667` auf `t2668`). Der explizite Launcher-Intent dagegen holt den
bestehenden Task der App nach vorn, die App wird fortgesetzt und bekommt den
Tastaturfokus zurück.

## Was ohne Gerät prüfbar ist (und was nicht)

Die Architektur trennt zwei Sorten Code, und das ist keine Stilfrage, sondern
eine Prüffrage:

| | Beispiele | Prüfbar |
|---|---|---|
| **reine Regeln**, kein Android-Typ | `ModeDecider`, `HomeKeyPolicy`, `AppCatalog`, `ConfigCodec`, `FavoritesCodec`, `GridColumns`, `IconGeometry`, `DockSignature`, `UsageEventFolder` | Unit-Test ohne Gerät — 93 Tests, 100 % Zeilen-Coverage |
| **Android-Verdrahtung** | `LauncherActivity`, die Adapter, `IconLoader`, `ForegroundProbe`, `AppRepository` | nur auf einem Gerät; hier zählt die Messung ([DEVICE_PERF.md](DEVICE_PERF.md)) |

Der Grund ist die Fehlerhistorie: die drei teuersten Fehler dieses Projekts waren
**Regeln**, keine Android-Eigenheiten — die Sackgasse nach dem ersten HOME-Druck,
der nicht erkannte zweite HOME-Druck und die fehlenden Leanback-Apps. Solche
Regeln stehen deshalb in einem `object` ohne Android-Import und sind in
Millisekunden prüfbar statt in einem 15-Minuten-Build plus Fernseher. Die
Activity hält nur noch den Zustand (`dockOnScreen`, `homeIntentPending`,
`launchedInternally`) und fragt die Regel.

Drei Gates halten das auf Dauer: Lint (`warningsAsErrors`), das Coverage-Gate
(`unitTestCoverageVerification`) und das Doku-Gate
(`tools/check_docs_drift.sh`) — alle drei laufen in der CI. Was sie messen und
warum die Gesamt-Coverage trotzdem niedrig aussieht, steht in
[DEVICE_PERF.md](DEVICE_PERF.md), Abschnitt „Lint und Coverage".

## Das Zeitbudget eines HOME-Drucks

Die 128 ms aus [DEVICE_PERF.md](DEVICE_PERF.md) sind keine Plattformkonstante —
sie sind die Summe von Arbeit, die auf dem Main-Thread liegt. 0.2 hat die
Posten einzeln adressiert; die Tabelle ist der Vertrag für alles Weitere.

| Posten | vorher | jetzt | Warum |
|---|---|---|---|
| Vordergrund-Abfrage | 6-Stunden-Fenster, alle Ereignisse | 5-Minuten-Fenster, langes nur als Rückfall | Ereignismenge war die Kostenquelle |
| Konfigurationsdatei | `stat` auf `/sdcard` je Zugriff (FUSE) | interner Speicher, im Speicher gehalten | FUSE-Daemon im Pfad des HOME-Drucks |
| Favoriten auflösen | `associateBy` über alle Apps je Druck | Index im `AppRepository` | HashMap-Bau für neun Nachschläge |
| Fenstergröße | zweimal gesetzt (onNewIntent + onResume) | nur bei echter Änderung | zweites Setzen = Relayout über Binder |
| Fokus | `post { requestFocus() }` | direkt, `post` nur als Rückfall | ein zusätzlicher Durchlauf der Schleife |
| Symbole | bei jedem Bind bzw. Prozessstart aus Fremd-APKs | Speicher → Platte → Ressourcen | Mapping fremder Ressourcen (~25 MB Private Clean) |

Der letzte Posten ist der größte, und er ist der einzige, der auch **außerhalb**
des HOME-Drucks wirkt: `IconLoader.warm()` füllt beide Caches im Hintergrund, so
bald die App-Liste steht. Der Launcher bezahlt die Symbole damit einmal nach dem
Start — nicht bei jedem Öffnen des Rasters.

Was bleibt und nicht wegoptimierbar ist: Zygote-Fork (nur beim Kaltstart),
Fensteranmeldung, ein Measure/Layout-Lauf über das Raster und der erste Frame.
Das ist die Untergrenze, unter die ein Android-Launcher nicht kommt — außer man
benutzt keinen.

