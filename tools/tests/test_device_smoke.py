from __future__ import annotations

import contextlib
import io
import json
import unittest
from types import SimpleNamespace

from tools import device_smoke


class FakeRunner:
    def __init__(self, *, devices: str = "List of devices attached\nphone-1\tdevice\n",
                 model: str = "Pixel 7", qemu: str = "0", logcat: str = "",
                 resolve: str = "priority=0\napp.offline.compress/add.MainActivity\n",
                 pid: str = "4321\n"):
        self.devices = devices
        self.model = model
        self.qemu = qemu
        self.logcat = logcat
        self.resolve = resolve
        self.pid = pid
        self.commands: list[list[str]] = []

    def __call__(self, command, **kwargs):
        self.commands.append(command)
        args = command[1:]
        if args[:1] == ["-s"]:
            args = args[2:]
        if args[:1] == ["devices"]:
            return SimpleNamespace(returncode=0, stdout=self.devices, stderr="")
        if "getprop" in args and "ro.product.model" in args:
            return SimpleNamespace(returncode=0, stdout=self.model + "\n", stderr="")
        if "getprop" in args and "ro.kernel.qemu" in args:
            return SimpleNamespace(returncode=0, stdout=self.qemu + "\n", stderr="")
        if "getprop" in args and "ro.build.version.sdk" in args:
            return SimpleNamespace(returncode=0, stdout="35\n", stderr="")
        if "resolve-activity" in args:
            return SimpleNamespace(returncode=0, stdout=self.resolve, stderr="")
        if args[:3] == ["shell", "am", "force-stop"]:
            return SimpleNamespace(returncode=0, stdout="", stderr="")
        if args[:3] == ["shell", "am", "start"]:
            return SimpleNamespace(returncode=0, stdout="Status: ok\n", stderr="")
        if args[:2] == ["shell", "pidof"]:
            return SimpleNamespace(returncode=0, stdout=self.pid, stderr="")
        if args[:1] == ["logcat"] and "-d" in args:
            return SimpleNamespace(returncode=0, stdout=self.logcat, stderr="")
        return SimpleNamespace(returncode=0, stdout="", stderr="")


def smoke(runner: FakeRunner, **overrides):
    adb = device_smoke.Adb("adb", runner=runner)
    options = {
        "apk": None,
        "package": "app.offline.compress",
        "activity": "",
        "wait_seconds": 1,
        "skip_install": True,
        "sleep": lambda _seconds: None,
    }
    options.update(overrides)
    return device_smoke.smoke(adb, **options)


class DeviceSmokeTest(unittest.TestCase):
    def test_no_device_is_blocked_not_passed(self):
        result = smoke(FakeRunner(devices="List of devices attached\n"))
        self.assertEqual("blocked", result["status"])
        self.assertIn("no adb device", result["reason"])

    def test_missing_adb_binary_is_blocked_without_a_traceback(self):
        adb = device_smoke.Adb("definitely-not-an-adb-binary")
        result = device_smoke.smoke(
            adb, apk=None, package="app.offline.compress", activity="",
            wait_seconds=1, skip_install=True, sleep=lambda _seconds: None)
        self.assertEqual("blocked", result["status"])
        self.assertIn("adb is not available", result["reason"])

    def test_multiple_devices_without_serial_are_blocked(self):
        result = smoke(FakeRunner(
            devices="List of devices attached\none\tdevice\ntwo\tdevice\n"))
        self.assertEqual("blocked", result["status"])
        self.assertIn("--serial", result["reason"])

    def test_healthy_launch_passes_and_reports_physics(self):
        result = smoke(FakeRunner())
        self.assertEqual("passed", result["status"])
        self.assertEqual("Pixel 7", result["device"]["model"])
        self.assertTrue(result["device"]["physical"])
        self.assertEqual("4321", result["pid"])
        self.assertIn("app.offline.compress/add.MainActivity", result["activity"])

    def test_emulator_is_reported_but_still_runs(self):
        result = smoke(FakeRunner(qemu="1"))
        self.assertEqual("passed", result["status"])
        self.assertFalse(result["device"]["physical"])

    def test_fatal_logcat_fails_even_when_process_is_alive(self):
        result = smoke(FakeRunner(
            logcat="E/AndroidRuntime: FATAL EXCEPTION: main\n"
                   "Process: app.offline.compress, PID: 4321\n"))
        self.assertEqual("failed", result["status"])
        self.assertIn("crash", result["reason"])
        self.assertEqual(2, len(result["crashes"]))

    def test_missing_process_fails(self):
        result = smoke(FakeRunner(pid=""))
        self.assertEqual("failed", result["status"])
        self.assertIn("not running", result["reason"])

    def test_cli_json_uses_blocked_exit_status(self):
        adb = device_smoke.Adb("adb", runner=FakeRunner(
            devices="List of devices attached\n"))
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = device_smoke.main(["--skip-install", "--wait", "1", "--json"], adb=adb)
        self.assertEqual(2, status)
        self.assertEqual("blocked", json.loads(output.getvalue())["status"])


if __name__ == "__main__":
    unittest.main()
