# Installation (Chromecast / Google TV)

Getestet auf einem **Chromecast HD** (Google TV, Android 14, 1920×1080,
TV-Dichte, 1,4 GB RAM). Die Schritte gelten für alle Google-TV-Geräte; bei
Fire TV/anderen Android-TV-Geräten heißt der Hersteller-Launcher anders, das
Vorgehen bleibt dasselbe.

## 1. APK bauen und installieren

```bash
cd projects/navigate
bash build_navigate.sh release
bash install_navigate.sh app/build/outputs/apk/release/app-release.apk
```

`install_navigate.sh` nimmt das erste erreichbare Gerät oder das in
`NAVIGATE_DEVICE` gesetzte (z. B. `NAVIGATE_DEVICE=192.168.178.23:5555`).
Voraussetzung ist ein eingeschaltetes ADB auf dem Gerät
(Entwickleroptionen → USB-Debugging, bei Google TV zusätzlich
„Netzwerk-Debugging“, dann `adb connect <ip>:5555`).

Nach dem Installieren ist Navigate startbar, aber noch nicht die Home-App.

## 2. Als Home-App eintragen

Auf Android 10+ entscheidet die **Rolle** `android.app.role.HOME`, nicht ein
älterer „preferred activity"-Eintrag. `cmd package set-home-activity` setzt
zwar den Eintrag, aber nicht zuverlässig die Rolle — die Rolle ist der
verlässliche Weg:

```bash
adb shell cmd role add-role-holder android.app.role.HOME com.piotv.navigate
adb shell cmd role get-role-holders android.app.role.HOME   # Kontrolle
```

## 3. Googles Home-Watchdog abschalten

Ohne diesen Schritt holt sich `com.google.android.tungsten.setupwraith` die
HOME-Taste zurück — auf dem Testgerät nachweisbar: der HOME-Druck startete
`setupwraith/.RecoveryActivity` (dessen HOME-Filter hat `priority=1`, ein
Watchdog startet ihn immer wieder), obwohl Navigate die HOME-Rolle hielt.
Navigate deklariert deshalb selbst `priority=10`; das reicht aber nicht gegen
den Watchdog.

```bash
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Der Google-TV-Launcher (`com.google.android.apps.tv.launcherx`) muss dafür
**nicht** deaktiviert werden — anders als bei manchen Anleitungen behauptet.
Ist er bereits deaktiviert (z. B. weil vorher ein anderer Launcher lief),
schadet das nicht.

## 4. Nutzungszugriff erteilen (empfohlen)

Ohne Nutzungszugriff muss Navigate **raten**, ob hinter dem Dock noch eine App
läuft (Heuristik, siehe [ARCHITECTURE.md](ARCHITECTURE.md)). Mit Zugriff ist die
Antwort exakt:

```bash
adb shell appops set com.piotv.navigate GET_USAGE_STATS allow
adb shell appops get com.piotv.navigate GET_USAGE_STATS   # Kontrolle
```

Alternativ auf dem Gerät: **Einstellungen → Apps → Spezieller Zugriff →
Nutzungszugriff → Navigate**. Der Weg ist auch von Navigate aus erreichbar:
langes Drücken auf **Einstellungen** in der unteren Zeile.

## 5. Fertig

HOME drücken. Läuft eine App, erscheint nur die untere Zeile; läuft nichts,
erscheint die Home-Seite.

## Bedienung

| Taste | Wirkung |
|---|---|
| HOME | Zeile über der laufenden App, sonst Home-Seite |
| HOME (Zeile steht) | Zeile schließen, zurück zur App |
| ◀ ▲ ▼ ▶ | Fokus (Raster ↔ Zeile mit ▼ / ▲) |
| OK | App starten · Favorit an/aus (Einstellungen) · ◀ ▶ verschieben |
| langes OK im Raster | Favorit setzen/entfernen |
| langes OK in der Zeile | Favorit entfernen |
| langes OK auf Einstellungen | Nutzungszugriff |
| BACK in der Zeile | Zeile schließen, zurück zur App |
| BACK auf der Home-Seite | ohne Wirkung (nichts, was darüber liegt) |

**Wichtig für die Erwartung an die Zeile:** sie hält den Tastaturfokus, solange
sie steht — das ist ihr Zweck (man soll Favoriten wählen können), heißt aber
auch, dass die App darunter keine D-Pad-Eingabe mehr bekommt. HOME oder BACK
schließen sie, die App kommt zurück und ist wieder bedienbar.


## Rückweg

Alles ist umkehrbar. Reihenfolge egal:

```bash
# 1. Watchdog wieder an
adb shell pm enable com.google.android.tungsten.setupwraith

# 2. Rolle an den vorherigen Launcher (Beispiel: Projectivy)
adb shell cmd role add-role-holder android.app.role.HOME com.spocky.projengmenu
# oder ganz ohne Home-Rolle:
adb shell cmd role remove-role-holder android.app.role.HOME com.piotv.navigate

# 3. Google-TV-Launcher wieder anschalten, falls er deaktiviert war
adb shell pm enable com.google.android.apps.tv.launcherx

# 4. Navigate deinstallieren (optional)
adb uninstall com.piotv.navigate
```

Nach `pm enable` des Google-Launchers und dem HOME-Druck fragt das System, welche
Home-App verwendet werden soll.

## Fehlersuche

**HOME zeigt weiter den alten Launcher.** Meist fehlt Schritt 3 oder es läuft
noch ein alter Home-Task. Kontrolle:

```bash
adb shell cmd role get-role-holders android.app.role.HOME
adb shell dumpsys package com.google.android.tungsten.setupwraith | grep 'User 0'
# enabled=3 heißt: deaktiviert (so soll es aussehen)
```

Einen alten Home-Task loswerden: `adb shell am force-stop <alter-launcher>`.

**Die Zeile erscheint, aber dahinter ist ein leerer Bildschirm.** Dann ist das
Fensterformat nicht translucide — siehe
[ARCHITECTURE.md](ARCHITECTURE.md#das-fensterformat-gemessener-kompromiss).
In dieser Version ist das behoben; bei eigenen Änderungen an Theme oder
Fensterattributen darauf achten.

**Die Zeile zeigt nie etwas anderes als die Home-Seite.** Der Nutzungszugriff
fehlt (Schritt 4) und die Heuristik hält keine App für laufend. Prüfen mit
`adb shell appops get com.piotv.navigate GET_USAGE_STATS`.

**Ein Symbol fehlt im Raster.** Prüfen, ob die App überhaupt startbar ist:

```bash
adb shell cmd package query-activities -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER | grep -i <name>
```

Apps ohne Launcher-Eintrag (reine Bibliotheken, Systemdienste) gehören nicht ins
Raster. TV-Apps mit nur `LEANBACK_LAUNCHER` werden erfasst.
