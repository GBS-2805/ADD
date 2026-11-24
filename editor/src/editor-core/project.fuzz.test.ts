import { describe, expect, it, vi } from "vitest";
import { MAX_IMAGE_LAYERS, MAX_LAYERS } from "../editor/model/geometry";
import {
  MAX_PROJECT_BYTES,
  PROJECT_FORMAT,
  PROJECT_VERSION,
  ProjectFileError,
  parseProjectFile,
  serializeProject,
} from "./project";
import type { EditorDocument, EditorLayer } from "./types";

/**
 * Deterministic parser fuzz. Every case comes from a fixed seed, so a failure is
 * reproducible from the seed and mutation index in its message; there is no
 * Math.random() anywhere in this file.
 */
function mulberry32(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

const SEEDS = [0x5eed, 0xc0ffee, 0x12345678] as const;
const MUTATIONS_PER_SEED = 300;
const PNG = "data:image/png;base64,AA==";

const textLayer = (id = "headline"): EditorLayer => ({
  id,
  name: "Type / OPEN STUDIO",
  kind: "text",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 0, y: -8, scaleX: 1, scaleY: 1, rotation: 0 },
  visible: true,
  text: { content: "OPEN", size: 154, align: "center", font: "instrument-serif" },
  fill: "#111111",
});

const shapeLayer = (id = "star"): EditorLayer => ({
  id,
  name: "Shape / Star",
  kind: "shape",
  effect: null,
  blend: "multiply",
  opacity: 0.9,
  transform: { x: -92, y: 86, scaleX: 1.05, scaleY: 1.05, rotation: -9 },
  visible: true,
  shape: { variant: "star" },
  fill: "#111111",
});

const imageLayer = (id = "png"): EditorLayer => ({
  id,
  name: "PNG",
  kind: "image",
  effect: null,
  blend: "normal",
  opacity: 1,
  transform: { x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 },
  visible: true,
  image: { assetId: id, src: PNG, width: 1, height: 1 },
});

const baseDocument = (): EditorDocument => ({
  background: { kind: "solid", hex: "#fafaf7" },
  layers: [textLayer(), shapeLayer()],
  selectedLayerIds: ["headline"],
});

const projectSource = (document: unknown, version: number = PROJECT_VERSION, format: string = PROJECT_FORMAT) =>
  JSON.stringify({ format, version, savedAt: "2026-11-24T00:00:00.000Z", document });

const pick = <T>(rng: () => number, values: readonly T[]): T =>
  values[Math.floor(rng() * values.length)];

const svgDataUrl = (markup: string) => `data:image/svg+xml;base64,${btoa(markup)}`;

type Builder = (rng: () => number) => { label: string; source: string };

/**
 * Structured mutations, not random bytes: each builder moves one documented
 * boundary (header, layer count, effect controls, embedded source, selection).
 */
const builders: Builder[] = [
  // Malformed JSON.
  (rng) => {
    const source = projectSource(baseDocument());
    return { label: "truncated json", source: source.slice(0, Math.floor(rng() * (source.length - 1))) };
  },
  () => ({ label: "trailing comma", source: projectSource(baseDocument()).replace(/\}\s*$/, ",}") }),
  () => ({ label: "top-level null", source: "null" }),
  () => ({ label: "top-level array", source: "[]" }),
  () => ({ label: "top-level number", source: "42" }),
  (rng) => {
    const depth = 32 + Math.floor(rng() * 4096);
    return { label: "deeply nested arrays", source: "[".repeat(depth) + "]".repeat(depth) };
  },
  (rng) => {
    const document = baseDocument();
    document.layers[0].text!.content = "x".repeat(4000 + Math.floor(rng() * 120_000));
    return { label: "overlong layer text", source: projectSource(document) };
  },
  // Header.
  () => ({ label: "wrong format", source: projectSource(baseDocument(), PROJECT_VERSION, "other-format") }),
  (rng) => {
    const version = pick(rng, [0, 1, 3, 999]);
    return { label: `version ${version}`, source: projectSource(baseDocument(), version) };
  },
  () => {
    const payload = JSON.parse(projectSource(baseDocument())) as Record<string, unknown>;
    delete payload.document;
    return { label: "missing document", source: JSON.stringify(payload) };
  },
  (rng) => {
    const document = pick(rng, [[], "document", 1, null] as const);
    return {
      label: "document of the wrong type",
      source: JSON.stringify({ format: PROJECT_FORMAT, version: PROJECT_VERSION, savedAt: "x", document }),
    };
  },
  // Layers.
  () => ({
    label: "zero layers",
    source: projectSource({ background: null, layers: [], selectedLayerIds: [] }),
  }),
  () => ({
    label: "64 layers",
    source: projectSource({
      ...baseDocument(),
      layers: Array.from({ length: MAX_LAYERS }, (_, index) => shapeLayer(`shape-${index}`)),
      selectedLayerIds: [],
    }),
  }),
  () => ({
    label: "12 image layers",
    source: projectSource({
      ...baseDocument(),
      layers: Array.from({ length: MAX_IMAGE_LAYERS }, (_, index) => imageLayer(`png-${index}`)),
      selectedLayerIds: [],
    }),
  }),
  (rng) => {
    const document = baseDocument();
    const layer = pick(rng, document.layers);
    (layer as unknown as { kind: string }).kind = "video";
    return { label: "unsupported layer kind", source: projectSource(document) };
  },
  () => {
    const document = baseDocument();
    document.layers[1].id = document.layers[0].id;
    return { label: "duplicate layer ids", source: projectSource(document) };
  },
  () => {
    const document = baseDocument();
    document.layers[0].id = "";
    return { label: "empty layer id", source: projectSource(document) };
  },
  (rng) => {
    const document = baseDocument();
    (document.layers[0] as unknown as { blend: string }).blend = pick(rng, [
      "unsupported-mode",
      "",
      "normal ",
    ]);
    return { label: "invalid blend mode", source: projectSource(document) };
  },
  (rng) => {
    const document = baseDocument();
    (document.layers[0] as unknown as { opacity: unknown }).opacity = pick(rng, [
      1.5,
      -0.01,
      "1",
      null,
    ]);
    return { label: "invalid opacity", source: projectSource(document) };
  },
  (rng) => {
    const document = baseDocument();
    const mutation = pick(rng, ["x", "scaleX", "scaleY", "rotation"] as const);
    const value = pick(rng, [1e6, 0, 101, 360_001, Number.NaN, Number.POSITIVE_INFINITY]);
    document.layers[0].transform[mutation] = value;
    return { label: `transform ${mutation}=${String(value)}`, source: projectSource(document) };
  },
  () => {
    const document = baseDocument();
    delete (document.layers[0] as unknown as { transform?: unknown }).transform;
    return { label: "missing transform", source: projectSource(document) };
  },
  (rng) => {
    const document = baseDocument();
    const layer = imageLayer("source");
    (layer.image as unknown as { src: unknown }).src = pick(rng, [
      "https://example.com/a.png",
      "data:image/gif;base64,AA==",
      "data:image/png;base64,@@@",
      "data:image/png;base64,",
      "data:image/jpeg;base64,AA==",
      svgDataUrl('<svg xmlns="http://www.w3.org/2000/svg"><image href="https://example.com/x.png"/></svg>'),
      "data:image/svg+xml;base64,////",
    ]);
    document.layers.push(layer);
    return { label: "hostile image source", source: projectSource(document) };
  },
  (rng) => {
    const document = baseDocument();
    const kind = pick(rng, ["solid", "field", "shader", "texture"] as const);
    if (kind === "solid") document.background = { kind, hex: pick(rng, ["#12345", "red", "#GGGGGG"]) } as never;
    if (kind === "field") {
      document.background = {
        kind,
        preset: "riso",
        colors: Array.from({ length: pick(rng, [0, 7]) }, () => "#111111"),
      } as never;
    }
    if (kind === "shader") {
      const controls = Object.fromEntries(
        Array.from({ length: pick(rng, [33, 40]) }, (_, index) => [`k${index}`, index / 10]),
      );
      document.background = { kind, shader: "noise", preset: "grain", controls } as never;
    }
    if (kind === "texture") {
      document.background = {
        kind,
        preset: "paper",
        image: pick(rng, ["data:image/jpeg;base64,AA==", "data:image/webp;base64,AA==", "data:image/gif;base64,AA=="]),
        controls: {},
      } as never;
    }
    return { label: `effect boundary (${kind})`, source: projectSource(document) };
  },
  (rng) => {
    const document = baseDocument();
    document.background = {
      kind: "shader",
      shader: "noise",
      preset: "grain",
      controls: pick(rng, [
        { ["k".repeat(41)]: 1 },
        { color: "red" },
        { colour: "#1234567" },
        { amount: Number.NaN },
      ]),
    } as never;
    return { label: "invalid effect control", source: projectSource(document) };
  },
  () => {
    const document = baseDocument();
    document.background = { kind: "gradient-mesh-v2", stops: [0, 1] } as never;
    return { label: "unknown effect kind", source: projectSource(document) };
  },
  (rng) => {
    const document = baseDocument();
    (document as unknown as { selectedLayerIds: unknown }).selectedLayerIds = pick(rng, [
      "headline",
      [1, 2],
      {},
    ]);
    return { label: "invalid selection", source: projectSource(document) };
  },
  () => {
    const label = "unknown top-level fields";
    const payload = JSON.parse(projectSource(baseDocument())) as Record<string, unknown>;
    payload.comment = { kept: true };
    payload.flags = [1, 2, 3];
    return { label, source: JSON.stringify(payload) };
  },
];

type Outcome = "document" | "rejected";

interface SeedRun {
  labels: string[];
  outcomes: Outcome[];
}

/**
 * Accepts exactly two outcomes: a document, or a ProjectFileError. Anything else
 * is a parser crash and fails with the seed and mutation index that produced it.
 */
function runSeed(seed: number): SeedRun {
  const rng = mulberry32(seed);
  const labels: string[] = [];
  const outcomes: Outcome[] = [];
  for (let index = 0; index < MUTATIONS_PER_SEED; index += 1) {
    const builder = builders[index % builders.length];
    const { label, source } = builder(rng);
    const context = `seed 0x${seed.toString(16)} mutation ${index} (${label})`;
    labels.push(label);

    let parsed: EditorDocument | null = null;
    try {
      parsed = parseProjectFile(source);
    } catch (error) {
      if (!(error instanceof ProjectFileError)) {
        const name = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
        throw new Error(`${context} escaped as ${name}`, { cause: error });
      }
      outcomes.push("rejected");
      continue;
    }

    if (!Array.isArray(parsed.layers)) throw new Error(`${context} returned a non-document`);
    outcomes.push("document");
  }
  return { labels, outcomes };
}

describe("the .add parser under seeded mutation (300 per seed, 900 total)", () => {
  for (const seed of SEEDS) {
    it(`seed 0x${seed.toString(16)}: every mutation is a document or a ProjectFileError`, () => {
      const first = runSeed(seed);
      expect(first.outcomes).toHaveLength(MUTATIONS_PER_SEED);

      const rejected = first.outcomes.filter((outcome) => outcome === "rejected").length;
      const accepted = first.outcomes.length - rejected;
      // The corpus is boundary-heavy on purpose: most cases must reject, but a
      // fuzzer that never accepts a document would prove nothing about valid files.
      expect(rejected).toBeGreaterThan(200);
      expect(accepted).toBeGreaterThan(0);

      // Same seed must produce the same labels and outcomes, case for case.
      const second = runSeed(seed);
      expect(second).toEqual(first);
    });
  }

  it("covers 900 mutations across the three seeds", () => {
    expect(SEEDS.length * MUTATIONS_PER_SEED).toBe(900);
  });
});

describe("the valid corpus parses", () => {
  const cases: [string, EditorDocument][] = [
    ["empty document", { background: null, layers: [], selectedLayerIds: [] }],
    ["text and shape", baseDocument()],
    ["one image", { ...baseDocument(), layers: [imageLayer()], selectedLayerIds: [] }],
    [
      "12 images",
      {
        ...baseDocument(),
        layers: Array.from({ length: MAX_IMAGE_LAYERS }, (_, index) => imageLayer(`png-${index}`)),
        selectedLayerIds: [],
      },
    ],
    [
      "64 layers",
      {
        ...baseDocument(),
        layers: Array.from({ length: MAX_LAYERS }, (_, index) => shapeLayer(`shape-${index}`)),
        selectedLayerIds: [],
      },
    ],
  ];

  for (const [label, document] of cases) {
    it(`accepts ${label}`, () => {
      const parsed = parseProjectFile(projectSource(document));
      expect(parsed.layers).toHaveLength(document.layers.length);
      expect(parsed.selectedLayerIds.every((id) => parsed.layers.some((layer) => layer.id === id))).toBe(true);
    });
  }

  it("migrates a v1 file through the same entry point", () => {
    const v1 = {
      background: null,
      selectedLayerId: "legacy",
      layers: [
        {
          id: "legacy",
          name: "Type / LEGACY",
          kind: "text",
          effect: null,
          blend: "normal",
          opacity: 1,
          transform: { x: 10, y: 10, scale: 1, rotation: 0 },
          visible: true,
          text: { content: "LEGACY", size: 56, align: "center", font: "instrument-serif" },
          fill: "#111111",
        },
      ],
    };
    const parsed = parseProjectFile(projectSource(v1, 1));
    expect(parsed.selectedLayerIds).toEqual(["legacy"]);
    expect(parsed.layers[0].transform.scaleX).toBe(1);
  });

  it("round-trips an unknown effect without nesting a wrapper on save", async () => {
    const future = {
      kind: "gradient-mesh-v2",
      preset: "riso-falloff",
      stops: [{ at: 0.2, hex: "#111111" }, { at: 0.8, hex: "#8a8983" }],
    };
    const document = baseDocument();
    document.background = future as never;

    const first = parseProjectFile(projectSource(document));
    expect(first.background).toMatchObject({ kind: "unsupported", originalKind: "gradient-mesh-v2" });

    const savedOnce = JSON.parse(await serializeProject(first)) as { document: { background: unknown } };
    expect(savedOnce.document.background).toEqual(future);
    expect(JSON.stringify(savedOnce.document.background)).not.toContain("unsupported");

    const second = parseProjectFile(JSON.stringify({ ...savedOnce, format: PROJECT_FORMAT, version: PROJECT_VERSION, savedAt: "x" }));
    const savedTwice = JSON.parse(await serializeProject(second)) as { document: { background: unknown } };
    expect(savedTwice.document.background).toEqual(future);
  });
});

describe("length boundary", () => {
  it("rejects one character over the cap before parsing", () => {
    const parse = vi.spyOn(JSON, "parse");
    try {
      const over = " ".repeat(MAX_PROJECT_BYTES + 1);
      expect(() => parseProjectFile(over)).toThrow(ProjectFileError);
      expect(parse).not.toHaveBeenCalled();
    } finally {
      parse.mockRestore();
    }
  });

  it("accepts a source at exactly the cap", () => {
    const source = projectSource(baseDocument());
    const atCap = source + " ".repeat(MAX_PROJECT_BYTES - source.length);
    expect(atCap).toHaveLength(MAX_PROJECT_BYTES);
    expect(parseProjectFile(atCap).layers).toHaveLength(2);
  });
});
