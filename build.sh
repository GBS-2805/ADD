#!/usr/bin/env bash
# Builds the offline Compress APK with the raw Android SDK tools (no Gradle).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JDK="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"

if [ -z "${JDK:-}" ] || [ ! -x "$JDK/bin/javac" ]; then
    echo "JAVA_HOME is not set; install a JDK and export JAVA_HOME." >&2
    exit 1
fi
if [ ! -f "$PLATFORM" ]; then
    echo "Android platform 35 not found at $PLATFORM" >&2
    exit 1
fi

export PATH="$JDK/bin:$PATH"

APP="$ROOT/app"
BUILD="$ROOT/build"
OUT="$ROOT/dist"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$OUT"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "== aapt2 link =="
"$BT/aapt2" link \
    -o "$BUILD/unsigned.apk" \
    -I "$PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    -R "$BUILD/res.zip" \
    --java "$BUILD/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    --version-code 9 \
    --version-name 1.8 \
    --auto-add-overlay

echo "== javac =="
find "$APP/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
"$JDK/bin/javac" -nowarn -source 8 -target 8 -Xlint:-options \
    -bootclasspath "$PLATFORM" -classpath "$PLATFORM:$BT/core-lambda-stubs.jar" \
    -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "== d8 =="
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.txt"
"$BT/d8" --release --min-api 26 --lib "$PLATFORM" \
    --output "$BUILD/dex" $(cat "$BUILD/classes.txt")

echo "== package =="
python3 - "$BUILD/unsigned.apk" "$BUILD/dex/classes.dex" <<'PY'
import shutil, sys, zipfile

apk, dex = sys.argv[1], sys.argv[2]
shutil.copyfile(apk, apk + ".tmp")

# Rebuild the archive so resources.arsc stays STORED (required for
# targetSdk 30+ installs) and classes.dex is added.
with zipfile.ZipFile(apk + ".tmp") as src, zipfile.ZipFile(apk, "w") as dst:
    for info in src.infolist():
        data = src.read(info.filename)
        stored = info.filename == "resources.arsc"
        dst.writestr(info.filename, data,
                     compress_type=zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED)
    dst.write(dex, "classes.dex", compress_type=zipfile.ZIP_DEFLATED)
PY

echo "== zipalign =="
"$BT/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

KEYSTORE="${KEYSTORE:-$HOME/tools/compress-keystore.jks}"
if [ ! -f "$KEYSTORE" ]; then
    echo "== creating signing key =="
    mkdir -p "$(dirname "$KEYSTORE")"
    "$JDK/bin/keytool" -genkeypair -v -keystore "$KEYSTORE" \
        -storepass android -keypass android -alias compress \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Offline Compress, OU=Apps, O=Offline, L=NA, ST=NA, C=US" >/dev/null 2>&1
fi

echo "== sign =="
"$BT/apksigner" sign \
    --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version 26 \
    --out "$OUT/Compress-1.8.apk" "$BUILD/aligned.apk"

"$BT/apksigner" verify --print-certs "$OUT/Compress-1.8.apk" | head -4
echo "== done =="
ls -lh "$OUT/Compress-1.8.apk"
