#!/usr/bin/env bash
# Builds the offline Android APKs with the raw SDK tools (no Gradle).
#
#   ./build.sh            # the ADD app
#   ./build.sh editor     # the ADD research build with the browser editor
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
    local kind="${6:-release}"
    local app="$ROOT/$dir" build="$ROOT/build/$name"
    local assets=()
    local version_args=()
    if [ "$kind" = "research" ]; then
        # A research build has its own version; check_apk still checks everything else.
        version_args=(--expect-version "$version" --expect-code "$code")
    fi

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
    echo "== $name: APK manifest and signature =="
    python3 "$ROOT/tools/check_apk.py" \
        --apk "$OUT/$apk" \
        --catalog "$ROOT/tools/catalog.json" \
        ${version_args[@]+"${version_args[@]}"} \
        --aapt2 "$BT/aapt2" \
        --apksigner "$BT/apksigner"
    # Every lane answers for its own bundle here: research must ship the editor
    # page and its assets, release must ship none.
    echo "== $name: editor bundle =="
    python3 "$ROOT/tools/check_editor_bundles.py" --expect "$kind" --apk "$OUT/$apk"
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
    echo "== edition: sync app and engine from the site =="
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
    #
    # docs/classic/ is a snapshot of the whole 3.8 tree, so it carries the same three
    # directories a second time: 6.7 MB of captures, artboards and packs inside an
    # archive whose own pages reference the galleries only through their page
    # registry. The top-level excludes are anchored at ./ and miss them, so they are
    # named here too; the archive's galleries are the same destinations, left to the
    # site by the same fallback.
    # editor/ is the browser canvas editor. The release build leaves it to the site,
    # like the galleries; the research build stages it below, where the app paints its
    # projects with Skia.
    tar -C "$ROOT/docs" -cf - --exclude='ADD-*.apk' --exclude='vercel.json' \
        --exclude='sitemap.xml' --exclude='robots.txt' \
        --exclude='./renders' --exclude='./figma' --exclude='./packs' \
        --exclude='./editor' \
        --exclude='./classic/renders' --exclude='./classic/figma' \
        --exclude='./classic/packs' --exclude='./classic/editor' . | tar -C "$www" -xf -
    python3 "$ROOT/tools/check_bundle.py" "$www"
    # Guard the trim: a future staging edit that quietly brings 3.2 MB back fails here.
    for absent in renders figma packs editor classic/renders classic/figma classic/packs classic/editor; do
        if [ -e "$www/$absent" ]; then
            echo "stage_shelf_assets: $absent is staged; the galleries and the packs stay on the site" >&2
            exit 1
        fi
    done
    du -sh "$www"
}

# The browser editor, built here so the asset tree and the APK cannot drift. Its dist is
# gitignored; the research build is the only case that stages it, and the shipped app
# keeps its native editor.
stage_editor() {
    local dist="$ROOT/editor/dist"
    local www="$ROOT/app/assets/www/editor"
    if ! command -v npm >/dev/null 2>&1; then
        echo "npm is needed to build the editor; install Node and run ./build.sh editor" >&2
        exit 1
    fi
    echo "== ADD: building the editor =="
    ( cd "$ROOT/editor" && npm run build )
    if [ ! -f "$dist/index.html" ] || [ ! -d "$dist/assets" ]; then
        echo "editor build left no dist/index.html or dist/assets" >&2
        exit 1
    fi
    rm -rf "$www"
    mkdir -p "$www"
    tar -C "$dist" -cf - . | tar -C "$www" -xf -
    du -sh "$www"
}

# One app, one case. The old `compress` and `icons` cases are gone with the merge:
# ADD ships the shelf's web tools and the native compressor in one package.
case "${1:-add}" in
    add)
        check_palette
        stage_shelf_assets
        build_app app ADD ADD-3.9.apk 41 3.9
        # The newest build, not a hard-coded one: a stale path silently proved a copy
        # the release had already replaced.
        check_fallback "$(ls -t "$OUT"/ADD-*.apk | head -1)"
        ;;
    editor)
        # A research build beside 3.9: same package, so it installs over the old one.
        # Code 58 clears the engine-lane review builds (3.9.1 preview 56, BMO 57),
        # so it also installs over either of those without a downgrade refusal.
        check_palette
        stage_shelf_assets
        stage_editor
        build_app app ADD ADD-4.0-editor.apk 58 4.0-editor research
        check_fallback "$(ls -t "$OUT"/ADD-*.apk | head -1)"
        ;;
    stage)
        # Stage only: no JDK or SDK needed, which is how the bundle harness runs.
        check_palette
        stage_shelf_assets
        ;;
    *)
        echo "usage: $0 [add|editor|stage]" >&2
        exit 2
        ;;
esac
