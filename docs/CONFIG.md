# Konfiguration

Einstellbar sind in Navigate **vier Sachen**: die **Reihenfolge der Apps**
(`navigate.conf`), ob das **„Now Playing“-Feld** und die **Uhr** erscheinen und
was **hinter dem Raster** liegt (`settings.conf`). Dazu kommt der **Verlauf**
(`history.conf`) — was lief, neueste oben, dynamisch nachgeladen. Jede der
Dateien liegt an **zwei** Orten — der Unterschied ist wichtig:

| Datei | Inhalt | Quelle der Wahrheit (hier liest Navigate) | Import (`adb push`) |
|---|---|---|---|
| `navigate.conf` | Reihenfolge — eine Paketnamen-Zeile je Eintrag | `/data/data/com.piotv.navigate/files/navigate.conf` | `/storage/emulated/0/Android/data/com.piotv.navigate/files/navigate.conf` |
| `settings.conf` | Einstellungen — `schlüssel=wert` | `/data/data/com.piotv.navigate/files/settings.conf` | `/storage/emulated/0/Android/data/com.piotv.navigate/files/settings.conf` |
| `history.conf` | Verlauf — `zeit|paket|zeile`, neueste zuletzt | `/data/data/com.piotv.navigate/files/history.conf` | `/storage/emulated/0/Android/data/com.piotv.navigate/files/history.conf` (nur lesbar — ein `push` wird nur übernommen, wenn die Datei neuer ist) |

Übernommen wird eine Import-Datei nur, wenn sie **neuer** ist als beim letzten
Import. Beide Dateien benutzen dieselbe Mechanik (im Code
`data/ConfigFile.kt`): geschrieben wird intern, importiert wird von außen.

**Warum nicht direkt der externe Pfad?** `/sdcard` läuft seit Android 11 über
den MediaProvider-FUSE-Daemon: `stat` und `open` gehen dort **immer** durch
einen Userspace-Daemon, und FUSE-Passthrough hilft nur für read/write *nach* dem
Öffnen (source.android.com/docs/core/storage/fuse-passthrough). Der Launcher
prüft die Datei bei jedem `onResume` — auf einem 1,4-GB-Gerät ist ein
FUSE-Roundtrip auf dem HOME-Pfad messbare Zeit.

**Wie kommt eine gepushte Datei an?** Der Import-Pfad wird höchstens alle
5 Sekunden angesehen, und übernommen wird nur eine Datei mit **neuerem**
Zeitstempel als beim letzten Import. Danach liegt der Stand intern; eine alte,
einmal gepushte Datei überschreibt also keine Änderung, die auf dem Fernseher
gemacht wurde (langes Drücken).

> Bis 0.3 hat dieser Weg **nie** funktioniert: der Merker der Drossel stand auf
> `Long.MIN_VALUE`, `now - Long.MIN_VALUE` läuft über und wird negativ, die
> Drossel griff damit immer. Gefunden hat das in 0.4 der Test
> `OrderStoreTest.eine neuere Import-Datei wird uebernommen` (siehe
> [CHANGELOG](../CHANGELOG.md)).

## settings.conf — die Einstellungen

Eine Zeile pro Eintrag, Form `schlüssel=wert`:

```ini
# Navigate — Einstellungen
now_playing=on
show_clock=on
background=dark
background_file=
```

| Schlüssel | Werte | Wirkung |
|---|---|---|
| `now_playing` | `on` / `off` (auch `true`/`false`, `1`/`0`, `an`/`aus`, `ja`/`nein`, Groß-/Kleinschreibung egal) | zeigt das „Now Playing“-Feld oben links — seit 0.13 **eine Zeile je App** mit aktiver Media-Session, nur im zugeklappten Zustand |
| `show_clock` | wie `now_playing` | zeigt die **Uhr** oben rechts (seit 0.14 abschaltbar; „aus“ heißt auch: kein Takt mehr) |
| `background` | `dark` / `image` (auch `dunkel`, `bild`, `aus`) | was **hinter dem Raster** liegt: einfarbig (Standard) oder ein **Bild** (seit 0.14) |
| `background_file` | Pfad oder blosser Name; **leer** = Standardname | die Mediendatei für `background=image` (seit 0.14) |

Genau wie bei der Reihenfolge ist **Toleranz Absicht**: `#`-Zeilen, Leerzeilen,
umgebende Leerzeichen, `now-playing` statt `now_playing` und unbekannte
Schlüssel werden verstanden bzw. **ignoriert** — eine handgeschriebene Datei darf
Navigate nie am Starten hindern. Ein unbekannter **Wert** lässt den bisherigen
Stand stehen (geraten wird nicht).

Auf dem Fernseher schreibt die Einstellungsseite diese Datei (**langes OK auf
„…“**); per `adb` geht es genauso:

```bash
adb shell 'printf "now_playing=on\n" > /sdcard/Android/data/com.piotv.navigate/files/settings.conf'
```

> **Ohne Benachrichtigungszugriff bleibt das Feld leer** — auch bei
> `now_playing=on`. Warum, steht in [INSTALL.md](INSTALL.md), Schritt 5b.

## Hintergrund — zwei Möglichkeiten (seit 0.14)

Hinter dem Raster liegt entweder der einfarbige, sehr dunkle Hintergrund
(Standard) oder ein **Bild**:

| `background` | Was zu sehen ist | Datei |
|---|---|---|
| `dark` (Standard) | `@color/bg_home` — derselbe Ton wie das Startfenster | keine |
| `image` | ein Bild, formatfüllend, auf Bildschirmgröße verkleinert | `background.jpg` |

Ein **Video**-Hintergrund gab es in 0.14 als dritten Wert; er ist in 0.14.1
entfernt. Ein `background=video` in einer alten Datei wird jetzt wie ein
unbekannter Wert gelesen — der bisherige Stand bleibt stehen.

Die Datei liegt im **externen App-Ordner** — derselbe Ordner, in den auch die
Konfigurationsdateien gepusht werden:

```bash
# Bild
adb push urlaub.jpg /sdcard/Android/data/com.piotv.navigate/files/background.jpg
adb shell 'printf "background=image\n" > /sdcard/Android/data/com.piotv.navigate/files/settings.conf'
```

Ein **eigener** Pfad geht auch (`background_file=/sdcard/Pictures/wand.jpg`);
`background_file=` **leer** nimmt den Standardnamen wieder. Fehlt die Datei oder
ist sie kein lesbares Bild, bleibt es dunkel — die Einstellungsseite nennt
den Pfad, auf dem Bildschirm gibt es keine Fehlermeldung.

**Was es bewusst nicht gibt:** die Hintergründe, die Android TV „eingebaut“ hat.
Die Bilder des Google-TV-Ambient-Modus („Backdrop“) liegen im privaten Speicher
des Google-TV-Launchers, die Empfehlungs-/Kanalbilder aus `TvContract` verlangen
eine TV-Input-Berechtigung, und `WallpaperManager` liefert auf dem Chromecast HD
kein Bild. Ein normaler Launcher kommt an keines davon heran (Begründung und
Belege: [LAST_SEEN_TITEL.md](LAST_SEEN_TITEL.md), Abschnitt „Hintergrund“).

**Kosten, ehrlich:** Ein Bild ist billig (einmal dekodieren, dann eine Fläche).
Deshalb ist `dark` der Standard und nicht `image`.

## navigate.conf — die Reihenfolge

Eine **Paketnamen-Zeile pro Eintrag**, in Anzeige-Reihenfolge. `#` beginnt einen
Kommentar, Leerzeilen sind erlaubt, unbekannte Zeilen werden **ignoriert** statt
als Fehler behandelt: eine handgeschriebene Datei darf Navigate nie daran
hindern zu starten. Toleriert werden zusätzlich umgebende Leerzeichen,
Komma-Listen und Zeilen der alten Form `schlüssel=wert`; doppelte Einträge fallen
weg.

```ini
# Navigate — Reihenfolge der Apps
com.netflix.ninja
de.swr.avp.ard.tv
com.google.android.youtube.tv
```

- Nicht (mehr) installierte Einträge werden stillschweigend übersprungen — die
  Datei räumt sich also selbst auf, wenn eine App deinstalliert wird.
- Alle Apps, die nicht in der Datei stehen, folgen danach in ihrer
  sprachsortierten Reihenfolge (dieselbe, die der Hersteller-Launcher benutzt).

Die Paketnamen findet man mit:

```bash
adb shell pm list packages -3
```

## Die Reihenfolge auf dem Fernseher ändern

**Langes OK** auf einer App schiebt sie **eine Position nach vorne** und
schreibt die Datei sofort. Steht die App schon ganz vorne, passiert nichts
(Toast). Aus dem „Rest“ (also aus dem nicht konfigurierten Teil) bedeutet das
Anhängen genau eine Position nach vorne.

Für einen großen Umbau ist die Datei der bequemere Weg — sie kennt die
vollständige Reihenfolge, langes Drücken nur den nächsten Schritt.

## Sichern und Zurücksetzen

```bash
# sichern (der Import-Pfad; die interne Datei ist ohne Root nicht lesbar)
adb pull /sdcard/Android/data/com.piotv.navigate/files/navigate.conf ./navigate.conf

# zurückspielen — wird beim nächsten Zugriff übernommen (max. 5 s)
adb push ./navigate.conf /sdcard/Android/data/com.piotv.navigate/files/navigate.conf

# auf Werkszustand (keine Reihenfolge = sprachsortiert)
adb push /dev/null /sdcard/Android/data/com.piotv.navigate/files/navigate.conf
```

Der letzte Befehl schiebt eine **leere** Datei an den Import-Pfad; sie ist neuer
als alles Vorherige und wird übernommen — eine leere Datei ergibt genau die
Grundordnung.

## history.conf — der Verlauf

Was lief: ein Eintrag pro Zeile, `zeit|paket|zeile`, neueste zuletzt.

```ini
# Navigate — Verlauf (Now Playing)
1730000000000|com.zdf|ZDF · Tagesschau
1730000005000|com.spotify|Spotify · Der Weg
```

- Geschrieben wird die Datei von Navigate selbst: ersetzt ein neuer Titel die
  Zeile einer App, wandert der alte hierher — statt zu verschwinden.
- **Gelesen wird sie als „zuletzt gesehen“:** der **oberste** Eintrag ist der
  *last seen*, alles darunter das *seen before* — dieselbe Reihenfolge, in der
  ein Mensch zurückschaut. Die Live-Zeilen (Apps mit laufender Media-Session)
  stehen über dem Verlauf; die neueste Zeile ist damit immer die, die gerade
  läuft bzw. zuletzt lief.
- **Gespeichert wird der Inhalt, nicht der Zustand.** Die Zeile im Verlauf trägt
  **kein** „· pausiert“: sonst stünde derselbe Titel zweimal in der Liste — einmal
  mit, einmal ohne Zusatz (genau so war es auf dem Zielgerät zu sehen). Die
  Anzeige **über** dem Verlauf trägt den Zusatz weiter, weil eine pausierte
  Session sonst aussähe wie eine laufende.
- **Eine Regel für jede App.** Welche App die Session hält, steht im Feld
  `paket`; die Zeilen-Regel (`NowPlayingText.content`) kennt keinen Paketnamen —
  Netflix, Disney+, ZDF, WOW und Prime werden gleich behandelt. Sichtbar wird
  eine App nur, wenn ihre Session **Metadaten** veröffentlicht: Netflix liefert
  je nach Zustand gar keinen Titel, dann bleibt sie ohne Zeile (das ist eine
  Grenze der App, nicht von Navigate).
- Gezeigt wird neueste oben: Live-Zeilen zuerst, darunter der Verlauf. Ältere
  Einträge werden beim Runterscrollen seitenweise (8) dynamisch nachgeladen —
  die Datei wird nie am Stück in Views verwandelt. Der Block ist höchstens
  260 dp hoch (rund fünf Zeilen auf 1080p), danach scrollt er in sich; die
  Fernbedienung lädt die nächste Seite, wenn der Fokus auf der letzten
  sichtbaren Verlaufszeile steht.
- **Die Scrollbar steht dauerhaft, und `▲`/`▼` gehen zeilenweise** (0.15.1):
  jede Taste holt die nächste Zeile ins Bild, statt sie geometrisch zu erraten.
  Am unteren Ende des Verlaufs führt `▼` zurück ins Raster. In 0.15.0 war die
  Höhengrenze wirkungslos (`android:maxHeight` gibt es am `ScrollView` nicht) —
  der Block wuchs mit dem Verlauf, bis die App-Zeile aus dem Bild war.
- Höchstens 500 Einträge: ältere fallen beim Schreiben weg. Wer mehr will,
  zieht per `adb pull` — die Datei ist lesbar:

```bash
adb pull /sdcard/Android/data/com.piotv.navigate/files/history.conf ./history.conf
```
