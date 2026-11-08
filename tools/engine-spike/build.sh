#!/usr/bin/env bash
# Build and test the standalone native engine lab. Not part of ADD's release.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "JAVA_HOME is not set; install JDK 17." >&2
    exit 1
fi

cd "$ROOT"
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties

./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain

echo
echo "APK: $ROOT/app/build/outputs/apk/debug/app-debug.apk"
