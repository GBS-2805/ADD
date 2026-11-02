#!/usr/bin/env bash
# Builds the offline Android APKs with the raw SDK tools (no Gradle).
#
#   ./build.sh            # both apps
#   ./build.sh compress   # Compress only
#   ./build.sh icons      # Icons only
#
# The Icons app is a WebView over the site's icon browser, so its assets are
# staged out of docs/ at build time: the pages, the two catalogs, the stylesheet
# and the Poppins woff2. There is no second copy of any glyph.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JDK="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"
OUT="$ROOT/dist"
KEYSTORE="${KEYSTORE:-$HOME/tools/compress-keystore.jks}"

if [ -z "${JDK:-}" ] || [ ! -x "$JDK/bin/javac" ]; then
    echo "JAVA_HOME is not set; install a JDK and export JAVA_HOME." >&2
    exit 1
fi
if [ ! -f "$PLATFORM" ]; then
    echo "Android platform 35 not found at $PLATFORM" >&2
    exit 1
fi

export PATH="$JDK/bin:$PATH"

package_apk() {
    python3 - "$1" "$2" <<'PY'
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
}

ensure_key() {
    if [ ! -f "$KEYSTORE" ]; then
        echo "== creating signing key =="
        mkdir -p "$(dirname "$KEYSTORE")"
        "$JDK/bin/keytool" -genkeypair -v -keystore "$KEYSTORE" \
            -storepass android -keypass android -alias compress \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=Offline Apps, OU=Apps, O=Offline, L=NA, ST=NA, C=US" >/dev/null 2>&1
    fi
}

build_app() {
    local dir="$1" name="$2" apk="$3" code="$4" version="$5"
    local app="$ROOT/$dir" build="$ROOT/build/$name"
    local assets=()

    rm -rf "$build"
    mkdir -p "$build/gen" "$build/classes" "$build/dex" "$OUT"
    if [ -d "$app/assets" ]; then
        assets=(-A "$app/assets")
    fi

    echo "== $name: aapt2 compile =="
    "$BT/aapt2" compile --dir "$app/res" -o "$build/res.zip"

    echo "== $name: aapt2 link =="
    "$BT/aapt2" link \
        -o "$build/unsigned.apk" \
        -I "$PLATFORM" \
        --manifest "$app/AndroidManifest.xml" \
        -R "$build/res.zip" \
        ${assets[@]+"${assets[@]}"} \
        --java "$build/gen" \
        --min-sdk-version 26 \
        --target-sdk-version 35 \
        --version-code "$code" \
        --version-name "$version" \
        --auto-add-overlay

    echo "== $name: javac =="
    find "$app/src" "$build/gen" -name '*.java' > "$build/sources.txt"
    "$JDK/bin/javac" -nowarn -source 8 -target 8 -Xlint:-options \
        -bootclasspath "$PLATFORM" -classpath "$PLATFORM:$BT/core-lambda-stubs.jar" \
        -d "$build/classes" @"$build/sources.txt"

    echo "== $name: d8 =="
    find "$build/classes" -name '*.class' > "$build/classes.txt"
    "$BT/d8" --release --min-api 26 --lib "$PLATFORM" \
        --output "$build/dex" $(cat "$build/classes.txt")

    echo "== $name: package =="
    package_apk "$build/unsigned.apk" "$build/dex/classes.dex"

    echo "== $name: zipalign =="
    "$BT/zipalign" -f 4 "$build/unsigned.apk" "$build/aligned.apk"

    ensure_key
    echo "== $name: sign =="
    "$BT/apksigner" sign \
        --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
        --min-sdk-version 26 \
        --out "$OUT/$apk" "$build/aligned.apk"

    "$BT/apksigner" verify --print-certs "$OUT/$apk" | head -4
    echo "== $name: done =="
    ls -lh "$OUT/$apk"
}

stage_icons_assets() {
    local www="$ROOT/icons-app/assets/www"
    echo "== Icons: staging the browser out of docs/ =="
    rm -rf "$ROOT/icons-app/assets"
    mkdir -p "$www/icons" "$www/assets/fonts" "$www/data"
    cp "$ROOT/docs/icons/index.html" "$www/icons/"
    cp -r "$ROOT/docs/icons/lineicons" "$www/icons/"
    cp -r "$ROOT/docs/icons/coolicons" "$www/icons/"
    cp "$ROOT/docs/assets/site.css" "$ROOT/docs/assets/site.js" \
       "$ROOT/docs/assets/favicon.svg" "$www/assets/"
    cp "$ROOT"/docs/assets/fonts/*.woff2 "$www/assets/fonts/"
    cp "$ROOT/docs/data/icons.json" "$ROOT/docs/data/coolicons.json" "$www/data/"
    # The site root is not bundled; a page that asks for "/" gets the browser.
    cat > "$www/index.html" <<'HTML'
<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=icons/">
<title>Icons</title>
HTML
    du -sh "$www"
}

case "${1:-all}" in
    compress)
        build_app app Compress Compress-1.9.apk 10 1.9
        ;;
    icons)
        stage_icons_assets
        build_app icons-app Icons Icons-1.3.apk 4 1.3
        ;;
    all)
        build_app app Compress Compress-1.9.apk 10 1.9
        stage_icons_assets
        build_app icons-app Icons Icons-1.3.apk 4 1.3
        ;;
    *)
        echo "usage: $0 [all|compress|icons]" >&2
        exit 2
        ;;
esac
