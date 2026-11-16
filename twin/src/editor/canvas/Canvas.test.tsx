import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TwinProvider } from "../../twin/store";
import { Canvas } from "./Canvas";
import { StatusBar } from "../StatusBar";

const rect = (width: number, height: number) => ({
  width,
  height,
  left: 0,
  top: 0,
  right: width,
  bottom: height,
  x: 0,
  y: 0,
  toJSON: () => ({}),
}) as DOMRect;

function renderCanvas(width = 1000, height = 800) {
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect(width, height));
  return render(
    <TwinProvider>
      <Canvas />
      <StatusBar />
    </TwinProvider>,
  );
}

describe("Canvas", () => {
  beforeEach(() => {
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 1200 });
    Object.defineProperty(window, "innerHeight", { configurable: true, value: 900 });
  });
  afterEach(() => vi.restoreAllMocks());

  it("rendersArtboardAt765", async () => {
    renderCanvas();
    expect(screen.getByTestId("editor-artboard")).toHaveAttribute("data-artboard-width", "765");
    expect(screen.getByTestId("editor-artboard")).toHaveAttribute("data-artboard-height", "1000");
  });

  it("fitButtonCentersArtboard", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    expect(Number(screen.getByTestId("canvas").getAttribute("data-zoom"))).toBeCloseTo(1);
    const stage = screen.getByTestId("artboard-stage");
    fireEvent.click(screen.getByTestId("zoom-fit"));
    await waitFor(() => expect(Number(screen.getByTestId("canvas").getAttribute("data-zoom"))).toBeCloseTo(0.704, 2));
    expect(stage.style.transform).toContain("translate(calc(-50% + 0px), calc(-50% + 0px))");
  });

  it("spaceDragPansWithoutChangingDocument", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    const canvas = screen.getByTestId("canvas");
    const layer = screen.getByTestId("art-layer-shape-star");
    const beforeLeft = layer.style.left;
    fireEvent.keyDown(window, { key: " ", code: "Space" });
    fireEvent.pointerDown(canvas, { pointerId: 1, pointerType: "mouse", button: 0, clientX: 500, clientY: 400 });
    fireEvent.pointerMove(canvas, { pointerId: 1, pointerType: "mouse", button: 0, clientX: 550, clientY: 450 });
    fireEvent.pointerUp(canvas, { pointerId: 1, pointerType: "mouse", button: 0, clientX: 550, clientY: 450 });
    fireEvent.keyUp(window, { key: " ", code: "Space" });
    expect(screen.getByTestId("artboard-stage").style.transform).toContain("+ 50px");
    expect(layer.style.left).toBe(beforeLeft);
  });

  it("rulerToggles", async () => {
    renderCanvas();
    expect(screen.queryByTestId("ruler-x")).toBeNull();
    fireEvent.click(screen.getByTestId("toggle-rulers"));
    expect(screen.getByTestId("ruler-x")).toBeInTheDocument();
    expect(screen.getByTestId("ruler-y")).toBeInTheDocument();
  });

  it("touchActionIsNone", async () => {
    renderCanvas();
    expect(screen.getByTestId("canvas").style.touchAction).toBe("none");
  });

  it("noCanvasTextEditor", async () => {
    renderCanvas();
    expect(document.querySelector('[contenteditable="true"]')).toBeNull();
  });

  it("coverCanvasFitsAt349x316", async () => {
    renderCanvas(349, 316);
    await waitFor(() => {
      const zoom = Number(screen.getByTestId("canvas").getAttribute("data-zoom"));
      expect(zoom).toBeCloseTo(Math.max(0.1, Math.min((349 - 96) / 765, (316 - 96) / 1000)), 4);
    });
  });
});
