import { describe, expect, it } from "vitest";
import { clampViewport, docToScreen, fitViewport, screenToDoc, zoomAt } from "./viewport";
import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH } from "./geometry";

const container = { width: 1000, height: 800 };
const close = (actual: number, expected: number) => expect(actual).toBeCloseTo(expected, 5);

describe("viewport", () => {
  it("fitCentersArtboardWithPadding", () => {
    const viewport = fitViewport(container);
    close(viewport.zoom, Math.min((1000 - 96) / ARTBOARD_WIDTH, (800 - 96) / ARTBOARD_HEIGHT));
    expect(viewport.panX).toBe(0);
    expect(viewport.panY).toBe(0);
    const center = docToScreen({ x: ARTBOARD_WIDTH / 2, y: ARTBOARD_HEIGHT / 2 }, viewport, container);
    close(center.x, 500);
    close(center.y, 400);
  });

  it("fitClampsZoom", () => {
    expect(fitViewport({ width: 20, height: 20 }).zoom).toBe(0.1);
    expect(fitViewport({ width: 100000, height: 100000 }).zoom).toBe(8);
  });

  it("zoomAtPreservesDocPoint", () => {
    const viewport = fitViewport(container);
    const pointer = { x: 740, y: 280 };
    const before = screenToDoc(pointer, viewport, container);
    const next = zoomAt(viewport, pointer, 2.4, container);
    const after = screenToDoc(pointer, next, container);
    close(after.x, before.x);
    close(after.y, before.y);
    close(next.zoom, 2.4);
  });

  it("screenDocRoundTrip", () => {
    const viewport = { zoom: 1.7, panX: 38, panY: -24 };
    for (const point of [{ x: 0, y: 0 }, { x: 382.5, y: 500 }, { x: 765, y: 1000 }]) {
      const screen = docToScreen(point, viewport, container);
      const back = screenToDoc(screen, viewport, container);
      close(back.x, point.x);
      close(back.y, point.y);
    }
  });

  it("clampRejectsNaNAndOutOfRange", () => {
    expect(clampViewport({ zoom: Number.NaN, panX: Number.NaN, panY: Infinity })).toEqual({
      zoom: 1,
      panX: 0,
      panY: 0,
    });
    expect(clampViewport({ zoom: 0.001, panX: 10, panY: 20 }).zoom).toBe(0.1);
    expect(clampViewport({ zoom: 99, panX: -10, panY: 20 }).zoom).toBe(8);
  });
});
