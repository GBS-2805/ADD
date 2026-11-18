#!/usr/bin/env python3
"""Verify the release APK's manifest, signature and published digest.

    python3 tools/check_apk.py --apk dist/ADD-3.9.apk --catalog tools/catalog.json
    python3 tools/check_apk.py --apk dist/ADD-3.9.apk --catalog tools/catalog.json \
        --published docs/data/tools.json

The source catalog supplies the package and version. The generated
`docs/data/tools.json` supplies the measured SHA-256 and byte size; the source catalog
has neither. `--published` is optional for a fresh local build, because the generated
file still describes the previous release until `build_catalog.py` runs.

A research build (the twin spike) carries its own version, so `--expect-version` and
`--expect-code` replace the catalog's version for that one check. Every other gate --
package, SDK range, permissions, exported activities, signature -- still applies, and
`--release` refuses the override outright.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
A = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "app.offline.compress"
MIN_SDK = 26
TARGET_SDK = 35
PERMISSIONS = {
    "android.permission.INTERNET": None,
    "android.permission.WRITE_EXTERNAL_STORAGE": 28,
}


def load_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit("check_apk: cannot read %s: %s" % (path, exc))


def catalog_app(path):
    data = load_json(path)
    app = next((item for item in data.get("tools", []) if item.get("id") == "add"), None)
    if not app:
        raise SystemExit("check_apk: %s has no add tool" % path)
    return app


def expected_app(app, version=None, code=None):
    """The catalog entry, with a research build's own version when one is given."""
    expected = dict(app)
    if version is not None:
        expected["version"] = version
        expected.pop("versionName", None)
    if code is not None:
        expected["version_code"] = code
        expected.pop("versionCode", None)
    return expected


def version_tuple(value):
    return tuple(int(part) if part.isdigit() else part
                 for part in re.split(r"[.\-]", str(value)))


def inspect_xml(xml_text):
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as exc:
        raise SystemExit("check_apk: manifest XML does not parse: %s" % exc)
    sdk = root.find("uses-sdk")
    app = root.find("application")
    permissions = []
    for item in root.findall("uses-permission"):
        permissions.append({"name": item.get(A + "name"),
                            "maxSdkVersion": item.get(A + "maxSdkVersion")})
    activities = []
    if app is not None:
        for item in app.findall("activity"):
            activities.append({
                "name": item.get(A + "name"),
                "exported": (item.get(A + "exported") or "false").lower() == "true",
                "intent_filters": len(item.findall("intent-filter")),
            })
    return {
        "package": root.get("package"),
        "versionCode": root.get(A + "versionCode"),
        "versionName": root.get(A + "versionName"),
        "minSdkVersion": sdk.get(A + "minSdkVersion") if sdk is not None else None,
        "targetSdkVersion": sdk.get(A + "targetSdkVersion") if sdk is not None else None,
        "permissions": permissions,
        "allowBackup": (app.get(A + "allowBackup") if app is not None else None),
        "usesCleartextTraffic": (app.get(A + "usesCleartextTraffic") if app is not None else None),
        "debuggable": (app.get(A + "debuggable") if app is not None else None),
        "activities": activities,
    }


def inspect_dump(text):
    """Read the stable format of `aapt2 dump xmltree --file AndroidManifest.xml`."""
    lines = text.splitlines()
    found = {"permissions": [], "activities": [], "package": None,
             "versionCode": None, "versionName": None, "minSdkVersion": None,
             "targetSdkVersion": None, "allowBackup": None,
             "usesCleartextTraffic": None, "debuggable": None}
    current = None
    current_indent = -1
    for line in lines:
        stripped = line.strip()
        indent = len(line) - len(line.lstrip())
        if stripped.startswith("E: "):
            name = stripped[3:].split()[0]
            if indent <= current_indent:
                current = None
            if name == "uses-permission":
                current = {"name": None, "maxSdkVersion": None}
                found["permissions"].append(current)
                current_indent = indent
            elif name == "activity":
                current = {"name": None, "exported": False, "intent_filters": 0}
                found["activities"].append(current)
                current_indent = indent
            elif name == "intent-filter" and current is not None:
                current["intent_filters"] += 1
            continue
        if not stripped.startswith("A: "):
            continue
        attr = stripped[3:]
        key = attr.split("(", 1)[0].split("=", 1)[0].split(":")[-1]
        value = attr.split("=", 1)[1].split(" (Raw:", 1)[0].strip('"')
        if (current is not None and indent == current_indent + 2
                and key in ("name", "maxSdkVersion", "exported")):
            current[key] = value
            continue
        if key in found and found[key] is None:
            found[key] = value
        elif key in ("allowBackup", "usesCleartextTraffic", "debuggable") and found[key] is None:
            found[key] = value
    for activity in found["activities"]:
        activity["exported"] = str(activity.get("exported")).lower() == "true"
    return found


def validate(info, app, published=None, *, cert=None, signing=None, release=False):
    errors = []
    if info.get("package") != PACKAGE:
        errors.append("package is %r, expected %s" % (info.get("package"), PACKAGE))
    wanted_versions = {"versionName": app.get("version", app.get("versionName")),
                       "versionCode": app.get("version_code", app.get("versionCode"))}
    for key, expected in wanted_versions.items():
        if str(info.get(key)) != str(expected):
            if key == "versionCode" and str(info.get(key)).isdigit() and str(expected).isdigit():
                if int(info.get(key)) < int(expected):
                    errors.append("versionCode %s is below the catalog %s"
                                  % (info.get(key), expected))
                    continue
            errors.append("%s is %r, catalog says %r"
                          % (key, info.get(key), expected))
    if str(info.get("minSdkVersion")) != str(MIN_SDK):
        errors.append("minSdkVersion is %r, expected %d" % (info.get("minSdkVersion"), MIN_SDK))
    if str(info.get("targetSdkVersion")) != str(TARGET_SDK):
        errors.append("targetSdkVersion is %r, expected %d"
                      % (info.get("targetSdkVersion"), TARGET_SDK))
    actual = {}
    for item in info.get("permissions", []):
        actual[item.get("name")] = (None if item.get("maxSdkVersion") is None
                                    else str(item.get("maxSdkVersion")))
    expected = {name: (None if value is None else str(value))
                for name, value in PERMISSIONS.items()}
    if actual != expected:
        errors.append("permissions are %r, expected %r" % (actual, expected))
    if str(info.get("allowBackup")).lower() != "false":
        errors.append("allowBackup is %r, expected false" % info.get("allowBackup"))
    if str(info.get("usesCleartextTraffic")).lower() != "false":
        errors.append("usesCleartextTraffic is %r, expected false"
                      % info.get("usesCleartextTraffic"))
    if str(info.get("debuggable")).lower() == "true":
        errors.append("debuggable is true, release must be non-debuggable")
    if cert is not None and signing is not None:
        allowed = {item.get("sha256", "").lower()
                    for item in signing.get("certificates", [])}
        if cert.lower() not in allowed:
            errors.append("signing certificate %s is not in the public allowlist" % cert)
    elif cert is not None and signing is None:
        errors.append("no signing allowlist was supplied")
    if release and published is None:
        errors.append("release verification requires --published")
    for activity in info.get("activities", []):
        if activity.get("exported") and not activity.get("intent_filters"):
            errors.append("exported activity %r has no intent filter"
                          % activity.get("name"))
        if activity.get("exported") and activity.get("name") != "add.MainActivity":
            errors.append("only add.MainActivity may be exported, found %r"
                          % activity.get("name"))
    if published is not None:
        if info.get("sha256") != published.get("sha256"):
            errors.append("published sha256 is %r, APK is %r"
                          % (published.get("sha256"), info.get("sha256")))
        if int(info.get("bytes", 0)) != int(published.get("bytes", 0)):
            errors.append("published bytes are %r, APK is %r"
                          % (published.get("bytes"), info.get("bytes")))
    return errors


def find_tool(name, env_name):
    value = os.environ.get(env_name)
    if value and Path(value).is_file():
        return value
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if sdk:
        found = sorted(Path(sdk).glob("build-tools/*/" + name))
        if found:
            return str(found[-1])
    return name


def dump_manifest(apk, aapt2):
    done = subprocess.run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml",
                           str(apk)], capture_output=True, text=True)
    if done.returncode:
        raise SystemExit("check_apk: aapt2 could not read the manifest: %s"
                         % ((done.stderr or done.stdout).strip().splitlines()[-1:]))
    return inspect_dump(done.stdout)


def verify_signature(apk, apksigner):
    done = subprocess.run([apksigner, "verify", "--print-certs", str(apk)],
                          capture_output=True, text=True)
    if done.returncode:
        raise SystemExit("check_apk: signature verification failed\n  "
                         + "\n  ".join((done.stderr or done.stdout).strip().splitlines()[-8:]))
    return done.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--apk", required=True)
    parser.add_argument("--catalog", required=True)
    parser.add_argument("--published")
    parser.add_argument("--signing", default="tools/apk-signing.json")
    parser.add_argument("--release", action="store_true")
    parser.add_argument("--expect-version", default=None,
                        help="research build: expected versionName instead of the catalog's")
    parser.add_argument("--expect-code", type=int, default=None,
                        help="research build: expected versionCode instead of the catalog's")
    parser.add_argument("--aapt2", default=None)
    parser.add_argument("--apksigner", default=None)
    args = parser.parse_args()
    apk = Path(args.apk)
    if not apk.is_file():
        raise SystemExit("check_apk: %s is missing" % apk)
    if args.release and (args.expect_version is not None or args.expect_code is not None):
        raise SystemExit("check_apk: --release cannot use --expect-version or --expect-code")
    app = expected_app(catalog_app(args.catalog), args.expect_version, args.expect_code)
    info = dump_manifest(apk, args.aapt2 or find_tool("aapt2", "AAPT2"))
    info["bytes"] = apk.stat().st_size
    info["sha256"] = hashlib.sha256(apk.read_bytes()).hexdigest()
    published = None
    if args.published:
        published = next((item for item in load_json(args.published).get("tools", [])
                          if item.get("id") == "add"), None)
        if not published:
            raise SystemExit("check_apk: %s has no add tool" % args.published)
    signing = load_json(args.signing) if Path(args.signing).is_file() else None
    signature = verify_signature(apk, args.apksigner or find_tool("apksigner", "APKSIGNER"))
    match = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", signature)
    cert = match.group(1).lower() if match else None
    if cert is None:
        raise SystemExit("check_apk: apksigner did not print a certificate SHA-256")
    errors = validate(info, app, published, cert=cert, signing=signing,
                      release=args.release)
    if errors:
        for error in errors:
            print("  " + error)
        print("check_apk: %d problem(s)" % len(errors))
        return 1
    cert = next((line.strip() for line in signature.splitlines() if "certificate DN" in line),
                "signature verified")
    print("check_apk: OK - %s %s (%s), %d B, %s; %s"
          % (info["package"], info["versionName"], info["versionCode"],
             info["bytes"], info["sha256"][:12] + "…", cert))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
