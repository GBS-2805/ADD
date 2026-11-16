import { act, render } from "@testing-library/react";
import { useEffect } from "react";
import { describe, expect, it, vi } from "vitest";
import { commitHistory, createHistory } from "./history";
import { TwinProvider, useTwinStore, type TwinActions } from "./store";
import type { TwinDocument } from "./types";

let current: TwinActions | null = null;

function Bridge() {
  const store = useTwinStore();
  useEffect(() => {
    current = store;
  }, [store]);
  return null;
}

function renderStore() {
  current = null;
  render(
    <TwinProvider>
      <Bridge />
    </TwinProvider>,
  );
  return current as unknown as TwinActions;
}

const imageLayer = {
  id: "image-one",
  name: "Dot",
  kind: "image" as const,
  effect: null,
  blend: "normal" as const,
  opacity: 1,
  transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
  visible: true,
  image: { assetId: "image-one", src: "blob:one", width: 20, height: 20 },
};

describe("Twin store layers", () => {
  it("removingLastLayerLeavesEmptyDocument", () => {
    const store = renderStore();
    act(() => store.selectAllLayers());
    act(() => store.removeSelectedLayers());
    expect(current!.state.doc.layers).toEqual([]);
    expect(current!.state.doc.selectedLayerIds).toEqual([]);
    expect(current!.canUndo).toBe(true);
  });

  it("undoRestoresRemovedLayerSelection", () => {
    const store = renderStore();
    act(() => store.selectAllLayers());
    act(() => store.removeSelectedLayers());
    act(() => store.undo());
    expect(current!.state.doc.layers).toHaveLength(3);
    expect(current!.state.doc.selectedLayerIds.length).toBeGreaterThan(0);
  });

  it("imageObjectUrlSurvivesRemoveUndo", async () => {
    const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:one");
    vi.stubGlobal("createImageBitmap", vi.fn(async () => ({ width: 20, height: 20, close: () => undefined })));
    const store = renderStore();
    let id = "";
    await act(async () => {
      id = await store.addImageLayer(new File([new Uint8Array([1])], "one.png", { type: "image/png" }));
    });
    expect(current!.state.doc.layers.some((layer) => layer.id === id)).toBe(true);

    act(() => store.removeSelectedLayers());
    act(() => store.undo());
    const restored = current!.state.doc.layers.find((layer) => layer.id === id);
    expect(restored?.image?.src).toBe("blob:one");
    expect(restored?.visible).toBe(true);
    expect(revoke).not.toHaveBeenCalledWith("blob:one");
    revoke.mockRestore();
    vi.unstubAllGlobals();
  });

  it("historyEvictionRevokesUnusedImageUrl", () => {
    const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    const first: TwinDocument = { background: null, layers: [imageLayer], selectedLayerIds: [] };
    let history = createHistory(first);
    for (let index = 0; index < 35; index += 1) {
      history = commitHistory(history, { background: null, layers: [], selectedLayerIds: [] });
    }
    expect(revoke).toHaveBeenCalledWith("blob:one");
    revoke.mockRestore();
  });
});
