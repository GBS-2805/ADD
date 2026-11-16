#!/usr/bin/env python3
"""Compute effect-region drag metrics from the Playwright matrix captures."""
import json
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "artifacts/twin/effect-audit-deployed/drag"
ARTBOARD = {"x": 26, "y": 192.578125, "width": 338, "height": 441.828125}
THRESHOLD_CHANGED = 1.0
THRESHOLD_MEAN = 0.25
THRESHOLD_BOX = 1.0
PHASES = ["mid-1", "mid-2", "mid-3", "after"]


def crop_region(image, layer_box):
    x = round(layer_box["x"] - ARTBOARD["x"])
    y = round(layer_box["y"] - ARTBOARD["y"])
    width = round(layer_box["width"])
    height = round(layer_box["height"])
    return image.crop((x, y, x + width, y + height))


def difference(before, after):
    diff = ImageChops.difference(before, after)
    changed = sum(1 for pixel in diff.getdata() if max(pixel) >= 8) / (diff.width * diff.height) * 100
    mean = sum(ImageStat.Stat(diff).mean) / 3
    return round(changed, 3), round(mean, 3)


def max_box_delta(*deltas):
    values = [abs(value) for delta in deltas if delta for value in delta.values()]
    return round(max(values), 3) if values else None


def layer_shift(baseline_box, frame_box):
    return {
        key: round(frame_box[key] - baseline_box[key], 3)
        for key in ("x", "y", "width", "height")
    }


combined = {
    "generatedAt": datetime.now(timezone.utc).isoformat(),
    "baseUrl": None,
    "viewport": None,
    "drag": None,
    "artboardBox": ARTBOARD,
    "thresholds": {"changedPercent": THRESHOLD_CHANGED, "meanChannel": THRESHOLD_MEAN, "boxDeltaPx": THRESHOLD_BOX},
    "layers": {},
    "errors": [],
}

for layer in ("text", "shape", "image"):
    matrix_path = OUT / f"matrix-{layer}.json"
    data = json.loads(matrix_path.read_text())
    if combined["baseUrl"] is None:
        combined["baseUrl"] = data["baseUrl"]
        combined["viewport"] = data["viewport"]
        combined["drag"] = data["drag"]
    combined["errors"].extend(data.get("errors", []))
    baseline_mid = [
        {**frame, "phase": f"mid-{index + 1}"}
        for index, frame in enumerate(data["baseline"]["mid"])
    ]
    baseline_after = {**data["baseline"]["after"], "phase": "after"}
    baseline_images = {
        **{frame["phase"]: Image.open(OUT / frame["file"]).convert("RGB") for frame in baseline_mid},
        "after": Image.open(OUT / baseline_after["file"]).convert("RGB"),
    }
    baseline_p0 = Image.open(OUT / f"{layer}/baseline-p0.png").convert("RGB")
    static_p0 = Image.open(OUT / f"{layer}/baseline-p0.png").convert("RGB")

    rows = []
    for row in data["rows"]:
        static_image = Image.open(OUT / f"{layer}/{row['key']}-static.png").convert("RGB")
        static_before = crop_region(static_p0, row["start"])
        static_after = crop_region(static_image, row["start"])
        static_changed, static_mean = difference(static_before, static_after)
        frames = {}
        for frame in row["frames"] + [row["after"]]:
            phase = frame["phase"]
            baseline = baseline_images[phase]
            effect_image = Image.open(OUT / frame["file"]).convert("RGB")
            before = crop_region(baseline, frame["layerBox"])
            after = crop_region(effect_image, frame["layerBox"])
            changed, mean = difference(before, after)
            whole_changed, whole_mean = difference(baseline, effect_image)
            baseline_frame = next(
                (item for item in [*baseline_mid, baseline_after] if item["phase"] == phase),
                None,
            )
            position_delta = layer_shift(baseline_frame["layerBox"], frame["layerBox"])
            box_delta = frame["delta"]
            frames[phase] = {
                "changed": changed,
                "mean": mean,
                "wholeChanged": whole_changed,
                "wholeMean": whole_mean,
                "boxDelta": box_delta,
                "maxBoxDelta": max_box_delta(box_delta),
                "baselinePositionDelta": position_delta,
                "maxBaselinePositionDelta": max_box_delta(position_delta),
                "exists": bool(frame["effect"].get("exists")),
                "visible": bool(frame["effect"].get("visible")),
                "dragging": bool(frame.get("dragging")),
                "effectSurface": frame["effect"].get("surface"),
                "canvasSize": frame["effect"].get("canvasSize"),
            }
        frame_pass = all(
            metrics["changed"] >= THRESHOLD_CHANGED
            and metrics["mean"] >= THRESHOLD_MEAN
            and metrics["maxBoxDelta"] is not None
            and metrics["maxBoxDelta"] <= THRESHOLD_BOX
            and metrics["maxBaselinePositionDelta"] <= THRESHOLD_BOX
            and metrics["exists"]
            and metrics["visible"]
            for metrics in frames.values()
        )
        rows.append({
            "key": row["key"],
            "label": row["label"],
            "plugin": row["plugin"],
            "staticChanged": static_changed,
            "staticMean": static_mean,
            "frames": frames,
            "worstMidChanged": min(frames[phase]["changed"] for phase in ("mid-1", "mid-2", "mid-3")),
            "pass": frame_pass,
        })

    mid_values = [(row["frames"][phase]["changed"], row["key"], phase) for row in rows for phase in ("mid-1", "mid-2", "mid-3")]
    after_values = [(row["frames"]["after"]["changed"], row["key"]) for row in rows]
    box_values = [(row["frames"][phase]["maxBoxDelta"], row["key"], phase) for row in rows for phase in PHASES]
    worst_mid = min(mid_values)
    worst_after = min(after_values)
    worst_box = max(box_values)
    layer_result = {
        "combos": len(rows),
        "passed": sum(1 for row in rows if row["pass"]),
        "allPass": all(row["pass"] for row in rows),
        "worstMid": {"changed": worst_mid[0], "key": worst_mid[1], "phase": worst_mid[2]},
        "worstAfter": {"changed": worst_after[0], "key": worst_after[1]},
        "worstBoxDelta": {"delta": worst_box[0], "key": worst_box[1], "phase": worst_box[2]},
        "baselineDrag": {
            "p0": data["baseline"]["p0"],
            "p1": data["baseline"]["mid"][0]["layerBox"],
            "after": data["baseline"]["after"]["layerBox"],
            "effectMountedDuringBaseline": any(frame["effect"]["exists"] for frame in baseline_mid + [baseline_after]),
        },
        "rows": rows,
    }
    combined["layers"][layer] = layer_result
    print(f"{layer}: {layer_result['passed']}/{layer_result['combos']} pass, worst mid {worst_mid[0]}% ({worst_mid[1]} {worst_mid[2]}), worst after {worst_after[0]}%, box drift {worst_box[0]}px")

combined["summary"] = {
    "combinations": sum(layer["combos"] for layer in combined["layers"].values()),
    "passed": sum(layer["passed"] for layer in combined["layers"].values()),
    "allPass": all(layer["allPass"] for layer in combined["layers"].values()),
    "allFramesChangedFromBaseline": all(
        row["frames"][phase]["changed"] >= THRESHOLD_CHANGED
        for layer in combined["layers"].values()
        for row in layer["rows"]
        for phase in PHASES
    ),
    "noFrameRevertedToBaseline": all(
        row["frames"][phase]["changed"] >= THRESHOLD_CHANGED
        for layer in combined["layers"].values()
        for row in layer["rows"]
        for phase in PHASES
    ),
    "allBoxesTracked": all(
        row["frames"][phase]["maxBoxDelta"] is not None and row["frames"][phase]["maxBoxDelta"] <= THRESHOLD_BOX
        for layer in combined["layers"].values()
        for row in layer["rows"]
        for phase in PHASES
    ),
    "allEffectsMountedAndVisible": all(
        row["frames"][phase]["exists"] and row["frames"][phase]["visible"]
        for layer in combined["layers"].values()
        for row in layer["rows"]
        for phase in PHASES
    ),
    "errors": combined["errors"],
}
(OUT / "drag-metrics.json").write_text(json.dumps(combined, indent=2) + "\n")
print(json.dumps({key: combined["summary"][key] for key in ("combinations", "passed", "allPass", "allFramesChangedFromBaseline", "noFrameRevertedToBaseline", "allBoxesTracked", "allEffectsMountedAndVisible")}, indent=2))
