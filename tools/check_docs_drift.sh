#!/bin/bash
# Docs gate — checks the documented metrics against the built APK.
#
# Why it exists: in 0.3, README and docs/DEVICE_PERF.md quoted numbers nobody
# had verified — and one of them was already wrong when reproduced
# (73,686 B documented, 73,679 B built). A metric nobody checks is a claim.
# This script is the reason the numbers are right.
#
# Usage:         bash tools/check_docs_drift.sh
# Requirement:   bash build_navigate.sh release
#
# The source of truth is the machine-readable block in docs/DEVICE_PERF.md
# ("## Metrics, machine-readable"). Additionally, the APK and dex sizes must
# appear in the README.md prose — otherwise the two documents drift apart and
# the reader believes the wrong number.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APK="app/build/outputs/apk/release/app-release.apk"
# Two possible names, and that is no accident: with credentials AGP signs to
# `app-release.apk`, without them to `app-release-unsigned.apk`. In CI without
# a keystore secret the unsigned version is built — and it has a different
# size, because the signature sits in the archive as META-INF/CERT.* (and the
# file count is two entries higher because of it). That is why both have a
# documented value instead of skipping the check.
UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
DOCS="docs/DEVICE_PERF.md"
README="README.md"

if [ -f "$APK" ]; then
    KEY_APK="release_apk_bytes"
    KEY_FILES="apk_files"
elif [ -f "$UNSIGNED_APK" ]; then
    APK="$UNSIGNED_APK"
    KEY_APK="release_apk_unsigned_bytes"
    KEY_FILES="apk_unsigned_files"
else
    echo "❌ No release APK found:"
    echo "   $APK"
    echo "   $UNSIGNED_APK"
    echo "   Run 'bash build_navigate.sh release' first."
    exit 1
fi

apk_bytes=$(stat -c%s "$APK")
dex_bytes=$(unzip -l "$APK" | awk '/classes\.dex$/ { print $1 }')
dex_files=$(unzip -l "$APK" | grep -cE 'classes[0-9]*\.dex *$')
apk_files=$(unzip -l "$APK" | tail -1 | awk '{ print $2 }')
# Only real annotations at the start of a line: an "@Test" inside a comment
# does not count.
unit_tests=$(grep -rhoE '^[[:space:]]*@Test' app/src/test --include='*.kt' | wc -l | tr -d ' ')

# Coverage: the source is the JaCoCo report, not the docs. The root node's
# counters stand at the end of the file (after all packages and classes), so
# `tail -1` hits the total and not an intermediate value.
JACOCO="app/build/reports/jacoco/unitTestCoverageReport/unitTestCoverageReport.xml"
if [ ! -f "$JACOCO" ]; then
    echo "❌ $JACOCO missing — run 'sh gradlew :app:unitTestCoverageReport' first."
    exit 1
fi

# 202/1005 → "20.1" (English notation, as it appears in the documents).
coverage_of() { # coverage_of <LINE|BRANCH>
    local values missed covered
    values=$(grep -oE "<counter type=\"$1\" missed=\"[0-9]+\" covered=\"[0-9]+\"/>" "$JACOCO" | tail -1)
    missed=$(printf '%s' "$values" | grep -oE 'missed="[0-9]+"' | grep -oE '[0-9]+')
    covered=$(printf '%s' "$values" | grep -oE 'covered="[0-9]+"' | grep -oE '[0-9]+')
    if [ -z "$missed" ] || [ -z "$covered" ]; then
        printf '%s' "missing"
        return
    fi
    awk -v c="$covered" -v t="$((missed + covered))" 'BEGIN { printf "%.1f", 100*c/t }'
}

coverage_lines=$(coverage_of LINE)
coverage_branches=$(coverage_of BRANCH)

fail=0

# 76430 → 76,430 (English notation, as it appears in the documents).
commas() {
    printf '%s' "$1" | rev | sed 's/\([0-9][0-9][0-9]\)/\1,/g' | rev | sed 's/^,//'
}

expect() { # expect <key> <built value>
    key="$1"; actual="$2"
    documented=$(grep -E "^${key}=" "$DOCS" | head -1 | cut -d= -f2 | tr -d ' ')
    if [ "$documented" != "$actual" ]; then
        echo "❌ ${key}: documented='${documented:-missing}' built='${actual}'"
        fail=1
    else
        echo "✅ ${key}=${actual}"
    fi
}

echo "Docs gate — ${DOCS} against the built release APK ($(basename "$APK")):"
expect "$KEY_APK" "$apk_bytes"
expect release_dex_bytes "$dex_bytes"
expect release_dex_files "$dex_files"
expect "$KEY_FILES" "$apk_files"
expect unit_tests "$unit_tests"
expect coverage_lines_percent "$coverage_lines"
expect coverage_branches_percent "$coverage_branches"

for value in "$apk_bytes" "$dex_bytes"; do
    # In README the numbers use English notation (76,430) — both forms count.
    if ! grep -qF "$value" "$README" && ! grep -qF "$(commas "$value")" "$README"; then
        echo "❌ README.md does not mention $value (${DOCS} already does)"
        fail=1
    fi
done

# The coverage numbers just the same: until 0.12 they lived only in the prose
# and could silently go stale.
for value in "$coverage_lines" "$coverage_branches"; do
    if ! grep -qF "$value" "$README"; then
        echo "❌ README.md does not mention coverage ${value} (${DOCS} already does)"
        fail=1
    fi
done

# No test count other than the built test set's: exactly this error sat in
# ARCHITECTURE.md (54 tests two paragraphs next to the correct 94).
for doc in "$README" docs/ARCHITECTURE.md; do
    for value in $(grep -oiE '[0-9]+ (unit[- ])?tests' "$doc" | grep -oE '^[0-9]+' | sort -u); do
        if [ "$value" != "$unit_tests" ]; then
            echo "❌ ${doc} mentions ${value} tests, the test set has ${unit_tests}"
            fail=1
        fi
    done
done

if [ "$fail" != 0 ]; then
    echo ""
    echo "Docs and build disagree. Catch up:"
    echo "  - docs/DEVICE_PERF.md, block \"Metrics, machine-readable\""
    echo "  - README.md, table at the very top"
    exit 1
fi

echo "✅ Docs and build agree."
