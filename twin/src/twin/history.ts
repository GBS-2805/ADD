import { sameEffect } from "./catalogue";
import type { EffectTarget, TwinDocument, TwinEffect, TwinLayer } from "./types";

export const HISTORY_LIMIT = 30;

export interface DocumentHistory {
  past: TwinDocument[];
  present: TwinDocument;
  future: TwinDocument[];
}

/** Kept as an alias so existing imports and commands do not change shape. */
export type EffectHistory = DocumentHistory;

export function createHistory(doc: TwinDocument): DocumentHistory {
  return { past: [], present: doc, future: [] };
}

function imageUrls(doc: TwinDocument): string[] {
  return doc.layers
    .map((layer) => layer.image?.src)
    .filter((src): src is string => Boolean(src?.startsWith("blob:")));
}

/** Revoke blob URLs that fell out of the bounded history and no live snapshot uses. */
function revokeEvictedImages(evicted: TwinDocument[], retained: TwinDocument[]): void {
  if (evicted.length === 0 || typeof URL.revokeObjectURL !== "function") return;
  const live = new Set(retained.flatMap(imageUrls));
  for (const url of new Set(evicted.flatMap(imageUrls))) {
    if (!live.has(url)) URL.revokeObjectURL(url);
  }
}

export function commitHistory(history: DocumentHistory, next: TwinDocument): DocumentHistory {
  if (next === history.present) return history;
  const allPast = [...history.past, history.present];
  const past = allPast.slice(-HISTORY_LIMIT);
  const evicted = allPast.slice(0, Math.max(0, allPast.length - HISTORY_LIMIT));
  revokeEvictedImages(evicted, [...past, next, ...history.future]);
  return {
    past,
    present: next,
    future: [],
  };
}

export function applyToDocument(
  doc: TwinDocument,
  target: EffectTarget,
  effect: TwinEffect,
): TwinDocument {
  if (target === "artboard") {
    return { ...doc, background: effect };
  }
  const primary = doc.selectedLayerIds[0];
  if (!primary) return doc;
  return {
    ...doc,
    layers: doc.layers.map((layer) =>
      layer.id === primary ? { ...layer, effect } : layer,
    ),
  };
}

export function pushEffect(
  history: DocumentHistory,
  target: EffectTarget,
  effect: TwinEffect,
): DocumentHistory {
  const primary = history.present.selectedLayerIds[0];
  if (target === "layer" && !primary) return history;
  const current =
    target === "artboard"
      ? history.present.background
      : history.present.layers.find((layer) => layer.id === primary)?.effect ?? null;
  if (sameEffect(current, effect)) return history;
  return commitHistory(history, applyToDocument(history.present, target, effect));
}

function restoreSelection(restored: TwinDocument, current: TwinDocument): TwinDocument {
  const available = new Set(restored.layers.map((layer) => layer.id));
  // An empty live selection can be the result of deleting the last layer. In
  // that case undo restores the selection recorded with the snapshot.
  const source = current.selectedLayerIds.length === 0
    ? restored.selectedLayerIds
    : current.selectedLayerIds;
  const selectedLayerIds = source.filter((id) => available.has(id));
  if (
    selectedLayerIds.length === restored.selectedLayerIds.length &&
    selectedLayerIds.every((id, index) => id === restored.selectedLayerIds[index])
  ) {
    return restored;
  }
  return { ...restored, selectedLayerIds };
}

export function undoHistory(history: DocumentHistory): DocumentHistory {
  if (history.past.length === 0) return history;
  const previous = history.past[history.past.length - 1];
  return {
    past: history.past.slice(0, -1),
    present: restoreSelection(previous, history.present),
    future: [history.present, ...history.future],
  };
}

export function redoHistory(history: DocumentHistory): DocumentHistory {
  if (history.future.length === 0) return history;
  const [next, ...future] = history.future;
  return {
    past: [...history.past, history.present],
    present: restoreSelection(next, history.present),
    future,
  };
}

export function updateLayer(
  doc: TwinDocument,
  id: string,
  update: (layer: TwinLayer) => TwinLayer,
): TwinDocument {
  return {
    ...doc,
    layers: doc.layers.map((layer) => (layer.id === id ? update(layer) : layer)),
  };
}
