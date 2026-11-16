import { describe, expect, it } from "vitest";
import { snapAnchors, snapMove } from "./snapping";
import type { TwinLayer } from "../../twin/types";

const layer = (id: string, x: number, y: number, width = 100, height = 100, visible = true): TwinLayer => ({
  id,
  name: id,
  kind: "shape",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x, y, scaleX: width / 244, scaleY: height / 244, rotation: 0 },
  visible,
  shape: { variant: "block" },
  fill: "#111111",
});

const bounds = { x: 100, y: 100, width: 50, height: 50 };

describe("snapping", () => {
  it("moveSnapsLeftCenterRight", () => {
    expect(snapMove(bounds, { x: 8, y: 0 }, { x: [160], y: [] }, 6).dx).toBe(10);
    expect(snapMove(bounds, { x: 8, y: 0 }, { x: [130], y: [] }, 6).dx).toBe(5);
    expect(snapMove(bounds, { x: 8, y: 0 }, { x: [105], y: [] }, 6).dx).toBe(5);
  });

  it("moveSnapsTopMiddleBottom", () => {
    expect(snapMove(bounds, { x: 0, y: 8 }, { x: [], y: [160] }, 6).dy).toBe(10);
    expect(snapMove(bounds, { x: 0, y: 8 }, { x: [], y: [130] }, 6).dy).toBe(5);
    expect(snapMove(bounds, { x: 0, y: 8 }, { x: [], y: [105] }, 6).dy).toBe(5);
  });

  it("nearestTargetWins", () => {
    const result = snapMove(bounds, { x: 8, y: 0 }, { x: [107, 109], y: [] }, 6);
    expect(result.dx).toBe(7);
    expect(result.guides).toEqual([{ axis: "x", position: 107 }]);
  });

  it("thresholdScalesWithZoom", () => {
    expect(snapMove(bounds, { x: 8, y: 0 }, { x: [120], y: [] }, 2).dx).toBe(8);
    expect(snapMove(bounds, { x: 8, y: 0 }, { x: [120], y: [] }, 20).dx).toBe(20);
  });

  it("disabledReturnsRawDelta", () => {
    expect(snapMove(bounds, { x: 3, y: 4 }, { x: [104], y: [105] }, 0)).toEqual({
      dx: 3,
      dy: 4,
      guides: [],
    });
  });

  it("guidesAreDeduplicated", () => {
    const result = snapMove(bounds, { x: 8, y: 8 }, { x: [160, 160], y: [160, 160] }, 6);
    expect(result.guides).toHaveLength(2);
  });

  it("snapAnchorsIncludesArtboardAndLayerEdges", () => {
    const anchors = snapAnchors([layer("a", 100, 200), layer("b", 300, 300)], []);
    expect(anchors.x).toContain(0);
    expect(anchors.x).toContain(382.5);
    expect(anchors.x).toContain(765);
    expect(anchors.x).toContain(432.5);
    expect(anchors.x).toContain(532.5);
    expect(anchors.y).toContain(500);
  });

  it("snapAnchorsExcludesSelectedLayers", () => {
    const anchors = snapAnchors([layer("a", 100, 200), layer("b", 300, 300)], ["a"]);
    expect(anchors.x).not.toContain(432.5);
    expect(anchors.x).toContain(632.5);
  });
});
