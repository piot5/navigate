# Konfiguration

Alles, was Navigate einstellbar hat, steht in einer Datei. Sie liegt an **zwei**
Orten, und der Unterschied ist wichtig:

| | Pfad | Rolle |
|---|---|---|
| **Datei** | `/data/data/com.piotv.navigate/files/navigate.conf` | Quelle der Wahrheit — hier liest und schreibt Navigate |
| **Import** | `/storage/emulated/0/Android/data/com.piotv.navigate/files/navigate.conf` | Weg für `adb push` — wird übernommen, wenn er neuer ist |

Beide Pfade zeigt der Einstellungsbildschirm selbst an (unter dem Titel).

**Warum nicht direkt der externe Pfad?** `/sdcard` läuft seit Android 11 über
den MediaProvider-FUSE-Daemon: `stat` und `open` gehen dort **immer** durch
einen Userspace-Daemon, und FUSE-Passthrough hilft nur für read/write *nach* dem
Öffnen (source.android.com/docs/core/storage/fuse-passthrough). Die
Konfiguration wurde bei jedem HOME-Druck mehrfach geprüft — auf einem
1,4-GB-Gerät ist das messbare Zeit auf dem Main-Thread.

**Wie kommt eine gepushte Datei an?** Der Import-Pfad wird höchstens alle
5 Sekunden angesehen, und übernommen wird nur eine Datei mit **neuerem**
Zeitstempel als beim letzten Import. Danach liegt der Stand intern; eine alte,
einmal gepushte Datei überschreibt also keine Änderung aus den
Navigate-Einstellungen.

Bearbeitet wird die Datei normalerweise über **Navigate** in der unteren Zeile
(oder über den Import-Pfad). Die interne Datei ist ohne Root nicht beschreibbar
— das ist der Preis dafür, dass sie auf dem HOME-Pfad kostenlos ist.

## Format

Eine Anweisung pro Zeile, `Schlüssel=Wert`. `#` beginnt einen Kommentar,
Leerzeilen sind erlaubt, unbekannte Schlüssel und kaputte Zeilen werden
**ignoriert** statt als Fehler behandelt: eine handgeschriebene Datei darf
Navigate nie daran hindern zu starten. Groß-/Kleinschreibung der Schlüssel ist
egal, Leerzeichen um Schlüssel und Wert ebenfalls.

```ini
# Navigate — Konfiguration

# Favoriten in der unteren Zeile, in dieser Reihenfolge (Paketnamen).
favorites=com.netflix.ninja,de.swr.avp.ard.tv,com.google.android.youtube.tv

# Wie viele Favoriten die Zeile aufnehmen darf (0-6).
max_favorites=6

# Spalten im App-Raster. 0 = automatisch (Bildschirmbreite / 150dp).
columns=0

# Uhr oben rechts auf der Home-Seite anzeigen.
show_clock=true

# App-Symbole im Raster anzeigen. false = reine Textliste (sparsamer).
show_icons=true
```

## Schlüssel im Einzelnen

### `favorites`

Kommaseparierte **Paketnamen**, in der Reihenfolge, in der sie in der Zeile
stehen. Paketnamen enthalten kein Komma.

- Fehlt der Schlüssel, ist die Zeile leer (nur Home, Navigate, Einstellungen).
- Einträge, die nicht (mehr) installiert sind, werden stillschweigend
  übersprungen — die Zeile räumt sich also selbst auf, wenn eine App
  deinstalliert wird.
- Mehr Einträge als `max_favorites` erlaubt sind, werden beim Einlesen
  abgeschnitten (vorne bleiben).

Die Paketnamen findet man mit:

```bash
adb shell pm list packages -3
# oder, mit Beschriftung:
adb shell cmd package query-activities -a android.intent.action.MAIN \
  -c android.intent.category.LEANBACK_LAUNCHER | grep packageName
```

### `max_favorites`

Grenze für die Zeile, `0` bis `6`. Werte außerhalb werden auf diesen Bereich
begrenzt.

Warum hart 6: die Zeile ist 960dp breit (1080p bei TV-Dichte). Ein Element ist
96dp breit, dazu kommen die Ränder. 6 Favoriten + Home + Navigate +
Einstellungen = 9 Elemente = 864dp — das passt. Mit 7 Favoriten wären es
960dp und damit zu breit. Wer mehr Apps griffbereit braucht, nimmt sie über
das Raster: die Home-Seite ist nur einen Druck entfernt.

### `columns`

Spalten im Raster, `1` bis `12`. `0` bedeutet automatisch:
`Bildschirmbreite / 150dp`, begrenzt auf 4 bis 8. Auf dem Chromecast
(960dp breit) ergibt das 6 Spalten.

Der Wert ist vor allem für andere Geräte gedacht — auf einem 4K-Fernseher mit
anderer Dichte kann eine feste Zahl sinnvoll sein.

### `show_clock`

`true` oder `false`. Steuert nur die Uhr auf der Home-Seite; sie wird im
Minutentakt aktualisiert und nur, solange die Home-Seite sichtbar ist.
Ungültige Werte lassen die Einstellung unverändert.

### `show_icons`

`true` oder `false`. `false` zeigt im Raster nur die App-Namen.

Warum es den Schalter gibt: die Symbole sind der größte Einzelposten im
Speicher. Sie zu gewinnen heißt, die Ressourcen einer fremden App zu mappen —
auf dem Zielgerät gemessene ~25 MB „Private Clean" (docs/DEVICE_PERF.md). Mit
`show_icons=false` wird kein Symbol geladen und der Speicher-Cache geleert.
Betroffen ist **nur das Raster**; die Zeile behält ihre Symbole (dort sind es
neun Elemente, im Raster bis zu 24 sichtbare).

Nicht in den Navigate-Einstellungen auf dem Fernseher, sondern bewusst nur in
der Datei: die Einstellung ist eine Entscheidung über die Ausstattung des
Geräts, keine, die man beim Zappen umschaltet.

## Was Navigate selbst schreibt

Navigate schreibt die Datei bei jeder Änderung in den Einstellungen komplett neu
— mit Kommentaren, damit sie auch nach einer Bearbeitung auf dem Fernseher noch
lesbar ist. Reihenfolge der Schlüssel und Kommentare bleiben dabei stabil, ein
Öffnen und Schließen der Einstellungen verliert also nichts.

## Sichern und Zurücksetzen

```bash
# sichern (der Import-Pfad; die interne Datei ist ohne Root nicht lesbar)
adb pull /sdcard/Android/data/com.piotv.navigate/files/navigate.conf ./navigate.conf

# zurückspielen — wird beim nächsten Zugriff übernommen (max. 5 s)
adb push ./navigate.conf /sdcard/Android/data/com.piotv.navigate/files/navigate.conf

# auf Werkszustand (leere Zeile, Uhr an, Symbole an)
adb push /dev/null /sdcard/Android/data/com.piotv.navigate/files/navigate.conf
```

Der letzte Befehl schiebt eine **leere** Datei an den Import-Pfad; sie ist neuer
als alles Vorherige und wird übernommen — eine leere Datei ergibt genau die
Standardwerte. Wer die Favoriten behalten will, ändert stattdessen einzelne
Zeilen.

Zum Prüfen, was tatsächlich gilt, sind die Navigate-Einstellungen der
verlässlichste Ort: sie zeigen den internen Pfad und den Favoritenzähler.
