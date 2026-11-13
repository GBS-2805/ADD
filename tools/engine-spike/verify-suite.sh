#!/usr/bin/env bash
# Run every engine unit test on the JVM.
#
#   bash tools/engine-spike/verify-suite.sh
#
# The chrome goldens have their own guard, verify-chrome.sh: Roborazzi only compares in
# a verify-mode run of one test class. This is the rest of the suite -- document,
# authoring, inspector and layer logic -- so a regression there fails a push instead of
# only a screenshot. Needs a JDK 17 and the android-35 platform, like the golden guard.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JDK="${JAVA_HOME:-$(cat "$HOME/tools/JDK_PATH" 2>/dev/null || true)}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
RESULTS="$ROOT/app/build/test-results/testDebugUnitTest"

if [ -z "${JDK:-}" ] || [ ! -x "$JDK/bin/java" ]; then
    echo "JAVA_HOME is not set; install a JDK 17." >&2
    exit 1
fi
if [ ! -f "$SDK/platforms/android-35/android.jar" ]; then
    echo "Android platform 35 not found at $SDK." >&2
    exit 1
fi

cd "$ROOT"
printf 'sdk.dir=%s\n' "$SDK" > local.properties

export JAVA_HOME="$JDK"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

# Deleting the reports makes the task out of date, so the suite always runs rather than
# reporting totals left behind by an earlier run.
rm -rf "$RESULTS"
status=0
./gradlew --no-daemon :app:testDebugUnitTest || status=$?

python3 - "$RESULTS" "$status" <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET

tests = failures = errors = skipped = 0
failed = []
for path in glob.glob(sys.argv[1] + "/*.xml"):
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0))
    failures += int(root.get("failures", 0))
    errors += int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
    for case in root.iter("testcase"):
        for child in case:
            if child.tag not in ("failure", "error"):
                continue
            message = (child.get("message") or child.text or child.tag).strip()
            failed.append((case.get("classname", "?"), case.get("name", "?"),
                           message.splitlines()[0]))
if tests == 0:
    print("engine: no tests ran")
    raise SystemExit(1)
for classname, name, message in failed:
    print(f"engine: FAIL {classname}.{name}: {message}")
if int(sys.argv[2]) or failures or errors:
    print(f"engine: {tests} tests, {failures} failures, {errors} errors")
    raise SystemExit(1)
print(f"engine: {tests} tests, 0 failures, 0 errors, {skipped} skipped")
PY
