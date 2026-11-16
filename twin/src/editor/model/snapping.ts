import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH, layerBounds, type Rect } from "./geometry";
import type { ResizeHandle } from "./geometry";
import type { TwinLayer } from "../../twin/types";

export interface SnapGuide {
  axis: "x" | "y";
  position: number;
}

export interface SnapAnchors {
  x: number[];
  y: number[];
}

export interface SnapResult {
  dx: number;
  dy: number;
  guides: SnapGuide[];
}

const unique = (values: number[]) =>
  [...new Set(values.map((value) => Math.round(value * 1e6) / 1e6))].sort((a, b) => a - b);

export function snapAnchors(layers: TwinLayer[], excludeIds: string[]): SnapAnchors {
  const excluded = new Set(excludeIds);
  const x = [0, ARTBOARD_WIDTH / 2, ARTBOARD_WIDTH];
  const y = [0, ARTBOARD_HEIGHT / 2, ARTBOARD_HEIGHT];
  for (const layer of layers) {
    if (!layer.visible || excluded.has(layer.id)) continue;
    const bounds = layerBounds(layer);
    x.push(bounds.x, bounds.x + bounds.width / 2, bounds.x + bounds.width);
    y.push(bounds.y, bounds.y + bounds.height / 2, bounds.y + bounds.height);
  }
  return { x: unique(x), y: unique(y) };
}

function snapAxis(
  edges: number[],
  anchors: number[],
  delta: number,
  threshold: number,
): { delta: number; guide: number | null } {
  if (threshold <= 0) return { delta, guide: null };
  let best: { distance: number; delta: number; anchor: number } | null = null;
  for (const anchor of anchors) {
    for (const edge of edges) {
      const distance = anchor - (edge + delta);
      if (Math.abs(distance) > threshold) continue;
      if (!best || Math.abs(distance) < Math.abs(best.distance)) {
        best = { distance, delta: delta + distance, anchor };
      }
    }
  }
  return best ? { delta: best.delta, guide: best.anchor } : { delta, guide: null };
}

export function snapMove(bounds: Rect, delta: { x: number; y: number }, anchors: SnapAnchors, threshold: number): SnapResult {
  const x = snapAxis(
    [bounds.x, bounds.x + bounds.width / 2, bounds.x + bounds.width],
    anchors.x,
    delta.x,
    threshold,
  );
  const y = snapAxis(
    [bounds.y, bounds.y + bounds.height / 2, bounds.y + bounds.height],
    anchors.y,
    delta.y,
    threshold,
  );
  const guides: SnapGuide[] = [];
  if (x.guide !== null) guides.push({ axis: "x", position: x.guide });
  if (y.guide !== null) guides.push({ axis: "y", position: y.guide });
  return { dx: x.delta, dy: y.delta, guides };
}

export function snapResizeRect(
  rect: Rect,
  handle: ResizeHandle,
  anchors: SnapAnchors,
  threshold: number,
): { rect: Rect; guides: SnapGuide[] } {
  const movesWest = handle.includes("w");
  const movesEast = handle.includes("e");
  const movesNorth = handle.includes("n");
  const movesSouth = handle.includes("s");
  let left = rect.x;
  let right = rect.x + rect.width;
  let top = rect.y;
  let bottom = rect.y + rect.height;
  const guides: SnapGuide[] = [];

  if (threshold > 0) {
    if (movesWest) {
      const nearest = anchors.x.reduce<number | null>((best, anchor) => {
        if (Math.abs(anchor - left) > threshold) return best;
        return best === null || Math.abs(anchor - left) < Math.abs(best - left) ? anchor : best;
      }, null);
      if (nearest !== null) {
        left = nearest;
        guides.push({ axis: "x", position: nearest });
      }
    }
    if (movesEast) {
      const nearest = anchors.x.reduce<number | null>((best, anchor) => {
        if (Math.abs(anchor - right) > threshold) return best;
        return best === null || Math.abs(anchor - right) < Math.abs(best - right) ? anchor : best;
      }, null);
      if (nearest !== null) {
        right = nearest;
        guides.push({ axis: "x", position: nearest });
      }
    }
    if (movesNorth) {
      const nearest = anchors.y.reduce<number | null>((best, anchor) => {
        if (Math.abs(anchor - top) > threshold) return best;
        return best === null || Math.abs(anchor - top) < Math.abs(best - top) ? anchor : best;
      }, null);
      if (nearest !== null) {
        top = nearest;
        guides.push({ axis: "y", position: nearest });
      }
    }
    if (movesSouth) {
      const nearest = anchors.y.reduce<number | null>((best, anchor) => {
        if (Math.abs(anchor - bottom) > threshold) return best;
        return best === null || Math.abs(anchor - bottom) < Math.abs(best - bottom) ? anchor : best;
      }, null);
      if (nearest !== null) {
        bottom = nearest;
        guides.push({ axis: "y", position: nearest });
      }
    }
  }

  return { rect: { x: left, y: top, width: right - left, height: bottom - top }, guides };
}
