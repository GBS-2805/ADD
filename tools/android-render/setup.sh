#!/usr/bin/env bash
# Build the inputs for the Robolectric renderer without Gradle:
#   app/res -> binary resources + R.java -> app classes jar
#   AndroidX monitor AAR -> its classes.jar (Maven cannot use an AAR as a jar)
#   flat android-all-instrumented jar for Robolectric offline mode
#
# Run once per machine, then use tools/android-render/render.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/tools/android-render"
BUILD="$ROOT/build/android-render"
JDK="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"
MAVEN="${MAVEN:-$HOME/tools/maven/bin/mvn}"
M2="$HOME/.m2/repository"
VERSION=15-robolectric-12650502-i7
MONITOR="$BUILD/lib/monitor-1.7.2.jar"
MONITOR_AAR="$BUILD/lib/monitor-1.7.2.aar"

if [ -z "${JDK:-}" ] || [ ! -x "$JDK/bin/javac" ]; then
    echo "setup: JAVA_HOME is not set and no JDK was found." >&2
    exit 1
fi
if [ ! -x "$BT/aapt2" ] || [ ! -f "$PLATFORM" ]; then
    echo "setup: Android SDK 35 build-tools are not at $SDK." >&2
    exit 1
fi
if [ ! -x "$MAVEN" ]; then
    echo "setup: Maven is not at $MAVEN." >&2
    exit 1
fi

mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/lib" "$BUILD/deps"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir "$ROOT/app/res" -o "$BUILD/res.zip"

echo "== aapt2 link =="
"$BT/aapt2" link \
    -o "$BUILD/resources.apk" \
    -I "$PLATFORM" \
    --manifest "$ROOT/app/AndroidManifest.xml" \
    -R "$BUILD/res.zip" \
    --java "$BUILD/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    --auto-add-overlay

echo "== javac app =="
find "$ROOT/app/src" "$BUILD/gen" -name '*.java' | sort > "$BUILD/sources.txt"
"$JDK/bin/javac" -nowarn -source 8 -target 8 -Xlint:-options \
    -bootclasspath "$PLATFORM" -classpath "$PLATFORM:$BT/core-lambda-stubs.jar" \
    -d "$BUILD/classes" @"$BUILD/sources.txt"
"$JDK/bin/jar" --create --file "$BUILD/lib/add-app.jar" -C "$BUILD/classes" .

if [ ! -s "$MONITOR" ]; then
    echo "== androidx.test monitor =="
    # Google Maven serves the AAR; Central does not. Use a normal user agent
    # because this host has been rate-limited by Central's Maven client.
    curl -fsSL -A "Mozilla/5.0" \
        "https://dl.google.com/dl/android/maven2/androidx/test/monitor/1.7.2/monitor-1.7.2.aar" \
        -o "$MONITOR_AAR"
    unzip -p "$MONITOR_AAR" classes.jar > "$MONITOR"
fi

echo "== Robolectric offline runtime =="
ALL_JAR="$(find "$M2/org/robolectric/android-all-instrumented" \
    -name "android-all-instrumented-$VERSION.jar" -print -quit)"
if [ -z "$ALL_JAR" ]; then
    echo "setup: android-all-instrumented-$VERSION.jar is missing from $M2." >&2
    echo "Run a Maven resolution once, or copy it into $M2." >&2
    exit 1
fi
ln -sfn "$ALL_JAR" "$BUILD/deps/android-all-instrumented-$VERSION.jar"

echo "== ready =="
echo "resources: $BUILD/resources.apk"
echo "app jar:   $BUILD/lib/add-app.jar"
echo "monitor:   $MONITOR"
