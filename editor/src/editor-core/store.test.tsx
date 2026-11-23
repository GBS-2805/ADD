import { act, render } from "@testing-library/react";
import { useEffect } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const pngMocks = vi.hoisted(() => ({ renderArtboardPng: vi.fn() }));
const projectMocks = vi.hoisted(() => ({
  serializeProject: vi.fn(),
  actual: null as null | ((document: never) => Promise<string>),
}));
vi.mock("./pngExport", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./pngExport")>()),
  renderArtboardPng: pngMocks.renderArtboardPng,
}));
vi.mock("./project", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./project")>();
  projectMocks.actual = actual.serializeProject;
  projectMocks.serializeProject.mockImplementation(actual.serializeProject);
  return { ...actual, serializeProject: projectMocks.serializeProject };
});
import { commitHistory, createHistory } from "./history";
import { MAX_PROJECT_BYTES } from "./project";
import {
  EditorProvider,
  useEditorDocument,
  useEditorStore,
  useTransientTransforms,
  type EditorActions,
  type EditorDocumentState,
} from "./store";
import type { EditorDocument, EditorEffect } from "./types";

let current: EditorActions | null = null;
let currentDocument: EditorDocumentState | null = null;
let currentTransients: ReturnType<typeof useTransientTransforms> = null;

function Bridge() {
  const store = useEditorStore();
  const documentState = useEditorDocument();
  const transients = useTransientTransforms();
  useEffect(() => {
    current = store;
    currentDocument = documentState;
    currentTransients = transients;
  }, [store, documentState, transients]);
  return null;
}

function renderStore() {
  current = null;
  currentDocument = null;
  render(
    <EditorProvider>
      <Bridge />
    </EditorProvider>,
  );
  return current as unknown as EditorActions;
}

beforeEach(() => {
  projectMocks.serializeProject.mockReset();
  if (projectMocks.actual) projectMocks.serializeProject.mockImplementation(projectMocks.actual);
});

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
    expect(currentDocument!.doc.layers).toEqual([]);
    expect(currentDocument!.doc.selectedLayerIds).toEqual([]);
    expect(currentDocument!.canUndo).toBe(true);
  });

  it("undoRestoresRemovedLayerSelection", () => {
    const store = renderStore();
    act(() => store.selectAllLayers());
    act(() => store.removeSelectedLayers());
    act(() => store.undo());
    expect(currentDocument!.doc.layers).toHaveLength(3);
    expect(currentDocument!.doc.selectedLayerIds.length).toBeGreaterThan(0);
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
    expect(currentDocument!.doc.layers.some((layer) => layer.id === id)).toBe(true);

    act(() => store.removeSelectedLayers());
    act(() => store.undo());
    const restored = currentDocument!.doc.layers.find((layer) => layer.id === id);
    expect(restored?.image?.src).toBe("blob:one");
    expect(restored?.visible).toBe(true);
    expect(revoke).not.toHaveBeenCalledWith("blob:one");
    revoke.mockRestore();
    vi.unstubAllGlobals();
  });

  it("saveExportAndPrintIgnoreTransientTransforms", async () => {
    const store = renderStore();
    const orbit = currentDocument!.doc.layers.find((layer) => layer.id === "shape-orbit");
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
    expect(currentTransients?.["shape-orbit"].x).toBe(committed + 400);
    expect(currentDocument!.doc.layers.find((layer) => layer.id === "shape-orbit")?.transform.x).toBe(committed);

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
    expect(currentDocument!.doc.layers.find((layer) => layer.id === "shape-orbit")?.transform.x).toBe(committed);
    act(() => store.setTransientTransforms(null));
    create.mockRestore();
    revoke.mockRestore();
    print.mockRestore();
  });

  it("refuses to download a project over the parser cap", async () => {
    const store = renderStore();
    const create = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:saved");
    projectMocks.serializeProject.mockResolvedValueOnce({
      length: MAX_PROJECT_BYTES + 1,
    } as unknown as string);
    try {
      await act(async () => {
        await store.saveProject();
      });
      expect(create).not.toHaveBeenCalled();
      expect(current!.state.toast).toMatch(/64 MB/);
    } finally {
      create.mockRestore();
    }
  });

  it("nativeExportWritesThroughTheBridge", async () => {
    const store = renderStore();
    pngMocks.renderArtboardPng.mockClear();
    const saveEditor = vi.fn(() => JSON.stringify({
      status: "saved",
      bytes: 4096,
      filename: "open-studio.png",
      message: "Saved to Downloads/Shelf/open-studio.png",
    }));
    Object.defineProperty(window, "IconsApp", {
      value: { saveEditor },
      configurable: true,
    });
    try {
      await act(async () => {
        await store.exportNativePng();
      });
      expect(saveEditor).toHaveBeenCalledOnce();
      expect(pngMocks.renderArtboardPng).not.toHaveBeenCalled();
      expect(current!.state.lastExport?.filename).toBe("open-studio.png");
      expect(current!.state.lastExport?.bytes).toBe(4096);
      expect(current!.state.toast).toContain("Downloads/Shelf");
    } finally {
      delete (window as unknown as { IconsApp?: unknown }).IconsApp;
    }
  });

  it("defaultExportStaysOnCanvasInsideTheApp", async () => {
    const store = renderStore();
    const saveEditor = vi.fn(() => JSON.stringify({ status: "saved", bytes: 1, filename: "x.png" }));
    const create = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:png");
    const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    Object.defineProperty(window, "IconsApp", {
      value: { saveEditor },
      configurable: true,
    });
    pngMocks.renderArtboardPng.mockClear();
    pngMocks.renderArtboardPng.mockResolvedValue({
      blob: new Blob(["png"]),
      width: 1530,
      height: 2000,
      bytes: 3,
      filename: "open-studio.png",
      signature: "89504e470d0a1a0a",
    });
    try {
      await act(async () => {
        await store.exportPng();
      });
      expect(pngMocks.renderArtboardPng).toHaveBeenCalledOnce();
      expect(saveEditor).not.toHaveBeenCalled();
      expect(current!.state.toast).toContain("PNG exported");
    } finally {
      delete (window as unknown as { IconsApp?: unknown }).IconsApp;
      create.mockRestore();
      revoke.mockRestore();
    }
  });

  it("applies an effect once when it lands twice in one batch", () => {
    const store = renderStore();
    const effect: EditorEffect = {
      kind: "shader",
      shader: "MeshGradient",
      preset: "Ink",
      controls: { speed: 0.5 },
    };
    act(() => {
      store.applyEffect(effect, "artboard");
      store.applyEffect(effect, "artboard");
    });
    expect(currentDocument!.doc.background).toEqual(effect);
    // Document writes are synchronous now, so the second call sees the effect
    // already applied and says so instead of claiming a second application.
    expect(current!.state.toast).toBe("That effect is already on the artboard.");
  });

  it("reports the undo and redo toast when their updaters run again", () => {
    const store = renderStore();
    const start = currentDocument!.doc.background;
    const effect: EditorEffect = {
      kind: "texture",
      preset: "PaperTexture",
      controls: { roughness: 0.4 },
    };
    act(() => store.applyEffect(effect, "artboard"));
    act(() => {
      store.undo();
      store.undo();
    });
    expect(currentDocument!.doc.background).toEqual(start);
    expect(current!.state.toast).toBe("Undid the last edit.");
    act(() => {
      store.redo();
      store.redo();
    });
    expect(currentDocument!.doc.background).toEqual(effect);
    expect(current!.state.toast).toBe("Redid the edit.");
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
