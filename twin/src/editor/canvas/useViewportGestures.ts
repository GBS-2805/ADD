import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import { clampViewport, clampZoom, screenToDoc, zoomAt } from "../model/viewport";
import type { Viewport } from "../types";
import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH } from "../model/geometry";

export interface ViewportGestureOptions {
  containerRef: RefObject<HTMLElement | null>;
  viewport: Viewport;
  onChange: (viewport: Viewport) => void;
  enabled?: boolean;
  panEnabled?: boolean;
  onPinchStart?: () => void;
}

interface TrackedPointer { x: number; y: number }
interface PanState {
  pointerId: number;
  startX: number;
  startY: number;
  startViewport: Viewport;
}
interface PinchState {
  startDistance: number;
  startViewport: Viewport;
  docMid: { x: number; y: number };
}

const distance = (a: TrackedPointer, b: TrackedPointer) => Math.hypot(a.x - b.x, a.y - b.y) || 1;
const midpoint = (a: TrackedPointer, b: TrackedPointer) => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });

export function useViewportGestures({
  containerRef,
  viewport,
  onChange,
  enabled = true,
  panEnabled = false,
  onPinchStart,
}: ViewportGestureOptions) {
  const viewportRef = useRef(viewport);
  const onChangeRef = useRef(onChange);
  const panEnabledRef = useRef(panEnabled);
  const onPinchStartRef = useRef(onPinchStart);
  const pointersRef = useRef(new Map<number, TrackedPointer>());
  const panRef = useRef<PanState | null>(null);
  const pinchRef = useRef<PinchState | null>(null);
  const [pinching, setPinching] = useState(false);

  viewportRef.current = viewport;
  onChangeRef.current = onChange;
  panEnabledRef.current = panEnabled;
  onPinchStartRef.current = onPinchStart;

  const localPoint = useCallback((event: PointerEvent | WheelEvent) => {
    const rect = containerRef.current?.getBoundingClientRect();
    return {
      x: event.clientX - (rect?.left ?? 0),
      y: event.clientY - (rect?.top ?? 0),
      size: { width: rect?.width ?? 0, height: rect?.height ?? 0 },
    };
  }, [containerRef]);

  const beginPinch = useCallback(() => {
    const pointers = [...pointersRef.current.values()];
    if (pointers.length < 2) return;
    const [first, second] = pointers;
    const mid = midpoint(first, second);
    const rect = containerRef.current?.getBoundingClientRect();
    const size = { width: rect?.width ?? 0, height: rect?.height ?? 0 };
    const startViewport = viewportRef.current;
    pinchRef.current = {
      startDistance: distance(first, second),
      startViewport,
      docMid: screenToDoc(mid, startViewport, size),
    };
    panRef.current = null;
    setPinching(true);
    onPinchStartRef.current?.();
  }, [containerRef]);

  useEffect(() => {
    const node = containerRef.current;
    if (!node || !enabled) return;

    const onWheel = (event: WheelEvent) => {
      const { x, y, size } = localPoint(event);
      event.preventDefault();
      if (!size.width || !size.height) return;
      const current = viewportRef.current;
      if (event.ctrlKey || event.metaKey) {
        const nextZoom = current.zoom * Math.exp(-event.deltaY * 0.002);
        onChangeRef.current(clampViewport(zoomAt(current, { x, y }, nextZoom, size)));
      } else {
        onChangeRef.current(clampViewport({
          zoom: current.zoom,
          panX: current.panX - event.deltaX,
          panY: current.panY - event.deltaY,
        }));
      }
    };

    const onPointerDown = (event: PointerEvent) => {
      const pointers = pointersRef.current;
      if (event.pointerType === "touch" || event.pointerType === "pen" || pointers.size > 0) {
        pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      }
      if (pointers.size === 2) {
        beginPinch();
        return;
      }
      if (pointers.size > 2) return;
      if (pinchRef.current) return;
      if (event.button === 1 || (event.button === 0 && panEnabledRef.current)) {
        panRef.current = {
          pointerId: event.pointerId,
          startX: event.clientX,
          startY: event.clientY,
          startViewport: viewportRef.current,
        };
        event.preventDefault();
      }
    };

    const onPointerMove = (event: PointerEvent) => {
      if (pointersRef.current.has(event.pointerId)) {
        pointersRef.current.set(event.pointerId, { x: event.clientX, y: event.clientY });
      }
      const pinch = pinchRef.current;
      if (pinch) {
        const pointers = [...pointersRef.current.values()];
        if (pointers.length < 2) return;
        const [first, second] = pointers;
        const mid = midpoint(first, second);
        const rect = containerRef.current?.getBoundingClientRect();
        const size = { width: rect?.width ?? 0, height: rect?.height ?? 0 };
        if (!size.width || !size.height) return;
        const zoom = clampZoom(pinch.startViewport.zoom * distance(first, second) / pinch.startDistance);
        onChangeRef.current(clampViewport({
          zoom,
          panX: mid.x - size.width / 2 - (pinch.docMid.x - ARTBOARD_WIDTH / 2) * zoom,
          panY: mid.y - size.height / 2 - (pinch.docMid.y - ARTBOARD_HEIGHT / 2) * zoom,
        }));
        event.preventDefault();
        return;
      }
      const pan = panRef.current;
      if (!pan || pan.pointerId !== event.pointerId) return;
      onChangeRef.current(clampViewport({
        ...pan.startViewport,
        panX: pan.startViewport.panX + event.clientX - pan.startX,
        panY: pan.startViewport.panY + event.clientY - pan.startY,
      }));
      event.preventDefault();
    };

    const endPointer = (event: PointerEvent) => {
      pointersRef.current.delete(event.pointerId);
      if (panRef.current?.pointerId === event.pointerId) panRef.current = null;
      if (pinchRef.current) {
        pinchRef.current = null;
        setPinching(false);
      }
    };

    node.addEventListener("wheel", onWheel, { passive: false });
    node.addEventListener("pointerdown", onPointerDown, { capture: true });
    node.addEventListener("pointermove", onPointerMove, { capture: true });
    node.addEventListener("pointerup", endPointer, { capture: true });
    node.addEventListener("pointercancel", endPointer, { capture: true });
    return () => {
      node.removeEventListener("wheel", onWheel);
      node.removeEventListener("pointerdown", onPointerDown, { capture: true });
      node.removeEventListener("pointermove", onPointerMove, { capture: true });
      node.removeEventListener("pointerup", endPointer, { capture: true });
      node.removeEventListener("pointercancel", endPointer, { capture: true });
    };
  }, [beginPinch, containerRef, enabled, localPoint]);

  return {
    isPinching: () => pinchRef.current !== null,
    isPanning: () => panRef.current !== null,
    pinching,
  };
}
