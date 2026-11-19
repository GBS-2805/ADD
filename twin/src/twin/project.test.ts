import { describe, expect, it } from "vitest";
import {
  PROJECT_FORMAT,
  PROJECT_VERSION,
  ProjectFileError,
  parseProjectFile,
  projectFilename,
  serializeProject,
  validateDocument,
} from "./project";
import type { TwinDocument } from "./types";

const makeDocument = (): TwinDocument => ({
  background: { kind: "solid", hex: "#fafaf7" },
  selectedLayerIds: ["headline"],
  layers: [
    {
      id: "headline",
      name: "Type / OPEN STUDIO",
      kind: "text",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: -8, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      text: { content: "OPEN\nSTUDIO", size: 154, align: "center", font: "instrument-serif" },
      fill: "#111111",
    },
    {
      id: "star",
      name: "Shape / Star",
      kind: "shape",
      effect: null,
      blend: "multiply",
      opacity: 0.9,
      transform: { x: -92, y: 86, scaleX: 1.05, scaleY: 1.05, rotation: -9 },
      visible: true,
      shape: { variant: "star" },
      fill: "#111111",
    },
  ],
});

const project = (document: unknown, version = PROJECT_VERSION) => JSON.stringify({
  format: PROJECT_FORMAT,
  version,
  savedAt: new Date().toISOString(),
  document,
});

describe("effects from a newer library", () => {
  // The shelf publishes effect presets, so a newer catalog can describe a kind this
  // build has never heard of. Opened documents must survive that: degrade the render,
  // never reject the file, and never lose the payload on re-save.
  const futureEffect = {
    kind: "gradient-mesh-v2",
    preset: "riso-falloff",
    stops: [{ at: 0.2, hex: "#111111" }, { at: 0.8, hex: "#8a8983" }],
  };

  const withFutureEffect = (): TwinDocument => {
    const document = makeDocument();
    document.background = futureEffect as unknown as TwinDocument["background"];
    return document;
  };

  it("opens a file whose effect kind this build does not know", () => {
    const parsed = parseProjectFile(project(withFutureEffect()));
    expect(parsed.background).toMatchObject({
      kind: "unsupported",
      originalKind: "gradient-mesh-v2",
    });
  });

  it("keeps the document's layers rather than failing the whole file", () => {
    const parsed = parseProjectFile(project(withFutureEffect()));
    expect(parsed.layers.map((layer) => layer.id)).toEqual(["headline", "star"]);
    expect(parsed.selectedLayerIds).toEqual(["headline"]);
  });

  it("writes the unknown effect back byte-identical after a round-trip", async () => {
    const parsed = parseProjectFile(project(withFutureEffect()));
    const saved = JSON.parse(await serializeProject(parsed));
    expect(saved.document.background).toEqual(futureEffect);
  });

  it("keeps the payload when only a layer effect is unknown", () => {
    const document = makeDocument();
    document.layers[1].effect = futureEffect as unknown as TwinDocument["layers"][number]["effect"];
    const parsed = parseProjectFile(project(document));
    expect(parsed.layers[1].effect).toMatchObject({ kind: "unsupported", originalKind: "gradient-mesh-v2" });
    expect(parsed.layers[0].effect).toBeNull();
  });

  it("still rejects a malformed known effect", () => {
    const document = makeDocument();
    document.background = { kind: "field", preset: "x", colors: [] } as unknown as TwinDocument["background"];
    expect(() => parseProjectFile(project(document))).toThrow(/between one and six colours/);
  });
});

describe("Twin project files", () => {
  it("round-trips a v2 document and preserves an empty selection", async () => {
    const document = makeDocument();
    const source = await serializeProject(document);
    const parsed = parseProjectFile(source);
    expect(JSON.parse(source).version).toBe(2);
    expect(parsed.layers.map((layer) => layer.id)).toEqual(["headline", "star"]);
    expect(parsed.selectedLayerIds).toEqual(["headline"]);
    expect(parsed.background).toEqual({ kind: "solid", hex: "#fafaf7" });
    expect(parsed.layers[0].transform).toEqual({ x: 0, y: -8, scaleX: 1, scaleY: 1, rotation: 0 });

    const withoutSelection = parseProjectFile(project({ ...document, selectedLayerIds: ["missing"] }));
    expect(withoutSelection.selectedLayerIds).toEqual([]);
  });

  it("accepts an empty document and an empty selection", () => {
    const empty = { background: null, layers: [], selectedLayerIds: [] };
    expect(validateDocument(empty)).toEqual(empty);
  });

  it("rejects files that are not Twin projects and malformed v2 transforms", () => {
    expect(() => parseProjectFile("not json")).toThrow(ProjectFileError);
    expect(() => parseProjectFile(JSON.stringify({ format: "other", version: 2, document: makeDocument() }))).toThrow(/not a supported Twin project/);
    expect(() => parseProjectFile(project({ ...makeDocument(), selectedLayerIds: "headline" }))).toThrow(/selection/);
    const malformed = makeDocument();
    malformed.layers[0].transform = { x: 0, y: 0, scale: 1, rotation: 0 } as unknown as TwinDocument["layers"][number]["transform"];
    expect(() => validateDocument(malformed)).toThrow(/scaleX/);
  });

  it("migrates v1 coordinates, text and image sizes, and selection", () => {
    const v1 = {
      background: { kind: "solid", hex: "#fafaf7" },
      selectedLayerId: "legacy",
      layers: [
        {
          id: "legacy",
          name: "Type / LEGACY",
          kind: "text",
          effect: null,
          blend: "normal",
          opacity: 1,
          transform: { x: 10, y: -8, scale: 1.5, rotation: 12 },
          visible: true,
          text: { content: "LEGACY", size: 56, align: "center", font: "instrument-serif" },
          fill: "#111111",
        },
        {
          id: "legacy-png",
          name: "PNG",
          kind: "image",
          effect: null,
          blend: "normal",
          opacity: 1,
          transform: { x: 20, y: 30, scale: 2, rotation: 0 },
          visible: true,
          image: { assetId: "png", src: "data:image/png;base64,AA==", width: 10, height: 5 },
        },
      ],
    };
    const parsed = parseProjectFile(project(v1, 1));
    expect(parsed.selectedLayerIds).toEqual(["legacy"]);
    expect(parsed.layers[0].transform.x).toBeCloseTo(10 * (765 / 338), 8);
    expect(parsed.layers[0].transform.y).toBeCloseTo(-8 * (765 / 338), 8);
    expect(parsed.layers[0].transform.scaleX).toBe(1.5);
    expect(parsed.layers[0].transform.scaleY).toBe(1.5);
    expect(parsed.layers[0].text?.size).toBeCloseTo(56 * (765 / 338), 8);
    expect(parsed.layers[1].image?.width).toBeCloseTo(10 * (765 / 338), 8);
    expect(parsed.layers[1].image?.height).toBeCloseTo(5 * (765 / 338), 8);
    expect(parsed.layers[1].transform.scaleX).toBe(2);
    expect(parsed.layers[1].transform.scaleY).toBe(2);
  });

  it("rejects duplicate ids and unsupported image sources", () => {
    const duplicate = makeDocument();
    duplicate.layers[1] = { ...duplicate.layers[1], id: duplicate.layers[0].id };
    expect(() => validateDocument(duplicate)).toThrow(/duplicate layer ids/);

    const image = makeDocument();
    image.layers.push({
      id: "png",
      name: "Remote",
      kind: "image",
      effect: null,
      blend: "normal",
      opacity: 1,
      transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
      visible: true,
      image: { assetId: "png", src: "https://example.com/a.png", width: 10, height: 10 },
    });
    expect(() => validateDocument(image)).toThrow(/embedded image/);
  });

  it("rejects more than twelve PNG layers", () => {
    const document = makeDocument();
    for (let index = 0; index < 13; index += 1) {
      document.layers.push({
        id: `png-${index}`,
        name: `PNG ${index}`,
        kind: "image",
        effect: null,
        blend: "normal",
        opacity: 1,
        transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
        visible: true,
        image: { assetId: `png-${index}`, src: "data:image/png;base64,AA==", width: 1, height: 1 },
      });
    }
    expect(() => validateDocument(document)).toThrow(/more than 12 PNG/);
  });

  it("rejects a sixty-fifth layer", () => {
    const document = makeDocument();
    while (document.layers.length < 65) {
      document.layers.push({
        ...document.layers[1],
        id: `shape-${document.layers.length}`,
      });
    }
    expect(() => validateDocument(document)).toThrow(/too many layers/);
  });

  it("builds a readable project filename", () => {
    expect(projectFilename(makeDocument())).toBe("open-studio.twin");
  });
});
