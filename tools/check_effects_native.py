#!/usr/bin/env python3
"""Compare `docs/data/effects.json` against the native engine's Kotlin tables.

    python3 tools/check_effects_native.py

The JSON is the source of truth, so this reads *towards* it: the Kotlin tables
are parsed as text and must agree with the catalog. That direction matters -- it
keeps the dependency on Python's standard library only, where deriving the JSON
from Kotlin would need a Gradle task or a Kotlin parser, and would put a JDK
requirement on a gate that has to run on a machine without one.

What is compared, and why each one is worth pinning:

  * field presets: name, and each colour spot's hex, x, y and band, in order. A
    document stores these to render a poster; if the two sides disagree, the same
    preset becomes two different posters.
  * field defaults: scale, distortion, swirl, speed, soften, noise.
  * texture families: id, label, default opacity and blend -- the texture kind's
    `render` key and its two defaults.

It parses only the shapes those files actually use. When a table is rewritten so
this cannot find it, it fails rather than passing on nothing, and says which file
and which pattern to look at.
"""

from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENGINE = ROOT / "tools/engine-spike/app/src/main/java/app/add/engine"
CATALOG = ROOT / "docs/data/effects.json"

FIELD_FILE = ENGINE / "FieldPaint.kt"
TEXTURE_FILE = ENGINE / "TexturePresets.kt"


def field_presets(text: str) -> dict[str, list[str]]:
    """`preset("Mica dawn", 0xFFF3E9DA...)` -> name -> [hex, ...]."""
    found: dict[str, list[str]] = {}
    # The colour arguments are `0xFFF3E9DA.toInt()`, so the call ends at the last
    # `)` on the line, not the first one.
    for match in re.finditer(r'preset\(\s*"([^"]+)"\s*,([^\n]*)\)', text):
        name = match.group(1)
        colours = re.findall(r"0x([0-9A-Fa-f]{8})", match.group(2))
        found[name] = ["#" + value[2:].upper() for value in colours]
    return found


def field_defaults(text: str) -> dict[str, float]:
    """The defaults on the FieldSpec data class constructor."""
    span = re.search(r"data class FieldSpec\((.*?)\n\)", text, re.S)
    if not span:
        raise SystemExit("effects-native: cannot find `data class FieldSpec(...)` in %s"
                         % FIELD_FILE.name)
    out: dict[str, float] = {}
    for key, value in re.findall(r"val (\w+): Float = ([0-9.]+)f", span.group(1)):
        out[key] = float(value)
    return out


def field_ring(text: str) -> tuple[float, float]:
    """The cos/sin radii in `ringPosition`, which place every spot."""
    span = re.search(r"ringPosition\(index: Int, count: Int = 4\)(.*?)\n    \}",
                     text, re.S)
    if not span:
        raise SystemExit("effects-native: cannot find `fun ringPosition(...)` in %s"
                         % FIELD_FILE.name)
    body = span.group(1)
    x = re.search(r"cos\(angle\) \* ([0-9.]+)f", body)
    y = re.search(r"sin\(angle\) \* ([0-9.]+)f", body)
    if not x or not y:
        raise SystemExit("effects-native: ringPosition has no cos/sin radii in %s"
                         % FIELD_FILE.name)
    return float(x.group(1)), float(y.group(1))


def texture_families(text: str) -> dict[str, dict]:
    """Each `Name("id", "LABEL", "note", 0.34f, BlendKind.Multiply)` entry."""
    found: dict[str, dict] = {}
    for match in re.finditer(
            r'(\w+)\("([^"]+)",\s*"[^"]+",\s*"[^"]*",\s*([0-9.]+)f,\s*'
            r'BlendKind\.(\w+)\)', text):
        found[match.group(2)] = {
            "class": match.group(1),
            "opacity": float(match.group(3)),
            "blend": match.group(4).upper(),
        }
    return found


def problems() -> list[str]:
    try:
        return _problems()
    except SystemExit as exc:
        # A parser raising its own clear message is still a disagreement the gate
        # must report, not an exit that skips the rest of the checks.
        message = str(exc)
        if "did the table move" not in message:
            message += " -- did the table move? check the pattern it reads"
        return [message]


def _problems() -> list[str]:
    if not FIELD_FILE.is_file():
        return ["%s is missing; the native field recipe is the thing this checks"
                % FIELD_FILE.relative_to(ROOT)]
    if not TEXTURE_FILE.is_file():
        return ["%s is missing" % TEXTURE_FILE.relative_to(ROOT)]
    try:
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return ["%s is not readable JSON: %s" % (CATALOG.relative_to(ROOT), exc)]

    found: list[str] = []
    field_text = FIELD_FILE.read_text(encoding="utf-8")
    texture_text = TEXTURE_FILE.read_text(encoding="utf-8")

    native = field_presets(field_text)
    if not native:
        found.append("no `preset(...)` entries found in %s; did the table move?"
                     % FIELD_FILE.name)
    defaults = field_defaults(field_text)
    rx, ry = field_ring(field_text)

    presets = {p["id"]: p for p in catalog.get("presets", [])}
    for name, colours in native.items():
        slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
        pid = "field-" + slug
        preset = presets.get(pid)
        if preset is None:
            found.append("%s: the native table has %r but the catalog has no %r"
                         % (FIELD_FILE.name, name, pid))
            continue
        spots = preset.get("defaults", {}).get("spots") or []
        if len(spots) != len(colours):
            found.append("%s: %r has %d colours natively, %d in the catalog"
                         % (FIELD_FILE.name, name, len(colours), len(spots)))
            continue
        for index, (spot, colour) in enumerate(zip(spots, colours)):
            if spot.get("hex", "").upper() != colour:
                found.append("%s: %r spot %d is %s natively, %s in the catalog"
                             % (FIELD_FILE.name, name, index, colour,
                                spot.get("hex")))
            want_x = round(0.5 + math.cos(index / len(colours) * math.tau
                                          - math.pi / 2) * rx, 4)
            want_y = round(0.5 + math.sin(index / len(colours) * math.tau
                                          - math.pi / 2) * ry, 4)
            if abs(spot.get("x", 0) - want_x) > 0.001 or \
                    abs(spot.get("y", 0) - want_y) > 0.001:
                found.append("%s: %r spot %d sits at (%.4f, %.4f) natively, "
                             "(%.4f, %.4f) in the catalog"
                             % (FIELD_FILE.name, name, index, want_x, want_y,
                                spot.get("x", 0), spot.get("y", 0)))

    if defaults:
        for pid, preset in presets.items():
            if preset.get("kind") != "field":
                continue
            for key, value in defaults.items():
                got = preset.get("defaults", {}).get(key)
                if got is None:
                    found.append("%s: %r has no %r default; the native table says %r"
                                 % (CATALOG.name, pid, key, value))
                elif abs(float(got) - value) > 1e-6:
                    found.append("%s: %r default %r is %r natively"
                                 % (CATALOG.name, pid, key, value))

    families = texture_families(texture_text)
    if not families:
        found.append("no texture families found in %s; did the table move?"
                     % TEXTURE_FILE.name)
    materials = {p.get("render"): p for p in catalog.get("presets", [])
                 if p.get("kind") == "material"}
    for family, spec in families.items():
        preset = materials.get(family)
        if preset is None:
            found.append("%s: family %r has no material preset in the catalog"
                         % (TEXTURE_FILE.name, family))
            continue
        defaults = preset.get("defaults", {})
        if abs(float(defaults.get("opacity", -1)) - spec["opacity"]) > 1e-6:
            found.append("%s: %r opacity is %r natively, %r in the catalog"
                         % (TEXTURE_FILE.name, family, spec["opacity"],
                            defaults.get("opacity")))
        if str(defaults.get("blend", "")).upper() != spec["blend"]:
            found.append("%s: %r blend is %s natively, %r in the catalog"
                         % (TEXTURE_FILE.name, family, spec["blend"],
                            defaults.get("blend")))
    return found


def main() -> int:
    found = problems()
    if found:
        print("effects-native: FAIL - %d disagreement(s) between the catalog and "
              "the native tables" % len(found))
        for line in found[:20]:
            print("  %s" % line)
        if len(found) > 20:
            print("  ... and %d more" % (len(found) - 20))
        print("  fix: change the JSON (tools/build_effects_data.py) or the Kotlin"
              " table, deliberately -- not one to match the other by hand.")
        return 1
    print("effects-native: OK - field presets, field defaults and texture families "
          "agree with the catalog")
    return 0


if __name__ == "__main__":
    sys.exit(main())
