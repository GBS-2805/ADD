#!/usr/bin/env bash
# Capture engine benchmark evidence only from a real attached device.
#
# The VM/Robolectric suite is a correctness gate, not a device frame-time result.
# With no adb device this script writes a blocked record and exits non-zero, which
# is the honest outcome the plan requires.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${BENCH_OUT:-$ROOT/../../artifacts/bmo/bench.json}"
ADB="${ADB:-$(command -v adb || true)}"
mkdir -p "$(dirname "$OUT")"

if [ -z "$ADB" ] || ! "$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
  cat > "$OUT" <<'JSON'
{
  "status": "blocked",
  "reason": "no adb device attached; a JVM/Robolectric run is not a device benchmark",
  "metrics": null
}
JSON
  echo "bench: BLOCKED - no adb device; wrote $OUT"
  exit 2
fi

echo "bench: a device is attached, but this repository has no device benchmark instrumentation."
echo "bench: add the instrumentation output to $OUT before recording a device number."
exit 2
