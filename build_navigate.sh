#!/bin/bash
# Navigate Build-Skript
# Verwendung: bash build_navigate.sh [debug|release]
#
# Debug:  com.piotv.navigate.debug (läuft neben dem Release-Build)
# Release: R8 + Resource-Shrinking, signiert wenn keystore/keystore.properties
#          oder die NAVIGATE_*-Umgebungsvariablen vorhanden sind.

set -e

BUILD_TYPE="${1:-debug}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "🔨 Baue Navigate ($BUILD_TYPE)..."

cd "$PROJECT_DIR"

if [ "$BUILD_TYPE" = "release" ]; then
    # Signing-Credentials: keystore/keystore.properties (gitignored) ins Env
    # laden, falls vorhanden — sonst bleibt das APK unsigniert (kein stilles
    # Default-Passwort im Buildfile).
    if [ -f "keystore/keystore.properties" ]; then
        set -a; . ./keystore/keystore.properties; set +a
    fi
    sh ./gradlew :app:assembleRelease
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
    sh ./gradlew :app:assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -f "$APK_PATH" ]; then
    echo "✅ Build erfolgreich!"
    echo "📍 APK: $APK_PATH ($(du -h "$APK_PATH" | cut -f1))"
    echo ""
    echo "Installieren:"
    echo "  bash install_navigate.sh $APK_PATH"
else
    echo "❌ Build fehlgeschlagen!"
    exit 1
fi
