#!/bin/bash
# Doku-Gate — prüft die dokumentierten Messwerte gegen das gebaute APK.
#
# Warum es das gibt: In 0.3 standen in README und docs/DEVICE_PERF.md Zahlen, die
# niemand nachgeprüft hat — und eine davon stimmte schon beim Nachbauen nicht
# (73.686 B dokumentiert, 73.679 B gebaut). Ein Messwert, den niemand prüft, ist
# eine Behauptung. Dieses Skript ist der Grund, warum die Zahlen stimmen.
#
# Verwendung:      bash tools/check_docs_drift.sh
# Voraussetzung:   bash build_navigate.sh release
#
# Quelle der Wahrheit ist der maschinenlesbare Block in docs/DEVICE_PERF.md
# ("## Messwerte, maschinenlesbar"). Zusätzlich muss die APK- und Dex-Größe im
# Fließtext von README.md vorkommen — sonst driften die beiden Dokumente
# auseinander und der Leser glaubt der falschen Zahl.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APK="app/build/outputs/apk/release/app-release.apk"
DOCS="docs/DEVICE_PERF.md"
README="README.md"

if [ ! -f "$APK" ]; then
    echo "❌ $APK fehlt — erst 'bash build_navigate.sh release' ausführen."
    exit 1
fi

apk_bytes=$(stat -c%s "$APK")
dex_bytes=$(unzip -l "$APK" | awk '/classes\.dex$/ { print $1 }')
dex_files=$(unzip -l "$APK" | grep -cE 'classes[0-9]*\.dex *$')
apk_files=$(unzip -l "$APK" | tail -1 | awk '{ print $2 }')
# Nur echte Annotationen am Zeilenanfang: „@Test" in einem Kommentar zählt nicht.
unit_tests=$(grep -rhoE '^[[:space:]]*@Test' app/src/test --include='*.kt' | wc -l | tr -d ' ')

fail=0

# 76430 → 76.430 (deutsche Schreibweise, wie sie in den Dokumenten steht).
dots() {
    printf '%s' "$1" | rev | sed 's/\([0-9][0-9][0-9]\)/\1./g' | rev | sed 's/^\.//'
}

expect() { # expect <schlüssel> <gebauter Wert>
    key="$1"; actual="$2"
    documented=$(grep -E "^${key}=" "$DOCS" | head -1 | cut -d= -f2 | tr -d ' ')
    if [ "$documented" != "$actual" ]; then
        echo "❌ ${key}: dokumentiert='${documented:-fehlt}' gebaut='${actual}'"
        fail=1
    else
        echo "✅ ${key}=${actual}"
    fi
}

echo "Doku-Gate — ${DOCS} gegen das gebaute Release-APK:"
expect release_apk_bytes "$apk_bytes"
expect release_dex_bytes "$dex_bytes"
expect release_dex_files "$dex_files"
expect apk_files "$apk_files"
expect unit_tests "$unit_tests"

for value in "$apk_bytes" "$dex_bytes"; do
    # Im README stehen die Zahlen in deutscher Schreibweise (76.430) — beide
    # Formen gelten.
    if ! grep -qF "$value" "$README" && ! grep -qF "$(dots "$value")" "$README"; then
        echo "❌ README.md nennt $value nicht (${DOCS} schon)"
        fail=1
    fi
done

if [ "$fail" != 0 ]; then
    echo ""
    echo "Doku und Build passen nicht zusammen. Nachziehen:"
    echo "  - docs/DEVICE_PERF.md, Block \"Messwerte, maschinenlesbar\""
    echo "  - README.md, Tabelle ganz oben"
    exit 1
fi

echo "✅ Doku und Build stimmen überein."
