import { describe, expect, it } from "vitest";
import type { TwinLayer } from "../../twin/types";
import {
  ARTBOARD_HEIGHT,
  ARTBOARD_WIDTH,
  MAX_IMAGE_LAYERS,
  MAX_LAYERS,
  baseLayerSize,
  canAddLayer,
  fitImageInsideArtboard,
  layerBounds,
  layerFrame,
  normalizeAngle,
  referencePoint,
} from "./geometry";

const textLayer = (content = "OPEN\nSTUDIO", size = 154): TwinLayer => ({
  id: "headline",
  name: "Type / OPEN STUDIO",
  kind: "text",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
  visible: true,
  text: { content, size, align: "center", font: "instrument-serif" },
  fill: "#111111",
});

const shapeLayer = (variant: "star" | "orbit" | "block"): TwinLayer => ({
  id: `shape-${variant}`,
  name: `Shape / ${variant}`,
  kind: "shape",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
  visible: true,
  shape: { variant },
  fill: "#111111",
});

const imageLayer = (width: number, height: number, rotation = 0): TwinLayer => ({
  id: "image",
  name: "PNG layer",
  kind: "image",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation },
  visible: true,
  image: { assetId: "image", src: "blob:image", width, height },
});

describe("canonical layer geometry", () => {
  it("keeps the text base size stable", () => {
    expect(baseLayerSize(textLayer())).toEqual(expect.objectContaining({ width: 642 }));
    expect(baseLayerSize(textLayer()).height).toBeCloseTo(297.56, 5);
    expect(baseLayerSize(textLayer("ONE LINE", 154)).height).toBeCloseTo(171.28, 5);
  });

  it("keeps the shape base sizes stable", () => {
    expect(baseLayerSize(shapeLayer("star"))).toEqual({ width: 208, height: 208 });
    expect(baseLayerSize(shapeLayer("orbit"))).toEqual({ width: 222, height: 222 });
    expect(baseLayerSize(shapeLayer("block"))).toEqual({ width: 244, height: 244 });
  });

  it("returns an axis-aligned bounding box for a rotated frame", () => {
    const bounds = layerBounds(imageLayer(100, 50, 90));
    expect(bounds.x).toBeCloseTo(-25, 5);
    expect(bounds.y).toBeCloseTo(-50, 5);
    expect(bounds.width).toBeCloseTo(50, 5);
    expect(bounds.height).toBeCloseTo(100, 5);
  });

  it("keeps the frame center when scale changes", () => {
    const layer = textLayer();
    layer.transform = { x: 40, y: 50, scaleX: 2, scaleY: 0.5, rotation: 37 };
    const frame = layerFrame(layer);
    expect(frame.x).toBe(40);
    expect(frame.y).toBe(50);
    expect(frame.width).toBe(1284);
    expect(frame.height).toBeCloseTo(148.78, 5);
    expect(frame.rotation).toBe(37);
  });

  it("maps all nine reference points", () => {
    const frame = { x: 0, y: 0, width: 100, height: 50, rotation: 0 };
    expect(referencePoint(frame, "nw")).toEqual({ x: -50, y: -25 });
    expect(referencePoint(frame, "n")).toEqual({ x: 0, y: -25 });
    expect(referencePoint(frame, "ne")).toEqual({ x: 50, y: -25 });
    expect(referencePoint(frame, "w")).toEqual({ x: -50, y: 0 });
    expect(referencePoint(frame, "c")).toEqual({ x: 0, y: 0 });
    expect(referencePoint(frame, "e")).toEqual({ x: 50, y: 0 });
    expect(referencePoint(frame, "sw")).toEqual({ x: -50, y: 25 });
    expect(referencePoint(frame, "s")).toEqual({ x: 0, y: 25 });
    expect(referencePoint(frame, "se")).toEqual({ x: 50, y: 25 });
  });

  it("applies frame rotation to a reference point", () => {
    const point = referencePoint({ x: 0, y: 0, width: 100, height: 50, rotation: 90 }, "nw");
    expect(point.x).toBeCloseTo(25, 5);
    expect(point.y).toBeCloseTo(-50, 5);
  });

  it("normalizes angles into the half-open -180..180 range", () => {
    expect(normalizeAngle(0)).toBe(0);
    expect(normalizeAngle(180)).toBe(-180);
    expect(normalizeAngle(540)).toBe(-180);
    expect(normalizeAngle(-540)).toBe(-180);
    expect(normalizeAngle(37 + 720)).toBe(37);
  });
});

describe("layer limits and image fitting", () => {
  it("fits imported images inside the artboard", () => {
    const wide = fitImageInsideArtboard(2000, 1000);
    expect(wide.width).toBeCloseTo(ARTBOARD_WIDTH * 0.8, 8);
    expect(wide.height).toBeCloseTo(306, 8);
    const tall = fitImageInsideArtboard(1000, 2000);
    expect(tall.width).toBeCloseTo(400, 8);
    expect(tall.height).toBeCloseTo(ARTBOARD_HEIGHT * 0.8, 8);
  });

  it("keeps small images at their natural size", () => {
    expect(fitImageInsideArtboard(20, 10)).toEqual({ width: 20, height: 10 });
  });

  it("respects the total layer cap for text and shapes", () => {
    const layers = Array.from({ length: MAX_LAYERS }, (_, index) => ({
      ...shapeLayer("block"),
      id: `shape-${index}`,
    }));
    expect(canAddLayer(layers, "text")).toBe(false);
    expect(canAddLayer(layers, "shape")).toBe(false);
    expect(canAddLayer(layers.slice(1), "text")).toBe(true);
  });

  it("respects the total and image caps", () => {
    const images = Array.from({ length: MAX_IMAGE_LAYERS }, (_, index) => ({
      ...imageLayer(10, 10),
      id: `image-${index}`,
    }));
    expect(canAddLayer(images, "image")).toBe(false);
    expect(canAddLayer(images.slice(1), "image")).toBe(true);
    expect(canAddLayer([...images, ...Array.from({ length: MAX_LAYERS - MAX_IMAGE_LAYERS }, (_, index) => ({ ...shapeLayer("block"), id: `s-${index}` }))], "image")).toBe(false);
  });
});
