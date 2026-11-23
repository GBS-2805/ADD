import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { useEditorDocument, useEditorStore, useTransientTransforms } from "../../editor-core/store";
import type { EditorDocument } from "../../editor-core/types";
import type { Viewport } from "../types";
import type { Rect } from "../model/geometry";
import type { SnapGuide } from "../model/snapping";
import { ArtboardView } from "./ArtboardView";
import { CanvasRulers } from "./CanvasRulers";
import { SelectionOverlay } from "./SelectionOverlay";
import { usePointerSession } from "./usePointerSession";
import { useViewportGestures } from "./useViewportGestures";
import { useDeviceLayout } from "../useDeviceLayout";
import { fitViewport, screenToDoc, zoomAt, clampZoom, type Point, type Size } from "../model/viewport";
import { useRenderSpan } from "../perfProfile";
import { useRevealLayerCount } from "../useRevealLayerCount";

const NO_RULERS = new Set(["phone", "cover", "fold"]);

export function Canvas() {
  useRenderSpan("canvas");
  const store = useEditorStore();
  const { doc } = useEditorDocument();
  const layerIds = useMemo(() => doc.layers.map((layer) => layer.id), [doc.layers]);
  const layerLimit = useRevealLayerCount(layerIds);
  const { viewport, tool, snapEnabled, rulersVisible } = store.state;
  const layout = useDeviceLayout();
  const containerRef = useRef<HTMLDivElement>(null);
  const didFit = useRef(false);
  const [canvasSize, setCanvasSize] = useState<Size>({ width: 0, height: 0 });
  const [spacePressed, setSpacePressed] = useState(false);

  const session = usePointerSession({
    layers: doc.layers,
    selectedIds: doc.selectedLayerIds,
    zoom: viewport.zoom,
    tool,
    snapEnabled,
    isCoarse: layout.isCoarse,
    setTransientTransforms: store.setTransientTransforms,
    setLayerTransforms: store.setLayerTransforms,
    selectLayers: store.selectLayers,
    clearSelection: store.clearSelection,
  });

  const gestures = useViewportGestures({
    containerRef,
    viewport,
    onChange: store.setViewport,
    panEnabled: spacePressed || tool === "hand",
    onPinchStart: session.cancel,
  });

  const measure = useCallback(() => {
    const element = containerRef.current;
    if (!element) return;
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    const size = { width: rect.width, height: rect.height };
    setCanvasSize((current) => current.width === size.width && current.height === size.height ? current : size);
    store.setCanvasSize(size);
    if (!didFit.current) {
      didFit.current = true;
      const padding = layout.mode === "cover" ? 16 : layout.mode === "fold" ? 24 : layout.mode === "phone" ? 48 : 96;
      store.setViewport(fitViewport(size, padding));
    }
  }, [layout.mode, store.setCanvasSize, store.setViewport]);

  useEffect(() => {
    measure();
    const element = containerRef.current;
    const observer = typeof ResizeObserver === "function" ? new ResizeObserver(measure) : null;
    if (element) observer?.observe(element);
    window.addEventListener("resize", measure);
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, [measure]);

  useEffect(() => {
    const isTyping = (target: EventTarget | null) => {
      const element = target instanceof HTMLElement ? target : null;
      return Boolean(element?.closest("input, textarea, select, [contenteditable='true']"));
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (isTyping(event.target)) return;
      if (event.key === " " || event.code === "Space") {
        event.preventDefault();
        setSpacePressed(true);
      }
      if (event.key === "Escape") session.cancel();
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (event.key === " " || event.code === "Space") setSpacePressed(false);
    };
    const onBlur = () => setSpacePressed(false);
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    window.addEventListener("blur", onBlur);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("blur", onBlur);
    };
  }, [session]);

  const pointFromEvent = (event: ReactPointerEvent<HTMLElement>): Point => {
    const rect = containerRef.current?.getBoundingClientRect();
    return screenToDoc(
      { x: event.clientX - (rect?.left ?? 0), y: event.clientY - (rect?.top ?? 0) },
      viewport,
      canvasSize,
    );
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (gestures.isPinching() || event.button === 1) return;
    if (spacePressed || tool === "hand") return;
    if (tool === "zoom") {
      const rect = containerRef.current?.getBoundingClientRect();
      const pointer = { x: event.clientX - (rect?.left ?? 0), y: event.clientY - (rect?.top ?? 0) };
      store.setViewport(zoomAt(viewport, pointer, clampZoom(viewport.zoom * (event.altKey ? 0.8 : 1.25)), canvasSize));
      return;
    }
    session.onPointerDown(event, pointFromEvent(event));
  };

  const showRulers = rulersVisible && !NO_RULERS.has(layout.mode) && layout.width > 0 && layout.height > 0;
  const handleLayerSelect = useCallback(
    (id: string, event: ReactMouseEvent<HTMLElement> | ReactKeyboardEvent<HTMLElement>) => {
      if (event.type !== "keydown") return;
      const mode = event.shiftKey ? "add" : event.metaKey || event.ctrlKey ? "toggle" : "replace";
      store.selectLayers([id], mode);
    },
    [store.selectLayers],
  );

  return (
    <div
      ref={containerRef}
      className="ed-canvas"
      data-testid="canvas"
      data-zoom={viewport.zoom}
      data-tool={tool}
      role="region"
      aria-label="Artboard canvas"
      tabIndex={0}
      style={{ touchAction: "none", overscrollBehavior: "contain" }}
      onPointerDown={onPointerDown}
      onPointerMove={(event) => session.onPointerMove(event, pointFromEvent(event))}
      onPointerUp={(event) => session.onPointerUp(event, pointFromEvent(event))}
      onPointerCancel={session.onPointerCancel}
    >
      <div className="ed-canvas-crosshair" aria-hidden="true">
        <i /><i />
      </div>
      {showRulers && canvasSize.width > 0 ? (
        <CanvasRulers viewport={viewport} width={canvasSize.width} height={canvasSize.height} />
      ) : null}
      <ArtboardStage
        doc={doc}
        viewport={viewport}
        guides={session.guides}
        marquee={session.marquee}
        layerLimit={layerLimit}
        onLayerSelect={handleLayerSelect}
      />
      <div className="ed-canvas-caption">
        <span>765 × 1000</span>
        <span>{Math.round(viewport.zoom * 100)}%</span>
      </div>
    </div>
  );
}

interface ArtboardStageProps {
  doc: EditorDocument;
  viewport: Viewport;
  guides: SnapGuide[];
  marquee: Rect | null;
  layerLimit: number;
  onLayerSelect: (id: string, event: ReactMouseEvent<HTMLElement> | ReactKeyboardEvent<HTMLElement>) => void;
}

/** Subscribes to drag transforms so pointer moves leave the editor shell alone. */
function ArtboardStage({ doc, viewport, guides, marquee, layerLimit, onLayerSelect }: ArtboardStageProps) {
  const transientTransforms = useTransientTransforms();
  const stageStyle = {
    "--zoom": viewport.zoom,
    transform: `translate(calc(-50% + ${viewport.panX}px), calc(-50% + ${viewport.panY}px)) scale(${viewport.zoom})`,
  } as CSSProperties;

  return (
    <div
      className="ed-artboard-stage"
      data-testid="artboard-stage"
      style={stageStyle}
    >
      <ArtboardView
        doc={doc}
        layerLimit={layerLimit}
        onLayerSelect={onLayerSelect}
        testId="editor-artboard"
      />
      <SelectionOverlay
        layers={doc.layers.slice(0, layerLimit)}
        selectedIds={doc.selectedLayerIds}
        transforms={transientTransforms ?? undefined}
        guides={guides}
        marquee={marquee}
      />
    </div>
  );
}
