#!/usr/bin/env python3
"""Run the field device audit on a connected Android runtime.

This is the pre-ship gate for the parts unit tests cannot prove: the real Compose
window, the saved field recipe and (when requested) the in-app PNG. It captures
whatever live windows are already present. It deliberately uses
no screenshots through SurfaceFlinger; the app draws its live windows into files and
the script pulls them.

    python3 tools/device_audit.py --serial emulator-5554 --fixture artifacts/field-proof/device/document.json
    python3 tools/device_audit.py --no-launch --skip-export
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_ADB = str(Path.home() / "android-sdk/platform-tools/adb")
PACKAGE = "app.offline.compress"
ACTIVITY = f"{PACKAGE}/app.add.engine.MainActivity"
CAPTURE_ACTION = "app.add.engine.CAPTURE"
DEFAULT_OUT = ROOT / "artifacts/field-proof/device/audit"


class AuditError(RuntimeError):
    pass


def adb(*args: str, binary: bool = False, timeout: int = 60, check: bool = True):
    cmd = [ADB]
    if SERIAL:
        cmd += ["-s", SERIAL]
    cmd += list(args)
    result = subprocess.run(cmd, capture_output=True, text=not binary, timeout=timeout)
    if check and result.returncode:
        detail = "" if binary else (result.stderr or result.stdout or "").strip()
        raise AuditError(f"adb {' '.join(args)} failed ({result.returncode}): {detail}")
    if binary:
        return result.stdout
    return result.stdout


def shell(*args: str, timeout: int = 60, check: bool = True) -> str:
    return adb("shell", *args, timeout=timeout, check=check)


def run_as(*args: str, binary: bool = False, timeout: int = 60) -> bytes | str:
    return adb("exec-out", "run-as", PACKAGE, *args, binary=binary, timeout=timeout)


def read_file(name: str) -> bytes:
    return run_as("cat", f"files/{name}", binary=True)


def boot_trace() -> str:
    try:
        return read_file("engine-boot.txt").decode("utf-8", "replace")
    except Exception:
        return ""


def boot_starts() -> int:
    return boot_trace().count("engine process started")


def clear_boot_trace() -> None:
    """The engine truncates this file on start; remove it so READY is never stale."""
    try:
        shell("run-as", PACKAGE, "rm", "-f", "files/engine-boot.txt", check=False)
    except Exception:
        pass


def wait_ready(timeout: int, min_starts: int | None = None) -> bool:
    """Wait for a READY line from the current process, not a previous run."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        trace = boot_trace()
        starts = trace.count("engine process started")
        if min_starts is None or starts >= min_starts:
            tail = trace[trace.rfind("engine process started"):]
            if "READY" in tail:
                return True
        time.sleep(3)
    return False


def dump_ui() -> ET.Element:
    last = ""
    for _ in range(8):
        try:
            shell("uiautomator", "dump", "/sdcard/device-audit.xml", timeout=30)
            data = adb("exec-out", "cat", "/sdcard/device-audit.xml", binary=True)
            if data.startswith(b"<?xml"):
                return ET.fromstring(data)
        except Exception as error:
            last = str(error)
        time.sleep(2)
    raise AuditError(f"uiautomator never returned a tree: {last}")


def nodes(root: ET.Element):
    return list(root.iter())


def find_node(root: ET.Element, value: str, *, exact: bool = False):
    for node in nodes(root):
        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        for candidate in (text, desc):
            if (candidate == value) if exact else (value in candidate):
                return node
    return None


def tap_node(node: ET.Element):
    bounds = node.get("bounds") or ""
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        raise AuditError(f"node has no tappable bounds: {bounds!r}")
    x1, y1, x2, y2 = map(int, match.groups())
    shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def ui_text(root: ET.Element) -> str:
    lines = []
    for node in nodes(root):
        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        if text:
            lines.append(text)
        if desc and desc != text:
            lines.append(f"[{desc}]")
    return "\n".join(lines)


def capture_windows(out_dir: Path) -> tuple[list[Path], str]:
    shell("am", "broadcast", "-a", CAPTURE_ACTION, "-p", PACKAGE, timeout=30)
    time.sleep(2)
    listing = shell("run-as", PACKAGE, "ls", "files", timeout=30)
    names = [line.strip() for line in listing.splitlines() if line.strip().startswith("device-ui")]
    if "device-ui.png" not in names:
        raise AuditError("the app did not write device-ui.png; capture receiver missing")
    out_dir.mkdir(parents=True, exist_ok=True)
    pulled: list[Path] = []
    for name in names:
        data = read_file(name)
        destination = out_dir / name
        destination.write_bytes(data)
        pulled.append(destination)
    roots = ""
    try:
        roots = read_file("device-ui-roots.txt").decode("utf-8", "replace")
    except Exception:
        pass
    return pulled, roots


def latest_export() -> str | None:
    rows = shell(
        "content", "query", "--uri", "content://media/external/images/media",
        "--projection", "_display_name:relative_path",
        timeout=30,
    )
    names = []
    for row in rows.splitlines():
        name = re.search(r"_display_name=([^,]+)", row)
        rel = re.search(r"relative_path=([^,]+)", row)
        if name and rel and "Engine Spike" in rel.group(1):
            names.append(name.group(1).strip())
    if not names:
        return None
    return sorted(names)[-1]


def pull_export(name: str, destination: Path) -> Path:
    remote = f"/sdcard/Pictures/Engine Spike/{name}"
    adb("pull", remote, str(destination), timeout=60)
    return destination


def png_facts(path: Path) -> dict:
    try:
        from PIL import Image, ImageStat
    except Exception:
        return {"bytes": path.stat().st_size}
    with Image.open(path) as image:
        rgb = image.convert("RGB")
        thumb = rgb.resize((64, 64))
        colours = len(thumb.getcolors(maxcolors=64 * 64) or [])
        extrema = ImageStat.Stat(rgb).extrema
        return {
            "width": image.width,
            "height": image.height,
            "bytes": path.stat().st_size,
            "colours": colours,
            "range": max(high - low for low, high in extrema),
        }


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="", help="adb serial; default is the only device")
    parser.add_argument("--fixture", type=Path, help="document.json to install before launch")
    parser.add_argument("--no-launch", action="store_true", help="audit the running activity")
    parser.add_argument("--skip-export", action="store_true", help="do not run EXPORT PNG")
    parser.add_argument("--reopen", action="store_true", help="force-stop, relaunch and compare the saved field")
    parser.add_argument("--timeout", type=int, default=240, help="seconds to wait for READY")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    global ADB, SERIAL
    args = parser.parse_args()
    ADB = str(Path.home() / "android-sdk/platform-tools/adb")
    SERIAL = args.serial
    out = args.out_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    checks: dict[str, object] = {"package": PACKAGE, "activity": ACTIVITY}

    if not Path(ADB).exists():
        raise AuditError(f"adb not found at {ADB}")
    devices = adb("devices")
    if "\tdevice" not in devices:
        raise AuditError("no authorised Android device/emulator")
    checks["device"] = next(
        (line.split()[0] for line in devices.splitlines() if line.endswith("\tdevice")),
        "unknown",
    )

    if args.fixture:
        args.fixture = args.fixture.resolve()
        if not args.fixture.exists():
            raise AuditError(f"fixture not found: {args.fixture}")
        shell("am", "force-stop", PACKAGE, timeout=30)
        adb("push", str(args.fixture), "/data/local/tmp/device-audit-document.json", timeout=60)
        shell(
            "run-as", PACKAGE, "cp", "/data/local/tmp/device-audit-document.json",
            "files/document.json", timeout=30,
        )
        checks["fixture"] = str(args.fixture)

    if not args.no_launch:
        shell("am", "force-stop", PACKAGE, timeout=30)
        time.sleep(2)
        clear_boot_trace()
        shell("am", "start", "-n", ACTIVITY, timeout=30)
        ready = wait_ready(args.timeout)
    else:
        ready = wait_ready(args.timeout)
    checks["ready"] = ready
    checks["boot_trace"] = boot_trace().splitlines()[-8:]
    if not ready:
        (out / "audit.json").write_text(json.dumps(checks, indent=2) + "\n")
        raise AuditError(f"engine never reached READY in {args.timeout}s")

    pulled, roots = capture_windows(out / "windows")
    checks["capture_files"] = [str(path.relative_to(ROOT)) for path in pulled]
    checks["hashes"] = {path.name: sha256(path) for path in pulled}
    root_lines = [line for line in roots.splitlines() if line.strip()]
    checks["window_count"] = len(root_lines)
    checks["windows"] = root_lines

    # A dialog is optional: this receiver captures whatever windows are live, so an
    # already-open dialog root is recorded without the audit opening it.
    dialog_image = next((path for path in pulled if re.fullmatch(r"device-ui-\d+\.png", path.name)
                         and png_facts(path).get("height", 640) < 500), None)
    if dialog_image is not None:
        checks["dialog_capture"] = {
            "path": str(dialog_image.relative_to(ROOT)),
            **png_facts(dialog_image),
        }

    try:
        document = json.loads(read_file("document.json"))
    except Exception as error:
        raise AuditError(f"could not read files/document.json: {error}") from error
    field = document.get("field") or {}
    checks["field_colours"] = len(field.get("colors") or [])
    layer_fields = []
    for layer in document.get("layers", []):
        paint = layer.get("paint") or {}
        if paint.get("type") == "field":
            layer_fields.append(layer.get("id"))
    checks["layer_fields"] = layer_fields
    if checks["field_colours"] < 4:
        raise AuditError("the saved artboard field has fewer than four colours")
    if not layer_fields:
        raise AuditError("the saved document has no field-painted layer")

    if args.reopen:
        shell("am", "force-stop", PACKAGE, timeout=30)
        time.sleep(2)
        clear_boot_trace()
        shell("am", "start", "-n", ACTIVITY, timeout=30)
        if not wait_ready(args.timeout):
            raise AuditError(f"engine never reached READY after reopen in {args.timeout}s")
        reopened = json.loads(read_file("document.json"))
        reopened_field = reopened.get("field") or {}
        reopened_layers = [
            layer.get("id") for layer in reopened.get("layers", [])
            if (layer.get("paint") or {}).get("type") == "field"
        ]
        checks["reopen"] = {
            "field_colours": len(reopened_field.get("colors") or []),
            "layer_fields": reopened_layers,
        }
        if checks["reopen"]["field_colours"] != checks["field_colours"]:
            raise AuditError("the artboard field changed across reopen")
        if reopened_layers != layer_fields:
            raise AuditError("field-painted layers changed across reopen")

    if not args.skip_export:
        export = find_node(dump_ui(), "EXPORT", exact=True)
        if export is None:
            raise AuditError("the live UI has no EXPORT control")
        tap_node(export)
        time.sleep(2)
        png = find_node(dump_ui(), "EXPORT PNG", exact=True)
        if png is None:
            raise AuditError("the export sheet has no EXPORT PNG action")
        tap_node(png)
        deadline = time.monotonic() + 120
        name = None
        while time.monotonic() < deadline and name is None:
            time.sleep(5)
            name = latest_export()
        if name is None:
            raise AuditError("no PNG reached Pictures/Engine Spike")
        exported = pull_export(name, out / "export.png")
        facts = png_facts(exported)
        checks["export"] = {"path": str(exported.relative_to(ROOT)), **facts}
        if facts.get("width") != 1080 or facts.get("height") != 1350:
            raise AuditError(f"export is {facts.get('width')}x{facts.get('height')}, expected 1080x1350")

    checks["passed"] = True
    (out / "audit.json").write_text(json.dumps(checks, indent=2) + "\n")
    lines = [
        "# Device audit — PASS",
        "",
        f"- Device: `{checks['device']}`",
        f"- READY: {checks['ready']}",
        f"- Window captures: {checks['window_count']}",
        f"- Artboard field colours: {checks['field_colours']}",
        f"- Field-painted layers: {', '.join(layer_fields)}",
    ]
    if "dialog_capture" in checks:
        lines.insert(4, f"- Dialog image: `{checks['dialog_capture']['path']}`")
    if "reopen" in checks:
        lines.append(
            f"- Reopen: field colours {checks['reopen']['field_colours']}, "
            f"layers {', '.join(checks['reopen']['layer_fields'])}"
        )
    if "export" in checks:
        lines.append(f"- Export: `{checks['export']['path']}` ({checks['export'].get('width')}x{checks['export'].get('height')})")
    (out / "audit.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AuditError as error:
        print(f"DEVICE AUDIT FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
