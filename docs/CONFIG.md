# Konfiguration

Einstellbar sind in Navigate **zwei Sachen**: die **Reihenfolge der Apps**
(`navigate.conf`) und ob das **„Now Playing“-Feld** erscheint (`settings.conf`).
Jede der beiden Dateien liegt an **zwei** Orten — der Unterschied ist wichtig:

| Datei | Inhalt | Quelle der Wahrheit (hier liest Navigate) | Import (`adb push`) |
|---|---|---|---|
| `navigate.conf` | Reihenfolge — eine Paketnamen-Zeile je Eintrag | `/data/data/com.piotv.navigate/files/navigate.conf` | `/storage/emulated/0/Android/data/com.piotv.navigate/files/navigate.conf` |
| `settings.conf` | Einstellungen — `schlüssel=wert` | `/data/data/com.piotv.navigate/files/settings.conf` | `/storage/emulated/0/Android/data/com.piotv.navigate/files/settings.conf` |

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
```

| Schlüssel | Werte | Wirkung |
|---|---|---|
| `now_playing` | `on` / `off` (auch `true`/`false`, `1`/`0`, `an`/`aus`, `ja`/`nein`, Groß-/Kleinschreibung egal) | zeigt das „Now Playing“-Feld oben links — seit 0.13 **eine Zeile je App** mit aktiver Media-Session, nur im zugeklappten Zustand |

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
