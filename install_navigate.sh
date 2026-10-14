#!/bin/bash
# Navigate Installer
# Verwendung: bash install_navigate.sh [pfad/zur/apk]
#
# Gerät:  NAVIGATE_DEVICE=<ip>:5555 (oder das erste erreichbare Gerät)
#         ADB=/pfad/zu/adb, falls adb nicht im PATH liegt.
#
# Das Skript installiert nur. Die Home-Rolle und der Nutzungszugriff sind
# bewusste, einzeln umkehrbare Schritte — siehe docs/INSTALL.md.

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

# Gerät bestimmen: NAVIGATE_DEVICE gewinnt, sonst das erste Gerät.
if [ -z "$NAVIGATE_DEVICE" ]; then
    NAVIGATE_DEVICE="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
fi

if [ -z "$NAVIGATE_DEVICE" ]; then
    echo "❌ Kein Gerät gefunden. adb connect <ip>:5555 ausführen oder"
    echo "   NAVIGATE_DEVICE=<ip>:5555 setzen."
    exit 1
fi

echo "📦 Installiere Navigate auf $NAVIGATE_DEVICE ..."
"$ADB" -s "$NAVIGATE_DEVICE" install -r "$APK_PATH" | tail -2

echo ""
echo "✅ Installation durchgelaufen."
echo ""
echo "Nächste Schritte (einzeln umkehrbar, Details in docs/INSTALL.md):"
echo "  1) Home-Rolle:"
echo "     $ADB -s $NAVIGATE_DEVICE shell cmd role add-role-holder android.app.role.HOME $PACKAGE"
echo "  2) Google-TV-Watchdog abschalten (sonst holt er sich HOME zurück):"
echo "     $ADB -s $NAVIGATE_DEVICE shell pm disable-user --user 0 com.google.android.tungsten.setupwraith"
echo "  3) Nutzungszugriff (exakte Erkennung statt Heuristik):"
echo "     $ADB -s $NAVIGATE_DEVICE shell appops set $PACKAGE GET_USAGE_STATS allow"
echo ""
echo "Prüfen:  $ADB -s $NAVIGATE_DEVICE shell cmd role get-role-holders android.app.role.HOME"
