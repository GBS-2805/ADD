#!/usr/bin/env python3
"""Smoke the real ADD app on a connected Android device.

The unit and Robolectric suites cannot prove that the shipped manifest starts, that
the Compose window survives its first frame, or that the device decoder accepts the
APK. This runner installs the APK, clears logcat, launches the launcher activity,
waits, and fails on a crash, ANR or a missing process.

    python3 tools/device_smoke.py --apk artifacts/add-release.apk
    python3 tools/device_smoke.py --serial R58M123456 --apk app-debug.apk --json
    python3 tools/device_smoke.py --skip-install

Exit status is 0 for a passing launch, 1 for a failed launch and 2 when no device
is available. Exit 2 is a blocked check, never a pass. An emulator is allowed and
reported as `physical: false`; use a phone for the release checklist.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

DEFAULT_PACKAGE = "app.offline.compress"
DEFAULT_ACTIVITY = "app.offline.compress/add.MainActivity"
CRASH_MARKERS = (
    "FATAL EXCEPTION",
    "ANR in ",
    "Process: ",
    "has died",
)


class AdbError(RuntimeError):
    pass


class Adb:
    """A tiny injectable adb wrapper, so the smoke flow can be tested without one."""

    def __init__(self, binary: str, serial: str = "", runner=None):
        self.binary = binary
        self.serial = serial
        self.runner = runner or subprocess.run

    def run(self, *args: str, timeout: int = 60, check: bool = True) -> str:
        command = [self.binary]
        if self.serial:
            command += ["-s", self.serial]
        command += list(args)
        try:
            result = self.runner(command, capture_output=True, text=True, timeout=timeout)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise AdbError("adb %s failed: %s" % (" ".join(args), error)) from error
        if check and result.returncode:
            detail = (result.stderr or result.stdout or "").strip()
            raise AdbError("adb %s failed (%d): %s"
                           % (" ".join(args), result.returncode, detail))
        return result.stdout or ""


def ready_devices(adb: Adb) -> list[str]:
    output = adb.run("devices")
    devices = []
    for line in output.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == "device":
            devices.append(fields[0])
    return devices


def device_name(adb: Adb) -> dict:
    model = adb.run("shell", "getprop", "ro.product.model").strip() or "unknown"
    qemu = adb.run("shell", "getprop", "ro.kernel.qemu").strip()
    sdk = adb.run("shell", "getprop", "ro.build.version.sdk").strip()
    return {
        "serial": adb.serial or "default",
        "model": model,
        "sdk": sdk,
        "physical": qemu != "1",
    }


def resolve_activity(adb: Adb, package: str, activity: str) -> str:
    if activity:
        return activity
    output = adb.run("shell", "cmd", "package", "resolve-activity", "--brief", package,
                     check=False)
    for line in reversed(output.splitlines()):
        line = line.strip()
        if "/" in line and not line.startswith("priority="):
            return line
    # The manifest's launcher component is stable across releases. Keep this
    # fallback explicit rather than silently launching another package.
    return DEFAULT_ACTIVITY


def crash_lines(logcat: str, package: str) -> list[str]:
    found = []
    for line in logcat.splitlines():
        if any(marker in line for marker in CRASH_MARKERS):
            # "Process: " and "ANR in " are only ours when the package appears.
            if ("Process: " in line or "ANR in " in line) and package not in line:
                continue
            found.append(line.strip()[:300])
    return found[:12]


def smoke(adb: Adb, *, apk: Path | None, package: str, activity: str,
          wait_seconds: int, skip_install: bool, sleep=time.sleep) -> dict:
    try:
        devices = ready_devices(adb)
    except AdbError as error:
        return {
            "status": "blocked",
            "reason": "adb is not available: %s" % error,
            "commands": ["adb devices"],
        }
    if not devices:
        return {
            "status": "blocked",
            "reason": "no adb device in the `device` state; connect a phone and enable USB debugging",
            "commands": ["adb devices"],
        }
    if not adb.serial:
        if len(devices) > 1:
            return {
                "status": "blocked",
                "reason": "more than one device is connected; pass --serial",
                "devices": devices,
            }
        adb.serial = devices[0]

    result: dict = {
        "status": "failed",
        "device": device_name(adb),
        "package": package,
        "apk": str(apk) if apk else None,
        "installed": False,
        "launched": False,
    }

    try:
        if apk and not skip_install:
            result["install"] = adb.run("install", "-r", str(apk), timeout=300).strip()[-500:]
            result["installed"] = True
        elif skip_install:
            result["install"] = "skipped"
        else:
            result["install"] = "no APK supplied; testing the installed build"

        adb.run("logcat", "-c")
        adb.run("shell", "am", "force-stop", package)
        resolved = resolve_activity(adb, package, activity)
        result["activity"] = resolved
        result["launch"] = adb.run("shell", "am", "start", "-W", "-n", resolved,
                                   check=False).strip()[-1000:]
        sleep(max(1, wait_seconds))
        pid = adb.run("shell", "pidof", package, check=False).strip()
        result["pid"] = pid
        logcat = adb.run("logcat", "-d", "-t", "600", check=False)
        crashes = crash_lines(logcat, package)
        result["crashes"] = crashes

        if crashes:
            result["reason"] = "the app logged a crash or ANR"
        elif not pid:
            result["reason"] = "the app process is not running after launch"
        elif "Error type" in result["launch"]:
            result["reason"] = "am start reported an error"
        else:
            result["status"] = "passed"
            result["reason"] = "process alive with no crash or ANR in the captured logcat"
    except AdbError as error:
        result["reason"] = str(error)
    return result


def print_result(result: dict, as_json: bool) -> None:
    if as_json:
        print(json.dumps(result, indent=2, sort_keys=True))
        return
    print("device-smoke: %s" % result["status"].upper())
    print("  reason: %s" % result.get("reason", ""))
    device = result.get("device")
    if device:
        print("  device: %s (SDK %s, physical=%s)"
              % (device["model"], device["sdk"], device["physical"]))
    if result.get("activity"):
        print("  activity: %s" % result["activity"])
    if result.get("crashes"):
        for line in result["crashes"]:
            print("  crash: %s" % line)
    if result.get("status") == "blocked":
        print("  blocked checks are not passes; connect a phone and rerun.")


def main(argv: list[str] | None = None, adb: Adb | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--adb", default=os.environ.get("ADB", shutil.which("adb") or "adb"),
                        help="adb binary (default: $ADB, then PATH)")
    parser.add_argument("--serial", default="", help="device serial; default is the only device")
    parser.add_argument("--apk", type=Path, help="APK to install before launching")
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help="application id")
    parser.add_argument("--activity", default="",
                        help="component to launch; default resolver, then the manifest launcher")
    parser.add_argument("--wait", type=int, default=8, help="seconds to wait after launch")
    parser.add_argument("--skip-install", action="store_true", help="test the installed build")
    parser.add_argument("--json", action="store_true", help="print a machine-readable result")
    args = parser.parse_args(argv)

    if args.apk and not args.apk.is_file():
        parser.error("--apk does not exist: %s" % args.apk)
    if adb is None:
        adb = Adb(args.adb, args.serial)
    else:
        adb.serial = args.serial or adb.serial

    result = smoke(adb, apk=args.apk, package=args.package, activity=args.activity,
                   wait_seconds=args.wait, skip_install=args.skip_install)
    print_result(result, args.json)
    return {"passed": 0, "blocked": 2}.get(result["status"], 1)


if __name__ == "__main__":
    raise SystemExit(main())
