import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EditorProvider, useEditorDocument, useEditorStore, useTransientTransforms } from "../../editor-core/store";
import { Canvas } from "./Canvas";
import { StatusBar } from "../StatusBar";
import { docToScreen } from "../model/viewport";
import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH, layerFrame, referencePoint } from "../model/geometry";

const WIDTH = 1600;
const HEIGHT = 1000;
const rect = {
  width: WIDTH,
  height: HEIGHT,
  left: 0,
  top: 0,
  right: WIDTH,
  bottom: HEIGHT,
  x: 0,
  y: 0,
  toJSON: () => ({}),
} as DOMRect;

function Probe() {
  const store = useEditorStore();
  const { doc } = useEditorDocument();
  const transient = useTransientTransforms();
  const star = doc.layers.find((layer) => layer.id === "shape-star");
  return (
    <>
      <output data-testid="star-transform">{star ? JSON.stringify(star.transform) : ""}</output>
      <output data-testid="star-frame">{star ? JSON.stringify(layerFrame(star)) : ""}</output>
      <output data-testid="selection">{doc.selectedLayerIds.join(",")}</output>
      <output data-testid="transient">{JSON.stringify(transient)}</output>
      <button type="button" onClick={store.undo}>undo</button>
      <button type="button" onClick={() => store.setLayerTransform("shape-star", { rotation: 37 })}>rotate37</button>
    </>
  );
}

function renderCanvas() {
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect);
  render(
    <EditorProvider>
      <Canvas />
      <StatusBar />
      <Probe />
    </EditorProvider>,
  );
}

const viewport = { zoom: 1, panX: 0, panY: 0 };
const screenFor = (point: { x: number; y: number }) => docToScreen(point, viewport, { width: WIDTH, height: HEIGHT });
const screenForLayer = (point: { x: number; y: number }) => screenFor({ x: ARTBOARD_WIDTH / 2 + point.x, y: ARTBOARD_HEIGHT / 2 + point.y });
const starTransform = () => JSON.parse(screen.getByTestId("star-transform").textContent ?? "{}") as { x: number; y: number; scaleX: number; scaleY: number; rotation: number };
const starFrame = () => JSON.parse(screen.getByTestId("star-frame").textContent ?? "{}") as { x: number; y: number; width: number; height: number; rotation: number };
const selectStar = () => {
  const star = screen.getByTestId("art-layer-shape-star");
  const center = screenForLayer({ x: -208, y: 195 });
  fireEvent.pointerDown(star, { pointerId: 1, pointerType: "mouse", button: 0, clientX: center.x, clientY: center.y });
  fireEvent.pointerUp(star, { pointerId: 1, pointerType: "mouse", button: 0, clientX: center.x, clientY: center.y });
};

describe("Canvas pointer interactions", () => {
  beforeEach(() => {
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 1600 });
    Object.defineProperty(window, "innerHeight", { configurable: true, value: 1000 });
  });
  afterEach(() => vi.restoreAllMocks());

  it("dragsASelectionAndUndoesOnce", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    selectStar();
    await waitFor(() => expect(screen.getByTestId("selection").textContent).toBe("shape-star"));
    const before = starTransform();
    const center = screenForLayer({ x: -208, y: 195 });
    fireEvent.pointerDown(screen.getByTestId("art-layer-shape-star"), { pointerId: 2, pointerType: "mouse", button: 0, clientX: center.x, clientY: center.y });
    fireEvent.pointerMove(screen.getByTestId("canvas"), { pointerId: 2, pointerType: "mouse", clientX: center.x + 50, clientY: center.y + 50, metaKey: true });
    fireEvent.pointerUp(screen.getByTestId("canvas"), { pointerId: 2, pointerType: "mouse", button: 0, clientX: center.x + 50, clientY: center.y + 50, metaKey: true });
    await waitFor(() => expect(starTransform().x).not.toBe(before.x));
    fireEvent.click(screen.getByRole("button", { name: "undo" }));
    await waitFor(() => expect(starTransform().x).toBeCloseTo(before.x));
  });

  it("shiftClickBuildsMultiSelection", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    selectStar();
    const orbit = screen.getByTestId("art-layer-shape-orbit");
    const center = screenForLayer({ x: 217, y: 172 });
    fireEvent.pointerDown(orbit, { pointerId: 3, pointerType: "mouse", button: 0, shiftKey: true, clientX: center.x, clientY: center.y });
    fireEvent.pointerUp(orbit, { pointerId: 3, pointerType: "mouse", button: 0, shiftKey: true, clientX: center.x, clientY: center.y });
    await waitFor(() => expect(screen.getByTestId("selection").textContent).toBe("shape-star,shape-orbit"));
    expect(screen.getByTestId("selection-overlay")).toHaveAttribute("data-selection-count", "2");
  });

  it("marqueeSelectsLayers", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    const canvas = screen.getByTestId("canvas");
    const start = screenFor({ x: 100, y: 50 });
    const end = screenFor({ x: 350, y: 350 });
    fireEvent.pointerDown(canvas, { pointerId: 4, pointerType: "mouse", button: 0, clientX: start.x, clientY: start.y });
    fireEvent.pointerMove(canvas, { pointerId: 4, pointerType: "mouse", clientX: end.x, clientY: end.y });
    expect(screen.getByTestId("marquee")).toBeInTheDocument();
    fireEvent.pointerUp(canvas, { pointerId: 4, pointerType: "mouse", button: 0, clientX: end.x, clientY: end.y });
    await waitFor(() => expect(Number(screen.getByTestId("selection-overlay").getAttribute("data-selection-count"))).toBeGreaterThan(0));
  });

  it("resizesEastHandleAndKeepsWestEdgeFixed", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    selectStar();
    const before = starTransform();
    const frame = starFrame();
    const east = referencePoint(frame, "e");
    const start = screenForLayer(east);
    const handle = screen.getByTestId("selection-overlay").querySelector('[data-handle="e"]') as HTMLElement;
    fireEvent.pointerDown(handle, { pointerId: 5, pointerType: "mouse", button: 0, clientX: start.x, clientY: start.y });
    fireEvent.pointerMove(screen.getByTestId("canvas"), { pointerId: 5, pointerType: "mouse", clientX: start.x + 50, clientY: start.y, metaKey: true });
    fireEvent.pointerUp(screen.getByTestId("canvas"), { pointerId: 5, pointerType: "mouse", button: 0, clientX: start.x + 50, clientY: start.y, metaKey: true });
    await waitFor(() => expect(starTransform().scaleX).toBeGreaterThan(before.scaleX));
    const afterFrame = starFrame();
    expect(afterFrame.x - afterFrame.width / 2).toBeCloseTo(frame.x - frame.width / 2, 0);
  });

  it("pointerCancelLeavesDocumentUnchanged", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    selectStar();
    const before = starTransform();
    const center = screenForLayer({ x: -208, y: 195 });
    fireEvent.pointerDown(screen.getByTestId("art-layer-shape-star"), { pointerId: 6, pointerType: "mouse", button: 0, clientX: center.x, clientY: center.y });
    fireEvent.pointerMove(screen.getByTestId("canvas"), { pointerId: 6, pointerType: "mouse", clientX: center.x + 40, clientY: center.y, metaKey: true });
    fireEvent.pointerCancel(screen.getByTestId("canvas"), { pointerId: 6, pointerType: "mouse" });
    await waitFor(() => expect(screen.getByTestId("transient").textContent).toBe("null"));
    expect(starTransform().x).toBeCloseTo(before.x);
  });

  it("secondPointerCancelsMoveWithoutCommit", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    selectStar();
    const before = starTransform();
    const center = screenForLayer({ x: -208, y: 195 });
    const canvas = screen.getByTestId("canvas");
    fireEvent.pointerDown(screen.getByTestId("art-layer-shape-star"), { pointerId: 21, pointerType: "touch", button: 0, clientX: center.x, clientY: center.y });
    fireEvent.pointerMove(canvas, { pointerId: 21, pointerType: "touch", clientX: center.x + 20, clientY: center.y });
    fireEvent.pointerDown(canvas, { pointerId: 22, pointerType: "touch", button: 0, clientX: center.x + 100, clientY: center.y });
    fireEvent.pointerUp(canvas, { pointerId: 21, pointerType: "touch", button: 0, clientX: center.x + 20, clientY: center.y });
    await waitFor(() => expect(starTransform().x).toBeCloseTo(before.x));
  });

  it("rotatedResizeDoesNotDriftAfterPointerCancel", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    selectStar();
    fireEvent.click(screen.getByRole("button", { name: "rotate37" }));
    await waitFor(() => expect(starTransform().rotation).toBeCloseTo(37));
    const before = starTransform();
    const frame = starFrame();
    const east = referencePoint(frame, "e");
    const start = screenForLayer(east);
    const handle = screen.getByTestId("selection-overlay").querySelector('[data-handle="e"]') as HTMLElement;
    fireEvent.pointerDown(handle, { pointerId: 31, pointerType: "mouse", button: 0, clientX: start.x, clientY: start.y });
    fireEvent.pointerMove(screen.getByTestId("canvas"), { pointerId: 31, pointerType: "mouse", clientX: start.x + 80, clientY: start.y + 40, metaKey: true });
    fireEvent.pointerCancel(screen.getByTestId("canvas"), { pointerId: 31, pointerType: "mouse" });
    await waitFor(() => expect(screen.getByTestId("transient").textContent).toBe("null"));
    expect(starTransform().scaleX).toBeCloseTo(before.scaleX);
    expect(starTransform().scaleY).toBeCloseTo(before.scaleY);
    expect(starTransform().rotation).toBeCloseTo(before.rotation);
  });

  it("rotatesWithRotationHandle", async () => {
    renderCanvas();
    await waitFor(() => expect(screen.getByTestId("canvas")).toHaveAttribute("data-zoom"));
    fireEvent.click(screen.getByTestId("zoom-100"));
    selectStar();
    const before = starTransform();
    const frame = starFrame();
    const center = screenForLayer({ x: frame.x, y: frame.y });
    const handle = screen.getByTestId("selection-overlay").querySelector('[data-handle="rotate"]') as HTMLElement;
    fireEvent.pointerDown(handle, { pointerId: 7, pointerType: "mouse", button: 0, clientX: center.x, clientY: center.y - 100 });
    fireEvent.pointerMove(screen.getByTestId("canvas"), { pointerId: 7, pointerType: "mouse", clientX: center.x + 100, clientY: center.y, shiftKey: true });
    fireEvent.pointerUp(screen.getByTestId("canvas"), { pointerId: 7, pointerType: "mouse", button: 0, clientX: center.x + 100, clientY: center.y, shiftKey: true });
    await waitFor(() => expect(starTransform().rotation).not.toBe(before.rotation));
  });
});
