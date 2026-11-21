import { describe, expect, it } from "vitest";
import {
  commitHistory,
  createHistory,
  HISTORY_LIMIT,
  pushEffect,
  redoHistory,
  undoHistory,
  updateLayer,
} from "./history";
import type { EditorDocument, EditorEffect, EditorLayer } from "./types";

const initial: EditorDocument = {
  background: { kind: "solid", hex: "#F5ECD8" },
  selectedLayerIds: ["type"],
  layers: [
    {
      id: "type",
      name: "Type",
      kind: "text",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      text: { content: "TYPE", size: 60, align: "center", font: "instrument-serif" },
      fill: "#111111",
    },
    {
      id: "star",
      name: "Star",
      kind: "shape",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 40, y: 20, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      shape: { variant: "star" },
      fill: "#111111",
    },
  ],
};

const shader: EditorEffect = { kind: "shader", shader: "MeshGradient", preset: "Ink", controls: { speed: 0.5 } };
const texture: EditorEffect = { kind: "texture", preset: "PaperTexture", controls: { roughness: 0.4 } };

const edit = (doc: EditorDocument, id: string, change: (layer: EditorLayer) => EditorLayer): EditorDocument =>
  updateLayer(doc, id, change);

describe("document history", () => {
  it("records effects and restores them with undo and redo", () => {
    const first = pushEffect(createHistory(initial), "artboard", shader);

    expect(first.past).toHaveLength(1);
    expect(first.present.background).toEqual(shader);
    expect(undoHistory(first).present.background).toEqual(initial.background);
    expect(redoHistory(undoHistory(first)).present.background).toEqual(shader);
  });

  it("does not record an identical effect twice", () => {
    const first = pushEffect(createHistory(initial), "artboard", shader);
    const second = pushEffect(first, "artboard", { ...shader, controls: { speed: 0.5 } });

    expect(second).toBe(first);
    expect(second.past).toHaveLength(1);
  });

  it("records direct layer edits and reverts the whole edit", () => {
    const history = commitHistory(
      createHistory(initial),
      edit(initial, "type", (layer) => ({ ...layer, opacity: 0.5, transform: { ...layer.transform, x: 12 } })),
    );
    const undone = undoHistory(history);

    expect(history.past).toHaveLength(1);
    expect(undone.present.layers[0].opacity).toBe(1);
    expect(undone.present.layers[0].transform.x).toBe(0);
    expect(redoHistory(undone).present.layers[0].opacity).toBe(0.5);
  });

  it("records adds, removes, renames and reorders", () => {
    const added: EditorLayer = {
      id: "added",
      name: "Added",
      kind: "text",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      text: { content: "Added", size: 40, align: "center", font: "instrument-serif" },
      fill: "#111111",
    };
    const withAdded = commitHistory(createHistory(initial), {
      ...initial,
      selectedLayerIds: ["added"],
      layers: [...initial.layers, added],
    });
    const removed = commitHistory(withAdded, {
      ...withAdded.present,
      layers: withAdded.present.layers.filter((layer) => layer.id !== "added"),
      selectedLayerIds: ["type"],
    });
    const renamed = commitHistory(removed, edit(removed.present, "star", (layer) => ({ ...layer, name: "Renamed star" })));
    const reordered = commitHistory(renamed, {
      ...renamed.present,
      layers: [...renamed.present.layers].reverse(),
    });

    expect(undoHistory(reordered).present.layers[1].name).toBe("Renamed star");
    expect(undoHistory(undoHistory(reordered)).present.layers.map((layer) => layer.id)).toEqual(["type", "star"]);
    expect(undoHistory(undoHistory(undoHistory(reordered))).present.layers.map((layer) => layer.id)).toEqual(["type", "star", "added"]);
  });

  it("keeps selection changes out of history and preserves a still-valid selection across undo", () => {
    const effect = pushEffect(createHistory(initial), "artboard", shader);
    const selected = { ...effect.present, selectedLayerIds: ["star"] };
    const withSelection = { ...effect, present: selected };

    expect(withSelection.past).toHaveLength(1);
    const undone = undoHistory(withSelection);
    expect(undone.present.background).toEqual(initial.background);
    expect(undone.present.selectedLayerIds).toEqual(["star"]);
  });

  it("clears a selection that undo removes", () => {
    const added = commitHistory(createHistory(initial), {
      ...initial,
      selectedLayerIds: ["new"],
      layers: [
        ...initial.layers,
        {
          ...initial.layers[0],
          id: "new",
          name: "New",
        },
      ],
    });
    expect(undoHistory(added).present.selectedLayerIds).toEqual([]);
  });

  it("records a layer effect only on the selected layer", () => {
    const next = pushEffect(createHistory(initial), "layer", texture);

    expect(next.present.background).toEqual(initial.background);
    expect(next.present.layers[0].effect).toEqual(texture);
    expect(next.present.layers[1].effect).toBeNull();
  });

  it("is a no-op when there is nothing to undo or redo", () => {
    const history = createHistory(initial);
    expect(undoHistory(history)).toBe(history);
    expect(redoHistory(history)).toBe(history);
    expect(commitHistory(history, initial)).toBe(history);
  });

  it("bounds history to the newest snapshots", () => {
    let history = createHistory(initial);
    for (let index = 0; index < HISTORY_LIMIT + 5; index += 1) {
      history = commitHistory(history, edit(history.present, "type", (layer) => ({
        ...layer,
        opacity: Math.max(0, 1 - index / 100),
      })));
    }
    expect(history.past).toHaveLength(HISTORY_LIMIT);
    expect(history.future).toHaveLength(0);
  });
});
