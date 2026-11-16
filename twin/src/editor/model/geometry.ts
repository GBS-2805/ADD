import type { LayerTransform, TwinLayer } from "../../twin/types";

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
    x: ARTBOARD_WIDTH / 2 + frame.x - width / 2,
    y: ARTBOARD_HEIGHT / 2 + frame.y - height / 2,
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

export const MIN_LAYER_SIZE = 4;

export function selectionBounds(layers: TwinLayer[], ids: string[]): Rect | null {
  const selected = layers.filter((layer) => ids.includes(layer.id));
  if (selected.length === 0) return null;
  const rects = selected.map((layer) => layerBounds(layer));
  const left = Math.min(...rects.map((rect) => rect.x));
  const top = Math.min(...rects.map((rect) => rect.y));
  const right = Math.max(...rects.map((rect) => rect.x + rect.width));
  const bottom = Math.max(...rects.map((rect) => rect.y + rect.height));
  return { x: left, y: top, width: right - left, height: bottom - top };
}

export function moveTransform(transform: LayerTransform, dx: number, dy: number): LayerTransform {
  return { ...transform, x: transform.x + dx, y: transform.y + dy };
}

function rotateVector(point: Point, degrees: number): Point {
  const radians = (degrees * Math.PI) / 180;
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  return { x: point.x * cos - point.y * sin, y: point.x * sin + point.y * cos };
}

/** Resize around the opposite edge/corner, preserving the fixed edge exactly at any rotation. */
export function resizeTransform(
  layer: TwinLayer,
  start: LayerTransform,
  handle: ResizeHandle,
  pointer: Point,
  options: { fromCenter: boolean; keepAspect: boolean },
): LayerTransform {
  const base = baseLayerSize(layer);
  if (base.width <= 0 || base.height <= 0) return { ...start };
  const startFrame = layerFrame(layer, start);
  const local = rotateVector(
    { x: pointer.x - startFrame.x, y: pointer.y - startFrame.y },
    -startFrame.rotation,
  );
  const movesEast = handle.includes("e");
  const movesWest = handle.includes("w");
  const movesNorth = handle.includes("n");
  const movesSouth = handle.includes("s");
  const signX = movesEast ? 1 : movesWest ? -1 : 0;
  const signY = movesSouth ? 1 : movesNorth ? -1 : 0;
  let width = startFrame.width;
  let height = startFrame.height;

  if (signX !== 0) {
    width = options.fromCenter
      ? Math.abs(local.x) * 2
      : Math.abs(local.x - -signX * startFrame.width / 2);
    width = Math.max(MIN_LAYER_SIZE, width);
  }
  if (signY !== 0) {
    height = options.fromCenter
      ? Math.abs(local.y) * 2
      : Math.abs(local.y - -signY * startFrame.height / 2);
    height = Math.max(MIN_LAYER_SIZE, height);
  }

  if (options.keepAspect) {
    const factor = signX !== 0 && signY !== 0
      ? Math.max(width / startFrame.width, height / startFrame.height)
      : signX !== 0
        ? width / startFrame.width
        : height / startFrame.height;
    width = Math.max(MIN_LAYER_SIZE, startFrame.width * factor);
    height = Math.max(MIN_LAYER_SIZE, startFrame.height * factor);
  }

  let centerX = 0;
  let centerY = 0;
  if (signX !== 0) {
    centerX = options.fromCenter
      ? 0
      : -signX * startFrame.width / 2 + signX * width / 2;
  }
  if (signY !== 0) {
    centerY = options.fromCenter
      ? 0
      : -signY * startFrame.height / 2 + signY * height / 2;
  }
  const rotatedCenter = rotateVector({ x: centerX, y: centerY }, startFrame.rotation);
  const next = {
    x: startFrame.x + rotatedCenter.x,
    y: startFrame.y + rotatedCenter.y,
    scaleX: width / base.width,
    scaleY: height / base.height,
    rotation: startFrame.rotation,
  };
  return Number.isFinite(next.x) && Number.isFinite(next.y) && next.scaleX >= 0.01 && next.scaleY >= 0.01
    ? next
    : { ...start };
}

/** Rotation starts at the top handle; the pointer angle is relative to that stem. */
export function rotateTransform(
  layer: TwinLayer,
  start: LayerTransform,
  pointer: Point,
  options: { snap: boolean },
): LayerTransform {
  const frame = layerFrame(layer, start);
  const pointerAngle = (Math.atan2(pointer.y - frame.y, pointer.x - frame.x) * 180) / Math.PI;
  const delta = normalizeAngle(pointerAngle + 90);
  let rotation = normalizeAngle(start.rotation + delta);
  if (options.snap) rotation = normalizeAngle(Math.round(rotation / 15) * 15);
  return { ...start, rotation };
}

export function changeSizeAtReference(
  layer: TwinLayer,
  size: Size,
  ref: ReferencePoint,
  keepAspect: boolean,
): LayerTransform {
  const base = baseLayerSize(layer);
  const startFrame = layerFrame(layer);
  let width = Math.max(MIN_LAYER_SIZE, size.width);
  let height = Math.max(MIN_LAYER_SIZE, size.height);
  if (keepAspect) {
    const factor = size.width !== startFrame.width
      ? width / startFrame.width
      : height / startFrame.height;
    width = Math.max(MIN_LAYER_SIZE, startFrame.width * factor);
    height = Math.max(MIN_LAYER_SIZE, startFrame.height * factor);
  }
  const fixed = referencePoint(startFrame, ref);
  const localFrame = { x: 0, y: 0, width, height, rotation: startFrame.rotation };
  const offset = referencePoint(localFrame, ref);
  return {
    ...layer.transform,
    x: fixed.x - offset.x,
    y: fixed.y - offset.y,
    scaleX: width / base.width,
    scaleY: height / base.height,
  };
}

export function changePositionAtReference(layer: TwinLayer, point: Point, ref: ReferencePoint): LayerTransform {
  const frame = layerFrame(layer);
  const current = referencePoint(frame, ref);
  return {
    ...layer.transform,
    x: layer.transform.x + point.x - current.x,
    y: layer.transform.y + point.y - current.y,
  };
}

export function changeRotationAtReference(layer: TwinLayer, degrees: number, ref: ReferencePoint): LayerTransform {
  const frame = layerFrame(layer);
  const fixed = referencePoint(frame, ref);
  const rotation = normalizeAngle(degrees);
  const localFrame = { x: 0, y: 0, width: frame.width, height: frame.height, rotation };
  const offset = referencePoint(localFrame, ref);
  return { ...layer.transform, x: fixed.x - offset.x, y: fixed.y - offset.y, rotation };
}
