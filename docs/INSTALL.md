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

`install_navigate.sh` nimmt ohne Zutun das erste erreichbare **Fernsehgerät**
(`ro.build.characteristics` enthält `tv`) — Emulatoren und Telefone werden
übersprungen, damit ein gleichzeitig am adb hängendes Test-Telefon nicht die
falsche Zielscheibe ist. Ein anderes Ziel erzwingt man mit
`NAVIGATE_DEVICE=192.168.178.23:5555` (dann wird nicht geprüft).
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

## 4. Fertig

HOME drücken: die Home-Seite erscheint — unten eine Zeile mit den ersten Apps,
darunter der „…“-Knopf. „…“ klappt das ganze Raster auf und wieder zu.

## 5. Optional: Zugriffe (einzeln, unabhängig)

Beide Zugriffe sind freiwillig, jeder schaltet genau **eine** Funktion frei, und
beide sind einzeln rücknehmbar (siehe „Rückweg“). Ohne sie startet Navigate
unverändert — es fehlt nur das eine Feld bzw. die exakte Antwort auf „zurück in
die letzte App“.

### 5a. Nutzungszugriff — für „zurück in die letzte App“

Auf der **schon sichtbaren** Home-Seite holen HOME und BACK die zuletzt benutzte
App zurück. Woher Navigate das weiß, hängt an einem optionalen App-Op:

```bash
adb shell appops set com.piotv.navigate GET_USAGE_STATS allow
adb shell appops get com.piotv.navigate GET_USAGE_STATS   # Kontrolle
```

- **Mit** dem Zugriff liest Navigate die Nutzungsstatistik (kurzes Fenster, dann
  ein langes) — exakt, auch für Apps, die nicht hier gestartet wurden.
- **Ohne** ihn merkt sich Navigate die zuletzt **über Navigate gestartete** App.
  Das ist im Normalfall richtig und stört sonst nicht: ohne bekannte App bleibt
  die Home-Seite einfach stehen.

Alternativ auf dem Gerät: **Einstellungen → Apps → Spezieller Zugriff →
Nutzungszugriff → Navigate**.

### 5b. Benachrichtigungszugriff — für „Now Playing“

Das Feld oben links zeigt App, Interpret, Titel und Cover der laufenden
Media-Session — **ohne diesen Zugriff bleibt es leer**. Der Grund ist keine
Bequemlichkeit, sondern eine Grenze von Android: `getActiveSessions` verlangt,
dass der Aufrufer ein eingetragener Benachrichtigungs-Listener ist. (Die
Alternative wäre `MEDIA_CONTENT_CONTROL` — eine Signatur-Berechtigung, die
`pm grant` nicht vergeben kann.)

```bash
adb shell cmd notification allow_listener com.piotv.navigate/com.piotv.navigate.NowPlayingListener
# Kontrolle — der Name muss in der Liste stehen:
adb shell settings get secure enabled_notification_listeners
```

Navigate liest dabei **keine** Benachrichtigungen: `NowPlayingListener` ist ein
leerer Dienst, sein eingetragener Name ist der Ausweis für die Abfrage der
Media-Sessions. Ohne die Freigabe bleibt das Feld leer — und die
Einstellungsseite sagt das auch (langes OK auf „…“).

Alternativ auf dem Gerät: **Einstellungen → Apps → Spezieller Zugriff →
Benachrichtigungszugriff → Navigate — Now Playing**.

## Bedienung

| Taste | Wirkung |
|---|---|
| HOME | von einer App: Home-Seite · auf der Home-Seite: zurück in die letzte App |
| ◀ ▲ ▼ ▶ | Fokus im Raster |
| OK | App starten · auf „…“ das Raster auf-/zuklappen · auf „Now Playing“ zurück in die spielende App |
| BACK | aufgeklapptes Raster zuklappen · sonst zurück in die letzte App |
| langes OK | auf einer App: Kontextmenü (Position nach vorne/hinten · deinstallieren · schließen · alle schließen) · auf „…“: Einstellungen |


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

# 4. Zugriffe zurücknehmen (nur was gesetzt wurde)
adb shell appops set com.piotv.navigate GET_USAGE_STATS default
adb shell cmd notification disallow_listener com.piotv.navigate/com.piotv.navigate.NowPlayingListener

# 5. Navigate deinstallieren (optional)
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

**Die Zeile über der laufenden App fehlt.** Es gibt sie seit 0.6 nicht mehr —
der HOME-Druck zeigt immer das Raster. Wenn stattdessen etwas anderes passiert,
ist die Home-Rolle nicht gesetzt (Schritt 2) oder der Google-TV-Watchdog ist
wieder aktiv (Schritt 3).

**Das „Now Playing“-Feld bleibt leer.** Drei mögliche Gründe, in dieser
Reihenfolge prüfen:

```bash
# 1. Ist die Einstellung an? (langes OK auf „…“ — oder per Datei)
adb shell cat /sdcard/Android/data/com.piotv.navigate/files/settings.conf
# 2. Hat Navigate den Benachrichtigungszugriff? (Schritt 5b)
adb shell settings get secure enabled_notification_listeners
# 3. Spielt überhaupt etwas? (leer = nichts zu zeigen, das Feld bleibt aus)
adb shell dumpsys media_session | grep -E 'package=|state='
```

Leer ist kein Fehler: ohne Session wird der Block **ausgeblendet**, statt einen
Platzhalter zu zeigen. Ein pausierter Titel wird dagegen angezeigt (mit
`· pausiert`) — sonst sähe er aus wie „läuft“.

**Ein Symbol fehlt im Raster.** Prüfen, ob die App überhaupt startbar ist:

```bash
adb shell cmd package query-activities -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER | grep -i <name>
```

Apps ohne Launcher-Eintrag (reine Bibliotheken, Systemdienste) gehören nicht ins
Raster. TV-Apps mit nur `LEANBACK_LAUNCHER` werden erfasst.
