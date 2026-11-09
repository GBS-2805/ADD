#!/usr/bin/env bash
# Boots the review APK on an emulator and captures REAL frames of the native editor and of
# the engine screen.
#
# Why this file exists: the phone is the only real renderer, and this VM has no usable
# KVM (/dev/kvm is root:kvm, this account is not in that group, sudo is blocked), so the
# emulator runs under TCG. Under TCG the *headless* emulator never powers the display:
# `dumpsys display` reports `state UNKNOWN`, system_server stays ANR'd and every screencap
# is a flat 2,195-byte black frame. The fix is not a GPU mode or more cores -- it is
# giving the guest a window. This host has no X server, so the emulator is driven through
# Qt's bundled `offscreen` platform plugin, which needs a handful of system libraries
# this VM lacks; they are unpacked rootlessly into /tmp/rootfs (the same dpkg -x recipe
# the repo already uses for Chromium).
#
# With a window the guest reports `state ON` and `screencap` returns real composited
# frames. Two guest-side details are also required: `adb root` (the activities are not
# exported, so `am start` from the shell uid is refused) and `hide_error_dialogs`
# (otherwise a "System UI isn't responding" dialog is composited over the frame).
#
# The two screens need opposite capture strategies:
#
#   editor  -- stays up forever, so a settle-then-shoot capture works once the frame
#              carries more than 1,500 colours.
#
#   engine  -- renders and then aborts about a second later. Filament's "fxaa" fragment
#              shader fails to compile on this GLES implementation ("0xff000000u:
#              Integer overflow", then "OpenGL program fxaa failed to link or compile"),
#              a PostconditionPanic follows and the process dies with SIGABRT in
#              FEngine::loop; ActivityManager force-finishes the activity and the launcher
#              returns. Waiting even a couple of seconds photographs the launcher, which is
#              what made this look uncapturable. The harness therefore starts the engine and
#              shoots a burst immediately, keeping the first frame that is not the launcher.
#
#   REVIEW_X86=1 bash tools/emulate.sh        # build the x86_64 variant, then capture
#   bash tools/emulate.sh                     # capture the editor with the existing APK
#   bash tools/emulate.sh app.offline.compress/app.add.engine.MainActivity
#
# Output: artifacts/emu/editor.png or artifacts/emu/engine.png, 1080x2340.
#
# The handoff artifact stays arm64-only: REVIEW_X86=1 is what adds x86_64, and this
# script builds into artifacts/emu/ instead of touching artifacts/engine-review/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export JAVA_HOME="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
export PATH="$JAVA_HOME/bin:$SDK/platform-tools:$PATH"

AVD="${AVD:-add34full}"
IMAGE="system-images;android-34;default;x86_64"
OUT="$ROOT/artifacts/emu"
APK="$OUT/ADD-3.7-engine-review-x86_64.apk"
ACTIVITY="${1:-app.offline.compress/add.EditorActivity}"
ROOTFS="${ROOTFS:-/tmp/rootfs}"
export LD_LIBRARY_PATH="$ROOTFS/usr/lib/x86_64-linux-gnu:$ROOTFS/usr/lib/x86_64-linux-gnu/pulseaudio:$ROOTFS/lib/x86_64-linux-gnu"
mkdir -p "$OUT"

case "$ACTIVITY" in
    *.EditorActivity)     NAME="editor" ;;
    *engine.MainActivity) NAME="engine" ;;
    *) NAME="$(basename "${ACTIVITY##*.}" | tr '[:upper:]' '[:lower:]')" ;;
esac

# Every run must say what it proved, even when it dies before the launch: an install that
# fails on a half-booted guest used to leave no verdict at all.
SMOKE_INSTALL=no
SMOKE_LAUNCH=no
# A capture that was already on disk is not evidence of this run: engine.png was restored
# from an earlier frame, so existence alone made a run that never drew report painted=yes.
RUN_START=$(date +%s)
smoke_report() {
    local painted="no" colours=""
    if [ -f "$OUT/$NAME.png" ] && [ "$(stat -c %Y "$OUT/$NAME.png" 2>/dev/null || echo 0)" -ge "$RUN_START" ]; then
        painted="yes"
        colours=" colours=$(colours "$OUT/$NAME.png")"
    fi
    echo "SMOKE: $NAME installed=$SMOKE_INSTALL launched=$SMOKE_LAUNCH painted=$painted$colours"
}
trap smoke_report EXIT

colours() {
    python3 "$OUT/png-stats.py" "$1" 2>/dev/null | sed -n 's/.*colours=\([0-9]*\).*/\1/p' || true
}

top_colour() {
    python3 "$OUT/png-stats.py" "$1" 2>/dev/null | sed -n "s/.*top=\[('#\([0-9a-f]*\)'.*/\1/p" || true
}

# One frame, fast. `adb emu screenrecord screenshot` asks the emulator's own renderer for
# the frame it is presenting: 95-250 ms here, against 1.2 s for `screencap` and, on several
# boots, a `screencap` that never returns at all. The engine lives for about a second, so
# that difference is the capture -- a 1.2 s readback steps straight over the window.
# `screencap` stays as the fallback for a guest whose console will not answer.
#
# A PNG starts with these eight bytes. A readback that was cut short, or a command that
# wrote an error page, must never be renamed into the evidence path.
png_ok() {
    [ -s "$1" ] && [ "$(od -An -tx1 -N8 "$1" 2>/dev/null | tr -d ' \n')" = "89504e470d0a1a0a" ]
}

GRAB_CONSOLE_TIMEOUT="${GRAB_CONSOLE_TIMEOUT:-12}"
GRAB_SCREENCAP_TIMEOUT="${GRAB_SCREENCAP_TIMEOUT:-25}"

# The emulator console's screenshot command takes a *directory*, not a filename: a
# staging name like `shot.png.tmp` was read as a directory, left that directory behind
# and made the capture wait on a path that would never be a PNG. Stage
# inside a private directory whose name has no `.png` component, accept only a file that
# starts with the PNG signature, and rename it into place atomically. The guest-side
# screencap fallback writes to a plain file in the same directory and goes through the
# same check. Both calls are timeout-bounded with a kill-after, so a wedged adb cannot
# hang the run; a failure prints the console output and returns nonzero.
grab() {
    local file="$1" dir stage log shot rc
    dir="$(dirname "$file")"
    mkdir -p "$dir" || return 1
    stage="$(mktemp -d "$dir/.grab-XXXXXXXX")" || {
        echo "grab: cannot create a staging directory in $dir" >&2
        return 1
    }
    log="$stage/console.log"
    if timeout -k 2 "$GRAB_CONSOLE_TIMEOUT" adb emu screenrecord screenshot "$stage" \
            >"$log" 2>&1; then
        shot="$(find "$stage" -maxdepth 1 -type f -name '*.png' -print 2>/dev/null | head -1)"
        if [ -n "$shot" ] && png_ok "$shot"; then
            mv -f "$shot" "$file" || { rc=$?; rm -rf "$stage"; return "$rc"; }
            rm -rf "$stage"
            return 0
        fi
        echo "grab: console returned success but wrote no PNG for $file" >&2
    else
        rc=$?
        echo "grab: console screenshot failed (status $rc) for $file" >&2
    fi
    shot="$stage/shot"
    if timeout -k 2 "$GRAB_SCREENCAP_TIMEOUT" adb exec-out screencap -p \
            >"$shot" 2>>"$log"; then
        if png_ok "$shot"; then
            mv -f "$shot" "$file" || { rc=$?; rm -rf "$stage"; return "$rc"; }
            rm -rf "$stage"
            return 0
        fi
        echo "grab: screencap returned success but wrote no PNG for $file" >&2
    else
        rc=$?
        echo "grab: screencap failed (status $rc) for $file" >&2
    fi
    if [ -s "$log" ]; then
        echo "grab: console output:" >&2
        sed 's/^/  /' "$log" >&2
    fi
    rm -rf "$stage"
    return 1
}

# The engine's own colours: its page #0c1210 and its cream artboard #f3ebdd. A frame that
# carries either is the engine -- the launcher, the system dialogs and the app's splash
# carry neither. Brightness alone was the wrong test: the artboard can cover most of the
# frame, so a cream-dominant frame is a perfectly good engine frame, not a white flash.
engine_frame() {
    python3 "$OUT/png-stats.py" "$1" 2>/dev/null | grep -qE "top=\[.*'#(0c1210|f3ebdd)'"
}

# One frame, one decode: colours, dominant colour and the engine hint together. Calling
# png-stats.py three times per frame (as the burst first did) decodes the same 1080x2340
# image three times in pure Python and costs about a second, and that CPU is not free:
# the guest runs under TCG on this host, so the sampler was slow enough to step over the
# engine's one- or two-second painted window and to slow the guest down while doing it.
# PIL decodes in C; all three answers come back in well under a frame interval.
# Which frames in a batch could be the engine at all: one process, one load each, and a
# byte-level search for the two marker colours instead of a colour count. Prints the
# candidates so only they pay for the real decode.
scan_batch() {
    python3 - "$@" <<'PY' 2>/dev/null
import sys
from PIL import Image

last = None
for path in sys.argv[1:]:
    try:
        raw = Image.open(path).convert('RGB').tobytes()
    except Exception:
        continue
    last = raw
    if b'\x0c\x12\x10' in raw or b'\xf3\xeb\xdd' in raw:
        print(path)
# After the engine aborts its window stays a flat white sheet. Saying so is what lets a
# launch that never drew end in seconds instead of burning the whole 55 s window.
if last is not None and last.count(b'\xff\xff\xff') > 500000:
    print('!FLAT')
PY
}

frame_stat() {
    python3 - "$1" <<'PY' 2>/dev/null
import sys
from PIL import Image

try:
    im = Image.open(sys.argv[1]).convert('RGB')
except Exception:
    print('')
    raise SystemExit
# Reject the cheap way first. Counting every colour costs ~0.4 s, and the engine's painted
# frame can last well under a second (a run sampled the launcher at +9 s and the white
# post-abort window at +9 s), so a sampler that decodes every frame steps straight over it.
# A byte-level search for the two marker colours takes milliseconds, so only candidate
# frames pay for the full count and the burst keeps sampling at the grab rate.
raw = im.tobytes()
if b'\x0c\x12\x10' not in raw and b'\xf3\xeb\xdd' not in raw:
    print('0 - no')
    raise SystemExit
counts = im.getcolors(1 << 24)
if not counts:
    print('')
    raise SystemExit
counts.sort(reverse=True)
top = ['%02x%02x%02x' % c for _, c in counts[:4]]
hit = 'yes' if ('0c1210' in top or 'f3ebdd' in top) else 'no'
print(len(counts), top[0], hit)
PY
}

# A booted guest is not a usable one. Several boots reported `sys.boot_completed = 1`
# while `cmd package` and `settings` were both missing: one had lost `system_server`
# entirely (`cmd: Can't find service: package`, not one package listed) and another had
# no `settings`, after which `adb install` never returned. Ask the services themselves,
# and treat an empty package list as not ready -- `cmd package` exits 0 while printing
# nothing, so its status proves nothing on its own.
READY_WHY=""
guest_ready() {
    local settings pkgs
    # Settings first because it is the cheap probe, and with a generous timeout: under
    # TCG a busy guest answers in tens of seconds, and a 10 s cap turned into a loop of
    # timeouts that burned the whole run without ever reaching a launch.
    settings="$(timeout 30 adb shell settings get global hide_error_dialogs 2>&1 | tr -d '\r')"
    case "$settings" in
        *"Can't find service"*|"")
            READY_WHY="settings unanswered"
            return 1
            ;;
    esac
    pkgs="$(timeout 60 adb shell cmd package list packages 2>/dev/null | tr -d '\r')"
    case "$pkgs" in
        *package:*)
            READY_WHY=""
            return 0
            ;;
        *)
            READY_WHY="package list empty"
            return 1
            ;;
    esac
}

wait_for_guest_ready() {
    local budget="$1" start now
    start=$(date +%s)
    while :; do
        if guest_ready; then
            echo "  guest ready after ~$(( $(date +%s) - start ))s: package and settings both answer"
            return 0
        fi
        now=$(date +%s)
        echo "  not ready at $(( now - start ))s: $READY_WHY"
        [ $(( now - start )) -lt "$budget" ] || return 1
        sleep 5
    done
}

# sha256 of the base.apk the guest has installed, empty when there is none. Used to skip
# a reinstall that would throw away ahead-of-time compilation and push the first engine
# frame out by ~16 s for no gain.
installed_sha() {
    local path
    path="$(timeout 15 adb shell pm path app.offline.compress 2>/dev/null | tr -d '\r' \
            | sed -n 's/^package://p' | head -1)"
    [ -n "$path" ] || return 1
    timeout 20 adb shell sha256sum "$path" 2>/dev/null | tr -d '\r' | awk '{print $1}'
}

# Poll until the frame carries more than 1,500 distinct colours.
wait_for_frames() {
    local file="$1" tries="$2" c stage
    # grab stages and renames on its own, but the final name is evidence only once the
    # frame carries real colour. Capture to a plain pending name (no `.png` component,
    # so the console can never mistake it for a directory), then promote that file.
    stage="$(dirname "$file")/.grab-pending-$$"
    rm -f "$stage"
    for i in $(seq 1 "$tries"); do
        if grab "$stage"; then
            c="$(colours "$stage")"
            if [ -n "$c" ] && [ "$c" -gt 1500 ]; then
                mv -f "$stage" "$file"
                echo "  ${file##*/}: $c colours after $((i * 2))s"
                return 0
            fi
        fi
        rm -f "$stage"
        sleep 2
    done
    rm -f "$stage"
    echo "  ${file##*/}: still flat after $((tries * 2))s"
    return 1
}

# 1. The full AOSP image, not aosp_atd: ATD is the stripped automated-test build with no
#    SystemUI, and it never presents either.
if ! "$SDK/emulator/emulator" -list-avds | grep -qx "$AVD"; then
    echo "== creating AVD $AVD (android-34 default x86_64) =="
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" \
        -k "$IMAGE" --device pixel_5 --force >/dev/null
fi

# 2. The windowed binary links against libraries this VM does not ship. Resolve the whole
#    dependency closure of the emulator's own objects and unpack those packages rootlessly;
#    a short fixed list is not enough -- the chain runs through NSS, X session and codec
#    libraries that Qt loads before the offscreen plugin is even selected.
if [ ! -e "$ROOTFS/usr/lib/x86_64-linux-gnu/libnss3.so" ] || [ ! -e "$ROOTFS/usr/lib/x86_64-linux-gnu/libxkbfile.so.1" ]; then
    echo "== unpacking the windowed emulator's system libraries into $ROOTFS =="
    mkdir -p "$ROOTFS" /tmp/debs
    python3 - "$SDK/emulator" "$ROOTFS" <<'PY'
import gzip, os, re, subprocess, sys, urllib.request

EMU, ROOT = sys.argv[1], sys.argv[2]
LIBDIRS = [ROOT + '/usr/lib/x86_64-linux-gnu', ROOT + '/lib/x86_64-linux-gnu',
           ROOT + '/usr/lib/x86_64-linux-gnu/pulseaudio']
os.makedirs('/tmp/debs', exist_ok=True)

def index(release):
    out = {}
    for comp in ('main', 'universe'):
        url = f'http://archive.ubuntu.com/ubuntu/dists/{release}/{comp}/binary-amd64/Packages.gz'
        try:
            text = gzip.decompress(urllib.request.urlopen(url, timeout=120).read()).decode('utf-8', 'replace')
        except Exception as e:
            print('  index unavailable', release, comp, e)
            continue
        for st in text.split('\n\n'):
            n = re.search(r'^Package: (.+)$', st, re.M)
            f = re.search(r'^Filename: (.+)$', st, re.M)
            if n and f and n.group(1) not in out:
                out[n.group(1)] = f.group(1)
    return out

noble = index('noble')
jammy = None
bundle = {f for _, _, fs in os.walk(EMU) for f in fs if '.so' in f}

def targets():
    out = [EMU + '/qemu/linux-x86_64/qemu-system-x86_64']
    for dp, _, fs in os.walk(EMU + '/lib64'):
        out += [os.path.join(dp, f) for f in fs if f.endswith('.so') or '.so.' in f]
    return out

def missing():
    env = dict(os.environ, LD_LIBRARY_PATH=':'.join(LIBDIRS))
    miss = set()
    for t in targets():
        try:
            text = subprocess.run(['ldd', t], capture_output=True, text=True,
                                  env=env, timeout=25).stdout
        except Exception:
            continue
        for line in text.splitlines():
            m = re.search(r'(\S+) => not found', line)
            if m and m.group(1) not in bundle:
                miss.add(m.group(1))
    return miss

def package_for(soname):
    global jammy
    base = soname.split('.so')[0]
    cands = [base + str(d) for d in range(0, 4)] + [base, base + '-0', base + '1t64',
                                                    base + '2', base + '3']
    for c in cands:
        if c in noble:
            return c, noble[c]
    if jammy is None:
        jammy = index('jammy')
    for c in cands:
        if c in jammy:
            return c, jammy[c]
    return None, None

for rnd in range(5):
    miss = {m for m in missing()
            if not os.path.exists(os.path.join(ROOT, 'usr/lib/x86_64-linux-gnu', m))}
    if not miss:
        print('  closure complete')
        break
    print(f'  round {rnd + 1}: {len(miss)} missing')
    for soname in sorted(miss):
        pkg, path = package_for(soname)
        if not pkg:
            print('   unresolved', soname)
            continue
        deb = f'/tmp/debs/{pkg}.deb'
        if not os.path.exists(deb):
            try:
                urllib.request.urlretrieve('http://archive.ubuntu.com/ubuntu/' + path, deb)
            except Exception as e:
                print('   fetch failed', pkg, e)
                continue
        subprocess.run(['dpkg', '-x', deb, ROOT], check=False)
PY
fi

# 3. Boot with a window. `-no-window` picks the headless binary, whose display never
#    comes up; QT_QPA_PLATFORM=offscreen is the window this VM can host.
if ! adb get-state >/dev/null 2>&1; then
    echo "== booting $AVD windowed (TCG, 3-6 min) =="
    QT_QPA_PLATFORM=offscreen setsid nohup "$SDK/emulator/emulator" -avd "$AVD" \
        -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -no-metrics \
        -accel off -memory 2048 -cores 2 -qt-hide-window \
        -camera-back none -camera-front none > "$OUT/emulator.log" 2>&1 &
fi

# This guest never sets `sys.boot_completed`, even with the display up and every service
# answering, so waiting on that flag burned ten silent minutes and then failed a perfectly
# usable guest. Ask the services instead -- the same capability gate used below.
if ! wait_for_guest_ready "${GUEST_READY_BUDGET:-300}"; then
    echo "the guest never became usable; see $OUT/emulator.log" >&2
    exit 1
fi

# 4. Make the guest behave: root (the activities are not exported), no ANR dialogs over
#    the frame, screen on, animations off.
adb root >/dev/null 2>&1 || true
sleep 3
timeout 60 adb wait-for-device || true
# `adb root` restarts adbd, and the emulator console -- which serves the only readback
# fast enough for the engine's one-second window -- drops with it unless the client
# reconnects. Without this the whole run silently falls back to a 1.2 s screencap.
adb reconnect >/dev/null 2>&1 || true
timeout 30 adb wait-for-device || true
# Best-effort guest configuration. A guest that has booted part-way can still be
# missing the `settings` service, and with `set -e` one failed put kills the run
# before the gate even runs -- which is what "cmd: Can't find service: settings"
# followed by an early exit looked like. The gate below is what proves the guest usable.
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell input keyevent 224 >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
echo "  display: $(adb shell dumpsys display 2>/dev/null | grep -oE 'state [A-Z]+' | head -1)"

# 5. Prove the compositor before blaming the app: the launcher must produce a real frame.
#    The engine burst needs this too -- launched before the display composites, every
#    burst frame comes back flat black and the ~1 s engine window is lost.
echo "== waiting for the launcher to composite =="
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || true
wait_for_frames "$OUT/win-home.png" 8 || echo "  (launcher never composited; continuing)"

# 5b. The launch is not attempted until the guest can actually serve it: an install
#     against a guest whose package service is missing hangs until the timeout, and the
#     whole run is lost before the burst ever starts.
echo "== waiting for package and settings to answer =="
wait_for_guest_ready 180 || {
    echo "the guest reports booted but its package/settings services never answered" >&2
    exit 1
}

# 6. Build only on request, so the arm64 handoff artifact is never rebuilt or re-signed.
if [ "${REVIEW_X86:-0}" = "1" ] || [ ! -f "$APK" ]; then
    if [ "${REVIEW_X86:-0}" != "1" ]; then
        echo "no x86_64 APK at $APK" >&2
        echo "rebuild it with: REVIEW_X86=1 bash tools/emulate.sh" >&2
        exit 1
    fi
    echo "== building the x86_64 variant (opt-in) =="
    REVIEW_X86=1 "$ROOT/tools/engine-spike/gradlew" --no-daemon -q \
        -p "$ROOT/tools/engine-review" :app:assembleDebug
    cp "$ROOT/tools/engine-review/app/build/outputs/apk/debug/app-debug.apk" "$APK"
fi

echo "== install + launch $ACTIVITY =="
# Every call below is bounded: a guest that has stopped answering leaves `adb shell`
# hanging for minutes, which used to eat the whole run before it reached the launch.
apk_sha="$(sha256sum "$APK" | awk '{print $1}')"
if [ "$(installed_sha)" = "$apk_sha" ]; then
    echo "  same build already installed ($apk_sha); reinstall skipped"
    SMOKE_INSTALL=yes
else
    timeout 120 adb install -r -t --no-streaming "$APK" | tail -1
    SMOKE_INSTALL=yes
fi
timeout 15 adb logcat -c 2>/dev/null || true
timeout 20 adb shell am force-stop app.offline.compress 2>/dev/null || true

if [ "$NAME" = "engine" ]; then
    # The engine renders, then aborts within about a second (Filament's "fxaa" shader
    # fails to compile on this GLES implementation and the process panics). Settle-then-
    # shoot always catches the launcher instead, so shoot a burst from the moment of
    # launch and keep the frame whose dominant colour is not the launcher's.
    echo "== engine renders ~1 s then aborts; capturing a launch burst =="
    # These are this run's scratch frames, not evidence from a previous run.
    rm -f "$OUT"/.burst-*.png "$OUT/.baseline.png" "$OUT/$NAME.pending"
    # Say out loud how fast the readback is: the burst only catches the engine if it is
    # the console path, and a slow first grab means every frame will miss the window.
    t0=$(date +%s%N)
    grab "$OUT/.baseline.png" || true
    echo "  readback: $(( ($(date +%s%N) - t0) / 1000000 )) ms per frame"
    base="$(top_colour "$OUT/.baseline.png")"
    # Surface a refused launch instead of piping the error away: the activities are not
    # exported, so losing root turns this into a permission denial that must stop the run.
    start_out="$(adb shell am start -n "$ACTIVITY" 2>&1 | tr -d '\r')"
    printf '%s\n' "$start_out"
    case "$start_out" in
        *"Permission Denial"*|*"Error:"*|*"does not exist"*)
            echo "the engine activity did not start" >&2
            exit 1
            ;;
    esac
    SMOKE_LAUNCH=yes
    # A launch that draws and one that aborts before its first frame look the same from
    # outside, and on this guest roughly half of them never draw: the breadcrumb stops at
    # "document, typefaces and photos ready" with no "first frame drawn". One relaunch is
    # cheap insurance and only happens when the first launch produced nothing; the burst
    # itself is unchanged.
    attempts=0
    while :; do
        attempts=$((attempts + 1))
    best=0
    rich_at=0
    # Bound the burst by wall clock rather than a frame count. While the engine renders a
    # frame costs ~175 ms, but once it has aborted the guest's console starts timing out
    # at 12 s a grab, so a fixed count can outlast the whole run. The window is wide
    # because the install above is a reinstall: the first launch afterwards runs
    # ahead-of-time compilation, and its engine frame has arrived as late as ~30 s.
    burst_start=$(date +%s)
    deadline=$(( burst_start + 55 ))
    i=0
    flat_run=0
    while :; do
        now=$(date +%s)
        [ "$now" -lt "$deadline" ] || break
        # Stop once a rich engine frame is in hand: two more seconds of frames let the
        # richest variant arrive, and waiting out the window only slows a run that already
        # has its picture. The 2,000 floor is what keeps a slow launch safe -- a partial
        # paint (1,500+) does not end the burst, while the composited screen carries
        # 2,800-5,400 colours and does.
        if [ "$rich_at" -gt 0 ] && [ $(( now - rich_at )) -ge 2 ]; then
            echo "  burst ended early at frame $i after $(( now - burst_start ))s"
            break
        fi
        # Grab a short run of frames back to back and decode it in one pass afterwards.
        # Decoding between grabs paced the loop at ~0.4 s a frame, while the engine's
        # painted frame can last well under a second (one run saw the launcher and then the
        # white post-abort window a second apart), so the sampler stepped over it. Keeping
        # the grabs dense is what makes the window land on a frame; the batch decode only
        # has to answer afterwards.
        batch=""
        for _ in $(seq 1 6); do
            [ "$(date +%s)" -lt "$(( deadline + 10 ))" ] || break
            i=$((i + 1))
            grab "$OUT/.burst-$i.png" && batch="$batch $OUT/.burst-$i.png" || true
        done
        [ -n "$batch" ] || continue
        hits="$(scan_batch $batch)"
        if printf '%s\n' "$hits" | grep -q '^!FLAT'; then
            flat_run=$((flat_run + 1))
        else
            flat_run=0
        fi
        for hitfile in $(printf '%s\n' "$hits" | grep -v '^!FLAT'); do
            n="${hitfile##*.burst-}"
            read -r c t hit <<<"$(frame_stat "$hitfile")"
            # Accept a frame the engine actually painted, and take the richest one: on a
            # healthy boot the artboard fills the frame, on a slower one the first engine
            # frame is the darker variant, and both are the same screen.
            if [ -n "${c:-}" ] && [ -n "${t:-}" ] && [ "$c" -gt 1500 ] && [ "$t" != "$base" ] \
                && [ "${hit:-}" = "yes" ]; then
                echo "  frame ${n%.png}: $c colours, page #$t"
                if [ "$c" -ge 2000 ] && [ "$rich_at" -eq 0 ]; then rich_at=$(date +%s); fi
                if [ "$c" -gt "$best" ]; then
                    best="$c"
                    cp "$hitfile" "$OUT/$NAME.pending"
                fi
            fi
        done
        # A few seconds of flat white means this launch aborted before painting anything,
        # so relaunch instead of waiting out the window for a frame that is not coming.
        if [ "$flat_run" -ge 3 ] && [ $(( $(date +%s) - burst_start )) -ge 10 ]; then
            echo "  app aborted before drawing on launch $attempts"
            break
        fi
    done
    rm -f "$OUT"/.burst-*.png "$OUT/.baseline.png"
    if [ "$best" -eq 0 ] && [ "$attempts" -lt 6 ]; then
        echo "  no engine frame on launch $attempts; relaunching ($((attempts + 1))/6)"
        timeout 20 adb shell am force-stop app.offline.compress 2>/dev/null || true
        sleep 2
        start_out="$(adb shell am start -n "$ACTIVITY" 2>&1 | tr -d '\r')"
        printf '%s\n' "$start_out"
        continue
    fi
    if [ "$best" -gt 0 ]; then
        mv -f "$OUT/$NAME.pending" "$OUT/$NAME.png"
        echo "  $NAME.png: $best colours"
    else
        rm -f "$OUT/$NAME.pending"
        echo "the engine never produced a frame before it aborted" >&2
        echo "SMOKE: $NAME installed=yes launched=yes painted=no" >&2
        exit 1
    fi
    done
else
    adb shell am start -n "$ACTIVITY" | tail -2

    echo "== waiting for $NAME to composite =="
    wait_for_frames "$OUT/$NAME.png" 30 || {
        echo "the capture stayed flat; the guest window is up but nothing was drawn" >&2
        echo "SMOKE: $NAME installed=yes launched=yes painted=no" >&2
        exit 1
    }

    # The first frame past the bar can still be a splash or a half-painted hierarchy
    # (the app's splash passed 900 colours long before the editor drew). Let it settle and
    # keep whichever frame carries more colour.
    sleep 8
    grab "$OUT/$NAME.png.late" || true
    late="$(colours "$OUT/$NAME.png.late")"
    have="$(colours "$OUT/$NAME.png")"
    if [ -n "$late" ] && [ -n "$have" ] && [ "$late" -gt "$have" ]; then
        mv "$OUT/$NAME.png.late" "$OUT/$NAME.png"
    else
        rm -f "$OUT/$NAME.png.late"
    fi
fi

echo "== engine breadcrumb =="
timeout 20 adb shell run-as app.offline.compress cat files/engine-boot.txt 2>/dev/null || echo "  (no breadcrumb)"
echo
echo "Captured $OUT/$NAME.png (1080x2340)."
case "$NAME" in
    engine)
        echo "The engine renders for about a second and then aborts: Filament's fxaa shader"
        echo "fails to compile on this GLES implementation and FEngine::loop raises SIGABRT."
        ;;
    *)
        echo "Editor is a native View hierarchy and composites fully."
        ;;
esac
echo "Stop the emulator with: adb emu kill"
