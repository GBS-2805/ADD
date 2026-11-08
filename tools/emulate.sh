#!/usr/bin/env bash
# Runs the review APK on a headless emulator and reports what it actually did.
#
# Why this file exists: the phone is the only real renderer, and this VM has no usable
# KVM (/dev/kvm is root:kvm, this account is not in that group, sudo is blocked), so the
# emulator falls back to TCG. It does boot the ATD image and both activities do start --
# the native editor, and the engine with Filament on SwiftShader -- but system_server
# stays in a permanent ANR, so the compositor never presents and every screencap is a
# flat frame. Treat this as a behaviour harness (install, launch, logcat, and the review
# breadcrumb below), never as a renderer: a screenshot from here is black and proves
# nothing about layout or colour.
#
#   bash tools/emulate.sh                                  # the editor
#   bash tools/emulate.sh app.offline.compress/app.add.engine.MainActivity
#
# The artifact the handoff serves stays arm64-only: REVIEW_X86=1 adds x86_64 for this
# run, and this script builds into artifacts/emu/ instead of touching it.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export JAVA_HOME="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
export PATH="$JAVA_HOME/bin:$SDK/platform-tools:$PATH"

AVD="${AVD:-add34}"
IMAGE="system-images;android-34;aosp_atd;x86_64"
OUT="$ROOT/artifacts/emu"
APK="$OUT/ADD-3.7-engine-review-x86_64.apk"
ACTIVITY="${1:-app.offline.compress/.EditorActivity}"

mkdir -p "$OUT"
printf 'sdk.dir=%s\n' "$SDK" > "$ROOT/tools/engine-review/local.properties"

if ! "$SDK/emulator/emulator" -list-avds | grep -qx "$AVD"; then
    echo "== creating AVD $AVD =="
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" -k "$IMAGE" --force >/dev/null
fi

if ! adb get-state >/dev/null 2>&1; then
    echo "== booting $AVD under software emulation (3-5 min) =="
    setsid nohup "$SDK/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect -no-snapshot -no-metrics -accel off -memory 2048 -cores 2 \
        > "$OUT/emulator.log" 2>&1 &
fi

for _ in $(seq 1 72); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
        break
    fi
    sleep 5
done
if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; then
    echo "the emulator did not finish booting; see $OUT/emulator.log" >&2
    exit 1
fi

echo "== building the x86_64 variant =="
REVIEW_X86=1 "$ROOT/tools/engine-spike/gradlew" --no-daemon -q \
    -p "$ROOT/tools/engine-review" :app:assembleDebug
cp "$ROOT/tools/engine-review/app/build/outputs/apk/debug/app-debug.apk" "$APK"

echo "== install + launch $ACTIVITY =="
adb install -r -t --no-streaming "$APK" | tail -1
adb logcat -c 2>/dev/null || true
adb shell am start -W -n "$ACTIVITY" | tail -3
sleep 20

echo "== processes =="
adb shell pidof app.offline.compress 2>/dev/null | tr -d '\r' | sed 's/^/  main   /'
adb shell pidof app.offline.compress:engine 2>/dev/null | tr -d '\r' | sed 's/^/  engine /'
echo "== crash buffer =="
adb logcat -d -b crash 2>/dev/null | tail -20
echo "== engine breadcrumb =="
adb shell run-as app.offline.compress cat files/engine-boot.txt 2>/dev/null || echo "  (no breadcrumb yet)"
echo "== java trace =="
adb shell run-as app.offline.compress cat files/review-crash.txt 2>/dev/null || echo "  (none)"
echo
echo "Screens are flat frames here: system_server is ANR'd and never presents."
echo "Stop the emulator with: adb emu kill"
