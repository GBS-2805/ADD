#!/usr/bin/env python3
"""Build and check `docs/data/effects.json`, the library's effects catalog.

    python3 tools/build_effects.py            # write, then check
    python3 tools/build_effects.py --check    # check the file as it is

This file is the **source of truth** for the effects Twin renders. Twin fetches
it, builds its pickers from `controls`, and renders previews from each preset's
`defaults`. Nothing in it is a bitmap or a shader string: a preset is a recipe,
so the same entry can travel in a signed pack.

Why the checks are shaped this way:

  * `id` is what a document stores, so it must be stable and unique. The old Twin
    stored a *display name* for some kinds and an id for others; renaming a preset
    could change what a saved file rendered. This file makes the name display-only.
  * a preset may only use a control its kind declares, and every key in `defaults`
    must be one of those controls -- otherwise a control silently does nothing, or
    a default silently goes missing, and both look like a rendering bug.
  * `visibleWhen` must name a real preset of the same kind, so a condition cannot
    point at a preset that was renamed or deleted.

Requires no third-party package: it runs in CI's shelf job, which installs nothing.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/data/effects.json"

# The kinds this version understands. Adding a kind means adding a renderer in
# Twin as well, so the list is deliberately explicit rather than open-ended.
KINDS = ("shader", "texture", "pattern", "particles", "field", "material",
         "gradient", "pattern-stroke")

CONTROL_KINDS = ("range", "swatch", "toggle", "stops", "number")

# The catalog is fetched at runtime, so the page byte gate cannot see it: a page
# measurement parses link/script/img and a JSON request is invisible. It gets its
# own ceiling, raised deliberately rather than by drift. gzip of what is on disk
# is roughly a third of this, and the host serves brotli.
MAX_BYTES = 32_000

ID = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
HEX = re.compile(r"^#[0-9a-fA-F]{6}$")


def problems(data: dict) -> list[str]:
    """Everything wrong with the catalog, as sentences a person can act on."""
    found: list[str] = []

    if not isinstance(data, dict):
        return ["the file is not a JSON object"]
    version = data.get("version")
    if version != 1:
        found.append("version must be 1, found %r" % (version,))

    kinds = data.get("kinds")
    if kinds != list(KINDS):
        found.append("kinds must be exactly %s (found %r)"
                     % (list(KINDS), kinds))

    controls = data.get("controls") or {}
    for kind in KINDS:
        if kind not in controls:
            found.append("controls: %s is missing; every kind needs its own list"
                         % kind)
            continue
        seen = set()
        for control in controls[kind]:
            cid = control.get("id")
            if not cid or not ID.match(str(cid)):
                found.append("controls.%s: bad id %r" % (kind, cid))
                continue
            if cid in seen:
                found.append("controls.%s: %r declared twice" % (kind, cid))
            seen.add(cid)
            ckind = control.get("kind")
            if ckind not in CONTROL_KINDS:
                found.append("controls.%s.%s: kind %r is not one of %s"
                             % (kind, cid, ckind, list(CONTROL_KINDS)))
            if ckind in ("range", "number"):
                lo, hi = control.get("min"), control.get("max")
                if not isinstance(lo, (int, float)) or not isinstance(hi, (int, float)):
                    found.append("controls.%s.%s: a range needs numeric min and max"
                                 % (kind, cid))
                elif lo >= hi:
                    found.append("controls.%s.%s: min %r is not below max %r"
                                 % (kind, cid, lo, hi))
                step = control.get("step")
                if step is not None and (not isinstance(step, (int, float)) or step <= 0):
                    found.append("controls.%s.%s: step must be a positive number"
                                 % (kind, cid))
                default = control.get("default")
                if default is not None and isinstance(lo, (int, float)) and isinstance(hi, (int, float)):
                    if not lo <= default <= hi:
                        found.append("controls.%s.%s: default %r is outside %r..%r"
                                     % (kind, cid, default, lo, hi))
            if ckind == "swatch":
                options = control.get("options")
                if not isinstance(options, list) or not options:
                    found.append("controls.%s.%s: a swatch needs options"
                                 % (kind, cid))
                else:
                    for value in options:
                        if not isinstance(value, str) or not HEX.match(value):
                            found.append("controls.%s.%s: %r is not a #rrggbb colour"
                                         % (kind, cid, value))

    presets = data.get("presets")
    if not isinstance(presets, list) or not presets:
        return found + ["presets must be a non-empty list"]

    ids: dict[str, str] = {}
    by_kind: dict[str, set[str]] = {kind: set() for kind in KINDS}
    for index, preset in enumerate(presets):
        where = "presets[%d]" % index
        pid = preset.get("id")
        if not isinstance(pid, str) or not ID.match(pid):
            found.append("%s: bad id %r (lowercase, hyphens, no spaces)" % (where, pid))
            continue
        if pid in ids:
            found.append("%s: id %r is already used by %s; a document stores this id"
                         % (where, pid, ids[pid]))
        ids[pid] = where
        kind = preset.get("kind")
        if kind not in KINDS:
            found.append("%s (%s): kind %r is not one of %s"
                         % (where, pid, kind, list(KINDS)))
            continue
        by_kind[kind].add(pid)
        name = preset.get("name")
        if not isinstance(name, str) or not name.strip():
            found.append("%s (%s): needs a display name" % (where, pid))
        if not isinstance(preset.get("note"), str):
            found.append("%s (%s): needs a note" % (where, pid))
        if preset.get("source") not in ("twin", "native"):
            found.append("%s (%s): source must be \"twin\" or \"native\""
                         % (where, pid))
        defaults = preset.get("defaults")
        if not isinstance(defaults, dict) or not defaults:
            found.append("%s (%s): needs defaults" % (where, pid))
            continue
        declared = {control["id"] for control in controls.get(kind, [])
                    if isinstance(control, dict) and "id" in control}
        for key in defaults:
            if key not in declared:
                found.append("%s (%s): default %r has no control of that name "
                             "under controls.%s" % (where, pid, key, kind))

    # visibleWhen may only name a preset of the same kind, so a condition cannot
    # outlive the preset it refers to.
    for kind, ids_of_kind in by_kind.items():
        for control in controls.get(kind, []):
            if not isinstance(control, dict):
                continue
            condition = control.get("visibleWhen")
            if not isinstance(condition, dict):
                continue
            wanted = condition.get("preset")
            if wanted not in ids_of_kind:
                found.append("controls.%s.%s: visibleWhen names %r, which is not a "
                             "%s preset" % (kind, control.get("id"), wanted, kind))

    for kind, ids_of_kind in by_kind.items():
        if not ids_of_kind:
            found.append("no %s preset: a kind with no entry has no picker" % kind)
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="check the file on disk without rewriting it")
    args = parser.parse_args()

    if args.check and not OUT.is_file():
        print("effects: FAIL - %s is missing" % OUT.relative_to(ROOT))
        return 1
    try:
        data = json.loads(OUT.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print("effects: FAIL - %s is not readable JSON: %s"
              % (OUT.relative_to(ROOT), exc))
        return 1

    found = problems(data)
    if found:
        print("effects: FAIL - %d problem(s) in %s"
              % (len(found), OUT.relative_to(ROOT)))
        for line in found[:20]:
            print("  %s" % line)
        if len(found) > 20:
            print("  ... and %d more" % (len(found) - 20))
        return 1

    size = OUT.stat().st_size
    if size > MAX_BYTES:
        print("effects: FAIL - %s is %d bytes, over the %d-byte ceiling"
              % (OUT.relative_to(ROOT), size, MAX_BYTES))
        print("  fix: raise MAX_BYTES in tools/build_effects.py deliberately (a"
              " diff someone reviews), or trim the catalog. Twin fetches this"
              " file, so it is downloaded on every first visit.")
        return 1

    presets = data["presets"]
    counts = {}
    for preset in presets:
        counts[preset["kind"]] = counts.get(preset["kind"], 0) + 1
    print("effects: OK - %d presets across %d kinds (%s); %d bytes"
          % (len(presets), len(counts),
             ", ".join("%s %d" % (kind, counts[kind]) for kind in KINDS),
             OUT.stat().st_size))
    return 0


if __name__ == "__main__":
    sys.exit(main())
