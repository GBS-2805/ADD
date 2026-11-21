import { describe, expect, it } from "vitest";
import {
  changePositionAtReference,
  changeRotationAtReference,
  changeSizeAtReference,
  layerFrame,
  referencePoint,
  resizeTransform,
  rotateTransform,
  selectionBounds,
} from "./geometry";
import type { EditorLayer } from "../../editor-core/types";

const layer: EditorLayer = {
  id: "block",
  name: "Block",
  kind: "shape",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 100, y: 100, scaleX: 1, scaleY: 1, rotation: 0 },
  visible: true,
  shape: { variant: "block" },
  fill: "#111111",
};

const close = (actual: number, expected: number) => expect(actual).toBeCloseTo(expected, 5);
const frame = (transform = layer.transform) => layerFrame(layer, transform);
const point = (x: number, y: number) => ({ x, y });

describe("geometry selection and resize", () => {
  it("selectionBounds unions rotated bounds", () => {
    const second = { ...layer, id: "second", transform: { ...layer.transform, x: 300, y: 260 } };
    const bounds = selectionBounds([layer, second], ["block", "second"]);
    expect(bounds).not.toBeNull();
    close(bounds!.x, 360.5);
    close(bounds!.x + bounds!.width, 804.5);
  });

  it("eastHandleKeepsWestEdgeFixed", () => {
    const start = frame();
    const next = resizeTransform(layer, layer.transform, "e", point(200, 100), { fromCenter: false, keepAspect: false });
    const after = frame(next);
    close(after.x - after.width / 2, start.x - start.width / 2);
    close(after.y, start.y);
  });

  it("northHandleKeepsSouthEdgeFixed", () => {
    const start = frame();
    const next = resizeTransform(layer, layer.transform, "n", point(100, 20), { fromCenter: false, keepAspect: false });
    const after = frame(next);
    close(after.y + after.height / 2, start.y + start.height / 2);
  });

  it("cornerHandleKeepsOppositeCornerFixed", () => {
    const start = frame();
    const next = resizeTransform(layer, layer.transform, "se", point(220, 230), { fromCenter: false, keepAspect: false });
    const after = frame(next);
    const beforeCorner = referencePoint(start, "nw");
    const afterCorner = referencePoint(after, "nw");
    close(afterCorner.x, beforeCorner.x);
    close(afterCorner.y, beforeCorner.y);
  });

  it("shiftKeepsAspect", () => {
    const next = resizeTransform(layer, layer.transform, "se", point(320, 160), { fromCenter: false, keepAspect: true });
    const after = frame(next);
    close(after.width, after.height);
    expect(after.width).toBeGreaterThan(frame().width);
  });

  it("altResizesFromCenter", () => {
    const next = resizeTransform(layer, layer.transform, "e", point(220, 100), { fromCenter: true, keepAspect: false });
    close(next.x, 100);
    close(layerFrame(layer, next).width, 240);
  });

  it("minSizeClampsAtFour", () => {
    const next = resizeTransform(layer, layer.transform, "w", point(220, 100), { fromCenter: false, keepAspect: false });
    close(layerFrame(layer, next).width, 4);
  });

  it("resizeAt37DegreesKeepsOppositeCornerFixed", () => {
    const rotated = { ...layer, transform: { ...layer.transform, rotation: 37 } };
    const start = layerFrame(rotated);
    const startCorner = referencePoint(start, "nw");
    const next = resizeTransform(rotated, rotated.transform, "se", point(280, 280), { fromCenter: false, keepAspect: false });
    const afterCorner = referencePoint(layerFrame(rotated, next), "nw");
    close(afterCorner.x, startCorner.x);
    close(afterCorner.y, startCorner.y);
  });

  it("rotateAroundCenterKeepsCenter", () => {
    const next = rotateTransform(layer, layer.transform, point(200, 100), { snap: false });
    close(next.x, 100);
    close(next.y, 100);
    close(next.rotation, 90);
  });

  it("shiftSnapsRotationTo15", () => {
    const theta = (37 * Math.PI) / 180;
    const pointer = point(100 + Math.sin(theta) * 100, 100 - Math.cos(theta) * 100);
    const next = rotateTransform(layer, layer.transform, pointer, { snap: true });
    expect(next.rotation % 15).toBe(0);
    expect(next.rotation).toBe(30);
  });
});

describe("geometry reference point changes", () => {
  it("changeSizeAtReference keeps the reference point fixed", () => {
    const start = layerFrame(layer);
    const fixed = referencePoint(start, "nw");
    const next = changeSizeAtReference(layer, { width: 400, height: 300 }, "nw", false);
    const after = referencePoint(layerFrame(layer, next), "nw");
    close(after.x, fixed.x);
    close(after.y, fixed.y);
  });

  it("changePositionAtReference moves the reference point to the target", () => {
    const next = changePositionAtReference(layer, point(300, 400), "se");
    close(referencePoint(layerFrame(layer, next), "se").x, 300);
    close(referencePoint(layerFrame(layer, next), "se").y, 400);
  });

  it("changeRotationAtReference rotates around the fixed point", () => {
    const fixed = referencePoint(layerFrame(layer), "sw");
    const next = changeRotationAtReference(layer, 30, "sw");
    const after = referencePoint(layerFrame(layer, next), "sw");
    close(after.x, fixed.x);
    close(after.y, fixed.y);
    close(next.rotation, 30);
  });
});
