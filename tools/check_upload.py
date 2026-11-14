#!/usr/bin/env python3
"""Fail when docs/ cannot be uploaded by Vercel's Hobby CLI.

    python3 tools/check_upload.py

Vercel rejects a deployment whose source is larger than 100 MB or contains more
than 15,000 files. Those are limits on the upload, not on the built site, so the
check counts the exact docs/ tree that deploy_vercel.py hands to the CLI. Running
it before a deploy turns a late provider failure into a local verdict.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
MAX_BYTES = 100_000_000
MAX_FILES = 15_000


def measure(docs=DOCS):
    files = [path for path in Path(docs).rglob("*") if path.is_file()]
    return sum(path.stat().st_size for path in files), len(files)


def problems(docs=DOCS, max_bytes=MAX_BYTES, max_files=MAX_FILES):
    if not Path(docs).is_dir():
        return ["docs/ is not a directory"]
    size, count = measure(docs)
    found = []
    if size > max_bytes:
        found.append("docs/ is %.1f MB, over the %.1f MB upload limit"
                     % (size / 1e6, max_bytes / 1e6))
    if count > max_files:
        found.append("docs/ has %d files, over the %s-file upload limit"
                     % (count, format(max_files, ",")))
    return found


def main():
    size, count = measure()
    found = problems()
    if found:
        print("upload: FAILED - %d problem(s)" % len(found))
        for problem in found:
            print("  " + problem)
        return 1
    print("upload: OK - %.1f MB and %d files (limits %.1f MB, %s files)"
          % (size / 1e6, count, MAX_BYTES / 1e6, format(MAX_FILES, ",")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
