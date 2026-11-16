import { useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { useTwinStore } from "../twin/store";
import { useShortLandscape } from "../twin/useShortLandscape";
import { EffectSurface } from "../twin/ShaderView";
import { textFontStack, type TwinLayer } from "../twin/types";
import { fromLegacy, toLegacy } from "./legacy";

function ArtLayer({ layer, index, readOnly = false, shortLandscape = false }: { layer: TwinLayer; index: number; readOnly?: boolean; shortLandscape?: boolean }) {
  const store = useTwinStore();
  const { transform } = layer;
  const dragRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    originX: number;
    originY: number;
    moved: boolean;
  } | null>(null);
  const [dragging, setDragging] = useState(false);
  const [draft, setDraft] = useState<{ x: number; y: number } | null>(null);
  const draftRef = useRef<{ x: number; y: number } | null>(null);
  const movedRef = useRef(false);
  const position = draft ?? transform;
  const scale = transform.scaleX === transform.scaleY ? `${transform.scaleX}` : `${transform.scaleX}, ${transform.scaleY}`;
  const fontStack = textFontStack(layer.text?.font);
  const selected = !readOnly && store.state.doc.selectedLayerIds.includes(layer.id);
  const pulse = store.state.effectPulse;
  const pulseNonce = selected && pulse?.target === "layer" && pulse.layerId === layer.id ? pulse.nonce : 0;
  const layoutShift = shortLandscape
    ? layer.id === "shape-star"
      ? { x: -31, y: -38 }
      : layer.id === "shape-orbit"
        ? { x: 27, y: -28 }
        : { x: 0, y: 0 }
    : store.state.layout === "expanded"
      ? layer.id === "shape-star"
        ? { x: -30, y: 62 }
        : layer.id === "shape-orbit"
          ? { x: 36, y: 74 }
          : { x: 0, y: 0 }
      : { x: 0, y: 0 };
  const style = {
    zIndex: index + 1,
    opacity: layer.opacity,
    mixBlendMode: layer.blend,
    transform: `translate(calc(-50% + ${toLegacy(position.x) + layoutShift.x}px), calc(-50% + ${toLegacy(position.y) + layoutShift.y}px)) scale(${scale}) rotate(${transform.rotation}deg)`,
    display: layer.visible ? undefined : "none",
  } as const;

  const beginDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (readOnly) return;
    if (event.pointerType === "mouse" && event.button !== 0) return;
    movedRef.current = false;
    draftRef.current = null;
    setDraft(null);
    store.selectLayer(layer.id);
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: transform.x,
      originY: transform.y,
      moved: false,
    };
    setDragging(true);
  };

  const moveDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    if (!drag.moved && Math.hypot(dx, dy) < 2) return;
    drag.moved = true;
    movedRef.current = true;
    const next = { x: drag.originX + fromLegacy(dx), y: drag.originY + fromLegacy(dy) };
    draftRef.current = next;
    setDraft(next);
  };

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    if (draftRef.current) store.setLayerTransform(layer.id, draftRef.current);
    dragRef.current = null;
    draftRef.current = null;
    setDraft(null);
    setDragging(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  return (
    <div
      className={`art-layer art-layer-${layer.kind} ${selected ? "is-selected" : ""} ${dragging ? "is-dragging" : ""} ${readOnly ? "is-readonly" : ""}`}
      style={style}
      onPointerDown={readOnly ? undefined : beginDrag}
      onPointerMove={readOnly ? undefined : moveDrag}
      onPointerUp={readOnly ? undefined : endDrag}
      onPointerCancel={readOnly ? undefined : endDrag}
      onClick={readOnly ? undefined : (event) => {
        event.stopPropagation();
        store.selectLayer(layer.id);
        if (!movedRef.current) store.openInspector();
      }}
      data-testid={readOnly ? undefined : `art-layer-${layer.id}`}
      data-layer-id={layer.id}
      data-dragging={dragging ? "true" : undefined}
      role={readOnly ? undefined : "button"}
      tabIndex={readOnly ? undefined : 0}
      onKeyDown={readOnly ? undefined : (event) => {
        if (event.key !== "Enter" && event.key !== " ") return;
        event.preventDefault();
        store.selectLayer(layer.id);
        store.openInspector();
      }}
      aria-label={readOnly ? undefined : `Select ${layer.name}`}
    >
      <div className="layer-body">
        {layer.kind === "text" ? (
          <span
            className="sample-type"
            aria-label={layer.id === "type-open-studio" ? "Open Studio headline" : `${layer.name} text`}
            style={{
              color: layer.fill ?? "var(--ink)",
              fontFamily: fontStack,
              fontSize: `${toLegacy(layer.text?.size ?? 68 * (765 / 338))}px`,
              textAlign: layer.text?.align ?? "center",
              alignItems: layer.text?.align === "left" ? "flex-start" : layer.text?.align === "right" ? "flex-end" : "center",
            }}
          >
            {(layer.text?.content ?? layer.name).split(/\r?\n/).map((line, lineIndex) => (
              <span key={`${layer.id}-line-${lineIndex}`}>{line || "\u00a0"}</span>
            ))}
          </span>
        ) : null}
        {layer.kind === "shape" && (layer.shape?.variant ?? (layer.id.includes("orbit") ? "orbit" : layer.id.includes("star") ? "star" : "block")) === "star" ? (
          <svg className="sample-star" viewBox="0 0 100 100" aria-hidden="true" style={{ fill: layer.fill ?? "var(--ink)" }}>
            <path d="M50 4 61 34 93 35 68 55 77 86 50 68 23 86 32 55 7 35 39 34Z" />
          </svg>
        ) : null}
        {layer.kind === "shape" && (layer.shape?.variant ?? (layer.id.includes("orbit") ? "orbit" : layer.id.includes("star") ? "star" : "block")) === "orbit" ? (
          <span className="sample-orbit" aria-hidden="true" style={{ borderColor: layer.fill ?? "var(--ink)" }}><i /></span>
        ) : null}
        {layer.kind === "shape" && (layer.shape?.variant ?? (layer.id.includes("orbit") ? "orbit" : layer.id.includes("star") ? "star" : "block")) === "block" ? (
          <span className="sample-block" aria-hidden="true" style={{ background: layer.fill ?? "var(--ink)" }} />
        ) : null}
        {layer.kind === "image" && layer.image ? (
          <img className="sample-image" src={layer.image.src} alt={layer.name} draggable={false} />
        ) : null}
      </div>
      {layer.effect ? (
        <span className="layer-effect" data-testid={readOnly ? undefined : `layer-effect-${layer.id}`} aria-hidden="true">
          <EffectSurface effect={layer.effect} />
        </span>
      ) : null}
      {selected ? <span className="selection-pulse" key={`${layer.id}-${pulseNonce}`} aria-hidden="true" /> : null}
      <span className="layer-tag">{layer.name}</span>
    </div>
  );
}

export function Artboard({
  readOnly = false,
  testId = "artboard",
  shortLandscape = false,
}: {
  readOnly?: boolean;
  testId?: string;
  shortLandscape?: boolean;
}) {
  const store = useTwinStore();
  const { doc } = store.state;
  const pulse = store.state.effectPulse;
  const artboardPulse = !readOnly && pulse?.target === "artboard" ? pulse.nonce : null;

  return (
    <div
      className={`artboard ${readOnly ? "is-readonly" : ""} ${shortLandscape ? "is-short-landscape" : ""}`}
      data-testid={testId}
      onClick={readOnly ? undefined : () => {
        const primary = doc.selectedLayerIds[0];
        if (primary) store.selectLayer(primary);
      }}
      role="presentation"
    >
      <div className="artboard-ground">
        <EffectSurface effect={doc.background} />
      </div>
      <div className="artboard-grain" aria-hidden="true" />
      <div className="artboard-layers">
        {doc.layers.map((layer, index) => <ArtLayer key={layer.id} layer={layer} index={index} readOnly={readOnly} shortLandscape={shortLandscape} />)}
      </div>
      {artboardPulse ? <span className="artboard-pulse" key={artboardPulse} aria-hidden="true" /> : null}
      <div className="artboard-meta">
        <span>VOL. 01</span>
        <span>PRINT / 2026</span>
      </div>
    </div>
  );
}

export function Stage({ inert = false }: { inert?: boolean }) {
  const store = useTwinStore();
  const { doc } = store.state;
  const shortLandscape = useShortLandscape();

  return (
    <div className="stage" data-testid="stage" inert={inert}>
      <div className="stage-topline">
        <span>BMO / OPEN STUDIO</span>
        <span className="stage-live"><i /> Live</span>
      </div>
      <div className="artboard-wrap">
        <Artboard shortLandscape={shortLandscape} />
      </div>
      <div className="stage-footline">
        <span>{doc.layers.length} layers</span>
        <span>{doc.selectedLayerIds.length > 0 ? "Drag layer to move" : "Tap a layer"}</span>
      </div>
    </div>
  );
}
