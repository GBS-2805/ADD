#!/usr/bin/env bash
# Verify the Roborazzi goldens for the native Compose engine chrome.
#
# The captured states contain no live Filament SurfaceView: the 3D viewport is
# paused while the sheet is open. This guard is therefore chrome-only. Run with
# --update to re-record the committed PNGs after reviewing a deliberate UI change.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JDK="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BASELINE="${ENGINE_CHROME_DIR:-$ROOT/app/src/test/screenshots}"
TASK=verifyRoborazziDebug

if [ "${1:-}" = "--update" ]; then
    TASK=recordRoborazziDebug
    shift
fi

if [ -z "${JDK:-}" ] || [ ! -x "$JDK/bin/java" ]; then
    echo "JAVA_HOME is not set; install a JDK 17." >&2
    exit 1
fi
if [ ! -f "$SDK/platforms/android-35/android.jar" ]; then
    echo "Android platform 35 not found at $SDK." >&2
    exit 1
fi

cd "$ROOT"
printf 'sdk.dir=%s\n' "$SDK" > local.properties

export JAVA_HOME="$JDK"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export ENGINE_RENDER_OUT="$BASELINE"

./gradlew --no-daemon :app:"$TASK" \
    --tests app.add.engine.EngineFrameCaptureTest "$@"

echo
echo "Native chrome guard: $TASK"
echo "Baselines: $BASELINE"
if [ "$TASK" = verifyRoborazziDebug ]; then
    echo "chrome: goldens match"
else
    echo "chrome: goldens recorded"
fi
