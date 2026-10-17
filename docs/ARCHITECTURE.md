# Architektur

Zielsetzung: **minimale Oberfläche, maximale Performance** auf einem
Chromecast HD (1,4 GB RAM, 32-bit-ABI, Android 14). Jede Entscheidung unten
folgt aus dieser Vorgabe — und, wo eine Zahl steht, aus einer Messung.

## Ein Zustand, eine Activity

```
HOME-Druck  →  untere Zeile mit den ersten Apps  +  „…“ in der Zeile darunter
                    │
                    └── OK auf „…“  →  ganzes Raster (scrollbar), „…“ klappt zurück
```

Eine Activity, ein Fenster, keine Mehrdeutigkeit: zwei HOME-Activities in einer
App würden die Auflösung des Systems mehrdeutig machen (Auswahldialog statt
Direktstart).

Es gibt **keinen zweiten Zustand** — „zugeklappt“ und „aufgeklappt“ unterscheiden
sich nur in der Menge, die der Adapter meldet (`limit`), und in der Höhe des
Rasters. Kein zweites Layout, kein zweites Fenster, keine Animation.

## Das Fenster: opak, und warum das die eigentliche Korrektur war

Bis 0.6 war das Theme translucide (damit beim HOME-Druck kein schwarzer Blitz
kommt) und setzte alle Fenster-Animationen auf `@null`. Auf dem Zielgerät war das
Fenster damit:

```
mAttrs={(0,0)(fillxfill) … fmt=TRANSPARENT}      mHasSurface=true
mDrawState=HAS_DRAWN   mLastHidden=false   Surface: shown=true alpha=1.0
input_focus: [Focus entering … LauncherActivity]   ← bedienbar!
```

… und trotzdem **nicht zu sehen**: nur bei einem Klick blitzte kurz etwas auf.
Die Eingabe kam an (der Nutzer konnte „blind“ Apps starten), der Inhalt blieb
unsichtbar. Alle SurfaceFlinger-Ebenen meldeten dabei `alpha=1.000000`.

Was der Fall zeigt: ein **translucentes Fenster lässt die App darunter am Leben**
(sie wird nur pausiert, ihr Fenster bleibt), und jede auf diesem Fernseher
sichtbare App hat ein **opakes** Fenster, bei dem die App darunter gestoppt wird.
Die Korrelation war eindeutig, der Mechanismus dahinter nicht nachweisbar
(video-/DRM-Ebenen des Geräts). 0.7 zieht daraus die einzige belastbare
Konsequenz: das gewöhnlichste Theme, das es gibt.

```xml
<style name="Theme.Navigate" parent="@android:style/Theme.Material.NoActionBar">
    <item name="android:windowBackground">@color/bg_home</item>
    <item name="android:colorBackground">@color/bg_home</item>
</style>
```

Kein `windowIsTranslucent`, kein `windowDisablePreview`, kein eigener
`windowAnimationStyle`, keine Fenstergrößen-Tricks. `windowBackground` ist
derselbe Farbton wie die Home-Seite — das Starting-Window ist damit ein dunkles
Rechteck statt eines weißen Blitzes.

> **Was daran Hypothese ist:** dass genau die Translucenz die Ursache war, ist
> aus der Korrelation geschlossen, nicht bewiesen. Bewiesen ist: das Fenster war
> gezeichnet, fokussiert, bedienbar und unsichtbar.

## Erste Zeile und „…“: eine Ansicht, kein Umbau

Der Aufbau ist eine senkrechte `LinearLayout`-Spalte, die am **unteren** Rand
hängt:

```
┌──────────────────────────────────┐
│                            [Uhr] │
│                                  │   ← leer (zugeklappt)
│  [App][App][App][App][App][App]  │   ← das Raster, zugeklappt eine Zeile hoch
│                          [ ••• ] │   ← die Zeile darunter: der „…“-Knopf
└──────────────────────────────────┘
```

- **Zugeklappt:** `limit = columns` — der Adapter meldet genau eine Zeile; das
  Raster ist `wrap_content` hoch und hängt mit der Spalte unten. Der „…“-Knopf
  steht in der Zeile darunter, rechtsbündig unter der letzten Zelle (gleiche
  Ränder wie das Raster).
- **Aufgeklappt:** `limit = 0` (alle Apps), das Raster bekommt per
  `layout_weight = 1` den ganzen Platz über dem Knopf und scrollt. Der Knopf
  bleibt an derselben Stelle.

`layout_weight` statt `match_parent`, weil ein `match_parent`-Kind in einer
senkrechten `LinearLayout` den Knopf darunter aus dem Bild schieben würde.

## Die Auswahl-Markierung

Zwei Dinge, die auf dem Gerät zu sehen waren und die es so nicht bleiben sollten:

- **Der orange Auswahl-Hintergrund** kam vom **Theme** (`listSelector`), nicht
  vom Layout: `android:listSelector="@null"` schaltet ihn nicht ab. Deshalb steht
  jetzt `@android:color/transparent` im Layout — ausdrücklich, mit Begründung an
  der Fundstelle.
- **Der abgerundete blaue Rahmen bleibt** — er ist die Markierung, und er kommt
  aus einer einzigen Zeichenfläche (`bg_grid_item.xml`: `state_selected` →
  `item_focus_fill` + 2dp `item_focus_stroke`, 12dp Radius). Der „…“-Knopf
  benutzt dieselbe Sprache (`bg_more_item.xml`, dort auf `state_focused`, weil er
  eine echte View ist).

`HomeGridView` räumt zusätzlich die Markierung ab, wenn der Fokus das Raster
verlässt (GridView lässt `state_selected` stehen): sonst bliebe eine Zelle blau
umrahmt, während der Fokus auf dem „…“-Knopf steht.


## „Zurück in die letzte App“ — und warum es einen Merker braucht

Auf der schon sichtbaren Home-Seite holen HOME und BACK die zuletzt benutzte App
zurück. Der Unterschied zwischen „erster HOME-Druck“ (Home-Seite zeigen) und
„zweiter HOME-Druck“ (zurück in die App) hängt an `homeOnScreen`: gesetzt in
`onResume`, gelöscht in `onStop` — und zusätzlich beim Start einer App aus dieser
Oberfläche heraus (eine durchsichtige App würde uns nur pausieren, `onStop` käme
dann nicht).

Warum kein Lebenszyklus-Flag: das System pausiert die Activity bei **jedem**
HOME-Druck, *bevor* es den Intent zustellt (gemessen: onPause → onNewIntent →
onResume). `isResumed` ist dort immer schon `false` — genau daran ist die erste
Fassung dieses Verhaltens in 0.1 gescheitert.

Woher „die letzte App“ kommt ([LastApp](../app/src/main/java/com/piotv/navigate/data/LastApp.kt)),
in dieser Reihenfolge:

1. **Nutzungsstatistik** (`queryEvents`, 5-Minuten-Fenster, sonst 6 Stunden):
   exakt, braucht aber den App-Op `GET_USAGE_STATS`. Zwei Fenster, weil nicht die
   Abfrage kostet, sondern die **Ereignismenge** — ein 6-Stunden-Fenster enthält
   auf einem benutzten Gerät Tausende Ereignisse.
2. **Merker** (`SharedPreferences`): die zuletzt über Navigate gestartete App,
   nach einem Systemstart verworfen. Ohne Zugriff die ehrliche Näherung — und
   `null`, wenn nichts bekannt ist; dann bleibt die Home-Seite stehen.

Warum nicht `ActivityManager.getRunningTasks`: seit Android 5 liefert das
normalen Apps nur noch die eigenen Tasks — für einen Launcher wertlos.

Die Faltung der Ereignisse (`ForegroundLog.lastResumed`) ist reine Logik ohne
Android-Typen und getestet; `LastApp` selbst ist Verdrahtung und läuft nur auf
einem Gerät.

## Grüne Schrift: was „aktiv“ heißt

Die App-Beschriftungen sind grün, solange die App **aktiv** ist. Was „aktiv“ auf
diesem Gerät bedeutet, ist gemessen, nicht angenommen.

Weil unser Fenster **opak** ist, stoppt die Plattform die App darunter, sobald die
Home-Seite erscheint — ihre Activity bekommt `ACTIVITY_STOPPED`. Ihr **Prozess**
lebt aber weiter. Direkt nach einem HOME-Druck nachgemessen:

```
*APP* UID 10101 ProcessRecord{… com.netflix.ninja/u0a101}
*APP* UID 10102 ProcessRecord{… com.disney.disneyplus/u0a102}
*APP* UID 10106 ProcessRecord{… com.wbd.stream/u0a106}
*APP* UID 10064 ProcessRecord{… com.android.vending/u0a64}
```

Die richtige Frage ist deshalb nicht „läuft die Activity?“ (Antwort: nein),
sondern **„war die App zuletzt im Vordergrund?“** — und die beantwortet die
Nutzungsstatistik mit einem Zeitstempel je Paket (`UsageStats.getLastTimeUsed`):

- grün = in den letzten **24 Stunden** im Vordergrund, nie vor dem letzten
  Systemstart ([LastApp.recentlyUsed](../app/src/main/java/com/piotv/navigate/data/LastApp.kt));
- die Abfrage läuft im Hintergrund-Thread und höchstens alle 30 Sekunden — ein
  6-Stunden-Fenster auf dem HOME-Pfad wäre genau der Posten, den 0.2 dort entfernt
  hat;
- ohne Nutzungszugriff bleibt der letzte bekannte Stand stehen (beim ersten Mal
  eine leere Menge: nichts grün statt falsch grün).

Ehrlich dazu: „Prozess lebt“ ist **angenähert**. Eine App, die vor 23 Stunden
benutzt und inzwischen vom System beendet wurde, ist grün — das Fenster ist der
Kompromiss zwischen „alles Aktive“ und „nichts Falsches“.

## „Now Playing“: eine Zeile, die man anspringen kann

Oben links steht im zugeklappten Zustand, was gerade läuft — **App · Interpret –
Titel**, dazu `· pausiert` und das Cover. Der Block ist der einzige Teil der
Home-Seite, der etwas anderes tut als „starte diese App“: **OK darauf holt die
spielende App zurück**.

Woher die Angaben kommen:

| Angabe | Quelle |
|---|---|
| Titel, Interpret, Zustand | `MediaMetadata` der Session (`METADATA_KEY_TITLE`/`_ARTIST`) und `PlaybackState` |
| Cover | `MediaMetadata.getBitmap()`: erst `METADATA_KEY_ALBUM_ART`, dann `_ART`, dann `_DISPLAY_ICON`. Mehr bietet die API nicht — die `*_URI`-Varianten gäbe es zusätzlich, sie verlangen aber einen Content-Resolver für ein Bild, das meist schon mitkommt |
| App-Name | **nicht** aus der Session, sondern aus der eigenen App-Liste (`sessionActivity`-Paket → `AppEntry.label`): dieselben Namen wie im Raster |
| „wohin zurück“ | `MediaController.getSessionActivity()` — der „Jetzt läuft“-Bildschirm, den die App selbst anbietet. Setzt sie ihn nicht, greift ihr Launcher-Intent, wie aus dem Raster |

Drei Entscheidungen, die daran hängen:

1. **Ohne Benachrichtigungszugriff gibt es kein Feld.** `getActiveSessions` ist
   für normale Apps gesperrt; [NowPlayingListener](../app/src/main/java/com/piotv/navigate/NowPlayingListener.kt)
   ist der Ausweis — ein leerer Dienst, der nichts liest. Die entstehende
   `SecurityException` wird geschluckt, der Block bleibt leer: dieselbe Haltung
   wie bei „ohne Nutzungszugriff ist nichts grün“.
2. **Das Cover wird auf Symbolgröße verkleinert und einmal gehalten.** Ohne den
   Cache würde alle 5 Sekunden dasselbe Bild geholt und skaliert (ein
   1000×1000-Cover sind 4 MB); der Schlüssel ist das **Lied**, nicht das
   Bitmap-Objekt — jede Abfrage liefert ein neues. Fehlt ein Cover, steht dort
   das App-Symbol aus dem eigenen Symbol-Cache.
3. **Kein Rückkanal.** Kein `addOnActiveSessionsChangedListener`, kein
   `MediaController.Callback`: die Home-Seite fragt im Takt ihres eigenen Timers
   (5 s, nur sichtbar und zugeklappt). Ein Listener wäre genauer und wäre
   zusätzlicher Zustand — für eine Zeile mit Cover lohnt er nicht.

Die Regel „was steht in der Zeile“ liegt in
[NowPlayingText](../app/src/main/java/com/piotv/navigate/data/NowPlayingText.kt)
und kennt kein Android — dieselbe Trennung wie bei „grün“: geprüft wird die
Regel (94 Tests, ohne Gerät), nicht die Eigenheit des Geräts.

## Was ohne Gerät prüfbar ist (und was nicht)

| | Beispiele | Prüfbar |
|---|---|---|
| **reine Regeln**, kein Android-Typ | `AppCatalog`, `Order`, `OrderCodec`, `OrderStore` (Datei-Semantik), `NowPlayingBoard`, `NowPlayingText`, `GridColumns`, `IconGeometry`, `IconCacheKeys` | Unit-Test ohne Gerät — 94 Tests; die ohne Gerät prüfbaren Klassen liegen bei 100 % Zeilen-Coverage. Die Zahl selbst ist keine Prosa: `tools/check_docs_drift.sh` vergleicht sie mit dem gebauten Testset |
| **Android-Verdrahtung** | `LauncherActivity`, `AppAdapter`, `HomeGridView`, `IconLoader`, `IconDiskCache`, `AppRepository` | nur auf einem Gerät; hier zählt die Messung ([DEVICE_PERF.md](DEVICE_PERF.md)) |

Der Grund ist die Fehlerhistorie: die teuersten Fehler dieses Projekts waren
**Regeln**, keine Android-Eigenheiten. Solche Regeln stehen deshalb in einem
`object` ohne Android-Import und sind in Millisekunden prüfbar statt in einem
15-Minuten-Build plus Fernseher.

Drei Gates halten das auf Dauer: Lint (`warningsAsErrors`), das Coverage-Gate
(`unitTestCoverageVerification`) und das Doku-Gate (`tools/check_docs_drift.sh`).

## Entscheidungen, die geblieben sind

- **Keine Abhängigkeiten zur Laufzeit.** `GridView`, `LauncherApps`, `LruCache`,
  ein Executor, ein Handler — alles Framework. Kein AndroidX, kein Compose,
  keine Coroutines, kein Hilt. Was fehlt, kostet nichts.
- **Zwei PackageManager-Abfragen statt einer.** `LauncherApps` kennt nur
  `CATEGORY_LAUNCHER`; Prime Video, WOW und ARD tragen nur
  `CATEGORY_LEANBACK_LAUNCHER` und fehlten damit komplett. Die Vereinigung beider
  Kategorien ist der Grund für `QUERY_ALL_PACKAGES`.
- **App-Liste und Symbole werden vorgerechnet.** Die Liste liegt als
  Prozess-Singleton vor, jedes dekodierte Symbol zusätzlich als kleine WebP-Datei
  im app-internen Speicher. Nach einem Prozess-Neustart (auf einem 1,4-GB-Gerät
  der Normalfall) muss keine fremde APK mehr gemappt werden.
- **Die Einstellungen sind die Konfigurationsdateien** ([OrderStore],
  [SettingsStore], siehe [CONFIG.md](CONFIG.md)) — per langem Drücken bzw. in den
  Einstellungen auf dem Fernseher oder per `adb push`. Kein
  `SharedPreferences`-Sammelsurium: der Merker der zuletzt gestarteten App ist
  Laufzeitzustand, eine Einstellung ist eine Datei.

## Was gegenüber 0.1 weggefallen ist

| Posten (0.1) | heute |
|---|---|
| Vordergrund-Abfrage in der Nutzungsstatistik (bei **jedem** HOME-Druck) | entfällt — es gibt keinen zweiten Zustand mehr, der sie brauchte |
| Aufbau der unteren Zeile (Favoriten-Nachschlag, 9 × Layout-Inflation) | entfällt — die untere Zeile **ist** jetzt der Anfang des Rasters |
| Fenstergröße pro HOME-Druck setzen | entfällt — das Fenster ist immer derselbe Vollbild-Rahmen |
| Symbole bei jedem Bind bzw. Prozessstart aus fremden APKs | unverändert seit 0.2: Speicher → Platte → Ressourcen |

Was bleibt und nicht wegoptimierbar ist: ein Measure/Layout-Lauf über das Raster
und der erste Frame.
