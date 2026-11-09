#!/bin/bash
# Navigate installer
# Usage: bash install_navigate.sh [path/to/apk]
#
# Device: NAVIGATE_DEVICE=<ip>:5555 forces exactly this device (no check).
#         Without the variable the first reachable **TV device** is used
#         (`ro.build.characteristics` contains `tv`). Emulators and phones
#         are skipped — the development phone is often attached to adb at the
#         same time, and an install there would silently hit the wrong target.
#         ADB=/path/to/adb if adb is not on the PATH.
#
# The script only installs. The home role and switching off the
# Google TV watchdog are deliberate, individually reversible steps —
# see docs/INSTALL.md.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

APK_PATH="${1:-app/build/outputs/apk/release/app-release.apk}"
ADB="${ADB:-adb}"
PACKAGE="com.piotv.navigate"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Error: APK not found: $APK_PATH"
    echo "   Run 'bash build_navigate.sh release' first."
    exit 1
fi

# Pick the device: NAVIGATE_DEVICE wins, otherwise the first TV device.
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
    echo "❌ No TV device found. Reachable devices:"
    "$ADB" devices | awk 'NR>1 && $2=="device" {print "     " $1}'
    echo "   Either run 'adb connect <ip>:5555' or force the target:"
    echo "     NAVIGATE_DEVICE=<ip>:5555 bash install_navigate.sh"
    exit 1
fi

MODEL="$("$ADB" -s "$NAVIGATE_DEVICE" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
echo "📦 Installing Navigate on $NAVIGATE_DEVICE${MODEL:+ ($MODEL)} ..."
"$ADB" -s "$NAVIGATE_DEVICE" install -r "$APK_PATH" | tail -2

echo ""
echo "✅ Installation finished."
echo ""
echo "Next steps (individually reversible, details in docs/INSTALL.md):"
echo "  1) Home role:"
echo "     $ADB -s $NAVIGATE_DEVICE shell cmd role add-role-holder android.app.role.HOME $PACKAGE"
echo "  2) Switch off the Google TV watchdog (otherwise it takes HOME back):"
echo "     $ADB -s $NAVIGATE_DEVICE shell pm disable-user --user 0 com.google.android.tungsten.setupwraith"
echo ""
echo "Verify:  $ADB -s $NAVIGATE_DEVICE shell cmd role get-role-holders android.app.role.HOME"
