import { useCallback, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import {
  changeSizeAtReference,
  layerBounds,
  layerFrame,
  moveTransform,
  resizeTransform,
  rotateTransform,
  selectionBounds,
  ARTBOARD_HEIGHT,
  ARTBOARD_WIDTH,
  type Point,
  type Rect,
  type ResizeHandle,
  type ReferencePoint,
} from "../model/geometry";
import { snapAnchors, snapMove, snapResizeRect, type SnapGuide } from "../model/snapping";
import { nextSelection, type SelectionMode } from "../model/selection";
import type { LayerTransform, EditorLayer } from "../../editor-core/types";
import type { EditorTool } from "../types";

type SessionKind = "move" | "marquee" | "resize" | "rotate";

interface Session {
  pointerId: number;
  kind: SessionKind;
  startDoc: Point;
  startTransforms: Record<string, LayerTransform>;
  startBounds: Rect | null;
  startLayer: EditorLayer | null;
  handle: ResizeHandle | null;
  moved: boolean;
  transient: Record<string, LayerTransform> | null;
}

export interface UsePointerSessionOptions {
  layers: EditorLayer[];
  selectedIds: string[];
  zoom: number;
  tool: EditorTool;
  snapEnabled: boolean;
  isCoarse: boolean;
  setTransientTransforms: (updates: Record<string, LayerTransform> | null) => void;
  setLayerTransforms: (updates: Record<string, LayerTransform>) => void;
  selectLayers: (ids: string[], mode?: SelectionMode) => void;
  clearSelection: () => void;
}

export interface PointerSession {
  onPointerDown: (event: ReactPointerEvent<HTMLElement>, doc: Point) => void;
  onPointerMove: (event: ReactPointerEvent<HTMLElement>, doc: Point) => void;
  onPointerUp: (event: ReactPointerEvent<HTMLElement>, doc: Point) => void;
  onPointerCancel: () => void;
  cancel: () => void;
  guides: SnapGuide[];
  marquee: Rect | null;
}

const OPPOSITE: Record<ResizeHandle, ReferencePoint> = {
  nw: "se",
  n: "s",
  ne: "sw",
  e: "w",
  se: "nw",
  s: "n",
  sw: "ne",
  w: "e",
};

function intersects(a: Rect, b: Rect): boolean {
  return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
}

export function usePointerSession(options: UsePointerSessionOptions): PointerSession {
  const optionsRef = useRef(options);
  optionsRef.current = options;
  const sessionRef = useRef<Session | null>(null);
  const [guides, setGuides] = useState<SnapGuide[]>([]);
  const [marquee, setMarquee] = useState<Rect | null>(null);

  const cancel = useCallback(() => {
    sessionRef.current = null;
    setGuides([]);
    setMarquee(null);
    optionsRef.current.setTransientTransforms(null);
  }, []);

  const onPointerDown = useCallback((event: ReactPointerEvent<HTMLElement>, doc: Point) => {
    if (event.button !== 0) return;
    if (sessionRef.current) {
      cancel();
      return;
    }
    const current = optionsRef.current;
    if (current.tool !== "move") return;
    const target = event.target instanceof Element ? event.target : null;
    const handleElement = target?.closest<HTMLElement>("[data-handle]");
    const layerElement = target?.closest<HTMLElement>("[data-layer-id]");
    const layerId = layerElement?.dataset.layerId;
    const selectedIds = current.selectedIds;
    const selectedLayer = selectedIds.length === 1
      ? current.layers.find((layer) => layer.id === selectedIds[0]) ?? null
      : null;

    let kind: SessionKind;
    let startLayer: EditorLayer | null = null;
    let handle: ResizeHandle | null = null;
    let startIds = selectedIds;

    if (handleElement && selectedLayer) {
      const rawHandle = handleElement.dataset.handle ?? "";
      if (rawHandle === "rotate") {
        kind = "rotate";
      } else if (["nw", "n", "ne", "e", "se", "s", "sw", "w"].includes(rawHandle)) {
        kind = "resize";
        handle = rawHandle as ResizeHandle;
      } else {
        return;
      }
      startLayer = selectedLayer;
    } else if (layerId) {
      const layer = current.layers.find((item) => item.id === layerId);
      if (!layer || !layer.visible) return;
      if (!selectedIds.includes(layerId)) {
        const mode: SelectionMode = event.shiftKey ? "add" : event.metaKey || event.ctrlKey ? "toggle" : "replace";
        startIds = nextSelection(selectedIds, layerId, mode);
        current.selectLayers([layerId], mode);
        if (!startIds.includes(layerId)) return;
      }
      kind = "move";
    } else {
      kind = "marquee";
      startIds = [];
    }

    const startTransforms: Record<string, LayerTransform> = {};
    for (const id of startIds) {
      const layer = current.layers.find((item) => item.id === id);
      if (layer) startTransforms[id] = { ...layer.transform };
    }
    const startBounds = kind === "move" ? selectionBounds(current.layers, startIds) : null;
    if (kind === "move" && !startBounds) return;

    if (kind === "resize" || kind === "rotate") {
      const id = startLayer?.id ?? "";
      if (!id) return;
      startIds = [id];
      startTransforms[id] = { ...(startLayer?.transform as LayerTransform) };
    }

    sessionRef.current = {
      pointerId: event.pointerId,
      kind,
      startDoc: doc,
      startTransforms,
      startBounds,
      startLayer,
      handle,
      moved: false,
      transient: null,
    };
    setGuides([]);
    setMarquee(null);
    current.setTransientTransforms(null);
    try {
      event.currentTarget.setPointerCapture(event.pointerId);
    } catch {
      // Pointer capture is a progressive enhancement; window events still end the gesture.
    }
    event.preventDefault();
  }, [cancel]);

  const onPointerMove = useCallback((event: ReactPointerEvent<HTMLElement>, doc: Point) => {
    const session = sessionRef.current;
    if (!session || session.pointerId !== event.pointerId) return;
    const current = optionsRef.current;
    const delta = { x: doc.x - session.startDoc.x, y: doc.y - session.startDoc.y };
    if (!session.moved && Math.hypot(delta.x, delta.y) * current.zoom >= 1) session.moved = true;

    if (session.kind === "marquee") {
      if (!session.moved) return;
      const rect = {
        x: Math.min(session.startDoc.x, doc.x),
        y: Math.min(session.startDoc.y, doc.y),
        width: Math.abs(doc.x - session.startDoc.x),
        height: Math.abs(doc.y - session.startDoc.y),
      };
      session.transient = null;
      setMarquee(rect);
      return;
    }

    if (session.kind === "move") {
      if (!session.moved) return;
      let dx = delta.x;
      let dy = delta.y;
      if (event.shiftKey) {
        if (Math.abs(dx) >= Math.abs(dy)) dy = 0;
        else dx = 0;
      }
      let nextGuides: SnapGuide[] = [];
      if (current.snapEnabled && !(event.metaKey || event.ctrlKey) && session.startBounds) {
        const threshold = (current.isCoarse ? 10 : 6) / current.zoom;
        const snapped = snapMove(
          session.startBounds,
          { x: dx, y: dy },
          snapAnchors(current.layers, Object.keys(session.startTransforms)),
          threshold,
        );
        dx = snapped.dx;
        dy = snapped.dy;
        nextGuides = snapped.guides;
      }
      const updates: Record<string, LayerTransform> = {};
      for (const [id, transform] of Object.entries(session.startTransforms)) {
        updates[id] = moveTransform(transform, dx, dy);
      }
      session.transient = updates;
      current.setTransientTransforms(updates);
      setGuides(nextGuides);
      return;
    }

    const layer = session.startLayer;
    if (!layer || !session.handle && session.kind === "resize") return;
    const localDoc = { x: doc.x - ARTBOARD_WIDTH / 2, y: doc.y - ARTBOARD_HEIGHT / 2 };
    let next: LayerTransform;
    let nextGuides: SnapGuide[] = [];
    if (session.kind === "resize" && session.handle) {
      const fromCenter = event.altKey;
      const keepAspect = event.shiftKey;
      next = resizeTransform(layer, layer.transform, session.handle, localDoc, { fromCenter, keepAspect });
      if (
        current.snapEnabled &&
        !(event.metaKey || event.ctrlKey) &&
        layer.transform.rotation === 0 &&
        !fromCenter &&
        !keepAspect
      ) {
        const frame = layerFrame(layer, next);
        const rect = {
          x: ARTBOARD_WIDTH / 2 + frame.x - frame.width / 2,
          y: ARTBOARD_HEIGHT / 2 + frame.y - frame.height / 2,
          width: frame.width,
          height: frame.height,
        };
        const snapped = snapResizeRect(
          rect,
          session.handle,
          snapAnchors(current.layers, [layer.id]),
          (current.isCoarse ? 10 : 6) / current.zoom,
        );
        if (snapped.rect.width >= 4 && snapped.rect.height >= 4) {
          next = changeSizeAtReference(
            layer,
            { width: snapped.rect.width, height: snapped.rect.height },
            OPPOSITE[session.handle],
            false,
          );
        }
        nextGuides = snapped.guides;
      }
    } else {
      next = rotateTransform(layer, layer.transform, localDoc, { snap: event.shiftKey });
    }
    if (![next.x, next.y, next.scaleX, next.scaleY, next.rotation].every(Number.isFinite) || next.scaleX < 0.01 || next.scaleY < 0.01) {
      return;
    }
    const updates = { [layer.id]: next };
    session.transient = updates;
    current.setTransientTransforms(updates);
    setGuides(nextGuides);
  }, []);

  const onPointerUp = useCallback((event: ReactPointerEvent<HTMLElement>, doc: Point) => {
    const session = sessionRef.current;
    if (!session || session.pointerId !== event.pointerId) return;
    const current = optionsRef.current;
    try {
      event.currentTarget.releasePointerCapture(event.pointerId);
    } catch {
      // Capture may already be gone after a cancel.
    }

    if (session.kind === "marquee") {
      if (session.moved) {
        const rect = {
          x: Math.min(session.startDoc.x, doc.x),
          y: Math.min(session.startDoc.y, doc.y),
          width: Math.abs(doc.x - session.startDoc.x),
          height: Math.abs(doc.y - session.startDoc.y),
        };
        const ids = current.layers
          .filter((layer) => layer.visible && intersects(layerBounds(layer), rect))
          .map((layer) => layer.id);
        const mode: SelectionMode = event.shiftKey || event.metaKey || event.ctrlKey ? "add" : "replace";
        current.selectLayers(ids, mode);
      } else if (!event.shiftKey && !event.metaKey && !event.ctrlKey) {
        current.clearSelection();
      }
    } else if (session.moved && session.transient) {
      current.setLayerTransforms(session.transient);
    }

    sessionRef.current = null;
    current.setTransientTransforms(null);
    setGuides([]);
    setMarquee(null);
  }, []);

  return {
    onPointerDown,
    onPointerMove,
    onPointerUp,
    onPointerCancel: cancel,
    cancel,
    guides,
    marquee,
  };
}
