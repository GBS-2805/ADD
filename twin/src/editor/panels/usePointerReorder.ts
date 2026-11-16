import { useCallback, useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from "react";

const THRESHOLD = 8;
const EDGE = 38;
const SCROLL_STEP = 14;

export interface UsePointerReorderOptions {
  rowId: string;
  onReorder: (fromId: string, toId: string) => void;
  resolveTarget?: (point: { x: number; y: number }) => string | null;
  threshold?: number;
}

type ActiveGesture = {
  pointerId: number;
  startX: number;
  startY: number;
  moved: boolean;
  cancelled: boolean;
  targetId: string | null;
  targetEl: HTMLElement | null;
};

function targetAtPoint(point: { x: number; y: number }): string | null {
  if (typeof document.elementFromPoint !== "function") return null;
  const element = document.elementFromPoint(point.x, point.y);
  return element?.closest<HTMLElement>("[data-layer-row]")?.dataset.layerRow ?? null;
}

/**
 * First-party pointer reorder for coarse pointers. It never relies on HTML5
 * drag events and cancels cleanly on pointercancel or Escape.
 */
export function usePointerReorder({
  rowId,
  onReorder,
  resolveTarget = targetAtPoint,
  threshold = THRESHOLD,
}: UsePointerReorderOptions) {
  const gesture = useRef<ActiveGesture | null>(null);
  const [dragging, setDragging] = useState(false);
  const [activeTargetId, setActiveTargetId] = useState<string | null>(null);

  const clear = useCallback(() => {
    const active = gesture.current;
    if (active?.targetEl) {
      try {
        active.targetEl.releasePointerCapture(active.pointerId);
      } catch {
        // Pointer capture can already be gone after cancellation.
      }
    }
    gesture.current = null;
    setDragging(false);
    setActiveTargetId(null);
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape" || !gesture.current) return;
      event.preventDefault();
      gesture.current.cancelled = true;
      clear();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [clear]);

  const onPointerDown = useCallback((event: ReactPointerEvent<HTMLElement>) => {
    if (event.pointerType === "mouse" && event.button !== 0) return;
    const targetEl = event.currentTarget;
    try {
      targetEl.setPointerCapture(event.pointerId);
    } catch {
      // jsdom and some embedded browsers do not implement pointer capture.
    }
    gesture.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      moved: false,
      cancelled: false,
      targetId: null,
      targetEl,
    };
    setDragging(false);
    setActiveTargetId(null);
  }, []);

  const onPointerMove = useCallback((event: ReactPointerEvent<HTMLElement>) => {
    const active = gesture.current;
    if (!active || event.pointerId !== active.pointerId || active.cancelled) return;
    const dx = event.clientX - active.startX;
    const dy = event.clientY - active.startY;
    if (!active.moved && Math.hypot(dx, dy) < threshold) return;
    active.moved = true;
    setDragging(true);
    const target = resolveTarget({ x: event.clientX, y: event.clientY });
    active.targetId = target;
    setActiveTargetId(target);

    const scroller = active.targetEl?.closest<HTMLElement>("[data-layer-scroll]");
    if (scroller) {
      const rect = scroller.getBoundingClientRect();
      if (event.clientY < rect.top + EDGE) scroller.scrollBy({ top: -SCROLL_STEP });
      else if (event.clientY > rect.bottom - EDGE) scroller.scrollBy({ top: SCROLL_STEP });
    }
  }, [resolveTarget, threshold]);

  const onPointerUp = useCallback((event: ReactPointerEvent<HTMLElement>) => {
    const active = gesture.current;
    if (!active || event.pointerId !== active.pointerId) return;
    const target = active.targetId;
    const shouldReorder = active.moved && !active.cancelled && target !== null && target !== rowId;
    clear();
    if (shouldReorder) onReorder(rowId, target);
  }, [clear, onReorder, rowId]);

  const onPointerCancel = useCallback(() => {
    if (gesture.current) gesture.current.cancelled = true;
    clear();
  }, [clear]);

  return {
    dragging,
    activeTargetId,
    gripProps: {
      onPointerDown,
      onPointerMove,
      onPointerUp,
      onPointerCancel,
      style: { touchAction: "none" } as CSSProperties,
      "data-dragging": dragging ? "true" : "false",
    },
  };
}
