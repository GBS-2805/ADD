import { describe, expect, it } from "vitest";
import { ARTBOARD_WIDTH, ARTBOARD_HEIGHT, EXPORT_SCALE } from "../editor/model/geometry";
import { migrateV1Document } from "./project";
import {
  ARTBOARD_DESIGN_HEIGHT,
  ARTBOARD_DESIGN_WIDTH,
  ARTBOARD_EXPORT_HEIGHT,
  ARTBOARD_EXPORT_WIDTH,
  PNG_SIGNATURE,
  blendOperation,
  buildArtboardRenderPlan,
  readPngInfo,
  renderArtboardPng,
} from "./pngExport";
import { BLEND_MODES, type EditorDocument, type EditorLayer } from "./types";

const makeDocument = (): EditorDocument => ({
  background: { kind: "solid", hex: "#fafaf7" },
  selectedLayerIds: ["headline"],
  layers: [
    {
      id: "headline",
      name: "Type / OPEN STUDIO",
      kind: "text",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: -8, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      text: { content: "OPEN\nSTUDIO", size: 68, align: "center", font: "instrument-serif" },
      fill: "#111111",
    },
    {
      id: "star",
      name: "Shape / Star",
      kind: "shape",
      effect: null,
      blend: "multiply",
      opacity: 0.9,
      transform: { x: -92, y: 86, scaleX: 1.05, scaleY: 1.05, rotation: -9 },
      visible: false,
      shape: { variant: "star" },
      fill: "#111111",
    },
    {
      id: "orbit",
      name: "Shape / Orbit",
      kind: "shape",
      effect: null,
      blend: "screen",
      opacity: 0.8,
      transform: { x: 96, y: 76, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      shape: { variant: "orbit" },
      fill: "#111111",
    },
  ],
});

function pngHeader(width: number, height: number): Uint8Array {
  const bytes = new Uint8Array(24);
  bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const view = new DataView(bytes.buffer);
  view.setUint32(8, 13);
  bytes.set([...Buffer.from("IHDR")], 12);
  view.setUint32(16, width);
  view.setUint32(20, height);
  return bytes;
}

function fakeCanvasFactory() {
  const calls: string[] = [];
  const canvases: Array<{ width: number; height: number }> = [];
  const createCanvas = (width: number, height: number) => {
    const canvas = {
      width,
      height,
      toBlob(callback: (blob: Blob | null) => void) {
        callback(new Blob([pngHeader(canvas.width, canvas.height).buffer as ArrayBuffer], { type: "image/png" }));
      },
    };
    const context = new Proxy({}, {
      get(target, property) {
        if (property === "createLinearGradient" || property === "createRadialGradient") {
          return () => ({ addColorStop: () => undefined });
        }
        if (property in target) return target[property as keyof typeof target];
        return (...args: unknown[]) => {
          calls.push(String(property));
          return args.length ? undefined : undefined;
        };
      },
      set(target, property, value) {
        (target as Record<PropertyKey, unknown>)[property] = value;
        return true;
      },
    });
    Object.assign(canvas, { getContext: () => context });
    canvases.push(canvas);
    return canvas;
  };
  return { createCanvas, calls, canvases };
}

describe("artboard PNG export", () => {
  it("serializes the visible document into the canonical artboard plan", () => {
    const plan = buildArtboardRenderPlan(makeDocument());
    expect(plan.width).toBe(ARTBOARD_EXPORT_WIDTH);
    expect(plan.height).toBe(ARTBOARD_EXPORT_HEIGHT);
    expect(plan.width / plan.height).toBeCloseTo(0.765, 5);
    expect(plan.designWidth).toBe(ARTBOARD_WIDTH);
    expect(plan.designWidth).toBe(765);
    expect(plan.designHeight).toBe(ARTBOARD_HEIGHT);
    expect(plan.designHeight).toBe(1000);
    expect(plan.scale).toBe(EXPORT_SCALE);
    expect(plan.scale).toBe(2);
    expect(plan.filename).toBe("open-studio.png");
    expect(plan.layers.map((layer) => layer.id)).toEqual(["headline", "orbit"]);
    expect(plan.layers[1].blend).toBe("screen");
    expect(plan.layers[1].opacity).toBe(0.8);
    expect(plan.layers[1].transform).toEqual({ x: 96, y: 76, scaleX: 1, scaleY: 1, rotation: 0 });
  });

  it("reads and rejects PNG headers", () => {
    expect(readPngInfo(pngHeader(1530, 2000))).toEqual({
      width: 1530,
      height: 2000,
      signature: PNG_SIGNATURE,
    });
    expect(readPngInfo(new Uint8Array([1, 2, 3]))).toBeNull();
    const badSignature = pngHeader(1530, 2000);
    badSignature[1] = 0;
    expect(readPngInfo(badSignature)).toBeNull();
    const noDimensions = pngHeader(0, 0);
    expect(readPngInfo(noDimensions)).toBeNull();
  });

  it("maps every CSS blend mode to a canvas composite operation", () => {
    for (const mode of BLEND_MODES) {
      expect(blendOperation(mode)).toBe(mode === "normal" ? "source-over" : mode);
    }
  });

  it("renders visible layers and returns the encoded PNG metadata", async () => {
    const fake = fakeCanvasFactory();
    const result = await renderArtboardPng(makeDocument(), {
      createCanvas: fake.createCanvas as unknown as (width: number, height: number) => HTMLCanvasElement,
      loadImage: async () => null,
    });

    expect(result.width).toBe(ARTBOARD_EXPORT_WIDTH);
    expect(result.height).toBe(ARTBOARD_EXPORT_HEIGHT);
    expect(result.signature).toBe(PNG_SIGNATURE);
    expect(result.filename).toBe("open-studio.png");
    expect(result.blob.type).toBe("image/png");
    expect(result.bytes).toBeGreaterThan(0);
    expect(fake.canvases).toHaveLength(2);
    expect(fake.canvases.map((canvas) => [canvas.width, canvas.height])).toEqual([
      [ARTBOARD_EXPORT_WIDTH, ARTBOARD_EXPORT_HEIGHT],
      [ARTBOARD_EXPORT_WIDTH, ARTBOARD_EXPORT_HEIGHT],
    ]);
    expect(fake.calls).toContain("fillText");
    expect(fake.calls).toContain("arc");
    expect(fake.calls).toContain("drawImage");
  });

  it("loads and draws a visible PNG layer", async () => {
    const document = makeDocument();
    const imageLayer: EditorLayer = {
      id: "png-layer",
      name: "PNG layer",
      kind: "image",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      image: { assetId: "png-layer", src: "blob:png-layer", width: 20, height: 10 },
    };
    document.layers.push(imageLayer);
    const fake = fakeCanvasFactory();
    const loaded: string[] = [];
    const result = await renderArtboardPng(document, {
      createCanvas: fake.createCanvas as unknown as (width: number, height: number) => HTMLCanvasElement,
      loadImage: async (src) => {
        loaded.push(src);
        return { width: 20, height: 10 } as unknown as CanvasImageSource;
      },
    });

    expect(loaded).toEqual(["blob:png-layer"]);
    expect(result.width).toBe(ARTBOARD_EXPORT_WIDTH);
    expect(fake.calls).toContain("drawImage");
  });

  it("keeps a v1 image at the same physical export size", () => {
    const migrated = migrateV1Document({
      background: null,
      selectedLayerId: "png",
      layers: [
        {
          id: "png",
          name: "PNG",
          kind: "image",
          effect: null,
          blend: "normal",
          opacity: 1,
          transform: { x: 0, y: 0, scale: 1, rotation: 0 },
          visible: true,
          image: { assetId: "png", src: "data:image/png;base64,AA==", width: 10, height: 5 },
        },
      ],
    });
    const plan = buildArtboardRenderPlan(migrated);
    expect((migrated.layers[0].image?.width ?? 0) * plan.scale).toBeCloseTo(10 * (1530 / 338), 5);
    expect((migrated.layers[0].image?.height ?? 0) * plan.scale).toBeCloseTo(5 * (1530 / 338), 5);
  });

  it("exports an empty document background at the canonical PNG size", async () => {
    const fake = fakeCanvasFactory();
    const result = await renderArtboardPng(
      { background: { kind: "solid", hex: "#fafaf7" }, layers: [], selectedLayerIds: [] },
      {
        createCanvas: fake.createCanvas as unknown as (width: number, height: number) => HTMLCanvasElement,
        loadImage: async () => null,
      },
    );
    expect(result.width).toBe(1530);
    expect(result.height).toBe(2000);
    expect(fake.canvases[0]).toMatchObject({ width: 1530, height: 2000 });
  });
});
