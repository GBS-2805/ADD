#!/usr/bin/env python3
"""Generate the source torus ring for the engine-spike asset pipeline.

The ring stands upright in the XY plane so the default SceneView camera sees a
band rather than the top of a doughnut. The Node pipeline optimises and
simplifies it exactly like the gem.
"""
from __future__ import annotations

import json
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets-src"
SOURCE.mkdir(parents=True, exist_ok=True)

RINGS = 48
SIDES = 16
MAJOR = 0.72
MINOR = 0.16


def build() -> None:
    positions: list[float] = []
    normals: list[float] = []
    indices: list[int] = []

    for i in range(RINGS):
        u = 2.0 * math.pi * i / RINGS
        cu, su = math.cos(u), math.sin(u)
        for j in range(SIDES):
            v = 2.0 * math.pi * j / SIDES
            cv, sv = math.cos(v), math.sin(v)
            nx, ny, nz = cv * cu, cv * su, sv
            positions.extend(
                (
                    (MAJOR + MINOR * cv) * cu,
                    (MAJOR + MINOR * cv) * su,
                    MINOR * sv,
                )
            )
            normals.extend((nx, ny, nz))

    for i in range(RINGS):
        for j in range(SIDES):
            a = i * SIDES + j
            b = ((i + 1) % RINGS) * SIDES + j
            c = ((i + 1) % RINGS) * SIDES + (j + 1) % SIDES
            d = i * SIDES + (j + 1) % SIDES
            indices.extend((a, b, c, a, c, d))

    position_bytes = struct.pack("<" + "f" * len(positions), *positions)
    normal_bytes = struct.pack("<" + "f" * len(normals), *normals)
    index_bytes = struct.pack("<" + "H" * len(indices), *indices)

    def pad4(data: bytes) -> bytes:
        while len(data) % 4:
            data += b"\x00"
        return data

    position_bytes = pad4(position_bytes)
    normal_bytes = pad4(normal_bytes)
    index_bytes = pad4(index_bytes)
    binary = position_bytes + normal_bytes + index_bytes
    (SOURCE / "ring.bin").write_bytes(binary)

    xs = positions[0::3]
    ys = positions[1::3]
    zs = positions[2::3]
    doc = {
        "asset": {"version": "2.0", "generator": "ADD engine spike / make_ring.py"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0, "name": "Ring"}],
        "meshes": [{
            "name": "Ring",
            "primitives": [{
                "attributes": {"POSITION": 0, "NORMAL": 1},
                "indices": 2,
                "material": 0,
            }],
        }],
        "materials": [{
            "name": "Clay band",
            "pbrMetallicRoughness": {
                "baseColorFactor": [0.91, 0.42, 0.28, 1.0],
                "metallicFactor": 0.55,
                "roughnessFactor": 0.28,
            },
        }],
        "buffers": [{"uri": "ring.bin", "byteLength": len(binary)}],
        "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": len(position_bytes), "target": 34962},
            {"buffer": 0, "byteOffset": len(position_bytes), "byteLength": len(normal_bytes), "target": 34962},
            {"buffer": 0, "byteOffset": len(position_bytes) + len(normal_bytes), "byteLength": len(index_bytes), "target": 34963},
        ],
        "accessors": [
            {
                "bufferView": 0, "componentType": 5126, "count": RINGS * SIDES, "type": "VEC3",
                "min": [min(xs), min(ys), min(zs)], "max": [max(xs), max(ys), max(zs)],
            },
            {"bufferView": 1, "componentType": 5126, "count": RINGS * SIDES, "type": "VEC3"},
            {"bufferView": 2, "componentType": 5123, "count": len(indices), "type": "SCALAR"},
        ],
    }
    (SOURCE / "ring.gltf").write_text(json.dumps(doc, indent=2) + "\n")
    print(f"ring.gltf: {RINGS * SIDES} vertices, {len(indices) // 3} triangles")


if __name__ == "__main__":
    build()
