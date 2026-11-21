import { describe, expect, it } from "vitest";
import { rulerTicks } from "./ruler";

const majors = (ticks: ReturnType<typeof rulerTicks>) => ticks.filter((tick) => tick.major);

describe("ruler", () => {
  it("rulerPicksReadableMajorStep", () => {
    const ticks = rulerTicks(1, 100, 500);
    const major = majors(ticks);
    expect(major.length).toBeGreaterThan(2);
    expect(major[1].doc - major[0].doc).toBe(100);
    expect(major.every((tick) => tick.label === String(Math.round(tick.doc)))).toBe(true);
    expect(ticks.some((tick) => !tick.major)).toBe(true);
  });

  it("rulerLabelsAtZoom25And400", () => {
    for (const zoom of [0.25, 4]) {
      const ticks = rulerTicks(zoom, 0, 900);
      const major = majors(ticks);
      const spacing = (major[1].doc - major[0].doc) * zoom;
      expect(spacing).toBeGreaterThanOrEqual(50);
      expect(spacing).toBeLessThanOrEqual(160);
      expect(major.every((tick) => typeof tick.label === "string" && tick.label.length > 0)).toBe(true);
    }
  });

  it("includes negative document coordinates outside the artboard", () => {
    const ticks = rulerTicks(1, 300, 400);
    expect(ticks[0].doc).toBeLessThanOrEqual(-300);
    expect(ticks.some((tick) => tick.doc < 0)).toBe(true);
  });
});
