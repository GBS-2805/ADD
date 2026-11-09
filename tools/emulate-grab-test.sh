#!/usr/bin/env bash
# Exercises tools/emulate.sh's grab() without an emulator.
#
# The console screenshot command takes a directory, so a `*.png.tmp` staging name used
# to be read as a directory and stall the capture. This test sources the real functions
# out of emulate.sh and drives them with a fake adb: a successful console frame, the
# screencap fallback, a double failure with its diagnostics, a partial readback, and a
# console call that hangs long enough to need the timeout. It also asserts that no
# destination handed to the console contains `.png` and that no staging directory is
# left behind.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EMULATE="$ROOT/tools/emulate.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

[ -f "$EMULATE" ] || { echo "not found: $EMULATE" >&2; exit 1; }

# Pull png_ok() and grab() out of the real script rather than keeping a second copy.
eval "$(sed -n '/^png_ok() {/,/^}/p; /^grab() {/,/^}/p' "$EMULATE")"
type png_ok >/dev/null
type grab >/dev/null

mkdir -p "$TMP/bin"
LOG="$TMP/adb.log"
: > "$LOG"
export FAKE_LOG="$LOG"
cat > "$TMP/bin/adb" <<'ADB'
#!/usr/bin/env bash
set -u
printf '%s\n' "$*" >> "$FAKE_LOG"
png() {
    printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' \
        | base64 -d > "$1"
}
case "${1:-}" in
    emu)
        dest="${!#}"
        case "${FAKE_CONSOLE:-fail}" in
            ok) mkdir -p "$dest"; png "$dest/Screenshot_fake.png"; exit 0 ;;
            empty) exit 0 ;;
            fail) echo "console: refused" >&2; exit 7 ;;
            hang) sleep 30; exit 0 ;;
            *) echo "unknown FAKE_CONSOLE=$FAKE_CONSOLE" >&2; exit 2 ;;
        esac
        ;;
    exec-out)
        case "${FAKE_SCREENCAP:-fail}" in
            ok) png /dev/stdout; exit 0 ;;
            partial) printf '\x89PNG\r\n'; exit 0 ;;
            fail) echo "screencap: dead" >&2; exit 9 ;;
            hang) sleep 30; exit 0 ;;
            *) echo "unknown FAKE_SCREENCAP=$FAKE_SCREENCAP" >&2; exit 2 ;;
        esac
        ;;
esac
exit 0
ADB
chmod +x "$TMP/bin/adb"
PATH="$TMP/bin:$PATH"

failures=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1" >&2; failures=$((failures + 1)); }

# No `.grab-*` staging directory may survive any path.
clean() {
    local dir="$1" leftovers
    leftovers="$(find "$dir" -maxdepth 1 -name '.grab-*' -print -quit 2>/dev/null || true)"
    [ -z "$leftovers" ] || fail "staging directory left behind: $leftovers"
}

run() {
    local name="$1" console="$2" screencap="$3" timeout="$4" dest="$5"
    : > "$LOG"
    set +e
    err="$(FAKE_CONSOLE="$console" FAKE_SCREENCAP="$screencap" \
        GRAB_CONSOLE_TIMEOUT="$timeout" GRAB_SCREENCAP_TIMEOUT=2 \
        grab "$dest" 2>&1)"
    rc=$?
    set -e
}

# 1. The console path writes into the staging directory and the result is renamed in.
out="$TMP/console"; mkdir -p "$out"; dest="$out/capture"
run "console" ok fail 2 "$dest"
[ "$rc" -eq 0 ] || fail "console success returned $rc: $err"
png_ok "$dest" || fail "console success did not produce a PNG at $dest"
console_path="$(grep 'screenrecord screenshot' "$LOG" | tail -1 | awk '{print $NF}')"
case "$console_path" in
    *.png*) fail "console destination contains .png: $console_path" ;;
esac
[ -d "$console_path" ] && fail "console destination was left as a directory: $console_path"
[ -f "$dest" ] || fail "console success left no file"
clean "$out"
[ "$rc" -eq 0 ] && png_ok "$dest" && pass "console frame is staged, checked and renamed"

# 2. A console that returns success but writes nothing falls back to screencap.
out="$TMP/fallback"; mkdir -p "$out"; dest="$out/capture"
run "fallback" empty ok 2 "$dest"
[ "$rc" -eq 0 ] || fail "screencap fallback returned $rc: $err"
png_ok "$dest" || fail "screencap fallback did not produce a PNG at $dest"
clean "$out"
[ "$rc" -eq 0 ] && png_ok "$dest" && pass "empty console falls back to screencap"

# 3. Both paths fail: nonzero, destination absent, and both diagnostics are printed.
out="$TMP/failure"; mkdir -p "$out"; dest="$out/capture"
run "failure" fail fail 2 "$dest"
[ "$rc" -ne 0 ] || fail "double failure returned 0"
[ ! -e "$dest" ] || fail "double failure left a destination"
printf '%s' "$err" | grep -q 'console: refused' || fail "console diagnostic missing: $err"
printf '%s' "$err" | grep -q 'screencap: dead' || fail "screencap diagnostic missing: $err"
clean "$out"
[ "$rc" -ne 0 ] && [ ! -e "$dest" ] && pass "double failure exits nonzero with both diagnostics"

# 4. A truncated readback is not a PNG and must not replace evidence.
out="$TMP/partial"; mkdir -p "$out"; dest="$out/capture"
run "partial" fail partial 2 "$dest"
[ "$rc" -ne 0 ] || fail "partial screencap returned 0"
[ ! -e "$dest" ] || fail "partial screencap left a destination"
clean "$out"
[ "$rc" -ne 0 ] && [ ! -e "$dest" ] && pass "partial readback is rejected"

# 5. A hung console is killed by the timeout and the fallback still lands.
out="$TMP/hang"; mkdir -p "$out"; dest="$out/capture"
start=$(date +%s)
run "hang" hang ok 1 "$dest"
elapsed=$(( $(date +%s) - start ))
[ "$rc" -eq 0 ] || fail "hung console did not fall back: $err"
[ "$elapsed" -lt 6 ] || fail "hung console took ${elapsed}s; the timeout did not bound it"
png_ok "$dest" || fail "hung console fallback produced no PNG"
clean "$out"
[ "$rc" -eq 0 ] && [ "$elapsed" -lt 6 ] && pass "hung console is bounded and falls back"

if [ "$failures" -ne 0 ]; then
    echo "GRAB SELFTEST: FAIL ($failures)" >&2
    exit 1
fi
echo "GRAB SELFTEST: PASS (5 cases)"
