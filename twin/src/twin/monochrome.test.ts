import { describe, expect, it } from "vitest";
import { ADD_COLORS, MONO_COLORS, SHADER_COLORS } from "./presets";

function channels(hex: string): [number, number, number] {
  const value = hex.replace("#", "");
  return [0, 2, 4].map((offset) => Number.parseInt(value.slice(offset, offset + 2), 16)) as [number, number, number];
}

function isNeutral(hex: string): boolean {
  const [red, green, blue] = channels(hex);
  return Math.max(red, green, blue) - Math.min(red, green, blue) <= 10;
}

function luminance(hex: string): number {
  const [red, green, blue] = channels(hex).map((channel) => {
    const normalized = channel / 255;
    return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrast(a: string, b: string): number {
  return (Math.max(luminance(a), luminance(b)) + 0.05) / (Math.min(luminance(a), luminance(b)) + 0.05);
}

describe("monochrome palette", () => {
  it("keeps every generated palette neutral", () => {
    for (const color of [...MONO_COLORS, ...ADD_COLORS, ...SHADER_COLORS]) {
      expect(isNeutral(color), color).toBe(true);
    }
  });

  it("keeps the generated palette ordered from paper to ink", () => {
    const values = MONO_COLORS.map((color) => channels(color)[0]);
    expect(values).toEqual([...values].sort((left, right) => right - left));
    expect(values[0]).toBe(250);
    expect(values.at(-1)).toBe(17);
  });

  it("keeps ink and dim text well past normal-text contrast", () => {
    expect(contrast("#111111", "#fafaf7")).toBeGreaterThan(7);
    expect(contrast("#5a5a56", "#fafaf7")).toBeGreaterThan(4.5);
  });
});
