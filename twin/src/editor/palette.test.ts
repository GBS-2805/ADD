import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { EDITOR_PALETTE } from "./palette";

function channels(hex: string): [number, number, number] {
  const value = hex.replace("#", "");
  return [0, 2, 4].map((offset) => Number.parseInt(value.slice(offset, offset + 2), 16)) as [
    number,
    number,
    number,
  ];
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

describe("editor palette", () => {
  it("uses the approved graphite, cream, paper and red hex set", () => {
    expect(EDITOR_PALETTE).toEqual({
      studio: "#131513",
      panel: "#1B1D1A",
      raised: "#232622",
      rule: "#343832",
      cream: "#F2EEE3",
      muted: "#A9AA9F",
      red: "#DD4124",
      paper: "#E9E0CB",
      sheet: "#FAFAF7",
      ink: "#111111",
    });
  });

  it("keeps cream and muted text readable on the graphite shell", () => {
    expect(contrast(EDITOR_PALETTE.cream, EDITOR_PALETTE.studio)).toBeGreaterThanOrEqual(7);
    expect(contrast(EDITOR_PALETTE.muted, EDITOR_PALETTE.studio)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(EDITOR_PALETTE.muted, EDITOR_PALETTE.panel)).toBeGreaterThanOrEqual(4.5);
  });

  it("keeps colour literals out of editor.css outside the white print page", () => {
    const css = readFileSync(resolve(process.cwd(), "src/editor/editor.css"), "utf8");
    const literals = new Set(
      [...css.matchAll(/#[0-9a-f]{3,8}\b/gi)].map((match) => match[0].toLowerCase()),
    );
    expect(literals).toEqual(new Set(["#fff"]));
  });

  it("uses red only as an accent token, never as a decorative hard-coded fill", () => {
    const css = readFileSync(resolve(process.cwd(), "src/editor/editor.css"), "utf8");
    expect(css).not.toContain(EDITOR_PALETTE.red.toLowerCase());
    expect(EDITOR_PALETTE.red).toBe("#DD4124");
  });
});
