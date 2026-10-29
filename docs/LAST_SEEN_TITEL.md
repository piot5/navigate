# Last seen — kann darunter ein Titel stehen?

**Frage:** Unter der „Last seen"-Zeile (oben links) soll in einer **neuen Zeile über
den Symbolen** der **Titel** stehen — aber nur, **wenn die Serie ein Favorit ist**.
Geht das?

**Antwort in einem Satz:** Ja, mit den Daten, die Navigate heute schon liest — aber
„Serie" ist ein Begriff, den **weder Android noch die Media-Sessions kennen**.
Machbar ist „Titel der zuletzt gesehenen App, wenn diese App ein Favorit ist"; die
Erkennung *Serie gegen Film gegen Radio* ist es nicht.

---

## 1. Was es heute schon gibt

`NowPlayingPanel` (`app/src/main/java/com/piotv/navigate/ui/NowPlayingPanel.kt`) zeigt
oben links **eine Zeile je App** mit aktiver Media-Session:

| Bestandteil | Quelle | Zeile im Code |
|---|---|---|
| App-Name („ZDF") | `AppRepository` (App-Liste) | `NowPlayingInfo.app` |
| Titel / Interpret | `MediaMetadata` der Session | `NowPlayingInfo.title` / `.artist` |
| „· pausiert" | `PlaybackState` | `NowPlayingInfo.playing` |
| Cover (40 dp) | `MediaMetadata` → verkleinert, im LRU-Cache | `NowPlaying.art` |
| OK auf der Zeile | `MediaController.sessionActivity` (PendingIntent) | `NowPlayingTrack.activity` |
| Zeilentext | Regel ohne Android-Typen | `NowPlayingText.line()` |

Der Aufbau ist bereits **dynamisch**: Der Block ist leer im Layout
(`@+id/now_playing` in `activity_home.xml`), die Zeilen entstehen zur Laufzeit aus
`item_now_playing.xml`, und der Block **meldet seine Höhe** an das Raster
(`Listener.onHeightChanged` → `grid.setPadding(top, …)`). Eine zusätzliche Zeile ist
damit **kein Umbau des Layouts**, sondern eine zusätzliche Zeile im Block.

## 2. Woher „Favorit" kommt — und was es hier bedeutet

Ein Launcher erfährt nirgends, dass ein **Inhalt** ein Favorit ist. Es gibt keinen
Systembegriff „Serie", „Favorit" oder „Staffel" für Medieninhalte; die Media-Session
eines Players liefert genau vier verwertbare Felder (`title`, `artist`, `album`,
`albumArt`). „Favorit" kann Navigate deshalb nur über **Apps** verstehen:

- Die **erste Rasterzeile** ist in Navigate die Favoritenzeile (zugeklappt sichtbar).
- In der Datei entspricht das den ersten Einträgen in `navigate.conf`
  (`favorites=…`/`order=…`, siehe `docs/CONFIG.md`).

„Serie als Favorit" heißt also technisch: **die App, aus der der Titel kommt, steht
in der ersten Rasterzeile.** Das ist eine Näherung — sie ist ehrlich und prüfbar,
aber sie unterscheidet nicht zwischen „Netflix-Serie" und „Netflix-Film".

## 3. Drei Varianten, wie die Titelzeile aussehen könnte

| # | Variante | Was der Nutzer sieht | Aufwand | Risiko |
|---|---|---|---|---|
| 1 | **Zweite Textzeile in der bestehenden Zeile** | `ZDF · Wie viel Blödsinn…`<br>`Letzte Folge: Staffel 2, Folge 3` | klein (Layout `item_now_playing.xml`, `NowPlayingText`) | gering — aber „Staffel/Folge" liefert keine Session (siehe 4) |
| 2 | **Eigene Zeile „Last seen" unter dem Block** | Zeile 1: `ZDF · Titel`<br>Zeile 2 (grau): `Last seen · ZDF · Titel` | klein (`NowPlayingPanel` + `NowPlayingBoard`) | gering; doppelte Information, wenn nur eine App läuft |
| 3 | **Titel nur für Favoriten** (die App der ersten Zeile) | Für Favoriten-Apps: Zeile 1 + Titelzeile; alle anderen: nur Zeile 1 | mittel (Favoriten-Liste an das Panel durchreichen) | gering; Höhe des Rasters wächst nur, wenn ein Favorit läuft |

**Empfehlung:** Variante 3. Sie ist die einzige, die die Bedingung „sofern Serie als
Favorit" ohne neue Datenquelle erfüllt, und sie kostet nur eine Zeile in
`NowPlayingPanel` plus das Durchreichen der Favoriten-Pakete (die stehen schon in
`OrderStore.current()`).

## 4. Was **nicht** geht — die ehrlichen Grenzen

1. **„Serie" ist nicht erkennbar.** Keine der verfügbaren Quellen
   (`MediaMetadata`, `UsageStats`, `LauncherApps`) kennt Serien, Staffeln oder
   Folgen. Erkennbar ist nur, **was der Player freiwillig veröffentlicht**.
2. **Nicht jede App veröffentlicht etwas.** Auf dem Zielgerät lieferte ZDF Titel und
   Cover; Netflix liefert je nach Zustand **gar keine** Metadaten. Dann bleibt die
   Zeile leer — das ist heute schon so und wäre bei einer Titelzeile genauso.
3. **Ein Titel ist keine Serie.** `Wie viel Blödsinn ist zu viel? STREET FIGHTER &
   WHALE…` ist eine Sendung, kein Serienname; ein Radiosender liefert denselben Text
   wie ein Film. Eine Titelzeile kann also nicht „Serie" heißen, sondern nur „Titel".
4. **Kein Zurückschauen.** Die Session zeigt, was **jetzt** (oder pausiert) läuft.
   „Zuletzt gesehen" im Sinne von „vor drei Tagen, Folge 7" kann Navigate nicht
   wissen: `UsageStats` liefert nur Pakete und Zeitstempel, keine Inhalte. Der
   Titel „Last seen" ist deshalb auch nur für die **laufende** App belegbar.
5. **Platz.** Der Block meldet seine Höhe, das Raster rutscht nach unten
   (`grid_padding_top`). Zugeklappt ist auf dem Chromecast HD (1080p) Platz für
   ~4 Zeilen à 52 dp; ab drei Now-Playing-Zeilen plus Titelzeilen wird es eng, dann
   muss die Titelzeile weichen (z. B. nur die erste Zeile bekommt sie).

## 5. Was für die Umsetzung konkret zu tun wäre

1. `NowPlayingBoard.Row` um ein Feld `detail: String?` erweitern (reine Regel,
   ohne Android-Typen — damit in `NowPlayingBoardTest` prüfbar).
2. `item_now_playing.xml` um eine zweite `TextView` (grau, `text_dim`,
   `12–14 sp`) erweitern; die Höhe des Blocks ändert sich → `reportHeight` misst
   automatisch nach.
3. `NowPlayingPanel` bekommt vom `Listener` die Favoriten-Pakete
   (`OrderStore.current()` → erste `columns` Einträge) und setzt `detail` nur für
   diese.
4. `docs/CONFIG.md` um den Schalter `last_seen_title=on|off` ergänzen (Standard
   `off`, wie alle neuen Schalter in diesem Projekt).

**Geschätzter Aufwand:** eine halbe Stunde Code, ein Test, ein Doku-Absatz — keine
neue Berechtigung, keine neue Abhängigkeit, kein Netz.

---

## 6. Hintergrund — was Android TV „eingebaut“ hat (und warum es nicht geht)

Dieselbe Frage wie oben, nur für die Fläche: **drei** Hintergründe sollten zur
Wahl stehen, gern die, die Android TV schon mitbringt. Was davon erreichbar ist:

| Quelle | Was dort liegt | Erreichbar für einen Launcher? |
|---|---|---|
| Google-TV-Ambient/„Backdrop" | die wechselnden Bilder des Bildschirmschoners | **Nein.** Sie liegen im privaten Speicher `com.google.android.apps.tv.launcherx` (auf dem Zielgerät ohnehin deaktiviert, siehe [googletv-Doku](../../googletv/docs/TV_ANALYSE_2026-10-14.md)) |
| `TvContract`-Empfehlungen (`Channels`/`PreviewPrograms`) | Poster und Vorschaubilder der Empfehlungszeile | **Nein.** Sie verlangen TV-Input-Zugriff (`android.permission.ACCESS_TV_*`), den nur ein TV-Input-Dienst bekommt |
| `WallpaperManager` | das System-Hintergrundbild | **Nein.** Chromecast HD/Google TV hat keinen Wallpaper-Dienst; `getDrawable()` liefert nichts |
| eine **eigene Mediendatei** | ein Bild, per `adb push` in den App-Ordner | **Ja** — genau das ist umgesetzt |

Daraus sind die zwei Optionen entstanden, die seit 0.14 in den Einstellungen
stehen: **Dunkel** (Standard) und **Bild** — und warum das so ist, steht
in [CONFIG.md](CONFIG.md#hintergrund--zwei-möglichkeiten-seit-014). Ein
**Video**-Hintergrund gab es in 0.14 kurz als dritten Wert; er ist in 0.14.1
wieder entfernt, weil ein Video auf dem Chromecast dauerhaft einen
Hardware-Decoder belegt.

**Ehrliche Kosten:** Ein Bild kostet einen Speicherplatz in Bildschirmgröße,
sonst nichts.
