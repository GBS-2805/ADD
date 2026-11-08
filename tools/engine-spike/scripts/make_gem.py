#!/usr/bin/env python3
"""Generate the unoptimised source gem for the engine-spike asset pipeline.

The output is deliberately a plain .gltf + .bin pair. The Node step
(scripts/prepare-assets.mjs) is what runs glTF-Transform and meshoptimizer and
writes the small GLB that ships in app/src/main/assets/models.
"""
from __future__ import annotations

import json
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets-src"
SOURCE.mkdir(parents=True, exist_ok=True)

T = (1.0 + math.sqrt(5.0)) / 2.0
BASE_VERTICES = [
    (-1, T, 0), (1, T, 0), (-1, -T, 0), (1, -T, 0),
    (0, -1, T), (0, 1, T), (0, -1, -T), (0, 1, -T),
    (T, 0, -1), (T, 0, 1), (-T, 0, -1), (-T, 0, 1),
]
BASE_FACES = [
    (0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11),
    (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6), (7, 1, 8),
    (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9),
    (4, 9, 5), (2, 4, 11), (6, 2, 10), (8, 6, 7), (9, 8, 1),
]


def normalize(v: tuple[float, float, float]) -> tuple[float, float, float]:
    length = math.sqrt(sum(c * c for c in v)) or 1.0
    return (v[0] / length, v[1] / length, v[2] / length)


def subdivide(subdivisions: int):
    vertices = [normalize(v) for v in BASE_VERTICES]
    faces = list(BASE_FACES)
    for _ in range(subdivisions):
        cache: dict[tuple[int, int], int] = {}
        next_faces = []
        for a, b, c in faces:
            def midpoint(i: int, j: int) -> int:
                key = (min(i, j), max(i, j))
                if key not in cache:
                    va, vb = vertices[i], vertices[j]
                    vertices.append(normalize(tuple((va[k] + vb[k]) / 2 for k in range(3))))
                    cache[key] = len(vertices) - 1
                return cache[key]

            ab = midpoint(a, b)
            bc = midpoint(b, c)
            ca = midpoint(c, a)
            next_faces.extend(((a, ab, ca), (b, bc, ab), (c, ca, bc), (ab, bc, ca)))
        faces = next_faces
    return vertices, faces


def build():
    vertices, faces = subdivide(4)
    positions = []
    normals = []
    for x, y, z in vertices:
        # A slightly faceted, asymmetric gem silhouette rather than a sphere.
        scale = 1.0 + 0.10 * math.sin(5.0 * math.atan2(z, x)) * (1.0 - abs(y))
        positions.extend((x * scale, y * 0.86 * scale, z * scale))
        normals.extend((x, y, z))

    indices = [index for face in faces for index in face]
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
    (SOURCE / "gem.bin").write_bytes(binary)

    xs = positions[0::3]
    ys = positions[1::3]
    zs = positions[2::3]
    doc = {
        "asset": {"version": "2.0", "generator": "ADD engine spike / make_gem.py"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0, "name": "Gem"}],
        "meshes": [{
            "name": "Gem",
            "primitives": [{
                "attributes": {"POSITION": 0, "NORMAL": 1},
                "indices": 2,
                "material": 0,
            }],
        }],
        "materials": [{
            "name": "Mint glass",
            "pbrMetallicRoughness": {
                "baseColorFactor": [0.059, 0.749, 0.573, 1.0],
                "metallicFactor": 0.35,
                "roughnessFactor": 0.22,
            },
        }],
        "buffers": [{"uri": "gem.bin", "byteLength": len(binary)}],
        "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": len(position_bytes), "target": 34962},
            {"buffer": 0, "byteOffset": len(position_bytes), "byteLength": len(normal_bytes), "target": 34962},
            {"buffer": 0, "byteOffset": len(position_bytes) + len(normal_bytes), "byteLength": len(index_bytes), "target": 34963},
        ],
        "accessors": [
            {
                "bufferView": 0, "componentType": 5126, "count": len(vertices), "type": "VEC3",
                "min": [min(xs), min(ys), min(zs)], "max": [max(xs), max(ys), max(zs)],
            },
            {"bufferView": 1, "componentType": 5126, "count": len(vertices), "type": "VEC3"},
            {"bufferView": 2, "componentType": 5123, "count": len(indices), "type": "SCALAR"},
        ],
    }
    (SOURCE / "gem.gltf").write_text(json.dumps(doc, indent=2) + "\n")
    print(f"gem.gltf: {len(vertices)} vertices, {len(faces)} triangles")


if __name__ == "__main__":
    build()
