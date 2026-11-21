import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH } from "./geometry";
import type { Viewport } from "../types";

export interface Point { x: number; y: number }
export interface Size { width: number; height: number }
export const MIN_ZOOM = 0.1;
export const MAX_ZOOM = 8;
const FIT_PADDING = 96;

const finite = (value: number, fallback: number) => (Number.isFinite(value) ? value : fallback);
const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

export function clampZoom(zoom: number): number {
  return clamp(finite(zoom, 1), MIN_ZOOM, MAX_ZOOM);
}

export function clampViewport(viewport: Viewport): Viewport {
  return {
    zoom: clampZoom(viewport.zoom),
    panX: finite(viewport.panX, 0),
    panY: finite(viewport.panY, 0),
  };
}

export function fitViewport(container: Size, padding = FIT_PADDING): Viewport {
  const zoom = clamp(
    Math.min(
      (container.width - padding) / ARTBOARD_WIDTH,
      (container.height - padding) / ARTBOARD_HEIGHT,
    ),
    MIN_ZOOM,
    MAX_ZOOM,
  );
  return { zoom: Number.isFinite(zoom) ? zoom : MIN_ZOOM, panX: 0, panY: 0 };
}

export function docToScreen(point: Point, viewport: Viewport, container: Size): Point {
  return {
    x: container.width / 2 + (point.x - ARTBOARD_WIDTH / 2) * viewport.zoom + viewport.panX,
    y: container.height / 2 + (point.y - ARTBOARD_HEIGHT / 2) * viewport.zoom + viewport.panY,
  };
}

export function screenToDoc(point: Point, viewport: Viewport, container: Size): Point {
  return {
    x: (point.x - container.width / 2 - viewport.panX) / viewport.zoom + ARTBOARD_WIDTH / 2,
    y: (point.y - container.height / 2 - viewport.panY) / viewport.zoom + ARTBOARD_HEIGHT / 2,
  };
}

export function zoomAt(viewport: Viewport, pointer: Point, nextZoom: number, container: Size): Viewport {
  const doc = screenToDoc(pointer, viewport, container);
  const zoom = clampZoom(nextZoom);
  return {
    zoom,
    panX: pointer.x - container.width / 2 - (doc.x - ARTBOARD_WIDTH / 2) * zoom,
    panY: pointer.y - container.height / 2 - (doc.y - ARTBOARD_HEIGHT / 2) * zoom,
  };
}
