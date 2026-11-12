#!/usr/bin/env bash
# Builds the review-only ADD APK with the native engine spike inside it.
#
# This is deliberately outside build.sh: the shipping ADD build stays raw SDK
# tools, while this review takes the Gradle/Kotlin/Compose path the spike needs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JDK="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
HERE="$ROOT/tools/engine-review"
OUT="$ROOT/artifacts/engine-review"
APK="$OUT/ADD-3.9.1-field-preview.apk"

if [ -z "${JDK:-}" ] || [ ! -x "$JDK/bin/java" ]; then
    echo "JAVA_HOME is not set; install a JDK 17 and export JAVA_HOME." >&2
    exit 1
fi
if [ ! -f "$SDK/platforms/android-35/android.jar" ]; then
    echo "Android platform 35 not found at $SDK" >&2
    exit 1
fi

export JAVA_HOME="$JDK"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$JDK/bin:$PATH"

printf 'sdk.dir=%s\n' "$SDK" > "$HERE/local.properties"
mkdir -p "$OUT"

echo "== ADD engine review: icon catalog =="
python3 "$ROOT/tools/engine-spike/scripts/prepare_icons.py"

echo "== ADD engine review: assembleDebug =="
"$ROOT/tools/engine-spike/gradlew" --no-daemon -p "$HERE" :app:assembleDebug

cp "$HERE/app/build/outputs/apk/debug/app-debug.apk" "$APK"

echo "== ADD engine review: signature =="
"$SDK/build-tools/35.0.0/apksigner" verify --print-certs "$APK" | head -4
echo "== ADD engine review: done =="
ls -lh "$APK"
