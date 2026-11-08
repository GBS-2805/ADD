#!/usr/bin/env python3
"""Build the engine's local icon catalog from the site's icon data.

The site keeps each brand as a names-first index (``docs/data/icons.json``) plus
glyph chunks (``docs/data/icons/1.json`` ...).  This script merges both, keeps the
geometry and paint that the engine can draw, and writes one compact file per brand
plus a search index into the engine assets.

Only path geometry is emitted.  The four shipped sets use ``<path>`` inside
``<g>`` and nothing else, but the converter also understands the basic SVG
shapes and the usual affine transforms so a future set does not silently lose
glyphs.  Anything it cannot represent is counted and reported by name; the build
fails rather than shipping a catalog with quiet holes.

Usage:
    python3 tools/engine-spike/scripts/prepare_icons.py
    python3 tools/engine-spike/scripts/prepare_icons.py --check
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / "docs" / "data"
OUT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "icons"

BRANDS = [
    # (slug, label, style, index file)
    ("lineicons", "Lineicons", "", "icons.json"),
    ("coolicons", "Coolicons", "", "coolicons.json"),
    ("phosphor", "Phosphor", "Regular", "phosphor.json"),
    ("streamline", "Streamline", "Line", "streamline-line.json"),
]

IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
SVG_NS = "http://www.w3.org/2000/svg"
UNSUPPORTED_DRAW = {"use", "text", "image", "foreignObject"}
IGNORED = {"title", "desc", "metadata", "defs", "linearGradient", "radialGradient", "stop", "clipPath", "mask"}


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_brand(base: str) -> tuple[dict, list[dict]]:
    """Return the brand index and every glyph, merging chunks when present."""
    index = read_json(DATA / base)
    icons = list(index.get("icons") or [])
    for chunk_name in index.get("chunks") or []:
        chunk = read_json(DATA / chunk_name)
        icons.extend(chunk.get("icons") or [])
    if not icons and index.get("first"):
        icons.append(index["first"])
    return index, icons


def parse_attrs(text: str) -> dict[str, str]:
    return {k: v for k, v in re.findall(r'([\w:-]+)\s*=\s*"([^"]*)"', text or "")}


def floats(text: str) -> list[float]:
    return [float(value) for value in re.findall(r"[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?", text or "")]


def mat_mul(left, right):
    a1, b1, c1, d1, e1, f1 = left
    a2, b2, c2, d2, e2, f2 = right
    return (
        a1 * a2 + c1 * b2,
        b1 * a2 + d1 * b2,
        a1 * c2 + c1 * d2,
        b1 * c2 + d1 * d2,
        a1 * e2 + c1 * f2 + e1,
        b1 * e2 + d1 * f2 + f1,
    )


def parse_transform(text: str):
    matrix = IDENTITY
    for name, raw in re.findall(r"([a-zA-Z]+)\s*\(([^)]*)\)", text or ""):
        values = floats(raw)
        if name == "translate":
            tx = values[0] if values else 0.0
            ty = values[1] if len(values) > 1 else 0.0
            local = (1.0, 0.0, 0.0, 1.0, tx, ty)
        elif name == "scale":
            sx = values[0] if values else 1.0
            sy = values[1] if len(values) > 1 else sx
            local = (sx, 0.0, 0.0, sy, 0.0, 0.0)
        elif name == "rotate":
            angle = math.radians(values[0] if values else 0.0)
            cx = values[1] if len(values) > 2 else 0.0
            cy = values[2] if len(values) > 2 else 0.0
            cos_a = math.cos(angle)
            sin_a = math.sin(angle)
            rotate = (cos_a, sin_a, -sin_a, cos_a, 0.0, 0.0)
            local = mat_mul(mat_mul((1, 0, 0, 1, cx, cy), rotate), (1, 0, 0, 1, -cx, -cy))
        elif name == "matrix" and len(values) >= 6:
            local = tuple(values[:6])
        elif name == "skewX":
            local = (1.0, 0.0, math.tan(math.radians(values[0])), 1.0, 0.0, 0.0)
        elif name == "skewY":
            local = (1.0, math.tan(math.radians(values[0])), 0.0, 1.0, 0.0, 0.0)
        else:
            raise ValueError(f"unsupported transform {name}()")
        matrix = mat_mul(matrix, local)
    return matrix


def is_identity(matrix) -> bool:
    return all(abs(a - b) < 1e-9 for a, b in zip(matrix, IDENTITY))


def colour(value: str | None, fallback: str) -> str:
    if value is None:
        return fallback
    value = value.strip()
    if not value:
        return fallback
    if value.lower() in {"none", "transparent"}:
        return "none"
    if value.lower() == "currentcolor":
        return "currentColor"
    if value.startswith("url("):
        return value
    if value.startswith("#"):
        raw = value[1:]
        if len(raw) == 3:
            raw = "".join(ch * 2 for ch in raw)
        if len(raw) == 6:
            return "#" + raw.lower()
        return "#" + raw[:6].lower()
    named = {
        "black": "#000000",
        "white": "#ffffff",
        "red": "#ff0000",
        "green": "#008000",
        "blue": "#0000ff",
        "grey": "#808080",
        "gray": "#808080",
    }
    return named.get(value.lower(), "currentColor")


def shape_path(tag: str, a: dict[str, str]) -> str | None:
    def num(name: str, default: float = 0.0) -> float:
        try:
            return float(a.get(name, default))
        except (TypeError, ValueError):
            return default

    if tag == "path":
        return a.get("d")
    if tag == "rect":
        x, y, w, h = num("x"), num("y"), num("width"), num("height")
        if w <= 0 or h <= 0:
            return None
        rx, ry = num("rx"), num("ry")
        if rx <= 0 and ry > 0:
            rx = ry
        if ry <= 0 and rx > 0:
            ry = rx
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx <= 0:
            return f"M{x} {y}H{x + w}V{y + h}H{x}Z"
        return (
            f"M{x + rx} {y}H{x + w - rx}A{rx} {ry} 0 0 1 {x + w} {y + ry}"
            f"V{y + h - ry}A{rx} {ry} 0 0 1 {x + w - rx} {y + h}"
            f"H{x + rx}A{rx} {ry} 0 0 1 {x} {y + h - ry}"
            f"V{y + ry}A{rx} {ry} 0 0 1 {x + rx} {y}Z"
        )
    if tag in {"circle", "ellipse"}:
        cx, cy = num("cx"), num("cy")
        rx = num("r") if tag == "circle" else num("rx")
        ry = num("r") if tag == "circle" else num("ry")
        if rx <= 0 or ry <= 0:
            return None
        return (
            f"M{cx - rx} {cy}A{rx} {ry} 0 1 0 {cx + rx} {cy}"
            f"A{rx} {ry} 0 1 0 {cx - rx} {cy}Z"
        )
    if tag == "line":
        return f"M{num('x1')} {num('y1')}L{num('x2')} {num('y2')}"
    if tag in {"polyline", "polygon"}:
        points = a.get("points", "").strip().replace(",", " ")
        values = floats(points)
        if len(values) < 4:
            return None
        pairs = list(zip(values[0::2], values[1::2]))
        path = f"M{pairs[0][0]} {pairs[0][1]}" + "".join(f"L{x} {y}" for x, y in pairs[1:])
        return path + ("Z" if tag == "polygon" else "")
    return None


def gradient_stops(root: ET.Element) -> dict[str, str]:
    stops: dict[str, str] = {}
    for node in root.iter():
        tag = node.tag.split("}")[-1]
        if tag not in {"linearGradient", "radialGradient"}:
            continue
        identifier = node.attrib.get("id")
        if not identifier:
            continue
        for stop in node:
            if stop.tag.split("}")[-1] != "stop":
                continue
            style = stop.attrib.get("style", "")
            match = re.search(r"stop-color\s*:\s*([^;]+)", style)
            value = match.group(1) if match else stop.attrib.get("stop-color", "")
            if value:
                stops[identifier] = colour(value, "currentColor")
                break
    return stops


def convert_glyph(record: dict, brand: str, label: str, style: str) -> tuple[dict | None, str | None]:
    attrs = parse_attrs(record.get("attrs", ""))
    view = floats(attrs.get("viewBox", ""))
    if len(view) == 4 and view[2] > 0 and view[3] > 0:
        box = view
    else:
        width = floats(attrs.get("width", "24"))
        height = floats(attrs.get("height", "24"))
        if not width or not height:
            return None, "no viewBox"
        box = [0.0, 0.0, width[0], height[0]]
    if box[2] <= 0 or box[3] <= 0:
        return None, "empty viewBox"

    try:
        root = ET.fromstring(f'<svg xmlns="{SVG_NS}">{record.get("body", "")}</svg>')
    except ET.ParseError as error:
        return None, f"xml: {error}"

    gradients = gradient_stops(root)
    base = {
        "fill": colour(attrs.get("fill"), "#000000"),
        "stroke": colour(attrs.get("stroke"), "none"),
        "stroke-width": attrs.get("stroke-width", "1"),
        "fill-rule": attrs.get("fill-rule", "nonzero"),
        "opacity": attrs.get("opacity", "1"),
        "fill-opacity": attrs.get("fill-opacity", "1"),
        "stroke-opacity": attrs.get("stroke-opacity", "1"),
    }
    paths: list[dict] = []
    skipped_tags: set[str] = set()

    def inherit(parent: dict, a: dict[str, str]) -> dict:
        merged = dict(parent)
        for key in ("fill", "stroke"):
            if key in a:
                merged[key] = colour(a[key], parent[key])
        for key in ("stroke-width", "fill-rule", "fill-opacity", "stroke-opacity"):
            if key in a:
                merged[key] = a[key]
        if "opacity" in a:
            try:
                merged["opacity"] = str(float(parent.get("opacity", "1")) * float(a["opacity"]))
            except ValueError:
                pass
        return merged

    def resolve_gradient(value: str) -> str:
        match = re.match(r"url\(\s*#([^)\s]+)\s*\)", value)
        if not match:
            return value
        return gradients.get(match.group(1), "currentColor")

    def walk(node: ET.Element, inherited: dict, matrix, depth: int = 0) -> None:
        if depth > 12:
            return
        tag = node.tag.split("}")[-1]
        if tag in IGNORED:
            return
        if tag in UNSUPPORTED_DRAW:
            skipped_tags.add(tag)
            return
        if "clip-path" in node.attrib or "mask" in node.attrib:
            skipped_tags.add("clip/mask")
            return
        if tag in {"svg", "g"}:
            paint = inherit(inherited, node.attrib)
            local = parse_transform(node.attrib.get("transform", ""))
            child_matrix = mat_mul(matrix, local)
            for child in node:
                if tag == "svg" and child.tag.split("}")[-1] == "svg":
                    continue
                walk(child, paint, child_matrix, depth + 1)
            return
        path_data = shape_path(tag, node.attrib)
        if path_data is None:
            if tag not in {"svg"}:
                skipped_tags.add(tag)
            return
        paint = inherit(inherited, node.attrib)
        local = parse_transform(node.attrib.get("transform", ""))
        path_matrix = mat_mul(matrix, local)
        try:
            opacity = float(paint.get("opacity", "1"))
            fill_opacity = float(paint.get("fill-opacity", "1"))
            stroke_opacity = float(paint.get("stroke-opacity", "1"))
        except ValueError:
            opacity, fill_opacity, stroke_opacity = 1.0, 1.0, 1.0
        fill = resolve_gradient(paint.get("fill", "none"))
        stroke = resolve_gradient(paint.get("stroke", "none"))
        if fill == "none" and stroke == "none":
            return
        try:
            stroke_width = float(paint.get("stroke-width", "1"))
        except ValueError:
            stroke_width = 1.0
        entry = {
            "d": " ".join(str(path_data).split()),
            "f": fill,
            "s": stroke,
            "w": round(stroke_width, 4),
            "o": round(max(0.0, min(1.0, opacity * fill_opacity * stroke_opacity)), 4),
            "r": "evenodd" if paint.get("fill-rule") == "evenodd" else "nonzero",
        }
        if not is_identity(path_matrix):
            entry["m"] = [round(value, 6) for value in path_matrix]
        paths.append(entry)

    for child in root:
        walk(child, base, IDENTITY)

    if skipped_tags:
        return None, "unsupported " + ",".join(sorted(skipped_tags))
    if not paths:
        return None, "no paths"

    name = record.get("name", "").strip()
    if not name:
        return None, "no name"
    return {
        "id": f"{brand}/{name}",
        "name": name,
        "brand": brand,
        "brandLabel": label,
        "style": style,
        "category": record.get("cat", "") or "",
        "viewBox": [round(value, 4) for value in box],
        "paths": paths,
    }, None


def build() -> dict:
    index_entries = []
    brand_meta = []
    brand_files: dict[str, bytes] = {}
    total = 0
    excluded: list[str] = []
    body_bytes = 0

    for slug, label, style, filename in BRANDS:
        index, records = load_brand(filename)
        glyphs = []
        seen = set()
        base_ids = [record.get("name", "") for record in records]
        duplicate_names = {name for name in base_ids if base_ids.count(name) > 1}
        for record in records:
            body_bytes += len(record.get("body", ""))
            glyph, reason = convert_glyph(record, slug, label, style)
            if glyph is None:
                excluded.append(f"{slug}/{record.get('name', '?')}: {reason}")
                continue
            identifier = glyph["id"]
            if identifier in seen:
                identifier = f"{identifier}-{len(glyphs)}"
                glyph["id"] = identifier
            seen.add(identifier)
            glyphs.append(glyph)

        glyphs.sort(key=lambda item: item["name"].lower())
        payload = {
            "brand": slug,
            "label": label,
            "style": style,
            "licence": index.get("licence", ""),
            "source": index.get("source", ""),
            "count": len(glyphs),
            "icons": glyphs,
        }
        brand_files[slug] = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        cats = []
        counts: dict[str, int] = {}
        for glyph in glyphs:
            category = glyph["category"]
            if category:
                counts[category] = counts.get(category, 0) + 1
        for category in index.get("cat_order", []):
            if counts.get(category):
                cats.append({"name": category, "count": counts[category]})
        for category in sorted(counts):
            if category not in {item["name"] for item in cats}:
                cats.append({"name": category, "count": counts[category]})
        brand_meta.append(
            {
                "slug": slug,
                "label": label,
                "style": style,
                "licence": index.get("licence", ""),
                "source": index.get("source", ""),
                "count": len(glyphs),
                "cats": cats,
                "duplicates": sorted(duplicate_names),
            }
        )
        for glyph in glyphs:
            index_entries.append(
                {
                    "id": glyph["id"],
                    "name": glyph["name"],
                    "b": slug,
                    "c": glyph["category"],
                }
            )
        total += len(glyphs)
        print(f"{slug:10s} {len(glyphs):5d} glyphs  {len(brand_files[slug]):8,d} bytes  {index.get('licence', '')}")

    index_entries.sort(key=lambda item: (item["b"], item["name"].lower()))
    revision = hashlib.sha256()
    for slug, _, _, _ in BRANDS:
        revision.update(brand_files[slug])
    revision.update(str(len(index_entries)).encode())
    index_payload = {
        "rev": revision.hexdigest()[:12],
        "count": len(index_entries),
        "brands": brand_meta,
        "icons": index_entries,
    }
    index_bytes = json.dumps(index_payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

    print(f"source body     {body_bytes:9,d} bytes")
    print(f"index           {len(index_bytes):9,d} bytes")
    print(f"total           {total:5d} glyphs, {sum(len(v) for v in brand_files.values()) + len(index_bytes):9,d} bytes")
    if excluded:
        print(f"excluded        {len(excluded)}")
        for line in excluded[:20]:
            print("  " + line)
        if len(excluded) > 20:
            print(f"  ... {len(excluded) - 20} more")
    if total < 4300:
        raise SystemExit(f"catalog lost too many glyphs: {total} of 4373")
    return {**{f"{slug}.json": value for slug, value in brand_files.items()}, "index.json": index_bytes}


def write(files: dict[str, bytes]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, payload in files.items():
        (OUT / name).write_bytes(payload)


def check(files: dict[str, bytes]) -> None:
    problems = []
    for name, payload in files.items():
        path = OUT / name
        if not path.exists():
            problems.append(f"missing {name}")
        elif path.read_bytes() != payload:
            problems.append(f"stale {name}")
    if problems:
        raise SystemExit("icon catalog is stale: " + ", ".join(problems))
    print("icon catalog is current")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify the generated files are current")
    args = parser.parse_args()
    files = build()
    if args.check:
        check(files)
    else:
        write(files)
        print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
