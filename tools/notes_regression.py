#!/usr/bin/env python3
"""Fail when the note dating gets worse than it is now.

`tools/bump_version.py --check-notes` runs the same guard before every release; this is
the same check on its own, for a hook or a habit. It exits 1 on a regression.
"""
import importlib.util
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def load():
    spec = importlib.util.spec_from_file_location("bv", ROOT / "tools/bump_version.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules["bv"] = module
    spec.loader.exec_module(module)
    return module


def main():
    bv = load()
    fails, verified = bv.regress_notes()
    for line in fails:
        print("  " + line)
    print("notes regression: %d verified (floor %d), %d pinned, %s"
          % (verified, bv.NOTES_VERIFIED_FLOOR, len(bv.NOTES_PINS),
             "FAILED" if fails else "ok"))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
