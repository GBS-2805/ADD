import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { useFoldSegments } from "./useFoldSegments";

function Probe() {
  const insets = useFoldSegments();
  return <output data-testid="insets">{JSON.stringify(insets)}</output>;
}

const setSegments = (segments: { x: number; y: number; width: number; height: number }[] | undefined) => {
  Object.defineProperty(window, "viewport", {
    configurable: true,
    value: segments ? { segments } : undefined,
  });
};

const setPosture = (type: "folded" | "continuous" | undefined) => {
  Object.defineProperty(navigator, "devicePosture", {
    configurable: true,
    value: type ? { type } : undefined,
  });
};

afterEach(() => {
  setSegments(undefined);
  setPosture(undefined);
});

describe("useFoldSegments", () => {
  it("readsTopBottomSegmentsAndGap", () => {
    setSegments([
      { x: 0, y: 420, width: 360, height: 420 },
      { x: 0, y: 0, width: 360, height: 420 },
    ]);
    render(<Probe />);
    expect(JSON.parse(screen.getByTestId("insets").textContent ?? "{}")).toEqual({
      orientation: "vertical",
      top: 420,
      bottom: 420,
      gap: 0,
      source: "segments",
    });
  });

  it("postureWithoutSegmentsDoesNotGuessAFold", () => {
    setPosture("folded");
    render(<Probe />);
    expect(JSON.parse(screen.getByTestId("insets").textContent ?? "{}")).toEqual({
      orientation: null,
      top: 0,
      bottom: 0,
      gap: 0,
      source: "posture",
    });
  });

  it("noFoldReturnsNoSource", () => {
    render(<Probe />);
    expect(JSON.parse(screen.getByTestId("insets").textContent ?? "{}")).toEqual({
      orientation: null,
      top: 0,
      bottom: 0,
      gap: 0,
    });
  });

  it("segmentsWinOverPosture", () => {
    setPosture("folded");
    setSegments([
      { x: 0, y: 0, width: 360, height: 410 },
      { x: 0, y: 430, width: 360, height: 410 },
    ]);
    render(<Probe />);
    expect(JSON.parse(screen.getByTestId("insets").textContent ?? "{}")).toMatchObject({
      orientation: "vertical",
      gap: 20,
      source: "segments",
    });
  });
});
