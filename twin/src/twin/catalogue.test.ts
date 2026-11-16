import { describe, expect, it } from "vitest";
import { filterCatalogue, sameEffect } from "./catalogue";
import type { TwinEffect, TwinPlugin } from "./types";

const plugin = (name: string, blurb: string, group: TwinPlugin["group"], tags: string[]): TwinPlugin => ({
  id: name.toLowerCase(),
  name,
  blurb,
  group,
  tags,
  glyph: null,
  reality: "needs-native",
  tier: 1,
  panel: () => null,
});

const plugins = [
  plugin("Shader Lab", "Paper shaders with colour and grain", "Effects", ["gradient", "mesh"]),
  plugin("Overlay Lab", "PNG layers with sixteen blend modes", "Overlay", ["png", "blend"]),
  plugin("Texture Lab", "Paper, fluted glass and fur", "Texture", ["paper", "fur"]),
];

describe("filterCatalogue", () => {
  it("returns every plugin for a blank query", () => {
    expect(filterCatalogue(plugins, "   ")).toEqual(plugins);
  });

  it("matches names, blurbs, groups and tags without regard to case", () => {
    expect(filterCatalogue(plugins, "SHADER")).toEqual([plugins[0]]);
    expect(filterCatalogue(plugins, "sixteen")).toEqual([plugins[1]]);
    expect(filterCatalogue(plugins, "texture")).toEqual([plugins[2]]);
    expect(filterCatalogue(plugins, "gradient")).toEqual([plugins[0]]);
  });

  it("returns an empty list when nothing matches", () => {
    expect(filterCatalogue(plugins, "vaporwave")).toEqual([]);
  });
});

describe("sameEffect", () => {
  const shader: TwinEffect = { kind: "shader", shader: "MeshGradient", preset: "Ink", controls: { speed: 0.5 } };

  it("treats structurally equal effects as the same effect", () => {
    expect(sameEffect(shader, { ...shader, controls: { speed: 0.5 } })).toBe(true);
    expect(sameEffect(null, null)).toBe(true);
  });

  it("detects different controls and different kinds", () => {
    expect(sameEffect(shader, { ...shader, controls: { speed: 0.8 } })).toBe(false);
    expect(sameEffect(shader, { kind: "solid", hex: "#F5ECD8" })).toBe(false);
  });
});
