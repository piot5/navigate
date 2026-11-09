#!/bin/bash
# Navigate build script
# Usage: bash build_navigate.sh [debug|release]
#
# Debug:   com.piotv.navigate.debug (runs alongside the release build)
# Release: R8 + resource shrinking, signed when keystore/keystore.properties
#          or the NAVIGATE_* environment variables are present.

set -e

BUILD_TYPE="${1:-debug}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "🔨 Building Navigate ($BUILD_TYPE)..."

cd "$PROJECT_DIR"

if [ "$BUILD_TYPE" = "release" ]; then
    # Signing credentials: load keystore/keystore.properties (gitignored) into
    # the environment if present — otherwise the APK stays unsigned (no silent
    # default password in the build file).
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
    echo "✅ Build succeeded!"
    echo "📍 APK: $APK_PATH ($(du -h "$APK_PATH" | cut -f1))"
    echo ""
    echo "Install:"
    echo "  bash install_navigate.sh $APK_PATH"
else
    echo "❌ Build failed!"
    exit 1
fi
