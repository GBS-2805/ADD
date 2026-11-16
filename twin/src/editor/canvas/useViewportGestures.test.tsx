import { act, fireEvent, render, screen } from "@testing-library/react";
import { useRef, useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useViewportGestures } from "./useViewportGestures";
import { screenToDoc } from "../model/viewport";
import type { Viewport } from "../types";

const rect = {
  width: 1000,
  height: 800,
  left: 0,
  top: 0,
  right: 1000,
  bottom: 800,
  x: 0,
  y: 0,
  toJSON: () => ({}),
} as DOMRect;

function Harness({ initial, onPinchStart }: { initial: Viewport; onPinchStart?: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [viewport, setViewport] = useState(initial);
  const gestures = useViewportGestures({
    containerRef: ref,
    viewport,
    onChange: setViewport,
    onPinchStart,
    panEnabled: true,
  });
  return (
    <div
      ref={ref}
      data-testid="gesture-surface"
      data-zoom={viewport.zoom}
      data-pan={`${viewport.panX},${viewport.panY}`}
      data-pinching={gestures.isPinching() ? "true" : "false"}
      style={{ width: 1000, height: 800 }}
    />
  );
}

const readViewport = (): Viewport => {
  const surface = screen.getByTestId("gesture-surface");
  const [panX, panY] = (surface.getAttribute("data-pan") ?? "0,0").split(",").map(Number);
  return { zoom: Number(surface.getAttribute("data-zoom")), panX, panY };
};

describe("useViewportGestures", () => {
  afterEach(() => vi.restoreAllMocks());

  it("wheelZoomPreservesPointerDocPoint", () => {
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect);
    render(<Harness initial={{ zoom: 1, panX: 0, panY: 0 }} />);
    const surface = screen.getByTestId("gesture-surface");
    const pointer = { x: 740, y: 280 };
    const before = screenToDoc(pointer, readViewport(), { width: 1000, height: 800 });
    const event = new WheelEvent("wheel", {
      deltaY: -100,
      ctrlKey: true,
      clientX: pointer.x,
      clientY: pointer.y,
      bubbles: true,
      cancelable: true,
    });
    act(() => surface.dispatchEvent(event));
    const after = screenToDoc(pointer, readViewport(), { width: 1000, height: 800 });
    expect(readViewport().zoom).toBeGreaterThan(1);
    expect(after.x).toBeCloseTo(before.x, 5);
    expect(after.y).toBeCloseTo(before.y, 5);
    expect(event.defaultPrevented).toBe(true);
  });

  it("wheelZoomDoesNotScrollPage", () => {
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect);
    render(<Harness initial={{ zoom: 1, panX: 0, panY: 0 }} />);
    const event = new WheelEvent("wheel", { deltaY: 80, bubbles: true, cancelable: true });
    act(() => screen.getByTestId("gesture-surface").dispatchEvent(event));
    expect(event.defaultPrevented).toBe(true);
  });

  it("plainWheelPansWithoutZoom", () => {
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect);
    render(<Harness initial={{ zoom: 1.5, panX: 0, panY: 0 }} />);
    const event = new WheelEvent("wheel", {
      deltaX: 20,
      deltaY: 30,
      bubbles: true,
      cancelable: true,
    });
    act(() => screen.getByTestId("gesture-surface").dispatchEvent(event));
    expect(readViewport()).toEqual({ zoom: 1.5, panX: -20, panY: -30 });
    expect(event.defaultPrevented).toBe(true);
  });

  it("pinchZoomPreservesMidpoint", () => {
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect);
    render(<Harness initial={{ zoom: 1, panX: 0, panY: 0 }} />);
    const surface = screen.getByTestId("gesture-surface");
    const startMid = { x: 500, y: 400 };
    const before = screenToDoc(startMid, readViewport(), { width: 1000, height: 800 });
    act(() => {
      fireEvent.pointerDown(surface, { pointerId: 1, pointerType: "touch", clientX: 400, clientY: 400, button: 0 });
      fireEvent.pointerDown(surface, { pointerId: 2, pointerType: "touch", clientX: 600, clientY: 400, button: 0 });
      fireEvent.pointerMove(surface, { pointerId: 2, pointerType: "touch", clientX: 700, clientY: 400 });
    });
    const nextMid = { x: 550, y: 400 };
    const after = screenToDoc(nextMid, readViewport(), { width: 1000, height: 800 });
    expect(readViewport().zoom).toBeCloseTo(1.5);
    expect(after.x).toBeCloseTo(before.x, 5);
    expect(after.y).toBeCloseTo(before.y, 5);
  });

  it("pinchClampsAtMinAndMax", () => {
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect);
    render(<Harness initial={{ zoom: 1, panX: 0, panY: 0 }} />);
    const surface = screen.getByTestId("gesture-surface");
    act(() => {
      fireEvent.pointerDown(surface, { pointerId: 1, pointerType: "touch", clientX: 400, clientY: 400, button: 0 });
      fireEvent.pointerDown(surface, { pointerId: 2, pointerType: "touch", clientX: 600, clientY: 400, button: 0 });
      fireEvent.pointerMove(surface, { pointerId: 2, pointerType: "touch", clientX: 100000, clientY: 400 });
    });
    expect(readViewport().zoom).toBe(8);
  });

  it("pointerCancelEndsPinch", () => {
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect);
    render(<Harness initial={{ zoom: 1, panX: 0, panY: 0 }} />);
    const surface = screen.getByTestId("gesture-surface");
    act(() => {
      fireEvent.pointerDown(surface, { pointerId: 1, pointerType: "touch", clientX: 400, clientY: 400, button: 0 });
      fireEvent.pointerDown(surface, { pointerId: 2, pointerType: "touch", clientX: 600, clientY: 400, button: 0 });
      fireEvent.pointerMove(surface, { pointerId: 2, pointerType: "touch", clientX: 700, clientY: 400 });
    });
    const zoom = readViewport().zoom;
    act(() => {
      fireEvent.pointerCancel(surface, { pointerId: 2, pointerType: "touch", clientX: 700, clientY: 400 });
      fireEvent.pointerMove(surface, { pointerId: 2, pointerType: "touch", clientX: 900, clientY: 400 });
    });
    expect(readViewport().zoom).toBe(zoom);
  });

  it("twoFingerGestureCancelsSinglePointerMove", () => {
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(rect);
    const onPinchStart = vi.fn();
    render(<Harness initial={{ zoom: 1, panX: 0, panY: 0 }} onPinchStart={onPinchStart} />);
    const surface = screen.getByTestId("gesture-surface");
    act(() => {
      fireEvent.pointerDown(surface, { pointerId: 1, pointerType: "touch", clientX: 400, clientY: 400, button: 0 });
      fireEvent.pointerDown(surface, { pointerId: 2, pointerType: "touch", clientX: 600, clientY: 400, button: 0 });
    });
    expect(onPinchStart).toHaveBeenCalledTimes(1);
    expect(surface).toHaveAttribute("data-pinching", "true");
  });
});
