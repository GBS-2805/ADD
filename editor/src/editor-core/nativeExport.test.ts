import { afterEach, describe, expect, it, vi } from "vitest";
import { nativeBlockers, nativeExportAvailable, saveArtboardNatively } from "./nativeExport";
import type { EditorDocument, EditorEffect, EditorLayer } from "./types";

const headline: EditorLayer = {
  id: "headline",
  name: "Type / OPEN",
  kind: "text",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
  visible: true,
  text: { content: "OPEN", size: 68, align: "center", font: "instrument-serif" },
  fill: "#111111",
};

function documentWith(background: EditorEffect | null, layers: EditorLayer[] = [headline]): EditorDocument {
  return { background, layers, selectedLayerIds: [] };
}

const solid: EditorEffect = { kind: "solid", hex: "#5b2a4b" };
const field: EditorEffect = { kind: "field", preset: "Mica dawn", colors: ["#f3e9da", "#c7a88b", "#6b5a4e", "#2c2723"] };
const texture: EditorEffect = { kind: "texture", preset: "PaperTexture", controls: {} };
const pattern: EditorEffect = { kind: "pattern", preset: "jigsaw", controls: {} };
const particles: EditorEffect = { kind: "particles", preset: "dust", controls: {} };

function installBridge(saveEditor: (project: string, name: string, scale: number) => string) {
  Object.defineProperty(window, "IconsApp", {
    value: { saveEditor },
    configurable: true,
  });
}

afterEach(() => {
  delete (window as unknown as { IconsApp?: unknown }).IconsApp;
});

describe("Editor native export", () => {
  it("has no bridge in a browser", async () => {
    expect(nativeExportAvailable()).toBe(false);
    expect(await saveArtboardNatively(documentWith(solid))).toBeNull();
  });

  it("accepts the effects the native painter knows", () => {
    expect(nativeBlockers(documentWith(null))).toEqual([]);
    expect(nativeBlockers(documentWith(solid))).toEqual([]);
    expect(nativeBlockers(documentWith(field))).toEqual([]);
    expect(nativeBlockers(documentWith({ kind: "shader", shader: "PaperTexture", preset: "paper", controls: {} }))).toEqual([]);
  });

  it("names the effects that stay on the canvas", () => {
    expect(nativeBlockers(documentWith(pattern))).toEqual(["Pattern Library"]);
    expect(nativeBlockers(documentWith(solid, [
      headline,
      { ...headline, id: "particles", effect: particles },
      { ...headline, id: "texture", effect: texture },
    ]))).toEqual(["Particle Field", "Texture Lab"]);
  });

  it("sends the project file and reports the saved PNG", async () => {
    let sent = { project: "", name: "", scale: 0 };
    installBridge((project, name, scale) => {
      sent = { project, name, scale };
      return JSON.stringify({
        status: "saved",
        bytes: 4096,
        filename: "open.png",
        message: "Saved to Downloads/Shelf/open.png",
      });
    });

    const result = await saveArtboardNatively(documentWith(solid));

    expect(result).toMatchObject({
      status: "saved",
      bytes: 4096,
      filename: "open.png",
      message: "Saved to Downloads/Shelf/open.png",
      width: 1530,
      height: 2000,
      signature: "89504e470d0a1a0a",
    });
    expect(sent.name).toBe("open.png");
    expect(sent.scale).toBe(2);
    const parsed = JSON.parse(sent.project) as { format: string; document: EditorDocument };
    expect(parsed.format).toBe("bmo-twin-project");
    expect(parsed.document.background).toEqual(solid);
    expect(parsed.document.layers).toHaveLength(1);
  });

  it("does not call the bridge for a document it cannot paint", async () => {
    const saveEditor = vi.fn(() => "");
    installBridge(saveEditor);

    const result = await saveArtboardNatively(documentWith(pattern));

    expect(result).toEqual({ status: "canvas", reason: "Pattern Library" });
    expect(saveEditor).not.toHaveBeenCalled();
  });

  it("passes the bridge's refusal and its errors through", async () => {
    installBridge(() => JSON.stringify({ status: "canvas", reason: "pattern" }));
    expect(await saveArtboardNatively(documentWith(solid))).toEqual({
      status: "canvas",
      reason: "pattern",
    });

    installBridge(() => JSON.stringify({ status: "error", message: "Could not save open.png" }));
    expect(await saveArtboardNatively(documentWith(solid))).toEqual({
      status: "error",
      message: "Could not save open.png",
    });

    installBridge(() => "not a reply");
    expect(await saveArtboardNatively(documentWith(solid))).toEqual({
      status: "error",
      message: "The native export returned no answer.",
    });
  });
});
