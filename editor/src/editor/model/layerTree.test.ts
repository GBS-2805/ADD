import { describe, expect, it } from "vitest";
import { createLayerCollection, displayLayers, reorderLayerIds } from "./layerTree";
import type { EditorLayer } from "../../editor-core/types";

const layers = ["a", "b", "c", "d"].map((id) => ({
  id,
  name: id.toUpperCase(),
  kind: "shape",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
  visible: true,
  shape: { variant: "block" },
  fill: "#111111",
})) as EditorLayer[];

const ids = (items: EditorLayer[]) => items.map((item) => item.id);

describe("layer tree model", () => {
  it("flattens document layers top-first for display", () => {
    expect(ids(displayLayers(layers))).toEqual(["d", "c", "b", "a"]);
  });

  it("creates a root collection from the display order", () => {
    const collection = createLayerCollection(layers);
    expect(collection.rootNode.id).toBe("root");
    expect(collection.rootNode.children?.map((node) => node.id)).toEqual(["d", "c", "b", "a"]);
    expect(collection.getFirstNode()?.id).toBe("d");
  });

  it("returns an empty collection for zero layers", () => {
    const collection = createLayerCollection([]);
    expect(collection.rootNode.children).toEqual([]);
    expect(collection.getFirstNode()).toBeUndefined();
  });

  it("reorderMovesMultipleLayersInOrder", () => {
    const result = reorderLayerIds(layers, ["b", "d"], 4);
    expect(ids(result)).toEqual(["a", "c", "b", "d"]);
  });

  it("reorderToFrontAndBack", () => {
    expect(ids(reorderLayerIds(layers, ["b", "d"], 0))).toEqual(["b", "d", "a", "c"]);
    expect(ids(reorderLayerIds(layers, ["b", "d"], 4))).toEqual(["a", "c", "b", "d"]);
  });

  it("reorderIsNoOpWhenTargetIsInsideSelection", () => {
    expect(reorderLayerIds(layers, ["b", "c"], 1)).toBe(layers);
  });

  it("reorder ignores missing ids and clamps targets", () => {
    expect(ids(reorderLayerIds(layers, ["missing", "a"], -5))).toEqual(["a", "b", "c", "d"]);
    expect(ids(reorderLayerIds(layers, ["a"], 99))).toEqual(["b", "c", "d", "a"]);
  });
});
