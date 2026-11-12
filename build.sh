#!/usr/bin/env bash
# Builds the offline Android APKs with the raw SDK tools (no Gradle).
#
#   ./build.sh            # the ADD app
#   ./build.sh stage      # stage the web bundle only
#
# ADD is one package: a native compressor plus a WebView over the site's web tools,
# so the icons and fonts pages are staged out of docs/ at build time -- the pages,
# their catalogs, the stylesheet and Poppins. There is no second copy of any glyph.
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
    # R and the class names live in `add`; the manifest package is the install id,
    # so the APK still updates an installed ADD in place.
    "$BT/aapt2" link \
        -o "$build/unsigned.apk" \
        -I "$PLATFORM" \
        --manifest "$app/AndroidManifest.xml" \
        --custom-package add \
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

check_fallback() {
    local apk="$1"
    echo "== Icons: the unbundled-page fallback =="
    # Built in Java, so it is not a file in assets/www. Render the shipped dex
    # strings when the toolchain is here; otherwise still refuse a stale copy.
    #
    # That toolchain sits outside node's own lookup path -- the module in /tmp/pwshot,
    # the browsers in /tmp/pw, their libs unrooted into /tmp/rootfs by
    # tools/render_env.py -- and nothing exports it for a bare ./build.sh. Without it
    # the check dies on "Cannot find module 'playwright'", which reads as a broken
    # package rather than an unexported path, so hand it the same environment the
    # capture run uses. A value already exported still wins.
    local env_args=(
        "NODE_PATH=${NODE_PATH:-/tmp/pwshot/node_modules}"
        "PLAYWRIGHT_BROWSERS_PATH=${PLAYWRIGHT_BROWSERS_PATH:-/tmp/pw}"
        "LD_LIBRARY_PATH=${LD_LIBRARY_PATH:-/tmp/rootfs/usr/lib/x86_64-linux-gnu:/tmp/rootfs/lib/x86_64-linux-gnu}"
        "FONTCONFIG_FILE=${FONTCONFIG_FILE:-/tmp/rootfs/fonts.conf}"
    )
    if env "${env_args[@]}" node -e 'require("playwright")' >/dev/null 2>&1; then
        env "${env_args[@]}" node "$ROOT/tools/webview-fallback-check.js" "$apk" "$ROOT/artifacts/fallback-offline.png"
    else
        echo "  Playwright is not installed; checking the shipped strings without a render."
        env "${env_args[@]}" node "$ROOT/tools/webview-fallback-check.js" --static "$apk"
    fi
}


# The palette, not the build: colours.xml against site.css, all six themes against
# the attributes, and every layout against the theme. No browser needed, which is why
# this half runs here; `tools/check_palette.py --browser <url>` adds the rendered one.
check_palette() {
    echo "== edition: sync app and Studio from the site =="
    python3 "$ROOT/tools/sync_edition.py"
    echo "== palette: site and app =="
    python3 "$ROOT/tools/check_palette.py"
}

# The unified app: the whole shelf staged out of docs/ into the one package that also
# carries the native compressor. Same rule as the Icons build -- the pages, their
# catalogs, the stylesheets and the faces are copied, never re-authored.
stage_shelf_assets() {
    local www="$ROOT/app/assets/www"
    echo "== ADD: staging the shelf out of docs/ =="
    rm -rf "$ROOT/app/assets"
    mkdir -p "$www"
    # Every page, catalog, stylesheet and face travels; the two galleries do not.
    # renders/ and figma/ are the captures and the artboards themselves -- 3.2 MB of
    # the 8.9 MB APK -- and a gallery without its images is only a grid of broken
    # cells. They are the one pair of destinations the app leaves to the site, through
    # the "That page lives on the site" fallback in ShelfActivity.
    tar -C "$ROOT/docs" -cf - --exclude='ADD-*.apk' --exclude='vercel.json' \
        --exclude='sitemap.xml' --exclude='robots.txt' \
        --exclude='./renders' --exclude='./figma' --exclude='./packs' . | tar -C "$www" -xf -
    python3 "$ROOT/tools/check_bundle.py" "$www"
    # Guard the trim: a future staging edit that quietly brings 3.2 MB back fails here.
    for absent in renders figma packs; do
        if [ -e "$www/$absent" ]; then
            echo "stage_shelf_assets: $absent is staged; the galleries and the packs stay on the site" >&2
            exit 1
        fi
    done
    du -sh "$www"
}

# The Studio engine shell lives beside the shelf rather than inside it: it is not a
# site page, so it is copied into the bundle after the shelf staging has finished.
stage_studio_assets() {
    local dst="$ROOT/app/assets/www/studio"
    echo "== ADD: staging the Studio engine shell =="
    rm -rf "$dst"
    mkdir -p "$dst"
    cp -R "$ROOT/app/studio-assets/." "$dst/"
    test -s "$dst/fabric.min.js"
    test -s "$dst/index.html"
}

# One app, one case. The old `compress` and `icons` cases are gone with the merge:
# ADD ships the shelf's web tools and the native compressor in one package.
case "${1:-add}" in
    add)
        check_palette
        stage_shelf_assets
        stage_studio_assets
        build_app app ADD ADD-3.9.apk 41 3.9
        # The newest build, not a hard-coded one: a stale path silently proved a copy
        # the release had already replaced.
        check_fallback "$(ls -t "$OUT"/ADD-*.apk | head -1)"
        ;;
    stage)
        # Stage only: no JDK or SDK needed, which is how the bundle harness runs.
        check_palette
        stage_shelf_assets
        stage_studio_assets
        ;;
    *)
        echo "usage: $0 [add|stage]" >&2
        exit 2
        ;;
esac
