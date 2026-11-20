import { describe, expect, it } from "vitest";
import {
  AUTOSAVE_VERSION,
  createMemoryBackend,
  referencedAssets,
  reviveFromStorage,
  serializeForStorage,
} from "./autosave";
import type { TwinDocument } from "./types";

const document_: TwinDocument = {
  background: null,
  layers: [
    {
      id: "text-one",
      name: "Headline",
      kind: "text",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      text: { content: "Hello", size: 48, align: "left", font: "instrument-serif" },
      fill: "#1f1a17",
    },
    {
      id: "image-one",
      name: "Photo",
      kind: "image",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 10, y: 10, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      image: { assetId: "asset-one", src: "blob:local", width: 40, height: 30 },
    },
  ],
  selectedLayerIds: ["text-one"],
};

describe("autosave payload", () => {
  it("replaces a picture's blob URL with its asset id", () => {
    const payload = serializeForStorage(document_, new Date("2026-11-20T10:00:00Z"));
    expect(payload.version).toBe(AUTOSAVE_VERSION);
    const image = payload.document.layers[1] as { image?: Record<string, unknown> };
    expect(image.image).toEqual({ assetId: "asset-one", width: 40, height: 30 });
    expect(JSON.stringify(payload)).not.toContain("blob:");
  });

  it("keeps an effect kind this build does not know", () => {
    const unusual: TwinDocument = {
      ...document_,
      layers: [
        {
          ...document_.layers[0],
          effect: { kind: "unsupported", originalKind: "future", raw: { n: 1 } },
        },
      ],
    };
    const payload = serializeForStorage(unusual);
    expect(payload.document.layers[0].effect).toMatchObject({ originalKind: "future" });
  });

  it("reports every asset a payload still refers to", () => {
    const payload = serializeForStorage(document_);
    expect(referencedAssets(payload)).toEqual(["asset-one"]);
  });

  it("refuses a payload from another version rather than guessing", async () => {
    const payload = { ...serializeForStorage(document_), version: 99 };
    expect(await reviveFromStorage(payload, async () => null)).toBeNull();
  });
});

describe("autosave round trip", () => {
  it("restores the same layers, with the picture re-attached from storage", async () => {
    const backend = createMemoryBackend();
    const bytes = new Blob([new Uint8Array([1, 2, 3])], { type: "image/png" });
    await backend.writeAsset("asset-one", bytes);

    const revived = await reviveFromStorage(
      serializeForStorage(document_),
      (id) => backend.readAsset(id),
    );

    expect(revived).not.toBeNull();
    expect(revived!.droppedImages).toBe(0);
    expect(revived!.document.layers).toHaveLength(2);
    const image = revived!.document.layers[1].image!;
    expect(image.assetId).toBe("asset-one");
    expect(image.width).toBe(40);
    expect(image.height).toBe(30);
    expect(image.src.startsWith("blob:")).toBe(true);
  });

  it("drops a picture whose bytes are gone, and counts it", async () => {
    const backend = createMemoryBackend();
    const revived = await reviveFromStorage(
      serializeForStorage(document_),
      (id) => backend.readAsset(id),
    );
    expect(revived!.droppedImages).toBe(1);
    expect(revived!.document.layers.map((layer) => layer.id)).toEqual(["text-one"]);
    // The dangling selection is pruned with the layer it pointed at.
    expect(revived!.document.selectedLayerIds).not.toContain("image-one");
  });
});

describe("autosave backend", () => {
  it("writes, reads back and prunes pictures nothing uses", async () => {
    const backend = createMemoryBackend();
    await backend.writeAsset("kept", new Blob(["a"]));
    await backend.writeAsset("stale", new Blob(["b"]));
    await backend.write(serializeForStorage(document_));
    await backend.pruneAssets(["kept"]);

    expect([...backend.assets.keys()]).toEqual(["kept"]);
    expect(await backend.read()).toMatchObject({ version: AUTOSAVE_VERSION });
  });

  it("never hands out the live object, so a caller cannot mutate the store", async () => {
    const backend = createMemoryBackend();
    const payload = serializeForStorage(document_);
    await backend.write(payload);
    const first = (await backend.read()) as { document: { layers: unknown[] } };
    first.document.layers.length = 0;
    const second = (await backend.read()) as { document: { layers: unknown[] } };
    expect(second.document.layers).toHaveLength(2);
  });
});
