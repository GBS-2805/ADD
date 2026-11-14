#!/usr/bin/env python3
"""Plan and, only with --yes, remove the three approved cleanup waves.

    python3 tools/cleanup_waves.py --wave 1 --dry-run
    python3 tools/cleanup_waves.py --wave 1 --yes

The default is a dry run. The tool never follows a symlink, never removes a
tracked or protected path, and refuses anything outside the scratch/build
roots in ALLOWED_ROOTS.
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

KEEP = frozenset({
    Path("tools"),
    Path("docs"),
    Path("handoff"),
    Path("figma"),
    Path("app/src"),
    Path("app/res"),
    Path("app/studio-assets"),
    Path("docs/ADD-3.9.apk"),
    Path("dist/ADD-3.9.apk"),
    Path("docs/classic"),
    Path("artifacts/handoff-apk/ADD-3.9.1-field-preview.apk"),
    Path("artifacts/bmo"),
    Path("docs/superpowers/plans/2026-11-14-bmo-plan.md"),
})

WAVES = {
    1: (
        "dist/*.apk",
        "artifacts/handoff-apk/*",
        "artifacts/engine-review",
        "artifacts/emu",
        "artifacts/handoff-archive",
        "artifacts/engine-spike",
        "artifacts/apk-test",
        "artifacts/apk-check",
    ),
    2: (
        "tools/engine-spike/app/build",
        "tools/engine-review/app/build",
        "build",
        "app/assets/www",
    ),
    3: (
        "artifacts/reference-src",
        "artifacts/paparazzi",
        "artifacts/renderer",
        "artifacts/screen-review",
        "artifacts/brand",
    ),
}

ALLOWED_ROOTS = (
    Path("artifacts"),
    Path("dist"),
    Path("build"),
    Path("app/assets/www"),
    Path("tools/engine-spike/app/build"),
    Path("tools/engine-review/app/build"),
)

GENERATED = frozenset({
    Path("tools/engine-spike/app/build"),
    Path("tools/engine-review/app/build"),
})

CURRENT_APKS = {
    "dist/ADD-3.9.apk",
    "artifacts/handoff-apk/ADD-3.9.1-field-preview.apk",
}


def _relative(path, root):
    try:
        return path.resolve().relative_to(Path(root).resolve())
    except ValueError:
        raise ValueError("path escapes root: %s" % path)


def _tracked(path, root):
    """A real checkout protects every tracked file; temp fixtures are not Git."""
    try:
        rel = _relative(path, root)
    except ValueError:
        return True
    try:
        probe = subprocess.run(
            ["git", "-C", str(root), "ls-files", "--error-unmatch", "--", str(rel)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    except OSError:
        return False
    return probe.returncode == 0


def protected(path, root=ROOT):
    """Return True for a keep entry, anything under it, or any tracked path."""
    target = Path(path).resolve()
    root = Path(root).resolve()
    try:
        rel = target.relative_to(root)
    except ValueError:
        return True
    for generated in GENERATED:
        built = (root / generated).resolve()
        if target == built or built in target.parents:
            return _tracked(target, root)
    for keep in KEEP:
        kept = (root / keep).resolve()
        if target == kept or kept in target.parents or target in kept.parents:
            return True
    return _tracked(target, root)


def _allowed(path, root):
    target = Path(path).resolve()
    root = Path(root).resolve()
    try:
        rel = target.relative_to(root)
    except ValueError:
        return False
    return any(rel == allowed or allowed in rel.parents for allowed in ALLOWED_ROOTS)


def _size(path):
    if path.is_file():
        return path.stat().st_size
    total = 0
    for child in path.iterdir():
        if child.is_symlink():
            continue
        total += _size(child)
    return total


def wave_entries(wave, root=ROOT):
    if wave not in WAVES:
        raise ValueError("unknown cleanup wave: %s" % wave)
    root = Path(root).resolve()
    found = []
    for pattern in WAVES[wave]:
        matches = [root / pattern] if not any(char in pattern for char in "*?[") \
            else list(root.glob(pattern))
        for path in matches:
            if not path.exists() and not path.is_symlink():
                continue
            try:
                rel = path.resolve().relative_to(root)
            except ValueError:
                continue
            if str(rel) in CURRENT_APKS or rel == Path("dist/ADD-3.9.apk"):
                continue
            if path.is_symlink() or protected(path, root) or not _allowed(path, root):
                continue
            found.append(path)
    return sorted(set(found), key=lambda item: str(item))


def _validate_tree(path, root):
    path = Path(path)
    if path.is_symlink():
        raise ValueError("refusing symlink: %s" % path)
    try:
        path.resolve().relative_to(Path(root).resolve())
    except ValueError:
        raise ValueError("path escapes root: %s" % path)
    if path.is_dir():
        for child in path.iterdir():
            _validate_tree(child, root)
    elif not path.is_file():
        raise ValueError("not a regular file or directory: %s" % path)


def _remove(path, root):
    path = Path(path)
    if path.is_symlink():
        raise ValueError("refusing symlink: %s" % path)
    if path.is_dir():
        for child in sorted(path.iterdir(), key=lambda item: item.name):
            _remove(child, root)
        path.rmdir()
        return 0, 0
    size = path.stat().st_size
    path.unlink()
    return 1, size


def apply(entries, root=ROOT):
    """Remove entries after validating every one; return (files, bytes)."""
    root = Path(root).resolve()
    entries = [Path(item) for item in entries]
    for entry in entries:
        if entry.is_symlink():
            raise ValueError("refusing symlink: %s" % entry)
        if protected(entry, root) or not _allowed(entry, root):
            raise ValueError("refusing protected or outside path: %s" % entry)
        _validate_tree(entry, root)
    removed = 0
    removed_bytes = 0
    for entry in entries:
        count, size = _remove(entry, root)
        removed += count
        removed_bytes += size
    return removed, removed_bytes


def render(entries, root=ROOT, out=sys.stdout):
    for entry in entries:
        rel = Path(entry).resolve().relative_to(Path(root).resolve())
        if protected(entry, root):
            print("keep %s" % rel, file=out)
        else:
            print("remove %d %s" % (_size(entry), rel), file=out)


def main(argv=None, root=ROOT, out=sys.stdout):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wave", type=int, choices=sorted(WAVES), required=True)
    parser.add_argument("--dry-run", action="store_true",
                        help="list entries without changing anything (default)")
    parser.add_argument("--yes", action="store_true",
                        help="remove the planned entries")
    args = parser.parse_args(argv)
    if args.dry_run and args.yes:
        parser.error("--dry-run and --yes are mutually exclusive")
    entries = wave_entries(args.wave, root)
    if not args.yes:
        render(entries, root, out)
        return 0
    count, size = apply(entries, root)
    print("removed %d files, %d bytes" % (count, size), file=out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
