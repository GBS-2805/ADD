import { act, render } from "@testing-library/react";
import { useEffect } from "react";
import { describe, expect, it, vi } from "vitest";

const pngMocks = vi.hoisted(() => ({ renderArtboardPng: vi.fn() }));
vi.mock("./pngExport", () => ({ renderArtboardPng: pngMocks.renderArtboardPng }));
import { commitHistory, createHistory } from "./history";
import { serializeProject } from "./project";
import { EditorProvider, useEditorStore, type EditorActions } from "./store";
import type { EditorDocument } from "./types";

let current: EditorActions | null = null;

function Bridge() {
  const store = useEditorStore();
  useEffect(() => {
    current = store;
  }, [store]);
  return null;
}

function renderStore() {
  current = null;
  render(
    <EditorProvider>
      <Bridge />
    </EditorProvider>,
  );
  return current as unknown as EditorActions;
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

describe("Editor store layers", () => {
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

  it("saveExportAndPrintIgnoreTransientTransforms", async () => {
    const store = renderStore();
    const orbit = current!.state.doc.layers.find((layer) => layer.id === "shape-orbit");
    if (!orbit) throw new Error("Orbit missing.");
    const committed = orbit.transform.x;
    const blobs: Blob[] = [];
    const create = vi.spyOn(URL, "createObjectURL").mockImplementation((blob) => {
      blobs.push(blob as Blob);
      return "blob:saved";
    });
    const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    const print = vi.spyOn(window, "print").mockImplementation(() => undefined);
    const exported: { doc: EditorDocument | null } = { doc: null };
    pngMocks.renderArtboardPng.mockImplementation(async (document: EditorDocument) => {
      exported.doc = document;
      return {
        blob: new Blob(["png"]),
        width: 1530,
        height: 2000,
        bytes: 3,
        filename: "open-studio.png",
        signature: "89504e470d0a1a0a",
      };
    });

    act(() => store.setTransientTransforms({
      "shape-orbit": { ...orbit.transform, x: committed + 400 },
    }));
    expect(current!.state.transientTransforms?.["shape-orbit"].x).toBe(committed + 400);
    expect(current!.state.doc.layers.find((layer) => layer.id === "shape-orbit")?.transform.x).toBe(committed);

    await act(async () => {
      await store.saveProject();
    });
    await act(async () => {
      await store.exportPng();
      store.printStage();
    });

    const savedText = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = () => reject(reader.error);
      reader.readAsText(blobs[0]);
    });
    const saved = JSON.parse(savedText) as { document: EditorDocument };
    expect(saved.document.layers.find((layer) => layer.id === "shape-orbit")?.transform.x).toBe(committed);
    expect(exported.doc?.layers.find((layer) => layer.id === "shape-orbit")?.transform.x).toBe(committed);
    expect(print).toHaveBeenCalledOnce();
    expect(current!.state.doc.layers.find((layer) => layer.id === "shape-orbit")?.transform.x).toBe(committed);
    act(() => store.setTransientTransforms(null));
    create.mockRestore();
    revoke.mockRestore();
    print.mockRestore();
  });

  it("historyEvictionRevokesUnusedImageUrl", () => {
    const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    const first: EditorDocument = { background: null, layers: [imageLayer], selectedLayerIds: [] };
    let history = createHistory(first);
    for (let index = 0; index < 35; index += 1) {
      history = commitHistory(history, { background: null, layers: [], selectedLayerIds: [] });
    }
    expect(revoke).toHaveBeenCalledWith("blob:one");
    revoke.mockRestore();
  });
});
