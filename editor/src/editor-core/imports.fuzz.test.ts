import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { sanitizeSvgMarkup } from "./imports";

/**
 * Deterministic SVG boundary fuzz. The sanitizer is exercised directly (it throws
 * plain Errors by contract; the .add parser is what has to wrap them) and every
 * case runs with fetch/XHR traps installed: the sanitizer must decide locally,
 * never by loading a reference.
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

const fetchSpy = vi.fn(() => {
  throw new Error("the SVG sanitizer must not fetch");
});
class ForbiddenRequest {
  constructor() {
    throw new Error("the SVG sanitizer must not open a request");
  }
}

beforeEach(() => {
  fetchSpy.mockClear();
  vi.stubGlobal("fetch", fetchSpy);
  vi.stubGlobal("XMLHttpRequest", ForbiddenRequest);
});

afterEach(() => {
  vi.unstubAllGlobals();
  expect(fetchSpy).not.toHaveBeenCalled();
});

const ACTIVE_CONTENT = [
  /<script/i,
  /foreignObject/i,
  /\son[a-z]+\s*=/i,
  /href\s*=\s*["'](?:https?:|javascript:|data:text)/i,
];

const base = (inner: string) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">${inner}</svg>`;

describe("sanitizeSvgMarkup boundary cases", () => {
  const cases: [string, string, "sanitized" | "rejected"][] = [
    ["script element", base("<script>alert(1)</script><rect width='1' height='1'/>"), "sanitized"],
    [
      "foreignObject",
      base("<foreignObject><body xmlns='http://www.w3.org/1999/xhtml'/></foreignObject>"),
      "sanitized",
    ],
    ["event handler attribute", base('<rect width="1" height="1" onload="alert(1)" onclick="alert(2)"/>'), "sanitized"],
    ["remote href", base('<image href="https://example.com/x.png"/>'), "rejected"],
    ["remote xlink:href", base('<use xlink:href="https://example.com/y.svg#a"/>'), "rejected"],
    ["javascript url", base('<a href="javascript:alert(3)"><rect width="1" height="1"/></a>'), "rejected"],
    ["data text/html href", base('<a href="data:text/html;base64,PHNjcmlwdD4="/>'), "rejected"],
    ["malformed xml", '<svg xmlns="http://www.w3.org/2000/svg"><g></svg>', "rejected"],
    ["not an svg", "not an svg", "rejected"],
    ["empty input", "", "rejected"],
    ["local fragment reference", base('<use href="#mark"/>'), "sanitized"],
    ["embedded data image reference", base('<image href="data:image/png;base64,AA=="/>'), "sanitized"],
  ];

  for (const [label, markup, expected] of cases) {
    it(`${label} is ${expected}`, () => {
      if (expected === "rejected") {
        expect(() => sanitizeSvgMarkup(markup)).toThrow(Error);
        return;
      }
      const safe = sanitizeSvgMarkup(markup);
      for (const pattern of ACTIVE_CONTENT) expect(safe).not.toMatch(pattern);
    });
  }

  it("keeps re-serialization bounded when the output is larger than the input", () => {
    // XML serialization escapes ">" in text, so a compact input grows on output.
    const markup = '<svg xmlns="http://www.w3.org/2000/svg"><text>a > b</text></svg>';
    const safe = sanitizeSvgMarkup(markup);
    expect(safe.length).toBeGreaterThan(markup.length);
    expect(safe).toContain("&gt;");
    expect(safe.length).toBeLessThan(markup.length * 4);
  });
});

const attributePayloads = [
  'onload="alert(1)"',
  'onclick="alert(2)"',
  'onmouseover="alert(3)"',
  'href="https://example.com/x.png"',
  'href="javascript:alert(4)"',
  'xlink:href="https://example.com/y.svg#a"',
  'xlink:href="javascript:alert(5)"',
];

const elementPayloads = [
  "<script>alert(1)</script>",
  "<script><![CDATA[alert(2)]]></script>",
  "<foreignObject><body xmlns='http://www.w3.org/1999/xhtml'/></foreignObject>",
  '<a href="javascript:alert(3)"><rect width="1" height="1"/></a>',
  '<image href="https://example.com/z.png"/>',
  "<g><rect></g>",
  "<use xlink:href='#mark'/>",
];

const pick = <T>(rng: () => number, values: readonly T[]): T =>
  values[Math.floor(rng() * values.length)];

type Outcome = "sanitized" | "rejected";

interface SeedRun {
  outcomes: Outcome[];
  sources: string[];
}

function runSeed(seed: number): SeedRun {
  const rng = mulberry32(seed);
  const outcomes: Outcome[] = [];
  const sources: string[] = [];
  for (let index = 0; index < MUTATIONS_PER_SEED; index += 1) {
    const wrappers = Math.floor(rng() * 4);
    const inner =
      rng() < 0.5
        ? `<rect width="1" height="1" ${pick(rng, attributePayloads)}/>`
        : pick(rng, elementPayloads);
    const source = base(`${"<g>".repeat(wrappers)}${inner}${"</g>".repeat(wrappers)}`);
    sources.push(source);

    let safe: string | null = null;
    try {
      safe = sanitizeSvgMarkup(source);
    } catch (error) {
      if (!(error instanceof Error)) {
        throw new Error(
          `seed 0x${seed.toString(16)} mutation ${index} threw a non-Error: ${String(error)}`,
        );
      }
      outcomes.push("rejected");
      continue;
    }

    for (const pattern of ACTIVE_CONTENT) {
      if (pattern.test(safe)) {
        throw new Error(
          `seed 0x${seed.toString(16)} mutation ${index} left active content (${pattern}) in ${safe}`,
        );
      }
    }
    if (safe.length > source.length * 8 + 4096) {
      throw new Error(
        `seed 0x${seed.toString(16)} mutation ${index} expanded ${source.length} to ${safe.length}`,
      );
    }
    outcomes.push("sanitized");
  }
  return { outcomes, sources };
}

describe("sanitizeSvgMarkup under seeded mutation (300 per seed, 900 total)", () => {
  for (const seed of SEEDS) {
    it(`seed 0x${seed.toString(16)}: removes active content or rejects, and never requests a URL`, { timeout: 15_000 }, () => {
      const first = runSeed(seed);
      expect(first.outcomes).toHaveLength(MUTATIONS_PER_SEED);

      const rejected = first.outcomes.filter((outcome) => outcome === "rejected").length;
      // The corpus carries both removable payloads and reference payloads, so a run
      // that only ever rejects (or only ever sanitizes) would be a corpus bug.
      expect(rejected).toBeGreaterThan(0);
      expect(first.outcomes.length - rejected).toBeGreaterThan(0);

      expect(runSeed(seed)).toEqual(first);
    });
  }

  it("covers 900 mutations across the three seeds", () => {
    expect(SEEDS.length * MUTATIONS_PER_SEED).toBe(900);
  });
});
