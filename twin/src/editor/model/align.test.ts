import { describe, expect, it } from "vitest";
import { alignTransforms, distributeTransforms } from "./align";
import { layerBounds } from "./geometry";
import type { TwinLayer } from "../../twin/types";

const layer = (id: string, x: number, y: number, width = 100, height = 100, rotation = 0): TwinLayer => ({
  id,
  name: id,
  kind: "shape",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x, y, scaleX: width / 244, scaleY: height / 244, rotation },
  visible: true,
  shape: { variant: "block" },
  fill: "#111111",
});

const close = (actual: number, expected: number) => expect(actual).toBeCloseTo(expected, 5);

describe("align/distribute", () => {
  it("singleLayerAlignsToArtboard", () => {
    const one = layer("a", 100, 100, 50, 50);
    const next = alignTransforms([one], ["a"], "left");
    close(layerBounds({ ...one, transform: next.a }).x, 0);
  });

  it("twoLayersAlignToSelectionBounds", () => {
    const a = layer("a", 100, 100, 50, 50);
    const b = layer("b", 300, 100, 100, 50);
    const next = alignTransforms([a, b], ["a", "b"], "left");
    close(layerBounds({ ...a, transform: next.a }).x, layerBounds({ ...b, transform: next.b }).x);
  });

  it("alignLeftKeepsRelativeY", () => {
    const one = layer("a", 100, 180, 50, 50);
    const next = alignTransforms([one], ["a"], "left");
    close(next.a.y, 180);
  });

  it("alignCenterUsesRotatedBounds", () => {
    const one = layer("a", 100, 100, 100, 100, 45);
    const next = alignTransforms([one], ["a"], "hcenter");
    const moved = { ...one, transform: next.a };
    const bounds = layerBounds(moved);
    close(bounds.x + bounds.width / 2, 382.5);
    close(next.a.rotation, 45);
  });

  it("distributeThreeLayersEqualGaps", () => {
    const a = layer("a", 5, 100, 10, 10);
    const b = layer("b", 55, 100, 10, 10);
    const c = layer("c", 205, 100, 10, 10);
    const next = distributeTransforms([a, b, c], ["a", "b", "c"], "x");
    close(next.a.x, 5);
    close(next.c.x, 205);
    close(next.b.x, 105);
  });

  it("distributePreservesOuterLayers", () => {
    const a = layer("a", 5, 100, 10, 10);
    const b = layer("b", 55, 100, 10, 10);
    const c = layer("c", 205, 100, 10, 10);
    const next = distributeTransforms([a, b, c], ["a", "b", "c"], "x");
    close(next.a.x, 5);
    close(next.c.x, 205);
  });

  it("distributeNeedsThree", () => {
    expect(distributeTransforms([layer("a", 0, 0), layer("b", 100, 0)], ["a", "b"], "x")).toEqual({});
  });

  it("hiddenLayersStillAlignIfSelected", () => {
    const hidden = { ...layer("a", 100, 100, 50, 50), visible: false };
    const next = alignTransforms([hidden], ["a"], "right");
    const bounds = layerBounds({ ...hidden, transform: next.a });
    close(bounds.x + bounds.width, 765);
  });
});
