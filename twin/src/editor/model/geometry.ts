import type { TwinLayer } from "../../twin/types";

export const ARTBOARD_WIDTH = 765;
export const ARTBOARD_HEIGHT = 1000;
export const ARTBOARD_EXPORT_WIDTH = 1530;
export const ARTBOARD_EXPORT_HEIGHT = 2000;
export const EXPORT_SCALE = 2;
export const MAX_LAYERS = 64;
export const MAX_IMAGE_LAYERS = 12;

export interface Point { x: number; y: number }
export interface Size { width: number; height: number }
export interface Rect extends Point, Size {}
export interface Frame extends Rect { rotation: number }
export type ResizeHandle = "nw" | "n" | "ne" | "e" | "se" | "s" | "sw" | "w";
export type ReferencePoint = ResizeHandle | "c";

/** Text line height and padding are document-space values, not CSS guesses. */
const TEXT_LINE_HEIGHT = 0.82;
const TEXT_PADDING = 45;
const SHAPE_SIZES = { star: 208, orbit: 222, block: 244 } as const;

export function baseLayerSize(layer: TwinLayer): Size {
  if (layer.kind === "text" && layer.text) {
    const lines = Math.max(1, layer.text.content.split(/\r?\n/).length);
    return {
      width: 642,
      height: Math.max(layer.text.size * TEXT_LINE_HEIGHT, lines * layer.text.size * TEXT_LINE_HEIGHT + TEXT_PADDING),
    };
  }
  if (layer.kind === "shape") {
    const variant = layer.shape?.variant ?? "block";
    return { width: SHAPE_SIZES[variant], height: SHAPE_SIZES[variant] };
  }
  if (layer.kind === "image" && layer.image) {
    return { width: layer.image.width, height: layer.image.height };
  }
  return { width: 0, height: 0 };
}

export function layerFrame(layer: TwinLayer, transform = layer.transform): Frame {
  const base = baseLayerSize(layer);
  return {
    x: transform.x,
    y: transform.y,
    width: base.width * transform.scaleX,
    height: base.height * transform.scaleY,
    rotation: transform.rotation,
  };
}

export function layerBounds(layer: TwinLayer, transform = layer.transform): Rect {
  const frame = layerFrame(layer, transform);
  const radians = (frame.rotation * Math.PI) / 180;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  const width = Math.abs(frame.width * cos) + Math.abs(frame.height * sin);
  const height = Math.abs(frame.width * sin) + Math.abs(frame.height * cos);
  return {
    x: frame.x - width / 2,
    y: frame.y - height / 2,
    width,
    height,
  };
}

export function referencePoint(frame: Frame, ref: ReferencePoint): Point {
  let x = 0;
  let y = 0;
  if (ref.includes("w")) x = -frame.width / 2;
  if (ref.includes("e")) x = frame.width / 2;
  if (ref.includes("n")) y = -frame.height / 2;
  if (ref.includes("s")) y = frame.height / 2;
  const radians = (frame.rotation * Math.PI) / 180;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  return {
    x: frame.x + x * cos - y * sin,
    y: frame.y + x * sin + y * cos,
  };
}

export function normalizeAngle(degrees: number): number {
  return ((degrees + 180) % 360 + 360) % 360 - 180;
}

export function fitImageInsideArtboard(width: number, height: number): Size {
  const scale = Math.min(1, (ARTBOARD_WIDTH * 0.8) / width, (ARTBOARD_HEIGHT * 0.8) / height);
  return { width: width * scale, height: height * scale };
}

export function canAddLayer(layers: TwinLayer[], kind: TwinLayer["kind"]): boolean {
  if (layers.length >= MAX_LAYERS) return false;
  if (kind === "image" && layers.filter((layer) => layer.kind === "image").length >= MAX_IMAGE_LAYERS) return false;
  return true;
}
