#!/bin/bash
# Navigate Installer
# Verwendung: bash install_navigate.sh [pfad/zur/apk]
#
# Gerät:  NAVIGATE_DEVICE=<ip>:5555 erzwingt genau dieses Gerät (keine Prüfung).
#         Ohne die Variable wird das erste erreichbare **Fernsehgerät** genommen
#         (`ro.build.characteristics` enthält `tv`). Emulatoren und Telefone
#         werden übersprungen — das Entwicklungs-Telefon hängt oft gleichzeitig
#         am adb, und ein Install dort wäre lautlos die falsche Zielscheibe.
#         ADB=/pfad/zu/adb, falls adb nicht im PATH liegt.
#
# Das Skript installiert nur. Die Home-Rolle und das Abschalten des
# Google-TV-Watchdogs sind bewusste, einzeln umkehrbare Schritte —
# siehe docs/INSTALL.md.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

APK_PATH="${1:-app/build/outputs/apk/release/app-release.apk}"
ADB="${ADB:-adb}"
PACKAGE="com.piotv.navigate"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Fehler: APK nicht gefunden: $APK_PATH"
    echo "   Führe zuerst 'bash build_navigate.sh release' aus."
    exit 1
fi

# Gerät bestimmen: NAVIGATE_DEVICE gewinnt, sonst das erste Fernsehgerät.
if [ -z "$NAVIGATE_DEVICE" ]; then
    for serial in $("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}'); do
        case "$serial" in
            emulator-*) continue ;;
        esac
        characteristics="$("$ADB" -s "$serial" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r' || true)"
        case "$characteristics" in
            *tv*)
                NAVIGATE_DEVICE="$serial"
                break
                ;;
        esac
    done
fi

if [ -z "$NAVIGATE_DEVICE" ]; then
    echo "❌ Kein Fernsehgerät gefunden. Erreichbare Geräte:"
    "$ADB" devices | awk 'NR>1 && $2=="device" {print "     " $1}'
    echo "   Entweder 'adb connect <ip>:5555' ausführen oder das Ziel erzwingen:"
    echo "     NAVIGATE_DEVICE=<ip>:5555 bash install_navigate.sh"
    exit 1
fi

MODEL="$("$ADB" -s "$NAVIGATE_DEVICE" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
echo "📦 Installiere Navigate auf $NAVIGATE_DEVICE${MODEL:+ ($MODEL)} ..."
"$ADB" -s "$NAVIGATE_DEVICE" install -r "$APK_PATH" | tail -2

echo ""
echo "✅ Installation durchgelaufen."
echo ""
echo "Nächste Schritte (einzeln umkehrbar, Details in docs/INSTALL.md):"
echo "  1) Home-Rolle:"
echo "     $ADB -s $NAVIGATE_DEVICE shell cmd role add-role-holder android.app.role.HOME $PACKAGE"
echo "  2) Google-TV-Watchdog abschalten (sonst holt er sich HOME zurück):"
echo "     $ADB -s $NAVIGATE_DEVICE shell pm disable-user --user 0 com.google.android.tungsten.setupwraith"
echo ""
echo "Prüfen:  $ADB -s $NAVIGATE_DEVICE shell cmd role get-role-holders android.app.role.HOME"
