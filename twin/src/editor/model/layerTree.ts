import { createTreeCollection } from "@ark-ui/react/tree-view";
import type { TwinLayer } from "../../twin/types";

export interface LayerTreeNode {
  id: string;
  name: string;
  kind?: TwinLayer["kind"];
  visible?: boolean;
  children?: LayerTreeNode[];
}

/** Document order is bottom-first; the layers panel displays top-first. */
export function displayLayers(layers: readonly TwinLayer[]): TwinLayer[] {
  return [...layers].reverse();
}

export function createLayerCollection(layers: readonly TwinLayer[]) {
  const children: LayerTreeNode[] = displayLayers(layers).map((layer) => ({
    id: layer.id,
    name: layer.name,
    kind: layer.kind,
    visible: layer.visible,
  }));
  return createTreeCollection<LayerTreeNode>({
    nodeToValue: (node) => node.id,
    nodeToString: (node) => node.name,
    nodeToChildren: (node) => node.children ?? [],
    rootNode: { id: "root", name: "Layers", children },
  });
}

/**
 * Reorders document layers (bottom-first). `targetIndex` is the insertion
 * index after selected layers are removed. Selected layers keep their order.
 */
export function reorderLayerIds(
  layers: readonly TwinLayer[],
  ids: readonly string[],
  targetIndex: number,
): TwinLayer[] {
  const selected = new Set(ids.filter((id) => layers.some((layer) => layer.id === id)));
  if (selected.size === 0) return layers as TwinLayer[];
  const moving = layers.filter((layer) => selected.has(layer.id));
  const remaining = layers.filter((layer) => !selected.has(layer.id));
  const insert = Math.max(0, Math.min(remaining.length, Math.trunc(targetIndex)));
  const next = [...remaining.slice(0, insert), ...moving, ...remaining.slice(insert)];
  if (next.length === layers.length && next.every((layer, index) => layer === layers[index])) {
    return layers as TwinLayer[];
  }
  return next;
}
