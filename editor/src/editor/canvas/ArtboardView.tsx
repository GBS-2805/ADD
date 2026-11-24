import { memo, useRef, type CSSProperties, type KeyboardEvent, type MouseEvent } from "react";
import { EffectSurface, surfacesShader } from "../../editor-core/ShaderView";
import type { LayerTransform, EditorDocument, EditorEffect } from "../../editor-core/types";
import { ARTBOARD_HEIGHT, ARTBOARD_WIDTH } from "../model/geometry";
import { LayerView } from "./LayerView";

export interface ArtboardViewProps {
  doc: EditorDocument;
  transforms?: Record<string, LayerTransform>;
  selectedIds?: string[];
  readOnly?: boolean;
  testId?: string;
  /** Preview/print surfaces must not duplicate the editor's DOM test ids. */
  exposeLayerIds?: boolean;
  className?: string;
  onLayerSelect?: (id: string, event: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>) => void;
  /** Staged project reveals may show only the first N layers for a frame or two. */
  layerLimit?: number;
}

/**
 * The artboard background, with its WebGL context kept across a document swap.
 *
 * Opening a project replaces the document in one commit. Unmounting the previous
 * background there runs `@paper-design/shaders`' dispose, which forces the GL
 * context to be lost inside the open handler; the 20-layer open case measured that
 * as a long task. A document without a shader background therefore parks the last
 * shader host instead: still mounted, hidden with `display: none` (so the library's
 * intersection observer pauses it), and reused when a later document paints the
 * same shader again. A different shader still replaces it, because that is a new
 * program rather than a swap with nothing to draw.
 */
function ArtboardBackdrop({ effect }: { effect: EditorEffect | null }) {
  const parkedRef = useRef<EditorEffect | null>(null);
  const live = surfacesShader(effect) ? effect : null;
  if (live) parkedRef.current = live;
  const parked = parkedRef.current;
  return (
    <>
      {parked ? (
        <div className="artboard-backdrop" style={{ display: live ? "contents" : "none" }} aria-hidden="true">
          <EffectSurface effect={parked} />
        </div>
      ) : null}
      {live === null && effect ? <EffectSurface effect={effect} /> : null}
    </>
  );
}

export const ArtboardView = memo(function ArtboardView({
  doc,
  transforms,
  selectedIds,
  readOnly = false,
  testId = "artboard",
  exposeLayerIds = true,
  className = "",
  onLayerSelect,
  layerLimit,
}: ArtboardViewProps) {
  const selection = selectedIds ?? doc.selectedLayerIds;
  const layers = layerLimit === undefined ? doc.layers : doc.layers.slice(0, layerLimit);
  const background = doc.background;
  const backgroundPreset = background && "preset" in background ? background.preset : "";
  const style: CSSProperties = {
    width: ARTBOARD_WIDTH,
    height: ARTBOARD_HEIGHT,
  };

  return (
    <div
      className={`artboard ed-artboard ${readOnly ? "is-readonly" : ""} ${className}`.trim()}
      style={style}
      data-testid={testId}
      data-artboard={readOnly ? "readonly" : "editable"}
      data-artboard-width={ARTBOARD_WIDTH}
      data-artboard-height={ARTBOARD_HEIGHT}
      role="presentation"
    >
      <div
        className="artboard-ground"
        data-effect={background?.kind ?? "none"}
        data-effect-preset={backgroundPreset}
      >
        <ArtboardBackdrop effect={doc.background} />
      </div>
      <div className="artboard-grain" aria-hidden="true" />
      <div className="ed-layer-stack">
        {layers.map((layer) => (
          <LayerView
            key={layer.id}
            layer={layer}
            transform={transforms?.[layer.id] ?? layer.transform}
            selected={selection.includes(layer.id)}
            readOnly={readOnly}
            testId={exposeLayerIds ? undefined : null}
            layerId={exposeLayerIds ? undefined : null}
            onLayerSelect={onLayerSelect}
          />
        ))}
      </div>
      <div className="artboard-meta" aria-hidden="true">
        <span>VOL. 01</span>
        <span>PRINT / 2026</span>
      </div>
    </div>
  );
});
