#!/usr/bin/env bash
# Render ADD's native screens with Robolectric NATIVE graphics.
#
#   bash tools/android-render/setup.sh    # once per machine
#   bash tools/android-render/render.sh   # -> artifacts/android/*.png + review set
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JDK="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
MAVEN="${MAVEN:-$HOME/tools/maven/bin/mvn}"
STAGE="$ROOT/artifacts/.android-next"

export JAVA_HOME="$JDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$JDK/bin:$PATH"

rm -rf "$STAGE"
mkdir -p "$STAGE"

if ! "$MAVEN" -q -f "$ROOT/tools/android-render/pom.xml" test \
    -Dandroid.jar="$SDK/platforms/android-35/android.jar" \
    -Drender.root="$ROOT" \
    -Dadd.out="$STAGE"; then
    rm -rf "$STAGE"
    echo "render: failed; artifacts/android was left untouched" >&2
    exit 1
fi

# Nothing above touches the previous good set. Replace it only after every test
# has passed, so a failed render cannot leave a half-populated review set.
rm -rf "$ROOT/artifacts/android"
mv "$STAGE" "$ROOT/artifacts/android"
python3 "$ROOT/tools/android-render/review.py"

echo "== renders =="
ls -lh "$ROOT"/artifacts/android/*.png
