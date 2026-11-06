#!/usr/bin/env python3
"""Builds the font packs ADD imports, out of the TTFs the site already serves.

A pack is a flat zip named ``<slug>-<version>.addpack`` carrying one manifest.json
and the family's TTF files. The manifest lists every file with its byte count and
SHA-256, and the app checks both before it installs anything.

The version is derived from the file hashes, so a changed TTF produces a new pack
name and a browser cannot serve a stale one. The zip is written with a fixed
timestamp, which keeps a rebuild byte-for-byte identical -- ``--check`` depends on
that, and so does the deploy's "nothing changed" answer.

    python3 tools/build_packs.py            # write docs/packs/ and the download page
    python3 tools/build_packs.py --check    # fail if either is stale

Each manifest is signed with ECDSA P-256 and the signature travels inside the zip
as ``manifest.sig``; the app verifies it against the public key it carries. The
private half lives outside the repo, at ``$ADD_PACK_KEY`` or
``~/tools/add-pack-key.pem``. Only the public key is committed.

The packs are still not staged into the APK: the app fetches one from the site
when the reader asks for it.
"""

from __future__ import annotations

import hashlib
import html
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
OUT = DOCS / "packs"
FONTS = DOCS / "data" / "fonts.json"
DOWNLOAD = DOCS / "download" / "index.html"
PACKS_START = "<!-- packs:start -->"
PACKS_END = "<!-- packs:end -->"

# A fixed stamp, so two runs over the same TTFs make the same bytes.
STAMP = (2026, 1, 1, 0, 0, 0)

# The signature key. The private half never enters the repo; the app embeds the
# public half, and tools/pack-signing-public.pem is the copy the build checks with.
KEY_ID = "add-2026-1"
SIGN_ALG = "ES256"
PUBLIC_KEY = ROOT / "tools" / "pack-signing-public.pem"
PRIVATE_KEY = Path(os.environ.get("ADD_PACK_KEY",
                                  str(Path.home() / "tools" / "add-pack-key.pem")))

FACE_LABEL = {"normal": "", "italic": " Italic"}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def human(value: int) -> str:
    """Whole MB, whole KB, then bytes: the site's rounding rule."""
    if value >= 1048576:
        return f"{round(value / 1048576)} MB"
    if value >= 1024:
        return f"{round(value / 1024)} KB"
    return f"{value} bytes"


def face_name(style: dict) -> str:
    """The face label, without repeating an Italic the catalog already carries."""
    name = style["name"].strip()
    label = FACE_LABEL.get(style.get("style", "normal"), "").strip()
    if label and not name.lower().endswith(label.lower()):
        name = f"{name} {label}"
    return name


def validate(manifest: dict, entries: list[dict]) -> None:
    """The same rules PackStore applies, so a generated pack cannot be rejected."""
    if manifest["schema"] != 1 or manifest["kind"] != "font":
        raise SystemExit("build_packs: a pack must be schema 1, kind font")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,39}", manifest["slug"]):
        raise SystemExit(f'build_packs: bad slug {manifest["slug"]!r}')
    if not re.fullmatch(r"[0-9a-f]{6,16}", manifest["version"]):
        raise SystemExit(f'build_packs: bad version {manifest["version"]!r}')
    if len(entries) > 64:
        raise SystemExit("build_packs: a pack may hold at most 64 files")
    listed = {row["path"]: row for row in manifest["files"]}
    if len(listed) != len(entries):
        raise SystemExit("build_packs: the manifest and the archive disagree")
    total = 0
    for entry in entries:
        row = listed.get(entry["name"])
        if row is None or row["sha256"] != digest(entry["data"]):
            raise SystemExit(f'build_packs: hash mismatch for {entry["name"]}')
        if len(entry["data"]) > 6 * 1024 * 1024:
            raise SystemExit(f'build_packs: {entry["name"]} is over the 6 MB file cap')
        total += len(entry["data"])
    if total > 12 * 1024 * 1024:
        raise SystemExit("build_packs: the pack is over the 12 MB cap")
    for style in manifest["styles"]:
        if style["file"] not in listed:
            raise SystemExit("build_packs: a style names a file the pack does not carry")


def sign(manifest: bytes) -> bytes:
    """ECDSA P-256 over the exact manifest bytes; the key never enters the repo."""
    if not PRIVATE_KEY.is_file():
        raise SystemExit(
            "build_packs: no signing key at %s - restore it or set ADD_PACK_KEY" % PRIVATE_KEY)
    with tempfile.TemporaryDirectory() as work:
        source = Path(work) / "manifest.json"
        target = Path(work) / "manifest.sig"
        source.write_bytes(manifest)
        run = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(PRIVATE_KEY),
             "-out", str(target), str(source)],
            capture_output=True)
        if run.returncode != 0:
            raise SystemExit("build_packs: openssl could not sign - "
                             + run.stderr.decode("utf-8", "replace").strip())
        return target.read_bytes()


def signature_ok(manifest: bytes, signature: bytes) -> bool:
    """The half --check needs: no private key, just the committed public one."""
    if not signature or not PUBLIC_KEY.is_file():
        return False
    with tempfile.TemporaryDirectory() as work:
        source = Path(work) / "manifest.json"
        target = Path(work) / "manifest.sig"
        source.write_bytes(manifest)
        target.write_bytes(signature)
        run = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(PUBLIC_KEY),
             "-signature", str(target), str(source)],
            capture_output=True)
        return run.returncode == 0


def reusable_signature(path: Path | None, manifest: bytes) -> bytes | None:
    """A rebuild that changes nothing keeps its signature, so the zip stays stable."""
    if path is None or not path.is_file():
        return None
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if "manifest.json" not in names or "manifest.sig" not in names:
                return None
            if archive.read("manifest.json") != manifest:
                return None
            signature = archive.read("manifest.sig")
            return signature if signature_ok(manifest, signature) else None
    except (OSError, zipfile.BadZipFile):
        return None


def build(family: dict, existing_dir: Path | None = None) -> dict:
    """One pack in memory: its manifest, its entries and the bytes of the zip."""
    entries = []
    for style in family["styles"]:
        path = DOCS / style["ttf"].lstrip("/")
        if not path.is_file():
            raise SystemExit(f"build_packs: missing {path}")
        suffix = "-italic" if style.get("style") == "italic" else ""
        name = f'{family["slug"]}-{style["weight"]}{suffix}{path.suffix}'
        entries.append({"name": name, "data": path.read_bytes(), "style": style})

    files = sorted(
        ({"path": entry["name"], "bytes": len(entry["data"]), "sha256": digest(entry["data"])}
         for entry in entries),
        key=lambda row: row["path"],
    )
    version = digest("".join(row["path"] + row["sha256"] for row in files).encode())[:8]

    manifest = {
        "schema": 1,
        "kind": "font",
        "slug": family["slug"],
        "family": family["family"],
        "version": version,
        "licence": family["licence"],
        "source": family["source"],
        "note": family.get("note", ""),
        "key": KEY_ID,
        "alg": SIGN_ALG,
        "styles": [
            {
                "name": face_name(entry["style"]),
                "weight": entry["style"]["weight"],
                "style": entry["style"].get("style", "normal"),
                "file": entry["name"],
            }
            for entry in entries
        ],
        "files": files,
    }

    validate(manifest, entries)

    manifest_bytes = json.dumps(manifest, indent=2).encode("utf-8") + b"\n"
    existing = (existing_dir / f'{family["slug"]}-{version}.addpack'
                if existing_dir else None)
    signature = reusable_signature(existing, manifest_bytes)
    if signature is None:
        signature = sign(manifest_bytes)
    if not signature_ok(manifest_bytes, signature):
        raise SystemExit("build_packs: the signature does not verify against "
                         "tools/pack-signing-public.pem")

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        info = zipfile.ZipInfo("manifest.json", STAMP)
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, manifest_bytes)
        info = zipfile.ZipInfo("manifest.sig", STAMP)
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, signature)
        for entry in entries:
            info = zipfile.ZipInfo(entry["name"], STAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, entry["data"])

    data = buffer.getvalue()
    with zipfile.ZipFile(io.BytesIO(data)) as written:
        names = written.namelist()
        if names != ["manifest.json", "manifest.sig"] + [entry["name"] for entry in entries]:
            raise SystemExit("build_packs: the archive does not match the manifest order")
        if any(name.startswith(".") or "/" in name or "\\" in name for name in names):
            raise SystemExit("build_packs: an archive entry is not flat and plain")
    return {
        "slug": family["slug"],
        "family": family["family"],
        "version": version,
        "faces": len(entries),
        "styles": [face_name(entry["style"]) for entry in entries],
        "licence": family["licence"],
        "source": family["source"],
        "note": family.get("note", ""),
        "file": f'{family["slug"]}-{version}.addpack',
        "bytes": len(data),
        "sha256": digest(data),
        "key": KEY_ID,
        "data": data,
    }


def replace_section(text: str, markup: str) -> str:
    """Swap the generated packs block on the download page, leaving the rest alone."""
    start = text.find(PACKS_START)
    end = text.find(PACKS_END)
    if start < 0 or end < start:
        return text
    return text[:start] + markup + text[end + len(PACKS_END):]


def packs_section(packs: list[dict], total: int) -> str:
    # The one place packs are taken: cards on the download page, bytes under /packs/.
    faces = sum(pack["faces"] for pack in packs)
    cards = "\n".join(
        '      <a class="card" href="../packs/%s" download>\n'
        '        <span class="kind">Signed pack · %d faces</span>\n'
        '        <h3>%s</h3>\n'
        '        <p>%s · %s · %s</p>\n'
        '        <span class="tag"><span class="go">Download .addpack</span></span>\n'
        '      </a>'
        % (pack["file"], pack["faces"], html.escape(pack["family"]),
           html.escape(pack["note"] or pack["source"]), human(pack["bytes"]),
           html.escape(pack["licence"]))
        for pack in packs
    )
    return (
        PACKS_START + "\n"
        '  <section id="font-packs">\n'
        '    <h2>Font packs</h2>\n'
        '    <p class="kicker">Signed files for the editor · %d packs · %d faces · %s</p>\n'
        '    <p class="fine">Open <b>Font packs</b> in ADD’s editor and it fetches the list from this site; a file below is the same pack, for a manual import. Nothing is installed until you choose one.</p>\n'
        '    <div class="cards">\n'
        '%s\n'
        '    </div>\n'
        '    <p class="fine">Each pack is signed with ECDSA P-256. ADD checks that signature and a SHA-256 for every file before it installs anything, so a broken or altered download is refused. The version comes from the file hashes, so a changed typeface gets a new file name.</p>\n'
        '  </section>\n'
        '  ' + PACKS_END
    ) % (len(packs), faces, human(total), cards)


def collect() -> tuple[list[dict], str, str]:
    payload = json.loads(FONTS.read_text(encoding="utf-8"))
    families = payload.get("families") or []
    if not families:
        raise SystemExit("build_packs: docs/data/fonts.json has no families")
    packs = [build(family, OUT) for family in families]
    total = sum(pack["bytes"] for pack in packs)
    listing = {
        "generated": date.today().isoformat(),
        "count": len(packs),
        "faces": sum(pack["faces"] for pack in packs),
        "bytes": total,
        "key": KEY_ID,
        "packs": [{key: pack[key] for key in
                   ("slug", "family", "version", "faces", "styles", "licence", "source",
                    "note", "file", "bytes", "sha256", "key")} for pack in packs],
    }
    return packs, json.dumps(listing, indent=2) + "\n", packs_section(packs, total)


def write(target: Path) -> None:
    packs, listing, markup = collect()
    target.mkdir(parents=True, exist_ok=True)
    keep = set()
    for pack in packs:
        keep.add(pack["file"])
        (target / pack["file"]).write_bytes(pack["data"])
    (target / "packs.json").write_text(listing, encoding="utf-8")
    text = DOWNLOAD.read_text(encoding="utf-8")
    updated = replace_section(text, markup)
    if updated == text:
        raise SystemExit("build_packs: docs/download/index.html has no packs markers")
    DOWNLOAD.write_text(updated, encoding="utf-8")
    retired = target / "index.html"
    if retired.exists():
        retired.unlink()
    for old in target.glob("*.addpack"):
        if old.name not in keep:
            old.unlink()


def check() -> int:
    packs, listing, markup = collect()
    problems = []
    expected = {pack["file"]: pack["data"] for pack in packs}
    for name, data in expected.items():
        path = OUT / name
        if not path.is_file():
            problems.append(f"missing {path.relative_to(ROOT)}")
        elif path.read_bytes() != data:
            problems.append(f"stale {path.relative_to(ROOT)}")
    page_text = DOWNLOAD.read_text(encoding="utf-8") if DOWNLOAD.is_file() else ""
    if not page_text:
        problems.append("missing docs/download/index.html")
    elif replace_section(page_text, markup) != page_text:
        problems.append("stale docs/download/index.html (the packs section)")
    if (OUT / "index.html").exists():
        problems.append("docs/packs/index.html is retired; packs live on the download page")
    if not (OUT / "packs.json").is_file():
        problems.append("missing docs/packs/packs.json")
    else:
        on_disk = json.loads((OUT / "packs.json").read_text(encoding="utf-8"))
        expected_list = json.loads(listing)
        on_disk.pop("generated", None)
        expected_list.pop("generated", None)
        if on_disk != expected_list:
            problems.append("stale docs/packs/packs.json")
    for extra in OUT.glob("*.addpack"):
        if extra.name not in expected:
            problems.append(f"orphan {extra.relative_to(ROOT)}")
    if problems:
        print("build_packs: docs/packs is stale")
        for problem in problems:
            print(" ", problem)
        print("Run: python3 tools/build_packs.py")
        return 1
    print(f"build_packs: {len(packs)} packs, {sum(p['faces'] for p in packs)} faces, "
          f"{human(sum(p['bytes'] for p in packs))} — OK")
    return 0


def main() -> int:
    if "--check" in sys.argv:
        return check()
    write(OUT)
    packs, _, _ = collect()
    print(f"build_packs: {len(packs)} packs, {sum(pack['faces'] for pack in packs)} faces, "
          f"{human(sum(pack['bytes'] for pack in packs))} in docs/packs/, "
          "listed on docs/download/")
    for pack in packs:
        print(f"  {pack['file']:<32} {pack['faces']:>2} faces  {human(pack['bytes']):>7}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
