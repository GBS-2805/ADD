import { layerBounds, type Rect } from "./geometry";
import type { LayerTransform, TwinLayer } from "../../twin/types";

export type AlignEdge = "left" | "hcenter" | "right" | "top" | "vcenter" | "bottom";

export function selectionRect(layers: TwinLayer[], ids: string[]): Rect | null {
  const selected = layers.filter((layer) => ids.includes(layer.id));
  if (selected.length === 0) return null;
  const rects = selected.map((layer) => layerBounds(layer));
  const left = Math.min(...rects.map((rect) => rect.x));
  const top = Math.min(...rects.map((rect) => rect.y));
  const right = Math.max(...rects.map((rect) => rect.x + rect.width));
  const bottom = Math.max(...rects.map((rect) => rect.y + rect.height));
  return { x: left, y: top, width: right - left, height: bottom - top };
}

export function alignTransforms(layers: TwinLayer[], ids: string[], edge: AlignEdge): Record<string, LayerTransform> {
  const selected = layers.filter((layer) => ids.includes(layer.id));
  if (selected.length === 0) return {};
  const target = selected.length === 1
    ? { x: 0, y: 0, width: 765, height: 1000 }
    : selectionRect(layers, ids);
  if (!target) return {};
  const updates: Record<string, LayerTransform> = {};
  for (const layer of selected) {
    const bounds = layerBounds(layer);
    let dx = 0;
    let dy = 0;
    if (edge === "left") dx = target.x - bounds.x;
    if (edge === "hcenter") dx = target.x + target.width / 2 - (bounds.x + bounds.width / 2);
    if (edge === "right") dx = target.x + target.width - (bounds.x + bounds.width);
    if (edge === "top") dy = target.y - bounds.y;
    if (edge === "vcenter") dy = target.y + target.height / 2 - (bounds.y + bounds.height / 2);
    if (edge === "bottom") dy = target.y + target.height - (bounds.y + bounds.height);
    updates[layer.id] = { ...layer.transform, x: layer.transform.x + dx, y: layer.transform.y + dy };
  }
  return updates;
}

export function distributeTransforms(
  layers: TwinLayer[],
  ids: string[],
  axis: "x" | "y",
): Record<string, LayerTransform> {
  const selected = layers.filter((layer) => ids.includes(layer.id));
  if (selected.length < 3) return {};
  const sorted = [...selected].sort((a, b) => {
    const boundsA = layerBounds(a);
    const boundsB = layerBounds(b);
    return axis === "x" ? boundsA.x - boundsB.x : boundsA.y - boundsB.y;
  });
  const rects = sorted.map((layer) => layerBounds(layer));
  const first = rects[0];
  const last = rects[rects.length - 1];
  const totalSpan = axis === "x"
    ? last.x + last.width - first.x
    : last.y + last.height - first.y;
  const totalSize = rects.reduce((sum, rect) => sum + (axis === "x" ? rect.width : rect.height), 0);
  const gap = (totalSpan - totalSize) / (rects.length - 1);
  const updates: Record<string, LayerTransform> = {};
  let cursor = axis === "x" ? first.x : first.y;
  for (const [index, layer] of sorted.entries()) {
    const rect = rects[index];
    if (index === 0 || index === sorted.length - 1) {
      updates[layer.id] = { ...layer.transform };
    }
    if (index > 0 && index < sorted.length - 1) {
      const targetStart = cursor + gap;
      const delta = targetStart - (axis === "x" ? rect.x : rect.y);
      updates[layer.id] = {
        ...layer.transform,
        ...(axis === "x" ? { x: layer.transform.x + delta } : { y: layer.transform.y + delta }),
      };
      cursor = targetStart + (axis === "x" ? rect.width : rect.height);
    } else {
      cursor = (axis === "x" ? rect.x : rect.y) + (axis === "x" ? rect.width : rect.height);
    }
  }
  return updates;
}
